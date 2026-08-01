package com.jetgo.tv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.PlayerView
import com.jetgo.tv.data.model.Category
import com.jetgo.tv.data.model.ContentItem
import com.jetgo.tv.player.PlayerManager
import com.jetgo.tv.ui.screens.HomeViewModel

/**
 * Reproductor a pantalla completa (horizontal). Se muestra por encima de toda la demás UI
 * cuando el usuario toca el reproductor normal.
 *
 * Para películas/series/anime ([isVod] = true) muestra los controles completos (play/pausa,
 * avance/retroceso, barra de progreso, idioma/subtítulos).
 *
 * Para canales en vivo ([isVod] = false) muestra el banner de programación (EPG) al cambiar
 * de canal, y permite abrir el panel lateral de canales/categorías presionando IZQUIERDA en
 * el control remoto (1 vez = canales, 2 veces = categorías).
 */
@Composable
fun FullscreenPlayerOverlay(
    playerManager: PlayerManager,
    onExitFullscreen: () -> Unit,
    isVod: Boolean = false,
    posterUrl: String? = null,
    title: String = "",
    liveChannelInfo: HomeViewModel.LiveChannelInfo? = null,
    liveCategories: List<Category> = emptyList(),
    liveChannelsInCategory: List<ContentItem> = emptyList(),
    onLoadLiveCategories: () -> Unit = {},
    onSelectLiveCategory: (String) -> Unit = {},
    onSelectLiveChannel: (ContentItem) -> Unit = {}
) {
    var showLanguageDialog by remember { mutableStateOf(false) }
    var zapMode by remember { mutableStateOf(ZapPanelMode.HIDDEN) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(isVod) {
        if (!isVod) {
            onLoadLiveCategories()
            focusRequester.requestFocus()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .then(
                if (!isVod) {
                    Modifier
                        .focusRequester(focusRequester)
                        .focusable()
                        .onKeyEvent { event ->
                            if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionLeft) {
                                zapMode = when (zapMode) {
                                    ZapPanelMode.HIDDEN -> ZapPanelMode.CHANNELS
                                    ZapPanelMode.CHANNELS -> ZapPanelMode.CATEGORIES
                                    ZapPanelMode.CATEGORIES -> ZapPanelMode.HIDDEN
                                }
                                true
                            } else {
                                false
                            }
                        }
                } else Modifier
            )
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

            liveChannelInfo?.let { info ->
                LiveChannelInfoBanner(
                    info = info,
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }

            LiveZapPanel(
                mode = zapMode,
                categories = liveCategories,
                channels = liveChannelsInCategory,
                currentChannelName = liveChannelInfo?.channelName ?: "",
                onSelectCategory = { category ->
                    onSelectLiveCategory(category.id)
                    zapMode = ZapPanelMode.CHANNELS
                },
                onSelectChannel = { channel ->
                    onSelectLiveChannel(channel)
                    zapMode = ZapPanelMode.HIDDEN
                },
                modifier = Modifier.align(Alignment.CenterStart)
            )
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
