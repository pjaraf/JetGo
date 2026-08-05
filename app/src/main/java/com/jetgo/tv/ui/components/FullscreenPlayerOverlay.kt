package com.jetgo.tv.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.jetgo.tv.data.model.Category
import com.jetgo.tv.data.model.Channel
import com.jetgo.tv.data.model.ContentItem
import com.jetgo.tv.player.PlayerManager
import com.jetgo.tv.ui.screens.HomeViewModel
import com.jetgo.tv.ui.theme.SurfaceDark
import kotlin.random.Random

private val MidnightTop = Color(0xFF05070D)
private val MidnightBottom = Color(0xFF0B0E17)
private val LiveRed = Color(0xFFE53935)

/**
 * Reproductor a pantalla completa (horizontal). Se muestra por encima de toda la demás UI
 * cuando el usuario toca el reproductor normal.
 *
 * Para películas/series/anime ([isVod] = true) muestra los controles completos (play/pausa,
 * avance/retroceso, barra de progreso, idioma/subtítulos), ocupando toda la pantalla.
 *
 * Para canales en vivo ([isVod] = false): video con bordes redondeados a la izquierda, y una
 * guía de canales (mini EPG) SIEMPRE visible a la derecha, transparente y moderna, con el canal
 * activo resaltado en rojo. Arriba/abajo mueve y cambia de canal directo; no hace falta abrir
 * ningún panel oculto.
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
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(isVod) {
        if (!isVod) {
            try { focusRequester.requestFocus() } catch (e: Exception) { /* ignorar si aún no está listo */ }
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
            .background(if (isVod) Color.Black else Brush.verticalGradient(listOf(MidnightTop, MidnightBottom)))
            .then(
                if (!isVod) {
                    Modifier
                        .focusRequester(focusRequester)
                        .onFocusChanged { focusState ->
                            if (!focusState.isFocused) {
                                try { focusRequester.requestFocus() } catch (e: Exception) { /* ignorar */ }
                            }
                        }
                        .focusable()
                        .onKeyEvent { event ->
                            if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                            when (event.key) {
                                Key.ChannelUp, Key.DirectionUp -> { changeChannelDirect(1); true }
                                Key.ChannelDown, Key.DirectionDown -> { changeChannelDirect(-1); true }
                                else -> false
                            }
                        }
                } else Modifier
            )
    ) {
        if (!isVod) {
            // Fondo "medianoche" con partículas de luz sutiles, detrás de todo
            SubtleParticles(modifier = Modifier.fillMaxSize())
        }

        if (isVod) {
            var resizeMode by remember { mutableStateOf(androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FILL) }

            AndroidView(
                factory = { context ->
                    PlayerView(context).apply {
                        player = playerManager.exoPlayer
                        useController = false
                    }
                },
                update = { view -> view.resizeMode = resizeMode },
                modifier = Modifier.fillMaxSize()
            )

            VodPlayerControls(
                playerManager = playerManager,
                posterUrl = posterUrl,
                title = title,
                onExit = onExitFullscreen,
                onOpenLanguageMenu = { showLanguageDialog = true },
                onToggleAspectRatio = {
                    resizeMode = if (resizeMode == androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT) {
                        androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FILL
                    } else {
                        androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                    }
                }
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
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        } else {
            Row(modifier = Modifier.fillMaxSize().padding(20.dp)) {
                // ---- Video, con bordes redondeados ----
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(20.dp))
                ) {
                    var resizeMode by remember { mutableStateOf(androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FILL) }
                    AndroidView(
                        factory = { context ->
                            PlayerView(context).apply {
                                player = playerManager.exoPlayer
                                useController = false
                            }
                        },
                        update = { view -> view.resizeMode = resizeMode },
                        modifier = Modifier.fillMaxSize()
                    )

                    liveChannelInfo?.let { info ->
                        LiveChannelInfoBanner(
                            info = info,
                            videoQuality = playerManager.videoQuality.value,
                            modifier = Modifier.align(Alignment.BottomStart)
                        )
                    }
                }

                Box(modifier = Modifier.width(20.dp))

                // ---- Guía de canales (mini EPG), siempre visible ----
                MiniEpgSidebar(
                    channels = allLiveChannels,
                    activeChannelId = liveChannelInfo?.channelId,
                    nextProgramTitle = liveChannelInfo?.next?.title,
                    onSelectChannel = onChangeChannel,
                    modifier = Modifier.width(320.dp).fillMaxHeight()
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

/** Guía de canales moderna: lista transparente con logo + nombre, canal activo resaltado en rojo,
 *  y una tarjeta compacta de "A continuación" al final. */
@Composable
private fun MiniEpgSidebar(
    channels: List<Channel>,
    activeChannelId: String?,
    nextProgramTitle: String?,
    onSelectChannel: (Channel) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val activeIndex = channels.indexOfFirst { it.streamId == activeChannelId }

    LaunchedEffect(activeChannelId) {
        if (activeIndex >= 0) {
            listState.animateScrollToItem((activeIndex - 2).coerceAtLeast(0))
        }
    }

    Column(modifier = modifier) {
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp)
        ) {
            items(channels) { channel ->
                val isActive = channel.streamId == activeChannelId
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isActive) LiveRed.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.04f))
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .width(4.dp)
                            .size(width = 4.dp, height = 34.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(if (isActive) LiveRed else Color.Transparent)
                    )
                    Box(
                        modifier = Modifier
                            .padding(start = 10.dp)
                            .size(38.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.White.copy(alpha = 0.06f)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (!channel.logoUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = channel.logoUrl,
                                contentDescription = null,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize().padding(5.dp)
                            )
                        } else {
                            Text(channel.name.take(2).uppercase(), color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    Text(
                        text = channel.name,
                        color = if (isActive) Color.White else Color.White.copy(alpha = 0.75f),
                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                        fontSize = 13.sp,
                        maxLines = 1,
                        modifier = Modifier
                            .padding(start = 10.dp)
                            .weight(1f, fill = true)
                    )
                }
            }
        }

        if (!nextProgramTitle.isNullOrBlank()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.White.copy(alpha = 0.05f))
                    .padding(14.dp)
            ) {
                Text(
                    text = "A CONTINUACIÓN",
                    color = LiveRed,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = nextProgramTitle,
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

/** Puntitos de luz sutiles y estáticos sobre el fondo oscuro, para darle textura sin distraer */
@Composable
private fun SubtleParticles(modifier: Modifier = Modifier) {
    val particles = remember {
        List(28) {
            Triple(Random.nextFloat(), Random.nextFloat(), Random.nextFloat() * 1.6f + 0.6f)
        }
    }
    Canvas(modifier = modifier) {
        particles.forEach { (xFrac, yFrac, radius) ->
            drawCircle(
                color = Color.White.copy(alpha = 0.05f),
                radius = radius.dp.toPx(),
                center = Offset(size.width * xFrac, size.height * yFrac)
            )
        }
    }
}
