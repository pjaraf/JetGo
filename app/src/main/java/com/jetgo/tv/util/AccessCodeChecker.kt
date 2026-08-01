package com.jetgo.tv.util

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** Resultado de validar un código: si es válido, trae también las credenciales del servidor
 *  que el administrador cargó para ese código, para conectar la app automáticamente. */
data class AccessCodeResult(
    val valid: Boolean,
    val mode: String? = null,      // "xtream" o "m3u"
    val host: String? = null,
    val username: String? = null,
    val password: String? = null,
    val m3uUrl: String? = null,
    val deviceLimitReached: Boolean = false,
    val clientName: String? = null,
    val deviceCount: Int = 0,
    val maxDevices: Int = 3
)

private const val MAX_DEVICES_PER_CODE = 3

/**
 * Valida códigos de acceso contra Firestore usando su API REST pública (sin el SDK de Firebase,
 * para no agregar peso ni dependencias nativas extra — funciona incluso en TV Box sin
 * Google Play Services).
 *
 * Requiere que en Firestore exista una colección "access_codes" donde cada documento tiene
 * como ID el código en sí, con campos: active (bool), mode/host/username/password/m3uUrl (string),
 * y deviceIds (array de string) para llevar el control de máximo 3 dispositivos por código.
 */
object AccessCodeChecker {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    /**
     * Valida el código y, si está activo, registra este dispositivo (hasta un máximo de
     * [MAX_DEVICES_PER_CODE]). Si el dispositivo ya estaba registrado, no hace nada extra.
     */
    fun checkCodeAndRegisterDevice(projectId: String, code: String, deviceId: String): AccessCodeResult {
        if (projectId.isBlank() || code.isBlank()) return AccessCodeResult(valid = false)
        val normalizedCode = code.trim().uppercase()
        val docPath = "projects/$projectId/databases/(default)/documents/access_codes/$normalizedCode"
        val docUrl = "https://firestore.googleapis.com/v1/$docPath"

        return try {
            val getRequest = Request.Builder().url(docUrl).build()
            client.newCall(getRequest).execute().use { response ->
                if (!response.isSuccessful) return AccessCodeResult(valid = false)
                val body = response.body?.string() ?: return AccessCodeResult(valid = false)
                val json = JSONObject(body)
                val fields = json.optJSONObject("fields") ?: return AccessCodeResult(valid = false)

                val active = fields.optJSONObject("active")?.optBoolean("booleanValue", false) ?: false
                if (!active) return AccessCodeResult(valid = false)

                fun textField(name: String): String? =
                    fields.optJSONObject(name)?.optString("stringValue")?.takeIf { it.isNotBlank() }

                val deviceIds = parseDeviceIds(fields)

                if (!deviceIds.contains(deviceId)) {
                    if (deviceIds.size >= MAX_DEVICES_PER_CODE) {
                        return AccessCodeResult(valid = false, deviceLimitReached = true)
                    }
                    // Registra este dispositivo nuevo (no bloquea el acceso si falla la escritura)
                    registerDevice(docUrl, deviceIds + deviceId)
                }

                AccessCodeResult(
                    valid = true,
                    mode = textField("mode") ?: "xtream",
                    host = textField("host"),
                    username = textField("username"),
                    password = textField("password"),
                    m3uUrl = textField("m3uUrl"),
                    clientName = textField("clientName"),
                    deviceCount = if (deviceIds.contains(deviceId)) deviceIds.size else deviceIds.size + 1,
                    maxDevices = MAX_DEVICES_PER_CODE
                )
            }
        } catch (e: Exception) {
            AccessCodeResult(valid = false)
        }
    }

    private fun parseDeviceIds(fields: JSONObject): List<String> {
        val arr = fields.optJSONObject("deviceIds")?.optJSONObject("arrayValue")?.optJSONArray("values") ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i -> arr.optJSONObject(i)?.optString("stringValue") }
    }

    private fun registerDevice(docUrl: String, updatedDeviceIds: List<String>) {
        try {
            val valuesArray = JSONArray()
            updatedDeviceIds.forEach { id -> valuesArray.put(JSONObject().put("stringValue", id)) }
            val payload = JSONObject().put(
                "fields", JSONObject().put(
                    "deviceIds", JSONObject().put(
                        "arrayValue", JSONObject().put("values", valuesArray)
                    )
                )
            )
            val patchUrl = "$docUrl?updateMask.fieldPaths=deviceIds"
            val requestBody = payload.toString().toRequestBody("application/json".toMediaType())
            val patchRequest = Request.Builder().url(patchUrl).patch(requestBody).build()
            client.newCall(patchRequest).execute().close()
        } catch (e: Exception) {
            // Si falla el registro del dispositivo, no bloqueamos el acceso de este intento
        }
    }
}
