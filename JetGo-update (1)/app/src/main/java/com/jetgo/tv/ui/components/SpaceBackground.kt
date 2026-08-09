package com.jetgo.tv.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import kotlin.random.Random

private val SpaceTop = Color(0xFF05060C)
private val SpaceBottom = Color(0xFF0B0D1A)

/**
 * Fondo con temática espacial: degradado oscuro tipo cielo nocturno + estrellitas sutiles y
 * estáticas de distinto tamaño y brillo. Liviano (no se anima) para no gastar batería/CPU de más.
 */
@Composable
fun SpaceBackground(modifier: Modifier = Modifier) {
    val stars = remember {
        List(90) {
            Triple(Random.nextFloat(), Random.nextFloat(), Random.nextFloat())
        }
    }
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(SpaceTop, SpaceBottom)))
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            stars.forEach { (xFrac, yFrac, brightness) ->
                drawCircle(
                    color = Color.White.copy(alpha = 0.15f + brightness * 0.55f),
                    radius = 1.5f + brightness * 3f,
                    center = Offset(size.width * xFrac, size.height * yFrac)
                )
            }
        }
    }
}
