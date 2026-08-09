package com.jetgo.tv.ui.screens.phone

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.jetgo.tv.data.model.ContentItem
import com.jetgo.tv.ui.screens.HomeCatalogState
import com.jetgo.tv.ui.theme.BackgroundDark
import com.jetgo.tv.ui.theme.FocusOrange
import com.jetgo.tv.ui.theme.SurfaceDark
import kotlinx.coroutines.delay

private enum class InicioTab(val label: String) {
    RECOMENDADOS("Recomendados"),
    PELICULAS("Películas"),
    SERIES("Series"),
    ANIME("Anime")
}

@Composable
fun PhoneInicioScreen(
    catalog: HomeCatalogState,
    onEnterScreen: () -> Unit,
    onItemClick: (ContentItem) -> Unit,
    onSearchClick: () -> Unit
) {
    LaunchedEffect(Unit) { onEnterScreen() }

    var tab by remember { mutableStateOf(InicioTab.RECOMENDADOS) }

    val carouselItems = remember(tab, catalog) {
        when (tab) {
            InicioTab.RECOMENDADOS -> (catalog.movies + catalog.series).shuffled().take(8)
            InicioTab.PELICULAS -> catalog.movies.take(8)
            InicioTab.SERIES -> catalog.series.take(8)
            InicioTab.ANIME -> catalog.anime.take(8)
        }.filter { !it.imageUrl.isNullOrBlank() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
    ) {
        // ---- Buscador ----
        OutlinedTextField(
            value = "",
            onValueChange = {},
            readOnly = true,
            enabled = false,
            placeholder = { Text("Buscar por título", color = Color.Gray) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color.Gray) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp)
                .clickable { onSearchClick() }
        )

        // ---- Pestañas ----
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            InicioTab.values().forEach { t ->
                Text(
                    text = t.label,
                    color = if (t == tab) FocusOrange else Color.Gray,
                    fontWeight = if (t == tab) FontWeight.Bold else FontWeight.Normal,
                    fontSize = 15.sp,
                    modifier = Modifier.clickable { tab = t }
                )
            }
        }

        if (catalog.isLoading) {
            Box(modifier = Modifier.fillMaxWidth().height(200.dp)) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                // ---- Carrusel automático de destacados ----
                if (carouselItems.isNotEmpty()) {
                    FeaturedCarousel(items = carouselItems, onItemClick = onItemClick)
                }

                // ---- Filas de recomendados ----
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    if (tab == InicioTab.RECOMENDADOS || tab == InicioTab.PELICULAS) {
                        RecommendedRow(title = "Películas recomendadas", items = catalog.movies, onItemClick = onItemClick)
                    }
                    if (tab == InicioTab.RECOMENDADOS || tab == InicioTab.SERIES) {
                        RecommendedRow(title = "Series recomendadas", items = catalog.series, onItemClick = onItemClick)
                    }
                    if (tab == InicioTab.ANIME) {
                        RecommendedRow(title = "Anime recomendado", items = catalog.anime, onItemClick = onItemClick)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FeaturedCarousel(items: List<ContentItem>, onItemClick: (ContentItem) -> Unit) {
    val pagerState = rememberPagerState(pageCount = { items.size })

    // Auto-avance del carrusel cada 4 segundos
    LaunchedEffect(items) {
        while (true) {
            delay(4000)
            if (items.isNotEmpty()) {
                val next = (pagerState.currentPage + 1) % items.size
                pagerState.animateScrollToPage(next)
            }
        }
    }

    Column {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .aspectRatio(16f / 9f)
        ) { page ->
            val item = items[page]
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(14.dp))
                    .clickable { onItemClick(item) }
            ) {
                AsyncImage(
                    model = item.imageUrl,
                    contentDescription = item.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().background(SurfaceDark)
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f))
                            )
                        )
                        .padding(12.dp)
                ) {
                    Text(
                        text = item.name,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        maxLines = 1
                    )
                }
            }
        }

        // Indicador de puntos
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            items.indices.forEach { index ->
                Box(
                    modifier = Modifier
                        .padding(horizontal = 3.dp)
                        .size(if (index == pagerState.currentPage) 8.dp else 6.dp)
                        .clip(CircleShape)
                        .background(if (index == pagerState.currentPage) FocusOrange else Color.Gray)
                )
            }
        }
    }
}

@Composable
private fun RecommendedRow(title: String, items: List<ContentItem>, onItemClick: (ContentItem) -> Unit) {
    if (items.isEmpty()) return
    Column(modifier = Modifier.padding(bottom = 16.dp)) {
        Text(
            text = title,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(horizontal = 16.dp)
        ) {
            items(items) { item ->
                Column(
                    modifier = Modifier
                        .width(110.dp)
                        .clickable { onItemClick(item) }
                ) {
                    if (!item.imageUrl.isNullOrBlank()) {
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
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(2f / 3f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(SurfaceDark)
                        )
                    }
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
