package com.jetgo.tv.util

import okhttp3.OkHttpClient
import okhttp3.Request
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
    val m3uUrl: String? = null
)

/**
 * Valida códigos de acceso contra Firestore usando su API REST pública (sin el SDK de Firebase,
 * para no agregar peso ni dependencias nativas extra — funciona incluso en TV Box sin
 * Google Play Services).
 *
 * Requiere que en Firestore exista una colección "access_codes" donde cada documento tiene
 * como ID el código en sí, con los campos: active (bool), mode (string), host, username,
 * password, m3uUrl (todos string, según corresponda).
 */
object AccessCodeChecker {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    fun checkCode(projectId: String, code: String): AccessCodeResult {
        if (projectId.isBlank() || code.isBlank()) return AccessCodeResult(valid = false)
        return try {
            val normalizedCode = code.trim().uppercase()
            val url = "https://firestore.googleapis.com/v1/projects/$projectId/databases/(default)/documents/access_codes/$normalizedCode"
            val request = Request.Builder().url(url).build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return AccessCodeResult(valid = false)
                val body = response.body?.string() ?: return AccessCodeResult(valid = false)
                val json = JSONObject(body)
                val fields = json.optJSONObject("fields") ?: return AccessCodeResult(valid = false)

                val active = fields.optJSONObject("active")?.optBoolean("booleanValue", false) ?: false
                if (!active) return AccessCodeResult(valid = false)

                fun textField(name: String): String? =
                    fields.optJSONObject(name)?.optString("stringValue")?.takeIf { it.isNotBlank() }

                AccessCodeResult(
                    valid = true,
                    mode = textField("mode") ?: "xtream",
                    host = textField("host"),
                    username = textField("username"),
                    password = textField("password"),
                    m3uUrl = textField("m3uUrl")
                )
            }
        } catch (e: Exception) {
            AccessCodeResult(valid = false)
        }
    }

    /** Mantiene compatibilidad con código existente que solo necesita saber si es válido */
    fun isCodeValid(projectId: String, code: String): Boolean = checkCode(projectId, code).valid
}
