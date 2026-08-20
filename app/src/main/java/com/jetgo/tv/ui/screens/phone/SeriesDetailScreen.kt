package com.jetgo.tv.ui.screens.phone

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import org.videolan.libvlc.util.VLCVideoLayout
import coil.compose.AsyncImage
import com.jetgo.tv.data.model.ContentItem
import com.jetgo.tv.data.model.SeriesDetail
import com.jetgo.tv.data.model.SeriesEpisode
import com.jetgo.tv.player.PlayerManager
import com.jetgo.tv.ui.components.formatDurationLabel
import com.jetgo.tv.ui.screens.SeriesDetailUiState
import com.jetgo.tv.ui.theme.BackgroundDark
import com.jetgo.tv.ui.theme.FocusOrange
import com.jetgo.tv.ui.theme.SurfaceDark
import kotlinx.coroutines.delay

@Composable
fun SeriesDetailScreen(
    state: SeriesDetailUiState,
    playerManager: PlayerManager,
    recommendations: List<ContentItem>,
    onBack: () -> Unit,
    onSelectSeason: (Int) -> Unit,
    onPlayEpisode: (String) -> Unit,
    onEnterFullscreen: () -> Unit,
    onRecommendationClick: (ContentItem) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
    ) {
        when {
            state.isLoading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            state.errorMessage != null -> Text(
                text = state.errorMessage,
                color = Color.Red,
                modifier = Modifier.align(Alignment.Center).padding(24.dp)
            )
            state.detail != null -> SeriesDetailContent(
                detail = state.detail,
                selectedSeason = state.selectedSeason,
                currentEpisodeId = state.currentEpisodeId,
                playerManager = playerManager,
                recommendations = recommendations,
                onBack = onBack,
                onSelectSeason = onSelectSeason,
                onPlayEpisode = onPlayEpisode,
                onEnterFullscreen = onEnterFullscreen,
                onRecommendationClick = onRecommendationClick
            )
        }
    }
}

