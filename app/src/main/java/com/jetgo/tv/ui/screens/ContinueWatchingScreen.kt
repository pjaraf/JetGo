package com.jetgo.tv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.BackHandler
import coil.compose.AsyncImage
import com.jetgo.tv.data.model.ContentItem
import com.jetgo.tv.ui.theme.BackgroundDark
import com.jetgo.tv.ui.theme.FocusOrange
import com.jetgo.tv.ui.theme.SurfaceDark

@Composable
fun ContinueWatchingScreen(
    items: List<ContentItem>,
    onItemClick: (ContentItem) -> Unit,
    onRemoveItem: (ContentItem) -> Unit,
    onBack: () -> Unit
) {
    BackHandler { onBack() }
    val firstItemFocusRequester = remember { FocusRequester() }
    LaunchedEffect(items) {
        if (items.isNotEmpty()) {
            try { firstItemFocusRequester.requestFocus() } catch (e: Exception) { /* ignorar */ }
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(BackgroundDark)) {
        Text(
            text = "Seguir viendo",
            color = Color.White,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(24.dp)
        )

        if (items.isEmpty()) {
            Text(
                text = "Todavía no has visto ninguna película o serie",
                color = Color.Gray,
                modifier = Modifier.align(Alignment.Center).padding(24.dp)
            )
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 150.dp),
                modifier = Modifier.fillMaxSize().padding(top = 72.dp, start = 16.dp, end = 16.dp)
            ) {
                itemsIndexed(items, key = { _, item -> "${item.type}_${item.id}" }) { index, item ->
                    ContinueWatchingCard(
                        item = item,
                        onClick = { onItemClick(item) },
                        onRemove = { onRemoveItem(item) },
                        focusRequester = if (index == 0) firstItemFocusRequester else null
                    )
                }
            }
        }
    }
}

@Composable
private fun ContinueWatchingCard(
    item: ContentItem,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    focusRequester: FocusRequester? = null
) {
    var focused by remember { mutableStateOf(false) }

    Box(modifier = Modifier.padding(8.dp)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(10.dp))
                .background(SurfaceDark)
                .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
                .onFocusChanged { focused = it.isFocused }
                .border(
                    width = if (focused) 3.dp else 0.dp,
                    color = if (focused) FocusOrange else Color.Transparent,
                    shape = RoundedCornerShape(10.dp)
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick
                )
        ) {
            if (!item.imageUrl.isNullOrBlank()) {
                AsyncImage(
                    model = item.imageUrl,
                    contentDescription = item.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            Text(
                text = item.name,
                color = Color.White,
                fontSize = 12.sp,
                maxLines = 2,
                modifier = Modifier.align(Alignment.BottomStart).padding(6.dp)
            )

            // Botón de basurero, para quitarlo manualmente de "Seguir viendo"
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(28.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.Black.copy(alpha = 0.55f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onRemove
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Quitar de Seguir viendo",
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}
