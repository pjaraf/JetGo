package com.jetgo.tv.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import com.jetgo.tv.ui.theme.SurfaceDark
import kotlinx.coroutines.delay

/**
 * Carrusel automático (se va cambiando solo) con las películas/series más nuevas del catálogo.
 * Se usa al lado del reproductor en el Inicio de TV, ajustado a su misma altura.
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
                .clip(RoundedCornerShape(10.dp))
                .background(SurfaceDark)
        )
        return
    }

    val pagerState = rememberPagerState(pageCount = { validItems.size })

    LaunchedEffect(validItems) {
        while (true) {
            delay(4500)
            if (validItems.isNotEmpty()) {
                val next = (pagerState.currentPage + 1) % validItems.size
                pagerState.animateScrollToPage(next)
            }
        }
    }

    HorizontalPager(
        state = pagerState,
        modifier = modifier
    ) { page ->
        val item = validItems[page]
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(10.dp))
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
