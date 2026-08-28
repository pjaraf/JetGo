package com.jetgo.tv.util

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** Una fuente de contenido (un servidor Xtream o una lista M3U) — un código puede tener
 *  hasta 2, para juntar el contenido de ambas en la misma app. */
data class ContentSource(
    val type: String, // "xtream" o "m3u"
    val serverId: String?,
    val host: String? = null,
    val username: String? = null,
    val password: String? = null,
    val m3uUrl: String? = null
)

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
    val expirationDate: String? = null,
    val deviceCount: Int = 0,
    val maxDevices: Int = 3,
    /** Nombres de categorías que el administrador ocultó para este servidor/lista — la app
     *  no debe mostrarlas en ningún listado (vivo, películas ni series). Nota: esto es un
     *  respaldo para códigos viejos; si hay [sources] con serverId, se usa esa info en vivo. */
    val hiddenCategories: List<String> = emptyList(),
    /** Tipos de contenido ocultos por completo (valores: "live", "movie", "series") — el
     *  administrador puede ocultar toda una sección (ej. Vivo) para un servidor/lista. */
    val hiddenTypes: List<String> = emptyList(),
    /** Una o dos fuentes de contenido combinadas para este código (nuevo formato). Si viene
     *  vacío, se usa el formato viejo (host/username/password/m3uUrl sueltos, una sola fuente). */
    val sources: List<ContentSource> = emptyList()
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
                    expirationDate = textField("expirationDate"),
                    deviceCount = if (deviceIds.contains(deviceId)) deviceIds.size else deviceIds.size + 1,
                    maxDevices = MAX_DEVICES_PER_CODE,
                    hiddenCategories = parseStringArray(fields, "hiddenCategories"),
                    hiddenTypes = parseStringArray(fields, "hiddenTypes"),
                    sources = parseSources(fields)
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

    private fun parseStringArray(fields: JSONObject, fieldName: String): List<String> {
        val arr = fields.optJSONObject(fieldName)?.optJSONObject("arrayValue")?.optJSONArray("values") ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i -> arr.optJSONObject(i)?.optString("stringValue")?.takeIf { it.isNotBlank() } }
    }

    /** Parsea el campo "sources" (arreglo de objetos) del código, si el código fue creado con
     *  el sistema nuevo de servidores guardados (uno o dos combinados). */
    private fun parseSources(fields: JSONObject): List<ContentSource> {
        val arr = fields.optJSONObject("sources")?.optJSONObject("arrayValue")?.optJSONArray("values") ?: return emptyList()
        val result = mutableListOf<ContentSource>()
        for (i in 0 until arr.length()) {
            val entryFields = arr.optJSONObject(i)?.optJSONObject("mapValue")?.optJSONObject("fields") ?: continue
            fun text(name: String): String? = entryFields.optJSONObject(name)?.optString("stringValue")?.takeIf { it.isNotBlank() }
            result.add(
                ContentSource(
                    type = text("type") ?: "xtream",
                    serverId = text("serverId"),
                    host = text("host"),
                    username = text("username"),
                    password = text("password"),
                    m3uUrl = text("m3uUrl")
                )
            )
        }
        return result
    }

    /** Datos de un servidor/lista guardado, consultados EN VIVO — así, si el administrador
     *  oculta o reactiva una categoría, el cambio se aplica de inmediato a todos los clientes
     *  que usan ese servidor, no solo a los códigos que se generen después. */
    data class ServerLiveConfig(val hiddenCategories: List<String>, val hiddenTypes: List<String>)

    fun fetchServerLiveConfig(projectId: String, serverId: String): ServerLiveConfig {
        if (projectId.isBlank() || serverId.isBlank()) return ServerLiveConfig(emptyList(), emptyList())
        return try {
            val docPath = "projects/$projectId/databases/(default)/documents/servers/$serverId"
            val docUrl = "https://firestore.googleapis.com/v1/$docPath"
            val getRequest = Request.Builder().url(docUrl).build()
            client.newCall(getRequest).execute().use { response ->
                if (!response.isSuccessful) return ServerLiveConfig(emptyList(), emptyList())
                val body = response.body?.string() ?: return ServerLiveConfig(emptyList(), emptyList())
                val fields = JSONObject(body).optJSONObject("fields") ?: return ServerLiveConfig(emptyList(), emptyList())
                ServerLiveConfig(
                    hiddenCategories = parseStringArray(fields, "hiddenCategories"),
                    hiddenTypes = parseStringArray(fields, "hiddenTypes")
                )
            }
        } catch (e: Exception) {
            ServerLiveConfig(emptyList(), emptyList())
        }
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
            val response = client.newCall(patchRequest).execute()
            if (!response.isSuccessful) {
                val errorBody = response.body?.string()
                android.util.Log.e("JetGo_DIAG", "Heartbeat failed: ${response.code} $errorBody")
            }
            response.close()
        } catch (e: Exception) {
            android.util.Log.e("JetGo_DIAG", "Heartbeat exception", e)
        }
    }

    fun sendOffline(projectId: String, code: String, deviceId: String) {
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
                                deviceId, JSONObject().put("timestampValue", "1970-01-01T00:00:00Z")
                            )
                        )
                    )
                )
            )
            val patchUrl = "$docUrl?updateMask.fieldPaths=deviceActivity.$deviceId"
            val requestBody = payload.toString().toRequestBody("application/json".toMediaType())
            val patchRequest = Request.Builder().url(patchUrl).patch(requestBody).build()
            val response = client.newCall(patchRequest).execute()
            if (!response.isSuccessful) {
                val errorBody = response.body?.string()
                android.util.Log.e("JetGo_DIAG", "Offline signal failed: ${response.code} $errorBody")
            }
            response.close()
        } catch (e: Exception) {
            android.util.Log.e("JetGo_DIAG", "Offline signal exception", e)
        }
    }

    private fun nowIsoUtc(): String {
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US)
        sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
        return sdf.format(java.util.Date())
    }

    /** Resultado de revisar si el código sigue siendo válido AHORA MISMO (para cerrar la app
     *  sola si dejó de serlo, aunque el cliente esté viendo algo en ese momento). */
    data class ValidityCheck(
        val stillValid: Boolean,
        /** Marca de tiempo (en milisegundos) que el panel usa para avisar "cerrar la app
         *  ahora" — por ejemplo, justo después de renovar un plan. Null si no hay ninguna. */
        val forceCloseSignalMs: Long? = null
    )

    /**
     * Revisa el estado actual del código: si sigue activo, si una demo ya venció, y si el
     * panel mandó una señal de "cerrar la app ahora" (por ejemplo, al renovar un plan).
     * Se llama a esto cada cierto tiempo mientras la app está abierta.
     */
    fun checkStillValid(projectId: String, code: String): ValidityCheck {
        if (projectId.isBlank() || code.isBlank()) return ValidityCheck(stillValid = true)
        return try {
            val normalizedCode = code.trim().uppercase()
            val docPath = "projects/$projectId/databases/(default)/documents/access_codes/$normalizedCode"
            val docUrl = "https://firestore.googleapis.com/v1/$docPath"

            val getRequest = Request.Builder().url(docUrl).build()
            client.newCall(getRequest).execute().use { response ->
                // Si Firestore ya bloqueó la consulta (código revocado, o demo vencida — las
                // reglas de seguridad rechazan la consulta sola en ese caso), ya no es válido.
                if (!response.isSuccessful) return ValidityCheck(stillValid = false)
                val body = response.body?.string() ?: return ValidityCheck(stillValid = true)
                val json = JSONObject(body)
                val fields = json.optJSONObject("fields") ?: return ValidityCheck(stillValid = true)

                val active = fields.optJSONObject("active")?.optBoolean("booleanValue", true) ?: true
                if (!active) return ValidityCheck(stillValid = false)

                val isDemo = fields.optJSONObject("isDemo")?.optBoolean("booleanValue", false) ?: false
                if (isDemo) {
                    val demoExpiresAtIso = fields.optJSONObject("demoExpiresAt")?.optString("timestampValue")
                    if (!demoExpiresAtIso.isNullOrBlank()) {
                        val expiresAtMs = parseIsoToMillis(demoExpiresAtIso)
                        if (expiresAtMs != null && expiresAtMs <= System.currentTimeMillis()) {
                            return ValidityCheck(stillValid = false)
                        }
                    }
                }

                val signalIso = fields.optJSONObject("forceCloseSignal")?.optString("timestampValue")
                val signalMs = signalIso?.let { parseIsoToMillis(it) }

                ValidityCheck(stillValid = true, forceCloseSignalMs = signalMs)
            }
        } catch (e: Exception) {
            // Si falla la consulta (sin internet, etc.) NO se cierra la app — solo se cierra
            // cuando se confirma de verdad que el código ya no es válido.
            ValidityCheck(stillValid = true)
        }
    }

    private fun parseIsoToMillis(iso: String): Long? {
        return try {
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US)
            sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
            sdf.parse(iso.substring(0, 19).replace("T", "T"))?.time
        } catch (e: Exception) {
            null
        }
    }
}
