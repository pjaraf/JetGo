package com.jetgo.tv.util

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File

/** Maneja la descarga del APK de actualización y su instalación, sin salir de la app. */
object UpdateInstaller {

    private const val FILE_NAME = "jetgo-update.apk"
    private const val SUB_DIR = "updates"

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
     * Descarga el APK en segundo plano (con notificación de progreso del sistema) y,
     * al terminar, abre automáticamente el instalador. [onDownloadStarted] avisa a la UI
     * que ya se encoló la descarga.
     */
    fun downloadAndInstall(context: Context, apkUrl: String, onDownloadStarted: () -> Unit = {}) {
        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

        val request = DownloadManager.Request(Uri.parse(apkUrl))
            .setTitle("Actualizando JetGo")
            .setDescription("Descargando la última versión...")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(context, SUB_DIR, FILE_NAME)
            .setMimeType("application/vnd.android.package-archive")

        val downloadId = downloadManager.enqueue(request)

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                val completedId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                if (completedId == downloadId) {
                    try { context.unregisterReceiver(this) } catch (e: Exception) { /* ya estaba desregistrado */ }
                    installDownloadedApk(context)
                }
            }
        }

        val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            context.registerReceiver(receiver, filter)
        }

        onDownloadStarted()
    }

    private fun installDownloadedApk(context: Context) {
        val file = File(context.getExternalFilesDir(SUB_DIR), FILE_NAME)
        if (!file.exists()) return

        val apkUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(installIntent)
    }
}
