package com.jetgo.tv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

private val NetflixDark1 = Color(0xFF141416)
private val NetflixDark2 = Color(0xFF0C0C0E)
private val NetflixDark3 = Color(0xFF050507)

/**
 * Fondo estilo Netflix optimizado: gradientes cinemáticos oscuros de alto rendimiento
 * para TV Boxes y Android TV sin sobrecarga de memoria ni uso de blur inseguro.
 */
@Composable
fun NetflixBackground(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        NetflixDark1,
                        NetflixDark2,
                        NetflixDark3,
                        Color.Black
                    )
                )
            )
    )
}

@Composable
fun SpaceBackground(modifier: Modifier = Modifier) {
    NetflixBackground(modifier = modifier)
}

