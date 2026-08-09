package com.jetgo.tv.util

import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration

/**
 * Detecta si la app corre en un dispositivo de perfil TV (Android TV, Google TV, la mayoría
 * de TV Box). En ese caso se mantiene la interfaz original optimizada para D-pad.
 * En teléfonos/tablets se usa la interfaz con barra inferior (Inicio/TV/Perfil).
 */
fun isTelevision(context: Context): Boolean {
    val uiModeManager = context.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
    return uiModeManager?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
}
