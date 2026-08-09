package com.jetgo.tv.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jetgo.tv.data.model.Channel
import com.jetgo.tv.data.model.ContentItem
import com.jetgo.tv.player.PlayerManager
import com.jetgo.tv.ui.components.ChannelLogoCarousel
import com.jetgo.tv.ui.components.HeightMatchedPlayerRow
import com.jetgo.tv.ui.components.NewestContentCarousel
import com.jetgo.tv.ui.components.PlayerPanel
import com.jetgo.tv.ui.components.SpaceBackground
import com.jetgo.tv.ui.theme.SurfaceDark
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val SelectedRed = Color(0xFFE53935)

@Composable
fun HomeScreen(
    playerManager: PlayerManager,
    liveChannels: List<Channel>,
    newestItems: List<ContentItem> = emptyList(),
    onItemClick: (ContentItem) -> Unit = {},
    onChannelSelected: (Channel) -> Unit,
    onCategoryClick: (String) -> Unit,
    onLiveClick: () -> Unit = {},
    onSearchClick: () -> Unit = {},
    onFavoritesClick: () -> Unit = {},
    onContinueWatchingClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
    isFullscreen: Boolean = false,
    hiddenTypes: Set<String> = emptySet()
) {
    val vivoFocusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        try { vivoFocusRequester.requestFocus() } catch (e: Exception) { /* ignorar si aún no está listo */ }
    }

    var currentTime by remember { mutableStateOf(formatNow()) }
    LaunchedEffect(Unit) {
        while (true) {
            currentTime = formatNow()
            delay(30_000)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        SpaceBackground(modifier = Modifier.fillMaxSize())

        BoxWithConstraints(modifier = Modifier.fillMaxSize().padding(20.dp)) {
            val spacing = 14.dp
            val buttonCount = 5
            val buttonWidth = ((maxWidth - spacing * (buttonCount - 1)) / buttonCount).coerceAtLeast(0.dp)

            Column(modifier = Modifier.fillMaxSize()) {

                // ---- Cabecera: logo + nombre a la izquierda, hora/fecha a la derecha ----
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(id = com.jetgo.tv.R.drawable.logo_splash),
                            contentDescription = "JetGo",
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(9.dp))
                        )
                        Text(
                            text = "JetGo",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 10.dp)
                        )
                    }
                    Text(
                        text = currentTime,
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // ---- Reproductor + carrusel: el carrusel calza exacto con el ancho de un botón ----
                Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    HeightMatchedPlayerRow(
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        sideAspectRatio = 2f / 3f,
                        playerContent = {
                            PlayerPanel(
                                playerManager = playerManager,
                                showVideo = !isFullscreen,
                                modifier = Modifier.fillMaxWidth()
                            )
                        },
                        sideContent = {
                            NewestContentCarousel(
                                items = newestItems,
                                onItemClick = onItemClick,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    )

                    Spacer(modifier = Modifier.width(4.dp))

                    ChannelLogoCarousel(
                        channels = liveChannels,
                        modifier = Modifier.width(64.dp).fillMaxHeight()
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // ---- Botones inferiores: neutros por defecto, rojo el que está seleccionado ----
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(spacing)
                ) {
                    HomeActionButton(
                        label = "VIVO", icon = Icons.Default.PlayCircle,
                        width = buttonWidth,
                        focusRequester = vivoFocusRequester
                    ) { onLiveClick() }

                    HomeActionButton(
                        label = "SERIE", icon = Icons.Default.Tv,
                        width = buttonWidth
                    ) { onCategoryClick("SERIES") }

                    HomeActionButton(
                        label = "PELÍCULA", icon = Icons.Default.Movie,
                        width = buttonWidth
                    ) { onCategoryClick("MOVIE") }

                    HomeActionButton(
                        label = "SEGUIR VIENDO", icon = Icons.Default.History,
                        width = buttonWidth
                    ) { onContinueWatchingClick() }

                    HomeActionButton(
                        label = "AJUSTES", icon = Icons.Default.Settings,
                        width = buttonWidth
                    ) { onSettingsClick() }
                }
            }
        }
    }
}

/** Botón inferior del Inicio: color neutro en reposo, ROJO cuando el control lo enfoca */
@Composable
private fun HomeActionButton(
    label: String,
    icon: ImageVector,
    width: Dp,
    focusRequester: FocusRequester? = null,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val actualFocusRequester = focusRequester ?: remember { FocusRequester() }

    Column(
        modifier = Modifier
            .width(width)
            .clip(RoundedCornerShape(14.dp))
            .background(if (isFocused) SelectedRed else SurfaceDark.copy(alpha = 0.85f))
            .focusRequester(actualFocusRequester)
            .onFocusChanged { focusState -> isFocused = focusState.isFocused }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(24.dp))
        Text(
            text = label,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 6.dp)
        )
    }
}

private fun formatNow(): String =
    SimpleDateFormat("HH:mm  ·  dd/MM/yyyy", Locale.getDefault()).format(Date())
