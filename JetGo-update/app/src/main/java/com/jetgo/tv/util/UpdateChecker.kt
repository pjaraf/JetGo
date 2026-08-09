package com.jetgo.tv.util

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class UpdateInfo(
    val versionCode: Int,
    val versionLabel: String,
    val apkDownloadUrl: String
)

object UpdateChecker {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    /**
     * Consulta el último Release publicado en GitHub (repo "usuario/repositorio") y devuelve
     * la info de actualización SOLO si el versionCode publicado es mayor al [currentVersionCode]
     * instalado. Devuelve null si no hay actualización o si algo falla (sin conexión, etc.).
     *
     * Esta llamada es de red (bloqueante) y debe ejecutarse en un hilo de fondo.
     */
    fun checkForUpdate(repo: String, currentVersionCode: Int): UpdateInfo? {
        return try {
            val request = Request.Builder()
                .url("https://api.github.com/repos/$repo/releases/latest")
                .header("Accept", "application/vnd.github+json")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body?.string() ?: return null
                val json = JSONObject(body)
                val assets = json.optJSONArray("assets") ?: return null

                var apkUrl: String? = null
                var versionTxtUrl: String? = null
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    val name = asset.optString("name")
                    val url = asset.optString("browser_download_url")
                    if (name.endsWith(".apk")) apkUrl = url
                    if (name == "version.txt") versionTxtUrl = url
                }
                if (apkUrl == null || versionTxtUrl == null) return null

                val remoteVersionCode = fetchText(versionTxtUrl)?.trim()?.toIntOrNull() ?: return null

                if (remoteVersionCode > currentVersionCode) {
                    UpdateInfo(
                        versionCode = remoteVersionCode,
                        versionLabel = json.optString("tag_name", "latest"),
                        apkDownloadUrl = apkUrl
                    )
                } else null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun fetchText(url: String): String? {
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            return response.body?.string()
        }
    }
}
