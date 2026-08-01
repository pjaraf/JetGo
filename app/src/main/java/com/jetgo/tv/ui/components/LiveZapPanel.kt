package com.jetgo.tv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jetgo.tv.data.model.Category
import com.jetgo.tv.data.model.ContentItem

enum class ZapPanelMode { HIDDEN, CHANNELS, CATEGORIES }

/**
 * Panel lateral semi-transparente para "zapear" canales sin salir de pantalla completa:
 * 1 toque de izquierda = lista de canales de la categoría actual, 2 toques = categorías.
 */
@Composable
fun LiveZapPanel(
    mode: ZapPanelMode,
    categories: List<Category>,
    channels: List<ContentItem>,
    currentChannelName: String,
    onSelectCategory: (Category) -> Unit,
    onSelectChannel: (ContentItem) -> Unit,
    modifier: Modifier = Modifier
) {
    if (mode == ZapPanelMode.HIDDEN) return

    Box(
        modifier = modifier
            .fillMaxHeight()
            .width(300.dp)
            .background(Color.Black.copy(alpha = 0.80f))
    ) {
        Text(
            text = if (mode == ZapPanelMode.CATEGORIES) "Categorías" else "Canales",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            modifier = Modifier.padding(16.dp)
        )

        LazyColumn(modifier = Modifier.fillMaxWidth().padding(top = 48.dp)) {
            if (mode == ZapPanelMode.CATEGORIES) {
                items(categories) { category ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectCategory(category) }
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Text(category.name, color = Color.White, fontSize = 14.sp)
                    }
                }
            } else {
                items(channels) { channel ->
                    val isCurrent = channel.name == currentChannelName
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(if (isCurrent) Color(0xFFFF7A2E).copy(alpha = 0.25f) else Color.Transparent)
                            .clickable { onSelectChannel(channel) }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = channel.name,
                            color = if (isCurrent) Color(0xFFFF7A2E) else Color.White,
                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 14.sp,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}
