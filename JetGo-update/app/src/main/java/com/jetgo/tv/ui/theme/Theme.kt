package com.jetgo.tv.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val BackgroundDark = Color(0xFF0D0F14)
val SurfaceDark = Color(0xFF1A1D24)
val FocusOrange = Color(0xFFFF7A2E)
val LiveGreen = Color(0xFF3DDC84)

private val DarkColors = darkColorScheme(
    background = BackgroundDark,
    surface = SurfaceDark,
    primary = FocusOrange
)

@Composable
fun JetGoTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColors,
        content = content
    )
}
