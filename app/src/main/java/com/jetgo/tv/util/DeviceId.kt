package com.jetgo.tv.util

import android.content.Context
import android.provider.Settings

/** Identificador único y estable de este dispositivo (no cambia entre aperturas de la app) */
fun getDeviceId(context: Context): String {
    return Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown-device"
}
