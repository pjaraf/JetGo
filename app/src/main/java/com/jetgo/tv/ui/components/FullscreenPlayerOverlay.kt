package com.jetgo.tv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.PlayerView
import com.jetgo.tv.player.PlayerManager

/**
 * Reproductor a pantalla completa (horizontal). Se muestra por encima de toda la demás UI
 * cuando el usuario toca el reproductor normal.
 *
 * Para películas/series/anime ([isVod] = true) muestra los controles completos (play/pausa,
 * avance/retroceso, barra de progreso, idioma/subtítulos). Para canales en vivo solo el
 * botón de salir, ya que no tiene sentido "avanzar" o mostrar duración en un stream en vivo.
 */
@Composable
fun FullscreenPlayerOverlay(
    playerManager: PlayerManager,
    onExitFullscreen: () -> Unit,
    isVod: Boolean = false,
    posterUrl: String? = null,
    title: String = ""
) {
    var showLanguageDialog by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        AndroidView(
            factory = { context ->
                PlayerView(context).apply {
                    player = playerManager.exoPlayer
                    useController = false
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        if (isVod) {
            VodPlayerControls(
                playerManager = playerManager,
                posterUrl = posterUrl,
                title = title,
                onExit = onExitFullscreen,
                onOpenLanguageMenu = { showLanguageDialog = true }
            )
        } else {
            IconButton(
                onClick = onExitFullscreen,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp)
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.5f))
            ) {
                Icon(
                    imageVector = Icons.Default.FullscreenExit,
                    contentDescription = "Salir de pantalla completa",
                    tint = Color.White
                )
            }
        }
    }

    if (showLanguageDialog) {
        LanguageTracksDialog(
            audioTracks = playerManager.getAudioTracks(),
            subtitleTracks = playerManager.getSubtitleTracks(),
            onSelectAudio = { playerManager.selectTrack(it); showLanguageDialog = false },
            onSelectSubtitle = { playerManager.selectSubtitleTrack(it); showLanguageDialog = false },
            onDisableSubtitles = { playerManager.disableSubtitles(); showLanguageDialog = false },
            onDismiss = { showLanguageDialog = false }
        )
    }
}
