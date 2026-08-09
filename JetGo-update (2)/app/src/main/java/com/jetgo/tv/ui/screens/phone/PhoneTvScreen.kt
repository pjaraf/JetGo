package com.jetgo.tv.ui.screens.phone

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.PlayArrow
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.jetgo.tv.data.model.Category
import com.jetgo.tv.data.model.ContentItem
import com.jetgo.tv.data.model.ContentType
import com.jetgo.tv.player.PlayerManager
import com.jetgo.tv.ui.components.PlayerPanel
import com.jetgo.tv.ui.theme.BackgroundDark
import com.jetgo.tv.ui.theme.FocusOrange
import com.jetgo.tv.ui.theme.SurfaceDark

private enum class PhoneTvTab { CATEGORIA, FAVORITOS }

@Composable
fun PhoneTvScreen(
    playerManager: PlayerManager,
    categories: List<Category>,
    categoriesLoading: Boolean,
    channelsInCategory: List<ContentItem>,
    channelsLoading: Boolean,
    favorites: List<ContentItem>,
    activeChannelId: String?,
    persistedCategoryId: String?,
    onLoadCategories: () -> Unit,
    onLoadChannelsForCategory: (String) -> Unit,
    onChannelTap: (ContentItem) -> Unit,
    onFavoriteTap: (ContentItem) -> Unit,
    onSearchClick: () -> Unit,
    onEnterFullscreen: () -> Unit
) {
    var tab by remember { mutableStateOf(PhoneTvTab.CATEGORIA) }
    var selectedCategoryId by remember { mutableStateOf(persistedCategoryId) }

    LaunchedEffect(Unit) { onLoadCategories() }

    // Si ya había una categoría elegida antes (guardada en el ViewModel), se recupera esa;
    // si no, recién ahí se elige la primera de la lista por defecto.
    LaunchedEffect(categories) {
        if (selectedCategoryId == null && categories.isNotEmpty()) {
            val restored = persistedCategoryId?.takeIf { id -> categories.any { it.id == id } }
            selectedCategoryId = restored ?: categories.first().id
            onLoadChannelsForCategory(selectedCategoryId!!)
        }
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
            placeholder = { Text("Búsqueda por canal", color = Color.Gray) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color.Gray) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp)
                .clickable { onSearchClick() }
        )

        // ---- Reproductor ----
        PlayerPanel(
            playerManager = playerManager,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            isFocused = false,
            showFullscreenHint = true,
            onTap = onEnterFullscreen
        )

        // ---- Pestañas Categoría / Favoritos ----
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 18.dp, bottom = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(32.dp, alignment = Alignment.CenterHorizontally)
        ) {
            PhoneTvTabLabel("Categoría", tab == PhoneTvTab.CATEGORIA) { tab = PhoneTvTab.CATEGORIA }
            PhoneTvTabLabel("Favoritos", tab == PhoneTvTab.FAVORITOS) { tab = PhoneTvTab.FAVORITOS }
        }

        when (tab) {
            PhoneTvTab.CATEGORIA -> Row(modifier = Modifier.fillMaxSize()) {
                // Columna izquierda: categorías
                LazyColumn(
                    modifier = Modifier
                        .width(120.dp)
                        .fillMaxSize()
                        .background(Color(0xFF15171D))
                ) {
                    items(categories) { category ->
                        val selected = category.id == selectedCategoryId
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(if (selected) FocusOrange.copy(alpha = 0.85f) else Color.Transparent)
                                .clickable {
                                    selectedCategoryId = category.id
                                    onLoadChannelsForCategory(category.id)
                                }
                                .padding(vertical = 16.dp, horizontal = 10.dp)
                        ) {
                            Text(
                                text = category.name,
                                color = if (selected) Color.White else Color(0xFFAFAFAF),
                                fontSize = 13.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }

                // Columna derecha: canales de la categoría seleccionada
                Box(modifier = Modifier.fillMaxSize()) {
                    when {
                        channelsLoading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                        channelsInCategory.isEmpty() -> Text(
                            "Sin canales en esta categoría",
                            color = Color.Gray,
                            modifier = Modifier.align(Alignment.Center).padding(16.dp)
                        )
                        else -> LazyColumn {
                            itemsIndexed(channelsInCategory) { index, item ->
                                ChannelRow(
                                    index = index,
                                    item = item,
                                    isActive = item.id == activeChannelId,
                                    onClick = { onChannelTap(item) }
                                )
                            }
                        }
                    }
                }
            }

            PhoneTvTab.FAVORITOS -> Box(modifier = Modifier.fillMaxSize()) {
                if (favorites.isEmpty()) {
                    Text(
                        "Aún no tienes favoritos.\nToca la estrella en cualquier canal para agregarlo aquí.",
                        color = Color.Gray,
                        modifier = Modifier.align(Alignment.Center).padding(24.dp)
                    )
                } else {
                    LazyColumn {
                        itemsIndexed(favorites) { index, item ->
                            ChannelRow(
                                index = index,
                                item = item,
                                isActive = item.id == activeChannelId,
                                onClick = { onFavoriteTap(item) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PhoneTvTabLabel(label: String, selected: Boolean, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable { onClick() }
    ) {
        Text(
            text = label,
            color = if (selected) FocusOrange else Color.Gray,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            fontSize = 16.sp
        )
        if (selected) {
            Box(
                modifier = Modifier
                    .padding(top = 4.dp)
                    .height(2.dp)
                    .width(28.dp)
                    .background(FocusOrange)
            )
        }
    }
}

@Composable
private fun ChannelRow(index: Int, item: ContentItem, isActive: Boolean = false, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (isActive) FocusOrange.copy(alpha = 0.16f) else Color.Transparent)
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (!item.imageUrl.isNullOrBlank()) {
            AsyncImage(
                model = item.imageUrl,
                contentDescription = item.name,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(SurfaceDark)
            )
        } else {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(SurfaceDark),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.Gray)
            }
        }

        Text(
            text = String.format("%03d", index + 1),
            color = Color(0xFFAFAFAF),
            fontSize = 13.sp,
            modifier = Modifier.padding(start = 12.dp).width(36.dp)
        )

        Text(
            text = item.name,
            color = if (isActive) FocusOrange else Color.White,
            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
            fontSize = 15.sp,
            maxLines = 1,
            modifier = Modifier.weight(1f).padding(start = 4.dp)
        )

        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color.Gray)
    }
}
