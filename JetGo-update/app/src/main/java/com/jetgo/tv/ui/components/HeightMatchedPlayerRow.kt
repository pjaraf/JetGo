package com.jetgo.tv.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Fila de dos paneles (reproductor + panel lateral) donde el panel lateral SIEMPRE queda
 * exactamente con la misma altura que el reproductor.
 *
 * Ojo: a propósito NO se usa `Modifier.height(IntrinsicSize.Min)` para esto, porque el
 * reproductor contiene un `AndroidView` (PlayerView) y los AndroidView NO soportan medición
 * intrínseca — usarla ahí hace que la app se cierre sola apenas se dibuja esta pantalla.
 * Este Layout mide el reproductor de forma normal y usa esa altura real para el panel lateral.
 */
@Composable
fun HeightMatchedPlayerRow(
    modifier: Modifier = Modifier,
    playerWeight: Float = 2.1f,
    sideWeight: Float = 1f,
    /** Si se da, el panel lateral usa ESTE ancho/alto exacto (ej. 2f/3f para una carátula
     *  vertical) en vez de ocupar todo el espacio que le tocaría por [sideWeight]. */
    sideAspectRatio: Float? = null,
    /** Si se da, el panel lateral usa EXACTAMENTE este ancho en dp (por ejemplo, para que
     *  calce igual que otro botón/elemento de la pantalla). Tiene prioridad sobre [sideAspectRatio]. */
    sideFixedWidth: Dp? = null,
    spacing: Dp = 16.dp,
    playerContent: @Composable () -> Unit,
    sideContent: @Composable () -> Unit
) {
    SubcomposeLayout(modifier = modifier) { constraints ->
        val totalWidth = if (constraints.hasBoundedWidth) constraints.maxWidth else 0
        val spacingPx = spacing.roundToPx()
        val totalWeight = playerWeight + sideWeight
        val playerWidth = (((totalWidth - spacingPx).coerceAtLeast(0)) * (playerWeight / totalWeight)).toInt()
        val maxSideWidth = (totalWidth - spacingPx - playerWidth).coerceAtLeast(0)

        val playerPlaceables = subcompose("player", playerContent).map { measurable ->
            measurable.measure(
                Constraints(
                    minWidth = playerWidth,
                    maxWidth = playerWidth,
                    minHeight = 0,
                    maxHeight = constraints.maxHeight
                )
            )
        }
        val playerHeight = (playerPlaceables.maxOfOrNull { it.height } ?: 0).coerceAtMost(constraints.maxHeight)

        // Ancho real del panel lateral: ancho fijo explícito > proporción calculada > todo
        // el espacio disponible (en ese orden de prioridad).
        val sideWidth = when {
            sideFixedWidth != null -> sideFixedWidth.roundToPx().coerceIn(0, maxSideWidth)
            sideAspectRatio != null -> (playerHeight * sideAspectRatio).toInt().coerceIn(0, maxSideWidth)
            else -> maxSideWidth
        }

        val sidePlaceables = subcompose("side", sideContent).map { measurable ->
            measurable.measure(
                Constraints(
                    minWidth = sideWidth,
                    maxWidth = sideWidth,
                    minHeight = playerHeight,
                    maxHeight = playerHeight
                )
            )
        }

        layout(totalWidth, playerHeight) {
            playerPlaceables.forEach { it.placeRelative(0, 0) }
            sidePlaceables.forEach { it.placeRelative(playerWidth + spacingPx, 0) }
        }
    }
}
