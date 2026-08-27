package com.jetgo.tv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import kotlinx.coroutines.delay
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout
import com.jetgo.tv.data.model.Category
import com.jetgo.tv.data.model.Channel
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
 * Para canales en vivo ([isVod] = false) muestra el banner de información al cambiar de canal,
 * y permite abrir el panel lateral de canales/categorías presionando IZQUIERDA en el control
 * remoto (1 vez = canales, 2 veces = categorías). Con el panel abierto: ARRIBA/ABAJO mueve la
 * selección y el CENTRO/OK del control confirma (cambia de canal o entra a la categoría).
 */
@Composable
fun FullscreenPlayerOverlay(
    playerManager: PlayerManager,
    onExitFullscreen: () -> Unit,
    isVod: Boolean = false,
    posterUrl: String? = null,
    title: String = "",
    liveChannelInfo: HomeViewModel.LiveChannelInfo? = null,
    allLiveChannels: List<Channel> = emptyList(),
    onChangeChannel: (Channel) -> Unit = {},
    liveCategories: List<Category> = emptyList(),
    liveChannelsInCategory: List<ContentItem> = emptyList(),
    onLoadLiveCategories: () -> Unit = {},
    onSelectLiveCategory: (String) -> Unit = {},
    onSelectLiveChannel: (ContentItem) -> Unit = {},
    showNextEpisodeMessage: Boolean = false
) {
    var showLanguageDialog by remember { mutableStateOf(false) }
    var zapMode by remember { mutableStateOf(ZapPanelMode.HIDDEN) }
    var zapSelectedIndex by remember { mutableStateOf(0) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(isVod) {
        if (!isVod) {
            onLoadLiveCategories()
            try { focusRequester.requestFocus() } catch (e: Exception) { /* ignorar si aún no está listo */ }
        }
    }

    // Al abrir el panel, la selección arranca en el canal (o categoría) que está REALMENTE
    // activo ahora mismo — no siempre en el primero de la lista — así se mantiene "en lo
    // último seleccionado" cada vez que el cliente vuelve a abrir el panel.
    LaunchedEffect(zapMode) {
        zapSelectedIndex = when (zapMode) {
            ZapPanelMode.CHANNELS -> {
                val idx = liveChannelsInCategory.indexOfFirst { it.id == liveChannelInfo?.channelId }
                idx.coerceAtLeast(0)
            }
            ZapPanelMode.CATEGORIES -> {
                val idx = liveCategories.indexOfFirst { it.id == liveChannelInfo?.categoryId }
                idx.coerceAtLeast(0)
            }
            ZapPanelMode.HIDDEN -> 0
        }
    }

    fun confirmCategorySelection() {
        liveCategories.getOrNull(zapSelectedIndex)?.let { category ->
            onSelectLiveCategory(category.id)
            zapMode = ZapPanelMode.CHANNELS
        }
    }

    fun confirmChannelSelection() {
        liveChannelsInCategory.getOrNull(zapSelectedIndex)?.let { channel ->
            onSelectLiveChannel(channel)
            zapMode = ZapPanelMode.HIDDEN
        }
    }

    /** Cambia de canal directo, como el botón CH+/CH- de un control de TV normal */
    var lastChannelChangeAt by remember { mutableStateOf(0L) }
    fun changeChannelDirect(step: Int) {
        val now = System.currentTimeMillis()
        if (now - lastChannelChangeAt < 400) return // evita procesar el mismo cambio dos veces
        lastChannelChangeAt = now
        if (allLiveChannels.isEmpty()) return
        val currentIndex = allLiveChannels.indexOfFirst { it.streamId == liveChannelInfo?.channelId }
        val nextIndex = if (currentIndex == -1) 0
        else ((currentIndex + step) % allLiveChannels.size + allLiveChannels.size) % allLiveChannels.size
        onChangeChannel(allLiveChannels[nextIndex])
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .then(
                if (!isVod) {
                    Modifier
                        .focusRequester(focusRequester)
                        .onFocusChanged { focusState ->
                            // Si algo más le robó el foco (ej. el botón de salir), lo recuperamos
                            // para que el control remoto siga funcionando en la pantalla completa.
                            if (!focusState.isFocused) {
                                try { focusRequester.requestFocus() } catch (e: Exception) { /* ignorar */ }
                            }
                        }
                        .focusable()
                        .onKeyEvent { event ->
                            if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                            when (event.key) {
                                Key.ChannelUp -> { changeChannelDirect(1); true }
                                Key.ChannelDown -> { changeChannelDirect(-1); true }
                                Key.DirectionLeft -> {
                                    zapMode = when (zapMode) {
                                        ZapPanelMode.HIDDEN -> ZapPanelMode.CHANNELS
                                        ZapPanelMode.CHANNELS -> ZapPanelMode.CATEGORIES
                                        ZapPanelMode.CATEGORIES -> ZapPanelMode.HIDDEN
                                    }
                                    true
                                }
                                Key.DirectionUp -> {
                                    if (zapMode != ZapPanelMode.HIDDEN) {
                                        zapSelectedIndex = (zapSelectedIndex - 1).coerceAtLeast(0)
                                    } else {
                                        changeChannelDirect(-1) // como el canal - de una tele normal
                                    }
                                    true
                                }
                                Key.DirectionDown -> {
                                    if (zapMode != ZapPanelMode.HIDDEN) {
                                        val maxIndex = (
                                            if (zapMode == ZapPanelMode.CATEGORIES) liveCategories.size
                                            else liveChannelsInCategory.size
                                            ) - 1
                                        zapSelectedIndex = (zapSelectedIndex + 1).coerceAtMost(maxIndex.coerceAtLeast(0))
                                    } else {
                                        changeChannelDirect(1) // como el canal + de una tele normal
                                    }
                                    true
                                }
                                Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                                    when (zapMode) {
                                        ZapPanelMode.CATEGORIES -> { confirmCategorySelection(); true }
                                        ZapPanelMode.CHANNELS -> { confirmChannelSelection(); true }
                                        ZapPanelMode.HIDDEN -> false
                                    }
                                }
                                Key.Back -> {
                                    if (zapMode != ZapPanelMode.HIDDEN) {
                                        zapMode = ZapPanelMode.HIDDEN
                                        true
                                    } else false
                                }
                                else -> false
                            }
                        }
                } else Modifier
            )
    ) {
        var fillScreen by remember { mutableStateOf(true) }
        val activePlayer = if (isVod) playerManager.vodPlayer else playerManager.livePlayer
        var attachedPlayer by remember { mutableStateOf<MediaPlayer?>(null) }

        var isVideoReady by remember { mutableStateOf(false) }
        LaunchedEffect(activePlayer) {
            isVideoReady = false
            delay(900L) // Oculta el fondo negro/buffer mientras VLC renderiza el primer frame
            isVideoReady = true
        }

        AndroidView(
            factory = { context ->
                VLCVideoLayout(context)
            },
            update = { layout ->
                if (attachedPlayer !== activePlayer) {
                    try { attachedPlayer?.detachViews() } catch (e: Exception) { /* ignorar */ }
                    try {
                        activePlayer.attachViews(layout, null, true, false)
                        attachedPlayer = activePlayer
                    } catch (e: Exception) { /* ignorar */ }
                }
                try {
                    if (fillScreen) {
                        activePlayer.aspectRatio = null
                        activePlayer.scale = 0f
                    } else {
                        activePlayer.aspectRatio = null
                        activePlayer.scale = 0f
                    }
                } catch (e: Exception) { /* ignorar */ }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Backdrop temporal mientras VLC renderiza el primer frame para evitar pantalla negra al expandir
        if (!isVideoReady) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                val bgImage = if (isVod) posterUrl else liveChannelInfo?.channelLogo
                if (!bgImage.isNullOrBlank()) {
                    AsyncImage(
                        model = bgImage,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().alpha(0.25f)
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Black.copy(alpha = 0.5f),
                                    Color.Black.copy(alpha = 0.88f)
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(24.dp)
                    ) {
                        if (!bgImage.isNullOrBlank()) {
                            AsyncImage(
                                model = bgImage,
                                contentDescription = null,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .size(130.dp)
                                    .clip(RoundedCornerShape(16.dp))
                            )
                        }
                        Text(
                            text = if (isVod) title else (liveChannelInfo?.channelName ?: "Cargando..."),
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
        if (isVod) {
            VodPlayerControls(
                playerManager = playerManager,
                posterUrl = posterUrl,
                title = title,
                onExit = onExitFullscreen,
                onOpenLanguageMenu = { showLanguageDialog = true },
                onToggleAspectRatio = { fillScreen = !fillScreen }
            )
            if (showNextEpisodeMessage) {
                Box(
                    modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "Siguiente capítulo",
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                    )
                }
            }
        } else {
            liveChannelInfo?.let { info ->
                LiveChannelInfoBanner(
                    info = info,
                    videoQuality = playerManager.videoQuality.value,
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }
            LiveZapPanel(
                mode = zapMode,
                categories = liveCategories,
                channels = liveChannelsInCategory,
                currentChannelName = liveChannelInfo?.channelName ?: "",
                selectedIndex = zapSelectedIndex,
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
