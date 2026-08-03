package com.jetgo.tv.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.jetgo.tv.data.model.ContentItem
import com.jetgo.tv.data.model.ContentType
import com.jetgo.tv.ui.theme.FocusOrange
import com.jetgo.tv.ui.theme.SurfaceDark
import kotlinx.coroutines.delay

/**
 * Carrusel automático (se va cambiando solo) con las películas/series más nuevas del catálogo.
 * Se usa al lado del reproductor en el Inicio de TV, ajustado a su misma altura, con el mismo
 * marco naranja que el reproductor en vivo.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NewestContentCarousel(
    items: List<ContentItem>,
    onItemClick: (ContentItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val validItems = items.filter { !it.imageUrl.isNullOrBlank() }

    if (validItems.isEmpty()) {
        Box(
            modifier = modifier
                .clip(RoundedCornerShape(6.dp))
                .background(SurfaceDark)
                .border(width = 3.dp, color = FocusOrange, shape = RoundedCornerShape(6.dp))
        )
        return
    }

    val pagerState = rememberPagerState(pageCount = { validItems.size })
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (isFocused) 1.05f else 1f, label = "carouselZoom")

    LaunchedEffect(validItems) {
        while (true) {
            delay(4500)
            if (validItems.isNotEmpty()) {
                val next = (pagerState.currentPage + 1) % validItems.size
                pagerState.animateScrollToPage(next)
            }
        }
    }

    Box(
        modifier = modifier
            .scale(scale)
            .clip(RoundedCornerShape(6.dp))
            .border(width = 3.dp, color = FocusOrange, shape = RoundedCornerShape(6.dp))
            .background(SurfaceDark)
            .focusable()
            .onFocusChanged { isFocused = it.isFocused }
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            val item = validItems[page]
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable { onItemClick(item) }
            ) {
                AsyncImage(
                    model = item.imageUrl,
                    contentDescription = item.name,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().background(SurfaceDark)
                )

                // Etiqueta arriba: la categoría real del servidor (con respaldo por tipo)
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(10.dp)
                        .background(FocusOrange, RoundedCornerShape(4.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = item.categoryName?.takeIf { it.isNotBlank() }
                            ?: if (item.type == ContentType.SERIES) "Serie" else "Película",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f)))
                        )
                        .padding(12.dp)
                ) {
                    Text(
                        text = item.name,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        maxLines = 1
                    )
                }
            }
        }
    }
}
