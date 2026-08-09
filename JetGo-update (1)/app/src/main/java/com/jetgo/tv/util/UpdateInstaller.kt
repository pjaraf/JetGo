package com.jetgo.tv.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/** Maneja la descarga del APK de actualización y su instalación, sin salir de la app. */
object UpdateInstaller {

    private const val FILE_NAME = "jetgo-update.apk"
    private const val SUB_DIR = "updates"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    /** true si el sistema ya permite instalar APKs desde esta app; si no, hay que pedirlo primero. */
    fun canInstallUnknownApps(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else true
    }

    /** Abre la pantalla del sistema donde el usuario habilita instalar APKs de esta app. */
    fun requestInstallPermission(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }

    /**
     * Descarga el APK manejando el progreso NOSOTROS MISMOS (no depende del cajón de
     * notificaciones del sistema, que en muchos TV Box no se ve ni se puede tocar).
     * [onProgress] recibe el porcentaje (0-100). Al terminar, abre el instalador solo.
     */
    suspend fun downloadWithProgress(
        context: Context,
        apkUrl: String,
        onProgress: (Int) -> Unit,
        onInstallLaunched: () -> Unit,
        onError: (String) -> Unit
    ) {
        withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder().url(apkUrl).build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        withContext(Dispatchers.Main) { onError("HTTP ${response.code}") }
                        return@use
                    }
                    val body = response.body ?: run {
                        withContext(Dispatchers.Main) { onError("Sin contenido") }
                        return@use
                    }

                    val totalBytes = body.contentLength()
                    val dir = File(context.getExternalFilesDir(SUB_DIR)?.path ?: context.filesDir.path)
                    if (!dir.exists()) dir.mkdirs()
                    val outFile = File(dir, FILE_NAME)

                    body.byteStream().use { input ->
                        outFile.outputStream().use { output ->
                            val buffer = ByteArray(8 * 1024)
                            var bytesReadTotal = 0L
                            var lastReportedPercent = -1
                            while (true) {
                                val read = input.read(buffer)
                                if (read == -1) break
                                output.write(buffer, 0, read)
                                bytesReadTotal += read
                                if (totalBytes > 0) {
                                    val percent = ((bytesReadTotal * 100) / totalBytes).toInt().coerceIn(0, 100)
                                    if (percent != lastReportedPercent) {
                                        lastReportedPercent = percent
                                        withContext(Dispatchers.Main) { onProgress(percent) }
                                    }
                                }
                            }
                        }
                    }

                    withContext(Dispatchers.Main) {
                        onProgress(100)
                        val opened = installDownloadedApk(context)
                        if (opened) {
                            onInstallLaunched()
                        } else {
                            onError("La descarga terminó pero no se pudo abrir el instalador. Busca el archivo \"jetgo-update.apk\" con un explorador de archivos y ábrelo manualmente.")
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { onError(e.message ?: "Error desconocido") }
            }
        }
    }

    private fun installDownloadedApk(context: Context): Boolean {
        return try {
            val file = File(context.getExternalFilesDir(SUB_DIR), FILE_NAME)
            if (!file.exists()) return false

            val apkUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(installIntent)
            true
        } catch (e: Exception) {
            false
        }
    }
}
