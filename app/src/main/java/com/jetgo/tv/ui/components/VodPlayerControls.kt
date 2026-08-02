package com.jetgo.tv.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.jetgo.tv.player.PlayerManager
import kotlinx.coroutines.delay

/**
 * Controles de reproducción para películas/series/anime (VOD): barra inferior con carátula,
 * título, tiempo transcurrido/total y botones de retroceder/reproducir-pausar/adelantar/idioma,
 * más un botón grande de play/pause al centro. Se ocultan solos tras unos segundos sin uso.
 *
 * La navegación con el control remoto NO depende del foco automático de Android (que en la
 * práctica falla en varios TV Box): izquierda/derecha del control mueven manualmente cuál
 * botón está resaltado, y el centro/OK del control lo activa. Atrás siempre sale directo.
 */
@Composable
fun VodPlayerControls(
    playerManager: PlayerManager,
    posterUrl: String?,
    title: String,
    onExit: (() -> Unit)?,
    onOpenLanguageMenu: () -> Unit,
    modifier: Modifier = Modifier
) {
    var controlsVisible by remember { mutableStateOf(true) }
    var positionMs by remember { mutableStateOf(0L) }
    var durationMs by remember { mutableStateOf(0L) }
    var isSeeking by remember { mutableStateOf(false) }
    var seekPreview by remember { mutableStateOf(0f) }
    val isPlaying by playerManager.isPlaying
    val focusRequester = remember { FocusRequester() }

    // Botones disponibles en la barra (el de "Salir" solo existe en pantalla completa)
    val buttonCount = if (onExit != null) 5 else 4
    var selectedIndex by remember { mutableStateOf(if (onExit != null) 2 else 1) } // arranca en play/pause

    fun activateSelected() {
        val actions: List<() -> Unit> = buildList {
            if (onExit != null) add { onExit() }
            add { playerManager.seekBackward() }
            add { playerManager.togglePlayPause() }
            add { playerManager.seekForward() }
            add { onOpenLanguageMenu() }
        }
        actions.getOrNull(selectedIndex)?.invoke()
    }

    LaunchedEffect(Unit) {
        try { focusRequester.requestFocus() } catch (e: Exception) { /* ignorar */ }
    }

    LaunchedEffect(Unit) {
        while (true) {
            if (!isSeeking) {
                positionMs = playerManager.currentPositionMs()
                durationMs = playerManager.durationMs()
            }
            delay(500)
        }
    }

    LaunchedEffect(controlsVisible, isPlaying) {
        if (controlsVisible && isPlaying) {
            delay(6000)
            controlsVisible = false
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
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
                    Key.Back -> {
                        if (onExit != null) { onExit(); true } else false
                    }
                    Key.DirectionLeft -> {
                        controlsVisible = true
                        selectedIndex = (selectedIndex - 1).coerceAtLeast(0)
                        true
                    }
                    Key.DirectionRight -> {
                        controlsVisible = true
                        selectedIndex = (selectedIndex + 1).coerceAtMost(buttonCount - 1)
                        true
                    }
                    Key.DirectionUp, Key.DirectionDown -> {
                        controlsVisible = true
                        true
                    }
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                        if (controlsVisible) {
                            activateSelected()
                        } else {
                            controlsVisible = true
                        }
                        true
                    }
                    else -> false
                }
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { controlsVisible = !controlsVisible }
    ) {
        // ---- Botón central de play/pause ----
        AnimatedVisibility(
            visible = controlsVisible,
            modifier = Modifier.align(Alignment.Center),
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            IconButton(
                onClick = { playerManager.togglePlayPause() },
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFE5493B))
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pausar" else "Reproducir",
                    tint = Color.White,
                    modifier = Modifier.size(38.dp)
                )
            }
        }

        // ---- Barra inferior ----
        AnimatedVisibility(
            visible = controlsVisible,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f)))
                    )
                    .padding(16.dp)
            ) {
                // Barra de progreso
                val sliderPosition = if (isSeeking) seekPreview else positionMs.toFloat()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        formatDurationLabel(sliderPosition.toLong()),
                        color = Color.White,
                        fontSize = 12.sp,
                        modifier = Modifier.width(50.dp)
                    )
                    Slider(
                        value = sliderPosition,
                        onValueChange = {
                            isSeeking = true
                            seekPreview = it
                        },
                        onValueChangeFinished = {
                            playerManager.seekTo(seekPreview.toLong())
                            isSeeking = false
                        },
                        valueRange = 0f..(durationMs.toFloat().coerceAtLeast(1f)),
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFFE5493B),
                            activeTrackColor = Color(0xFFE5493B)
                        ),
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                    )
                    Text(
                        formatDurationLabel(durationMs),
                        color = Color.White,
                        fontSize = 12.sp,
                        modifier = Modifier.width(50.dp)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Carátula + título
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        if (!posterUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = posterUrl,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(width = 40.dp, height = 56.dp)
                                    .clip(RoundedCornerShape(6.dp))
                            )
                        }
                        Text(
                            text = title,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            maxLines = 1,
                            modifier = Modifier.padding(start = 10.dp)
                        )
                    }

                    // Botones de control (el resaltado naranja indica cuál activa el centro/OK)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        var idx = 0
                        if (onExit != null) {
                            VodControlButton(
                                icon = Icons.Default.ArrowBack,
                                description = "Salir",
                                selected = selectedIndex == idx,
                                onClick = onExit
                            )
                            idx++
                        }
                        VodControlButton(
                            icon = Icons.Default.FastRewind,
                            description = "Retroceder",
                            selected = selectedIndex == idx,
                            onClick = { playerManager.seekBackward() }
                        )
                        idx++
                        VodControlButton(
                            icon = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            description = "Reproducir/Pausar",
                            selected = selectedIndex == idx,
                            onClick = { playerManager.togglePlayPause() }
                        )
                        idx++
                        VodControlButton(
                            icon = Icons.Default.FastForward,
                            description = "Adelantar",
                            selected = selectedIndex == idx,
                            onClick = { playerManager.seekForward() }
                        )
                        idx++
                        VodControlButton(
                            icon = Icons.Default.Language,
                            description = "Idioma y subtítulos",
                            selected = selectedIndex == idx,
                            onClick = onOpenLanguageMenu
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun VodControlButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .padding(2.dp)
            .border(
                width = if (selected) 2.dp else 0.dp,
                color = if (selected) Color(0xFFE5493B) else Color.Transparent,
                shape = CircleShape
            )
    ) {
        Icon(icon, contentDescription = description, tint = Color.White)
    }
}
