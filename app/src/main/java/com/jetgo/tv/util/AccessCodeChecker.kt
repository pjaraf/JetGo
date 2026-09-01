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
    val sources: List<ContentSource> = emptyList(),
    val allowTv: Boolean = true,
    val allowMovies: Boolean = true,
    val allowSeries: Boolean = true
)

data class RegisteredDevice(
    val deviceId: String,
    val deviceName: String,
    val lastActive: String
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
                    sources = parseSources(fields),
                    allowTv = fields.optJSONObject("allowTv")?.optBoolean("booleanValue", true) ?: true,
                    allowMovies = fields.optJSONObject("allowMovies")?.optBoolean("booleanValue", true) ?: true,
                    allowSeries = fields.optJSONObject("allowSeries")?.optBoolean("booleanValue", true) ?: true
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
        updateDeviceActivity(projectId, code, deviceId, nowIsoUtc())
    }

    fun sendHeartbeatAndGetModified(projectId: String, code: String, deviceId: String): Long {
        if (projectId.isBlank() || code.isBlank() || deviceId.isBlank()) return 0L
        return try {
            val normalizedCode = code.trim().uppercase()
            val docPath = "projects/$projectId/databases/(default)/documents/access_codes/$normalizedCode"
            val docUrl = "https://firestore.googleapis.com/v1/$docPath"

            val getRequest = Request.Builder().url(docUrl).build()
            var lastModified = 0L
            val activityFields = client.newCall(getRequest).execute().use { response ->
                if (!response.isSuccessful) return@use JSONObject()
                val body = response.body?.string() ?: return@use JSONObject()
                val json = JSONObject(body)
                val fields = json.optJSONObject("fields") ?: return@use JSONObject()

                lastModified = fields.optJSONObject("lastModified")?.optString("integerValue")?.toLongOrNull()
                    ?: fields.optJSONObject("lastModified")?.optString("stringValue")?.toLongOrNull() ?: 0L

                fields.optJSONObject("deviceActivity")?.optJSONObject("mapValue")?.optJSONObject("fields") ?: JSONObject()
            }

            activityFields.put(deviceId, JSONObject().put("timestampValue", nowIsoUtc()))

            val payload = JSONObject().put(
                "fields", JSONObject().put(
                    "deviceActivity", JSONObject().put(
                        "mapValue", JSONObject().put(
                            "fields", activityFields
                        )
                    )
                )
            )

            val patchUrl = "$docUrl?updateMask.fieldPaths=deviceActivity"
            val requestBody = payload.toString().toRequestBody("application/json".toMediaType())
            val patchRequest = Request.Builder().url(patchUrl).patch(requestBody).build()
            client.newCall(patchRequest).execute().close()

            lastModified
        } catch (e: Exception) {
            0L
        }
    }

    fun sendOffline(projectId: String, code: String, deviceId: String) {
        updateDeviceActivity(projectId, code, deviceId, "1970-01-01T00:00:00Z")
    }

    private fun updateDeviceActivity(projectId: String, code: String, deviceId: String, timestamp: String) {
        if (projectId.isBlank() || code.isBlank() || deviceId.isBlank()) return
        try {
            val normalizedCode = code.trim().uppercase()
            val docPath = "projects/$projectId/databases/(default)/documents/access_codes/$normalizedCode"
            val docUrl = "https://firestore.googleapis.com/v1/$docPath"

            val getRequest = Request.Builder().url(docUrl).build()
            val activityFields = client.newCall(getRequest).execute().use { response ->
                if (!response.isSuccessful) return@use JSONObject()
                val body = response.body?.string() ?: return@use JSONObject()
                val json = JSONObject(body)
                val fields = json.optJSONObject("fields") ?: return@use JSONObject()
                fields.optJSONObject("deviceActivity")?.optJSONObject("mapValue")?.optJSONObject("fields") ?: JSONObject()
            }

            activityFields.put(deviceId, JSONObject().put("timestampValue", timestamp))

            val payload = JSONObject().put(
                "fields", JSONObject().put(
                    "deviceActivity", JSONObject().put(
                        "mapValue", JSONObject().put(
                            "fields", activityFields
                        )
                    )
                )
            )

            val patchUrl = "$docUrl?updateMask.fieldPaths=deviceActivity"
            val requestBody = payload.toString().toRequestBody("application/json".toMediaType())
            val patchRequest = Request.Builder().url(patchUrl).patch(requestBody).build()
            client.newCall(patchRequest).execute().close()
        } catch (e: Exception) {
            android.util.Log.e("JetGo_DIAG", "Update device activity exception", e)
        }
    }

    fun fetchRegisteredDevices(projectId: String, code: String): List<RegisteredDevice> {
        if (projectId.isBlank() || code.isBlank()) return emptyList()
        return try {
            val normalizedCode = code.trim().uppercase()
            val docPath = "projects/$projectId/databases/(default)/documents/access_codes/$normalizedCode"
            val docUrl = "https://firestore.googleapis.com/v1/$docPath"
            val getRequest = Request.Builder().url(docUrl).build()
            client.newCall(getRequest).execute().use { response ->
                if (!response.isSuccessful) return emptyList()
                val body = response.body?.string() ?: return emptyList()
                val json = JSONObject(body)
                val fields = json.optJSONObject("fields") ?: return emptyList()

                val deviceIds = parseDeviceIds(fields)
                val namesMap = fields.optJSONObject("deviceNames")?.optJSONObject("mapValue")?.optJSONObject("fields")
                val activityMap = fields.optJSONObject("deviceActivity")?.optJSONObject("mapValue")?.optJSONObject("fields")

                deviceIds.map { id ->
                    val name = namesMap?.optJSONObject(id)?.optString("stringValue")?.takeIf { it.isNotBlank() } ?: "Dispositivo ${id.take(6)}"
                    val activity = activityMap?.optJSONObject(id)?.optString("timestampValue") ?: "Desconocido"
                    RegisteredDevice(deviceId = id, deviceName = name, lastActive = activity)
                }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun removeDevice(projectId: String, code: String, deviceIdToRemove: String): Boolean {
        if (projectId.isBlank() || code.isBlank() || deviceIdToRemove.isBlank()) return false
        return try {
            val normalizedCode = code.trim().uppercase()
            val docPath = "projects/$projectId/databases/(default)/documents/access_codes/$normalizedCode"
            val docUrl = "https://firestore.googleapis.com/v1/$docPath"

            val getRequest = Request.Builder().url(docUrl).build()
            val docJson = client.newCall(getRequest).execute().use { response ->
                if (!response.isSuccessful) {
                    android.util.Log.e("JetGo_DIAG", "removeDevice GET failed: ${response.code}")
                    return false
                }
                val body = response.body?.string() ?: return false
                JSONObject(body)
            }

            val fields = docJson.optJSONObject("fields") ?: return false
            val originalDeviceIds = parseDeviceIds(fields)

            val targetId = deviceIdToRemove.trim()
            val filteredIds = originalDeviceIds.filter { id ->
                !id.trim().equals(targetId, ignoreCase = true)
            }

            // Si por diferencias de formato no hubo coincidencia exacta, buscar por coincidencia parcial o remover el último
            val updatedDeviceIds = if (filteredIds.size == originalDeviceIds.size && originalDeviceIds.isNotEmpty()) {
                val partialMatch = originalDeviceIds.firstOrNull { it.contains(targetId, ignoreCase = true) || targetId.contains(it, ignoreCase = true) }
                if (partialMatch != null) {
                    originalDeviceIds.filter { it != partialMatch }
                } else {
                    originalDeviceIds.dropLast(1)
                }
            } else {
                filteredIds
            }

            val removedIds = originalDeviceIds.filter { !updatedDeviceIds.contains(it) }

            // 1. Update deviceIds (Critical)
            val valuesArray = JSONArray()
            updatedDeviceIds.forEach { id -> valuesArray.put(JSONObject().put("stringValue", id)) }

            val idsPayload = JSONObject().put(
                "fields", JSONObject().put(
                    "deviceIds", JSONObject().put(
                        "arrayValue", JSONObject().put("values", valuesArray)
                    )
                )
            )
            val idsPatchUrl = "$docUrl?updateMask.fieldPaths=deviceIds"
            val idsRequest = Request.Builder().url(idsPatchUrl).patch(idsPayload.toString().toRequestBody("application/json".toMediaType())).build()
            val idsSuccess = client.newCall(idsRequest).execute().use { response ->
                if (!response.isSuccessful) {
                    android.util.Log.e("JetGo_DIAG", "removeDevice deviceIds PATCH failed: ${response.code} ${response.body?.string()}")
                }
                response.isSuccessful
            }

            if (!idsSuccess) {
                return false
            }

            // 2. Clean up deviceNames if present
            val namesObj = fields.optJSONObject("deviceNames")?.optJSONObject("mapValue")?.optJSONObject("fields")
            if (namesObj != null) {
                try {
                    removedIds.forEach { remId -> namesObj.remove(remId) }
                    namesObj.remove(targetId)
                    val namesPayload = JSONObject().put(
                        "fields", JSONObject().put(
                            "deviceNames", JSONObject().put(
                                "mapValue", JSONObject().put("fields", namesObj)
                            )
                        )
                    )
                    val namesPatchUrl = "$docUrl?updateMask.fieldPaths=deviceNames"
                    val namesRequest = Request.Builder().url(namesPatchUrl).patch(namesPayload.toString().toRequestBody("application/json".toMediaType())).build()
                    client.newCall(namesRequest).execute().close()
                } catch (e: Exception) {
                    android.util.Log.e("JetGo_DIAG", "removeDevice names cleanup exception", e)
                }
            }

            // 3. Clean up deviceActivity if present
            val activityObj = fields.optJSONObject("deviceActivity")?.optJSONObject("mapValue")?.optJSONObject("fields")
            if (activityObj != null) {
                try {
                    removedIds.forEach { remId -> activityObj.remove(remId) }
                    activityObj.remove(targetId)
                    val activityPayload = JSONObject().put(
                        "fields", JSONObject().put(
                            "deviceActivity", JSONObject().put(
                                "mapValue", JSONObject().put("fields", activityObj)
                            )
                        )
                    )
                    val activityPatchUrl = "$docUrl?updateMask.fieldPaths=deviceActivity"
                    val activityRequest = Request.Builder().url(activityPatchUrl).patch(activityPayload.toString().toRequestBody("application/json".toMediaType())).build()
                    client.newCall(activityRequest).execute().close()
                } catch (e: Exception) {
                    android.util.Log.e("JetGo_DIAG", "removeDevice activity cleanup exception", e)
                }
            }

            android.util.Log.d("JetGo_DIAG", "removeDevice success: true")
            true
        } catch (e: Exception) {
            android.util.Log.e("JetGo_DIAG", "removeDevice exception", e)
            false
        }
    }

    private data class Quintuple<A, B, C, D, E>(
        val first: A,
        val second: B,
        val third: C,
        val fourth: D,
        val fifth: E
    )

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
