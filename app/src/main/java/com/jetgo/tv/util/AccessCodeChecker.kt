package com.jetgo.tv.util

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Valida códigos de acceso contra Firestore usando su API REST pública (sin el SDK de Firebase,
 * para no agregar peso ni dependencias nativas extra — funciona incluso en TV Box sin
 * Google Play Services).
 *
 * Requiere que en Firestore exista una colección "access_codes" donde cada documento tiene
 * como ID el código en sí, y un campo booleano "active".
 *
 * Reglas de seguridad de Firestore necesarias (ver README/panel de administración):
 *   match /access_codes/{codeId} {
 *     allow get: if true;
 *     allow list, write: if request.auth != null;
 *   }
 */
object AccessCodeChecker {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    /** true si el código existe en Firestore y su campo "active" es true */
    fun isCodeValid(projectId: String, code: String): Boolean {
        if (projectId.isBlank() || code.isBlank()) return false
        return try {
            val normalizedCode = code.trim().uppercase()
            val url = "https://firestore.googleapis.com/v1/projects/$projectId/databases/(default)/documents/access_codes/$normalizedCode"
            val request = Request.Builder().url(url).build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return false
                val body = response.body?.string() ?: return false
                val json = JSONObject(body)
                val fields = json.optJSONObject("fields") ?: return false
                val activeField = fields.optJSONObject("active") ?: return false
                activeField.optBoolean("booleanValue", false)
            }
        } catch (e: Exception) {
            false
        }
    }
}