@Composable
private fun SeriesDetailContent(
    detail: SeriesDetail,
    selectedSeason: Int,
    currentEpisodeId: String?,
    playerManager: PlayerManager,
    recommendations: List<ContentItem>,
    onBack: () -> Unit,
    onSelectSeason: (Int) -> Unit,
    onPlayEpisode: (String) -> Unit,
    onEnterFullscreen: () -> Unit,
    onRecommendationClick: (ContentItem) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        // ---- Reproductor con controles simples propios (VLC no trae barra nativa) ----
        Box(modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Color.Black)) {
            var videoLayout by remember { mutableStateOf<VLCVideoLayout?>(null) }

            AndroidView(
                factory = { context ->
                    VLCVideoLayout(context).also { videoLayout = it }
                },
                modifier = Modifier.fillMaxSize()
            )

            DisposableEffect(videoLayout) {
                val layout = videoLayout
                if (layout != null) {
                    try { playerManager.vodPlayer.attachViews(layout, null, true, false) } catch (e: Exception) { /* ignorar */ }
                }
                onDispose {
                    try { playerManager.vodPlayer.detachViews() } catch (e: Exception) { /* ignorar */ }
                }
            }

            var controlsVisible by remember { mutableStateOf(true) }
            var positionMs by remember { mutableStateOf(0L) }
            var durationMs by remember { mutableStateOf(0L) }
            var isSeeking by remember { mutableStateOf(false) }
            var seekPreview by remember { mutableStateOf(0f) }
            val isPlaying by playerManager.isPlaying

            LaunchedEffect(Unit) {
                while (true) {
                    if (!isSeeking) {
                        positionMs = playerManager.currentPositionMs()
                        durationMs = playerManager.durationMs()
                    }
                    delay(500)
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { controlsVisible = !controlsVisible }
            ) {
                if (controlsVisible) {
                    IconButton(
                        onClick = { playerManager.togglePlayPause() },
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFE5493B))
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Pausar" else "Reproducir",
                            tint = Color.White,
                            modifier = Modifier.size(34.dp)
                        )
                    }

                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .background(Color.Black.copy(alpha = 0.55f))
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val sliderPosition = if (isSeeking) seekPreview else positionMs.toFloat()
                        Text(formatDurationLabel(sliderPosition.toLong()), color = Color.White, fontSize = 11.sp)
                        Slider(
                            value = sliderPosition,
                            onValueChange = { isSeeking = true; seekPreview = it },
                            onValueChangeFinished = {
                                playerManager.seekTo(seekPreview.toLong())
                                isSeeking = false
                            },
                            valueRange = 0f..(durationMs.toFloat().coerceAtLeast(1f)),
                            colors = SliderDefaults.colors(
                                thumbColor = Color(0xFFE5493B),
                                activeTrackColor = Color(0xFFE5493B)
                            ),
                            modifier = Modifier.weight(1f).padding(horizontal = 6.dp)
                        )
                        Text(formatDurationLabel(durationMs), color = Color.White, fontSize = 11.sp)
                    }
                }
            }

            IconButton(onClick = onBack, modifier = Modifier.align(Alignment.TopStart).padding(4.dp)) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Volver", tint = Color.White)
            }
            IconButton(onClick = onEnterFullscreen, modifier = Modifier.align(Alignment.TopEnd).padding(4.dp)) {
                Icon(Icons.Default.Fullscreen, contentDescription = "Pantalla completa", tint = Color.White)
            }
        }
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = detail.name, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            if (!detail.rating.isNullOrBlank() || !detail.genre.isNullOrBlank() || !detail.releaseDate.isNullOrBlank()) {
                Text(
                    text = listOfNotNull(
                        detail.releaseDate?.take(4)?.takeIf { it.isNotBlank() },
                        detail.genre
                    ).joinToString("  ·  "),
                    color = Color.Gray,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            // ---- Selector de temporada ----
            val seasons = detail.episodesBySeason.keys.sorted()
            if (seasons.size > 1) {
                var expanded by remember { mutableStateOf(false) }
                Box(modifier = Modifier.padding(top = 16.dp)) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(SurfaceDark)
                            .clickable { expanded = true }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Temporada $selectedSeason", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        seasons.forEach { season ->
                            DropdownMenuItem(
                                text = { Text("Temporada $season") },
                                onClick = { onSelectSeason(season); expanded = false }
                            )
                        }
                    }
                }
            } else if (seasons.isNotEmpty()) {
                Text(
                    "Temporada ${seasons.first()}",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 16.dp)
                )
            }
            // ---- Grilla de capítulos ----
            val episodes = detail.episodesBySeason[selectedSeason].orEmpty()
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(top = 14.dp)
            ) {
                items(episodes) { episode ->
                    EpisodeChip(
                        episode = episode,
                        isPlaying = episode.id == currentEpisodeId,
                        onClick = { onPlayEpisode(episode.id) }
                    )
                }
            }
            if (!detail.plot.isNullOrBlank()) {
                Text(
                    text = detail.plot,
                    color = Color(0xFFCCCCCC),
                    fontSize = 14.sp,
                    modifier = Modifier.padding(top = 20.dp)
                )
            }
            if (!detail.director.isNullOrBlank()) {
                Text("Director: ${detail.director}", color = Color.Gray, fontSize = 13.sp, modifier = Modifier.padding(top = 12.dp))
            }
            if (!detail.cast.isNullOrBlank()) {
                Text("Actores: ${detail.cast}", color = Color.Gray, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Share, contentDescription = null, tint = Color.White)
                    Text("Compartir", color = Color.White, fontSize = 12.sp)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Star, contentDescription = null, tint = Color.White)
                    Text("Favoritos", color = Color.White, fontSize = 12.sp)
                }
            }
        }
        if (recommendations.isNotEmpty()) {
            Text(
                "También podría gustarte",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                items(recommendations) { item ->
                    Column(
                        modifier = Modifier
                            .width(110.dp)
                            .clickable { onRecommendationClick(item) }
                    ) {
                        AsyncImage(
                            model = item.imageUrl,
                            contentDescription = item.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(2f / 3f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(SurfaceDark)
                        )
                        Text(
                            text = item.name,
                            color = Color.White,
                            fontSize = 12.sp,
                            maxLines = 2,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EpisodeChip(episode: SeriesEpisode, isPlaying: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(52.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (isPlaying) FocusOrange else SurfaceDark)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        if (isPlaying) {
            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White)
        } else {
            Text(text = "${episode.episodeNum}", color = Color.White, fontWeight = FontWeight.Bold)
        }
    }
}
