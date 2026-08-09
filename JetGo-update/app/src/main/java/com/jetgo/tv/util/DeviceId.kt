package com.jetgo.tv.util

import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.provider.Settings

/** Identificador único y estable de este dispositivo (no cambia entre aperturas de la app) */
fun getDeviceId(context: Context): String {
    return Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown-device"
}

/** Nombre legible del dispositivo para mostrar en el panel admin (ej. "TV · Xiaomi MIBOX4"
 *  o "Teléfono · Samsung SM-A125M") — así se puede identificar cuál es cuál al revisar o
 *  borrar dispositivos, en vez de solo ver un código sin sentido. */
fun getDeviceDisplayName(context: Context): String {
    val uiModeManager = context.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
    val isTv = uiModeManager?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
    val tipo = if (isTv) "TV" else "Teléfono"
    val marca = Build.MANUFACTURER?.replaceFirstChar { it.uppercase() } ?: ""
    val modelo = Build.MODEL ?: ""
    return "$tipo · $marca $modelo".trim()
}
