package com.jetgo.tv.ui.components

import android.app.Activity
import android.content.pm.ActivityInfo
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * Efecto que, mientras [isFullscreen] es true:
 * - Bloquea la orientación de la Activity a horizontal (landscape).
 * - Oculta la barra de estado y de navegación (modo inmersivo).
 * Al salir (isFullscreen = false), revierte ambas cosas.
 *
 * También intercepta el botón de "atrás" para salir de pantalla completa en vez de
 * cerrar la pantalla anterior, vía [onBackFromFullscreen].
 */
@Composable
fun FullscreenPlayerEffect(
    isFullscreen: Boolean,
    onBackFromFullscreen: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity

    BackHandler(enabled = isFullscreen) { onBackFromFullscreen() }

    DisposableEffect(isFullscreen) {
        val window = activity?.window
        if (isFullscreen) {
            try {
                activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            } catch (e: Exception) { /* algunos TV Box no permiten forzar orientación; no es crítico */ }
            if (window != null) {
                WindowCompat.setDecorFitsSystemWindows(window, false)
                val controller = WindowInsetsControllerCompat(window, window.decorView)
                controller.hide(WindowInsetsCompat.Type.systemBars())
                controller.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            try {
                activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            } catch (e: Exception) { /* ignorar */ }
            if (window != null) {
                WindowCompat.setDecorFitsSystemWindows(window, true)
                val controller = WindowInsetsControllerCompat(window, window.decorView)
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
        }
        onDispose { }
    }
}
