package com.jetgo.tv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.jetgo.tv.data.model.Channel
import com.jetgo.tv.ui.theme.SurfaceDark
import kotlinx.coroutines.delay

/**
 * Carrusel vertical con los logos de los canales, que se va desplazando solo de abajo hacia
 * arriba (como una cinta), en bucle. Puramente decorativo/informativo, al lado del carrusel
 * de películas y series.
 */
@Composable
fun ChannelLogoCarousel(
    channels: List<Channel>,
    modifier: Modifier = Modifier
) {
    val validChannels = channels.filter { !it.logoUrl.isNullOrBlank() }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(SurfaceDark.copy(alpha = 0.85f))
    ) {
        if (validChannels.isNotEmpty()) {
            // Se repite la lista varias veces para que el desplazamiento en bucle se vea continuo
            val loopedChannels = remember(validChannels) {
                List(20) { validChannels }.flatten()
            }
            val listState = rememberLazyListState()

            LaunchedEffect(loopedChannels) {
                var index = 0
                while (true) {
                    delay(1800)
                    index = (index + 1) % (loopedChannels.size - 5)
                    listState.animateScrollToItem(index)
                }
            }

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(loopedChannels) { channel ->
                    Box(
                        modifier = Modifier
                            .padding(vertical = 6.dp, horizontal = 8.dp)
                            .size(44.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.White.copy(alpha = 0.06f)),
                        contentAlignment = Alignment.Center
                    ) {
                        AsyncImage(
                            model = channel.logoUrl,
                            contentDescription = channel.name,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize().padding(6.dp)
                        )
                    }
                }
            }
        }
    }
}
