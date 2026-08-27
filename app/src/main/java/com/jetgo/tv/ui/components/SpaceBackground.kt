package com.jetgo.tv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

private val NetflixDark1 = Color(0xFF141414)
private val NetflixDark2 = Color(0xFF070709)

private val SampleBackdrops = listOf(
    "https://image.tmdb.org/t/p/w500/8cdWjvZQUExUUTzyp4t6EDMubfO.jpg",
    "https://image.tmdb.org/t/p/w500/qNBAXBIQlnOThrVvA6mA2B5ggV6.jpg",
    "https://image.tmdb.org/t/p/w500/rktDFPbfHfUbArZ6OOOKsXcv0Bm.jpg",
    "https://image.tmdb.org/t/p/w500/vpnVM9B6NMmQpWeZvzLvDESb2QY.jpg",
    "https://image.tmdb.org/t/p/w500/gEU2QniE6E77NI6lCU6MxlNBvIx.jpg"
)

/**
 * Fondo estilo Netflix con imágenes de pósters cinematográficos y gradientes oscuros en toda la aplicación.
 */
@Composable
fun NetflixBackground(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(NetflixDark1)
    ) {
        // Collage sutil de imágenes de fondo estilo Netflix
        Column(
            modifier = Modifier.fillMaxSize().alpha(0.18f),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                SampleBackdrops.take(3).forEach { url ->
                    AsyncImage(
                        model = url,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.weight(1f).fillMaxSize().blur(24.dp)
                    )
                }
            }
        }

        // Gradiente cinemático oscuro y viñeta estilo Netflix
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            NetflixDark1.copy(alpha = 0.85f),
                            NetflixDark2.copy(alpha = 0.95f),
                            Color.Black.copy(alpha = 0.99f)
                        )
                    )
                )
        )
    }
}

@Composable
fun SpaceBackground(modifier: Modifier = Modifier) {
    NetflixBackground(modifier = modifier)
}
