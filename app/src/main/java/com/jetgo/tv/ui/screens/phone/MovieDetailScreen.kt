package com.jetgo.tv.ui.screens.phone

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import com.jetgo.tv.ui.screens.MovieDetailUiState
import com.jetgo.tv.ui.theme.BackgroundDark
import com.jetgo.tv.ui.theme.SurfaceDark

/**
 * Ficha de película para teléfono — mismo estilo e interfaz que la ficha de serie
 * (SeriesDetailScreen): reproductor con controles nativos arriba, info abajo, recomendados.
 */
@Composable
fun MovieDetailScreen(
    state: MovieDetailUiState,
    playerManager: PlayerManager,
    recommendations: List<ContentItem>,
    onBack: () -> Unit,
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
            state.detail != null -> MovieDetailContent(
                detail = state.detail,
                playerManager = playerManager,
                recommendations = recommendations,
                onBack = onBack,
                onEnterFullscreen = onEnterFullscreen,
                onRecommendationClick = onRecommendationClick
            )
        }
    }
}

@Composable
private fun MovieDetailContent(
    detail: MovieDetail,
    playerManager: PlayerManager,
    recommendations: List<ContentItem>,
    onBack: () -> Unit,
    onEnterFullscreen: () -> Unit,
    onRecommendationClick: (ContentItem) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {

        // ---- Reproductor con controles nativos (play/pausa, barra de progreso, tiempo) ----
        Box(modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Color.Black)) {
            AndroidView(
                factory = { context ->
                    PlayerView(context).apply {
                        player = playerManager.exoPlayer
                        useController = true
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
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
