package com.jetgo.tv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.outlined.Face
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.jetgo.tv.data.model.Channel
import com.jetgo.tv.data.model.ContentItem
import com.jetgo.tv.player.PlayerManager
import com.jetgo.tv.ui.components.CategoryCard
import com.jetgo.tv.ui.components.HeightMatchedPlayerRow
import com.jetgo.tv.ui.components.NewestContentCarousel
import com.jetgo.tv.ui.components.PlayerPanel
import com.jetgo.tv.ui.theme.BackgroundDark

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
    isFullscreen: Boolean = false
) {
    val vivoFocusRequester = remember { androidx.compose.ui.focus.FocusRequester() }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        try { vivoFocusRequester.requestFocus() } catch (e: Exception) { /* ignorar si aún no está listo */ }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {

            // ---- Fila superior: reproductor + carrusel de contenido nuevo (misma altura) ----
            HeightMatchedPlayerRow(
                modifier = Modifier.fillMaxWidth().weight(1f),
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

            androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(20.dp))

            // ---- Fila inferior: categorías (Vivo, Serie, Película, Anime, Especial) ----
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                CategoryCard(
                    label = "VIVO", icon = Icons.Default.PlayCircle,
                    gradientStart = Color(0xFFFF6B5B), gradientEnd = Color(0xFFE5493B),
                    modifier = Modifier.weight(1f),
                    focusRequester = vivoFocusRequester
                ) { onLiveClick() }

                CategoryCard(
                    label = "SERIE", icon = Icons.Default.Tv,
                    gradientStart = Color(0xFF5BC8FF), gradientEnd = Color(0xFF2E8FE0),
                    modifier = Modifier.weight(1f)
                ) { onCategoryClick("SERIES") }

                CategoryCard(
                    label = "PELÍCULA", icon = Icons.Default.Movie,
                    gradientStart = Color(0xFF4FE0B0), gradientEnd = Color(0xFF22B88C),
                    modifier = Modifier.weight(1f)
                ) { onCategoryClick("MOVIE") }

                CategoryCard(
                    label = "SEGUIR VIENDO", icon = Icons.Default.History,
                    gradientStart = Color(0xFF8E8CFF), gradientEnd = Color(0xFF5B57E0),
                    modifier = Modifier.weight(1f)
                ) { onContinueWatchingClick() }

                CategoryCard(
                    label = "AJUSTES", icon = Icons.Default.Settings,
                    gradientStart = Color(0xFFFFA24E), gradientEnd = Color(0xFFE77A1F),
                    modifier = Modifier.weight(1f)
                ) { onSettingsClick() }
            }
        }
    }
}
