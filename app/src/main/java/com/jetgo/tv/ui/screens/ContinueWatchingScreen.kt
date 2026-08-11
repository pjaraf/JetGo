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
import com.jetgo.tv.ui.components.SpaceBackground
import com.jetgo.tv.ui.theme.BackgroundDark
import com.jetgo.tv.ui.theme.FocusOrange
import com.jetgo.tv.ui.theme.SurfaceDark

@Composable
fun ContinueWatchingScreen(
    items: List<ContentItem>,
    onItemClick: (ContentItem) -> Unit,
    onRemoveItem: (ContentItem) -> Unit,
    onClearAll: () -> Unit,
    onBack: () -> Unit
) {
    BackHandler { onBack() }
    val firstItemFocusRequester = remember { FocusRequester() }
    var showConfirmClearAll by remember { mutableStateOf(false) }
    LaunchedEffect(items) {
        if (items.isNotEmpty()) {
            try { firstItemFocusRequester.requestFocus() } catch (e: Exception) { /* ignorar */ }
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(BackgroundDark)) {
        SpaceBackground(modifier = Modifier.fillMaxSize())

        Box(modifier = Modifier.fillMaxSize().padding(24.dp)) {
            Text(
                text = "Seguir viendo",
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.CenterStart)
            )

            if (items.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Black.copy(alpha = 0.4f))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { showConfirmClearAll = true }
                        )
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Text(text = "Eliminar todo", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        if (showConfirmClearAll) {
            Box(
                modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.75f)),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(SurfaceDark)
                        .padding(24.dp)
                ) {
                    androidx.compose.foundation.layout.Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "¿Vaciar \"Seguir viendo\" por completo?",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Se van a quitar todas las películas y series de esta lista.",
                            color = Color.Gray,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(top = 6.dp, bottom = 18.dp)
                        )
                        androidx.compose.foundation.layout.Row {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color.White.copy(alpha = 0.12f))
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                        onClick = { showConfirmClearAll = false }
                                    )
                                    .padding(horizontal = 20.dp, vertical = 12.dp)
                            ) {
                                Text(text = "Cancelar", color = Color.White, fontSize = 14.sp)
                            }
                            androidx.compose.foundation.layout.Spacer(modifier = Modifier.size(12.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(FocusOrange)
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                        onClick = {
                                            showConfirmClearAll = false
                                            onClearAll()
                                        }
                                    )
                                    .padding(horizontal = 20.dp, vertical = 12.dp)
                            ) {
                                Text(text = "Eliminar todo", color = Color.Black, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

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
