package com.jetgo.tv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.jetgo.tv.data.model.ContentItem
import com.jetgo.tv.data.model.MovieDetail
import com.jetgo.tv.player.PlayerManager
import com.jetgo.tv.ui.components.LanguageTracksDialog
import com.jetgo.tv.ui.components.SpaceBackground
import com.jetgo.tv.ui.theme.BackgroundDark
import com.jetgo.tv.ui.theme.FocusOrange
import com.jetgo.tv.ui.theme.SurfaceDark

@Composable
fun TvMovieDetailScreen(
    state: MovieDetailUiState,
    playerManager: PlayerManager,
    isFavorite: Boolean,
    recommendations: List<ContentItem>,
    onBack: () -> Unit,
    onToggleFavorite: () -> Unit,
    onEnterFullscreen: () -> Unit,
    onRecommendationClick: (ContentItem) -> Unit,
    isFullscreen: Boolean = false
) {
    // Con el control remoto, "Atrás" debe volver a la grilla de películas (no cerrar la app
    // ni saltarse pasos). Solo activo cuando NO está en pantalla completa (ahí manda el
    // BackHandler de FullscreenPlayerEffect, que cierra la pantalla completa primero).
    androidx.activity.compose.BackHandler(enabled = !isFullscreen) { onBack() }

    Box(modifier = Modifier.fillMaxSize()) {
        SpaceBackground(modifier = Modifier.fillMaxSize())
        when {
            state.isLoading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            state.errorMessage != null -> Column(
                modifier = Modifier.align(Alignment.Center).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(text = state.errorMessage, color = Color.Red)
            }
            state.detail != null -> TvMovieDetailContent(
                detail = state.detail,
                playerManager = playerManager,
                isFavorite = isFavorite,
                recommendations = recommendations,
                onBack = onBack,
                onToggleFavorite = onToggleFavorite,
                onEnterFullscreen = onEnterFullscreen,
                onRecommendationClick = onRecommendationClick,
                isFullscreen = isFullscreen
            )
        }
    }
}

@Composable
private fun TvMovieDetailContent(
    detail: MovieDetail,
    playerManager: PlayerManager,
    isFavorite: Boolean,
    recommendations: List<ContentItem>,
    onBack: () -> Unit,
    onToggleFavorite: () -> Unit,
    onEnterFullscreen: () -> Unit,
    onRecommendationClick: (ContentItem) -> Unit,
    isFullscreen: Boolean = false
) {
    var sinopsisExpanded by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        SpaceBackground(modifier = Modifier.fillMaxSize())

        // ---- Fondo: la carátula ajustada a 16:9, completa y sin recortes ----
        if (!detail.coverUrl.isNullOrBlank()) {
            AsyncImage(
                model = detail.coverUrl,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .align(Alignment.TopCenter)
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(BackgroundDark.copy(alpha = 0.55f), BackgroundDark.copy(alpha = 0.97f))
                        )
                    )
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 28.dp, end = 28.dp, top = 20.dp, bottom = 48.dp)
        ) {

            Row(modifier = Modifier.fillMaxWidth()) {
                // ---- Columna izquierda: info ----
                Column(modifier = Modifier.weight(1.2f).padding(end = 24.dp)) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(detail.name, color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                    if (!detail.rating.isNullOrBlank()) {
                        Text(
                            "  ${detail.rating}",
                            color = FocusOrange,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Text(
                    text = listOfNotNull(
                        detail.country?.takeIf { it.isNotBlank() },
                        detail.releaseDate?.takeIf { it.isNotBlank() },
                        detail.name
                    ).joinToString("  |  "),
                    color = Color(0xFFCCCCCC),
                    fontSize = 14.sp,
                    modifier = Modifier.padding(top = 6.dp)
                )

                if (!detail.genre.isNullOrBlank()) {
                    Text("Género: ", color = Color.Gray, fontSize = 13.sp, modifier = Modifier.padding(top = 12.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        items(detail.genre.split(",").map { it.trim() }.filter { it.isNotBlank() }) { genre ->
                            Box(
                                modifier = Modifier
                                    .background(SurfaceDark, RoundedCornerShape(20.dp))
                                    .padding(horizontal = 14.dp, vertical = 6.dp)
                            ) {
                                Text(genre, color = Color.White, fontSize = 12.sp)
                            }
                        }
                    }
                }

                if (!detail.director.isNullOrBlank()) {
                    Text(
                        "Director: ${detail.director}",
                        color = Color(0xFFCCCCCC),
                        fontSize = 13.sp,
                        modifier = Modifier.padding(top = 14.dp)
                    )
                }
                if (!detail.cast.isNullOrBlank()) {
                    Text(
                        "Actores: ${detail.cast}",
                        color = Color(0xFFCCCCCC),
                        fontSize = 13.sp,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }

                if (!detail.plot.isNullOrBlank()) {
                    Column(modifier = Modifier.padding(top = 14.dp)) {
                        Text(
                            text = "Sinopsis: ${detail.plot}",
                            color = Color(0xFFCCCCCC),
                            fontSize = 13.sp,
                            maxLines = if (sinopsisExpanded) Int.MAX_VALUE else 2
                        )
                        Text(
                            text = if (sinopsisExpanded) "Menos" else "Más",
                            color = FocusOrange,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .padding(top = 4.dp)
                                .clickable { sinopsisExpanded = !sinopsisExpanded }
                        )
                    }
                }
            }

            // ---- Columna derecha: video (empieza a reproducirse automáticamente) ----
            Box(
                modifier = Modifier
                    .weight(1f)
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.Black)
            ) {
                if (!isFullscreen) {
                    AndroidView(
                        factory = { context ->
                            PlayerView(context).apply {
                                player = playerManager.exoPlayer
                                useController = false
                            }
                        },
                        update = { view ->
                            if (view.player !== playerManager.exoPlayer) {
                                view.player = playerManager.exoPlayer
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                    playerManager.playbackError.value?.let { errorMsg ->
                        Box(
                            modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.85f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = errorMsg,
                                color = Color.White,
                                fontSize = 13.sp,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
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

        // ---- Barra de acciones ----
        Row(
            modifier = Modifier.padding(top = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            MovieActionChip(
                icon = Icons.Default.Fullscreen,
                label = "Pantalla completa",
                onClick = onEnterFullscreen,
                requestInitialFocus = true
            )
            MovieActionChip(
                icon = Icons.Default.Language,
                label = "Idioma y subtítulos",
                onClick = { showLanguageDialog = true }
            )
        }
    }
    }
}

@Composable
private fun MovieActionChip(
    icon: ImageVector,
    label: String,
    highlighted: Boolean = false,
    requestInitialFocus: Boolean = false,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    if (requestInitialFocus) {
        LaunchedEffect(Unit) {
            try { focusRequester.requestFocus() } catch (e: Exception) { /* ignorar */ }
        }
    }

    Row(
        modifier = Modifier
            .focusRequester(focusRequester)
            .onFocusChanged { focused = it.isFocused }
            .clip(RoundedCornerShape(8.dp))
            .background(if (highlighted || focused) FocusOrange else SurfaceDark)
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(18.dp))
        Text(label, color = Color.White, fontSize = 13.sp, modifier = Modifier.padding(start = 8.dp))
    }
}
