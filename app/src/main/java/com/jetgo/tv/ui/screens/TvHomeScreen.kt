package com.jetgo.tv.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.jetgo.tv.data.model.Channel
import com.jetgo.tv.data.model.ContentItem
import com.jetgo.tv.player.PlayerManager
import com.jetgo.tv.ui.components.PlayerPanel
import com.jetgo.tv.ui.components.SpaceBackground
import com.jetgo.tv.ui.theme.SurfaceDark
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val SelectedRed = Color(0xFFE53935)

/**
 * Pantalla principal exclusiva para Android TV inspirada en Netflix:
 * - Banner lateral izquierdo con iconos limpios sin nombre (Vivo, Series, Películas, Seguir viendo, Ajustes, Buscar).
 * - Mini reproductor de canal en vivo ubicado donde estaba el banner principal (Hero).
 * - Carrusel de estrenos 2026 de una sola carátula que rota automáticamente.
 * - Carruseles horizontales de pósters en la parte inferior.
 */
@Composable
fun TvHomeScreen(
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
        delay(250L)
        try { vivoFocusRequester.requestFocus() } catch (e: Exception) {}
    }

    var currentTime by remember { mutableStateOf(formatNow()) }
    LaunchedEffect(Unit) {
        while (true) {
            currentTime = formatNow()
            delay(30_000)
        }
    }

    val featuredItem = newestItems.firstOrNull()
    val release2026Items = newestItems.drop(1).ifEmpty { newestItems }

    var currentReleaseIndex by remember { mutableStateOf(0) }
    LaunchedEffect(release2026Items) {
        if (release2026Items.isNotEmpty()) {
            while (true) {
                delay(4000L)
                currentReleaseIndex = (currentReleaseIndex + 1) % release2026Items.size
            }
        }
    }
    val currentRelease = release2026Items.getOrNull(currentReleaseIndex) ?: featuredItem

    Box(modifier = Modifier.fillMaxSize()) {
        SpaceBackground(modifier = Modifier.fillMaxSize())

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ---- 1. BANNER LATERAL IZQUIERDO (TV): SOLO ICONOS SIN NOMBRE ----
            Column(
                modifier = Modifier
                    .width(64.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Logo superior pequeño
                Image(
                    painter = painterResource(id = com.jetgo.tv.R.drawable.logo_splash),
                    contentDescription = "JetGo",
                    modifier = Modifier.size(32.dp).clip(RoundedCornerShape(8.dp))
                )

                Spacer(modifier = Modifier.height(10.dp))

                SidebarIconButton(
                    icon = Icons.Default.PlayCircle,
                    contentDescription = "Vivo",
                    focusRequester = vivoFocusRequester,
                    onClick = onLiveClick
                )
                SidebarIconButton(
                    icon = Icons.Default.Tv,
                    contentDescription = "Series",
                    onClick = { onCategoryClick("SERIES") }
                )
                SidebarIconButton(
                    icon = Icons.Default.Movie,
                    contentDescription = "Películas",
                    onClick = { onCategoryClick("MOVIE") }
                )
                SidebarIconButton(
                    icon = Icons.Default.History,
                    contentDescription = "Seguir viendo",
                    onClick = onContinueWatchingClick
                )
                SidebarIconButton(
                    icon = Icons.Default.Settings,
                    contentDescription = "Ajustes",
                    onClick = onSettingsClick
                )
                SidebarIconButton(
                    icon = Icons.Default.Search,
                    contentDescription = "Buscar",
                    onClick = onSearchClick
                )
            }

            // ---- 2. CONTENIDO PRINCIPAL DE TV ----
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                // Cabecera superior con hora
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = currentTime,
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // ---- MINI REPRODUCTOR EN VIVO (DONDE ESTABA EL HERO) + CARÁTULA 2026 AUTOMÁTICA ----
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Mini reproductor en vivo (reemplaza al Hero / Original Netflix)
                    Box(
                        modifier = Modifier
                            .weight(1.3f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.Black)
                            .clickable { onLiveClick() }
                    ) {
                        PlayerPanel(
                            playerManager = playerManager,
                            modifier = Modifier.fillMaxSize(),
                            onTap = onLiveClick
                        )
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(12.dp)
                                .background(Color.Red, RoundedCornerShape(4.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "EN VIVO",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Carrusel de Estrenos 2026: una sola carátula cambiando automáticamente
                    Column(
                        modifier = Modifier
                            .weight(0.9f)
                            .fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Estrenos 2026",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .clip(RoundedCornerShape(16.dp))
                                .background(SurfaceDark)
                                .clickable {
                                    if (currentRelease != null) {
                                        onItemClick(currentRelease)
                                    }
                                }
                        ) {
                            if (currentRelease?.imageUrl != null) {
                                AsyncImage(
                                    model = currentRelease.imageUrl,
                                    contentDescription = currentRelease.name,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.verticalGradient(
                                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f)),
                                            startY = 120f
                                        )
                                    ),
                                contentAlignment = Alignment.BottomStart
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp)
                                ) {
                                    Text(
                                        text = "2026",
                                        color = Color.Red,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Black,
                                        letterSpacing = 1.sp
                                    )
                                    Text(
                                        text = currentRelease?.name ?: "Estreno",
                                        color = Color.White,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // ---- CAROUSELS DE CONTENIDO ABAJO (Mi Lista / Recientes) ----
                Text(
                    text = "Mi Lista y Contenido Destacado",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(newestItems) { item ->
                        Box(
                            modifier = Modifier
                                .width(130.dp)
                                .fillMaxHeight(0.9f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(SurfaceDark)
                                .clickable { onItemClick(item) }
                        ) {
                            if (!item.imageUrl.isNullOrBlank()) {
                                AsyncImage(
                                    model = item.imageUrl,
                                    contentDescription = item.name,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.verticalGradient(
                                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f)),
                                            startY = 150f
                                        )
                                    ),
                                contentAlignment = Alignment.BottomStart
                            ) {
                                Text(
                                    text = item.name,
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    modifier = Modifier.padding(8.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Botón lateral del banner TV: solo icono, sin nombre, se pone rojo al enfocar */
@Composable
private fun SidebarIconButton(
    icon: ImageVector,
    contentDescription: String,
    focusRequester: FocusRequester? = null,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val actualFocusRequester = focusRequester ?: remember { FocusRequester() }

    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (isFocused) SelectedRed else Color.Transparent)
            .focusRequester(actualFocusRequester)
            .onFocusChanged { focusState -> isFocused = focusState.isFocused }
            .focusable()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = Color.White,
            modifier = Modifier.size(24.dp)
        )
    }
}

private fun formatNow(): String =
    SimpleDateFormat("HH:mm  ·  dd/MM/yyyy", Locale.getDefault()).format(Date())
