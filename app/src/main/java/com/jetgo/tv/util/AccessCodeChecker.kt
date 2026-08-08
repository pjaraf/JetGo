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
    fun checkCodeAndRegisterDevice(projectId: String, code: String, deviceId: String, deviceName: String = ""): AccessCodeResult {
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
                    if (deviceName.isNotBlank()) saveDeviceName(docUrl, deviceId, deviceName)
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

    /** Guarda un nombre legible para este dispositivo (ej. "Samsung TV Box" o "Xiaomi Redmi 10"),
     *  para que el panel admin pueda mostrar cuál es cuál en vez de solo un ID sin sentido. */
    private fun saveDeviceName(docUrl: String, deviceId: String, deviceName: String) {
        try {
            val payload = JSONObject().put(
                "fields", JSONObject().put(
                    "deviceNames", JSONObject().put(
                        "mapValue", JSONObject().put(
                            "fields", JSONObject().put(
                                deviceId, JSONObject().put("stringValue", deviceName)
                            )
                        )
                    )
                )
            )
            val patchUrl = "$docUrl?updateMask.fieldPaths=deviceNames.$deviceId"
            val requestBody = payload.toString().toRequestBody("application/json".toMediaType())
            val patchRequest = Request.Builder().url(patchUrl).patch(requestBody).build()
            client.newCall(patchRequest).execute().close()
        } catch (e: Exception) {
            // Silencioso: si falla, el panel solo mostrará el ID sin nombre para este dispositivo
        }
    }

    /**
     * Avisa "este dispositivo está usando la app AHORA MISMO", guardando la hora actual en
     * Firestore (deviceActivity.{deviceId}). El panel admin usa esto para mostrar qué
     * dispositivos están en línea en tiempo real. Se llama a esto cada cierto tiempo mientras
     * la app está abierta; si falla, no afecta nada de la reproducción (es solo informativo).
     */
    fun sendHeartbeat(projectId: String, code: String, deviceId: String) {
        if (projectId.isBlank() || code.isBlank() || deviceId.isBlank()) return
        try {
            val normalizedCode = code.trim().uppercase()
            val docPath = "projects/$projectId/databases/(default)/documents/access_codes/$normalizedCode"
            val docUrl = "https://firestore.googleapis.com/v1/$docPath"

            val payload = JSONObject().put(
                "fields", JSONObject().put(
                    "deviceActivity", JSONObject().put(
                        "mapValue", JSONObject().put(
                            "fields", JSONObject().put(
                                deviceId, JSONObject().put("timestampValue", nowIsoUtc())
                            )
                        )
                    )
                )
            )
            val patchUrl = "$docUrl?updateMask.fieldPaths=deviceActivity.$deviceId"
            val requestBody = payload.toString().toRequestBody("application/json".toMediaType())
            val patchRequest = Request.Builder().url(patchUrl).patch(requestBody).build()
            client.newCall(patchRequest).execute().close()
        } catch (e: Exception) {
            // Silencioso: si falla el aviso de "estoy en línea", no afecta nada más
        }
    }

    private fun nowIsoUtc(): String {
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US)
        sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
        return sdf.format(java.util.Date())
    }
}
