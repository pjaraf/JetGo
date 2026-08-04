package com.jetgo.tv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.jetgo.tv.data.model.Category
import com.jetgo.tv.data.model.ContentItem
import com.jetgo.tv.ui.theme.BackgroundDark
import com.jetgo.tv.ui.theme.FocusOrange
import com.jetgo.tv.ui.theme.SurfaceDark

/**
 * Pantalla para Android TV / Google TV / TV Box: la grilla de pósters ocupa toda la pantalla,
 * y las categorías viven en un panel semi-transparente que aparece con la flecha IZQUIERDA del
 * control (igual que el panel de canales en Vivo). Con el panel abierto, arriba/abajo cambia
 * de categoría al instante (sin tener que confirmar con OK).
 */
@Composable
fun TvCategoryGridScreen(
    typeLabel: String,
    categories: List<Category>,
    categoriesLoading: Boolean,
    items: List<ContentItem>,
    itemsLoading: Boolean,
    onLoadCategories: () -> Unit,
    onCategorySelected: (String) -> Unit,
    onItemSelected: (ContentItem) -> Unit,
    isFavorite: (ContentItem) -> Boolean,
    onToggleFavorite: (ContentItem) -> Unit
) {
    var selectedCategoryId by remember { mutableStateOf<String?>(null) }
    var selectedCategoryName by remember { mutableStateOf("") }
    var sidebarVisible by remember { mutableStateOf(false) }
    var sidebarIndex by remember { mutableStateOf(0) }
    val keyInterceptFocusRequester = remember { FocusRequester() }
    val firstItemFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        onLoadCategories()
    }
    // Apenas hay algo en la grilla, el foco va directo a la primera carátula (no al
    // contenedor general): así el control remoto puede resaltarla de una con el borde naranjo.
    LaunchedEffect(items) {
        if (items.isNotEmpty()) {
            try { firstItemFocusRequester.requestFocus() } catch (e: Exception) { /* ignorar */ }
        } else {
            try { keyInterceptFocusRequester.requestFocus() } catch (e: Exception) { /* ignorar */ }
        }
    }
    LaunchedEffect(categories) {
        if (selectedCategoryId == null && categories.isNotEmpty()) {
            sidebarIndex = 0
            selectedCategoryId = categories.first().id
            selectedCategoryName = categories.first().name
            onCategorySelected(categories.first().id)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
    ) {
        // ---- Encabezado: "Películas > Categoría" + total de títulos ----
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (selectedCategoryName.isNotBlank()) "$typeLabel > $selectedCategoryName" else typeLabel,
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            if (!itemsLoading) {
                Text(
                    text = "${items.size} títulos",
                    color = Color.Gray,
                    fontSize = 14.sp
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .focusRequester(keyInterceptFocusRequester)
                .focusable()
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (event.key) {
                        Key.DirectionLeft -> {
                            sidebarVisible = !sidebarVisible
                            true
                        }
                        Key.DirectionUp -> {
                            if (sidebarVisible) {
                                sidebarIndex = (sidebarIndex - 1).coerceAtLeast(0)
                                categories.getOrNull(sidebarIndex)?.let { cat ->
                                    selectedCategoryId = cat.id
                                    selectedCategoryName = cat.name
                                    onCategorySelected(cat.id)
                                }
                                true
                            } else false
                        }
                        Key.DirectionDown -> {
                            if (sidebarVisible) {
                                sidebarIndex = (sidebarIndex + 1).coerceAtMost((categories.size - 1).coerceAtLeast(0))
                                categories.getOrNull(sidebarIndex)?.let { cat ->
                                    selectedCategoryId = cat.id
                                    selectedCategoryName = cat.name
                                    onCategorySelected(cat.id)
                                }
                                true
                            } else false
                        }
                        Key.DirectionCenter, Key.Enter -> {
                            if (sidebarVisible) {
                                sidebarVisible = false
                                true
                            } else false
                        }
                        Key.Back -> {
                            if (sidebarVisible) {
                                sidebarVisible = false
                                true
                            } else false
                        }
                        else -> false
                    }
                }
        ) {
            // ---- Grilla de pósters (ocupa toda la pantalla) ----
            when {
                itemsLoading || (selectedCategoryId == null && categories.isEmpty()) ->
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                items.isEmpty() -> Text(
                    text = "Sin contenido en esta categoría",
                    color = Color.Gray,
                    modifier = Modifier.align(Alignment.Center).padding(24.dp)
                )
                else -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 150.dp),
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)
                ) {
                    itemsIndexed(items) { index, item ->
                        TvPosterCard(
                            item = item,
                            isFavorite = isFavorite(item),
                            onToggleFavorite = { onToggleFavorite(item) },
                            onClick = { onItemSelected(item) },
                            focusRequester = if (index == 0) firstItemFocusRequester else null
                        )
                    }
                }
            }

            // ---- Panel de categorías: semi-transparente, solo visible con flecha izquierda ----
            if (sidebarVisible) {
                CategorySidebar(
                    categories = categories,
                    selectedIndex = sidebarIndex,
                    modifier = Modifier.align(Alignment.CenterStart)
                )
            }
        }
    }
}

@Composable
private fun CategorySidebar(
    categories: List<Category>,
    selectedIndex: Int,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    LaunchedEffect(selectedIndex) {
        listState.animateScrollToItem(selectedIndex.coerceAtLeast(0))
    }

    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxHeight()
            .width(240.dp)
            .background(Color.Black.copy(alpha = 0.80f))
            .padding(vertical = 8.dp)
    ) {
        itemsIndexed(categories) { index, category ->
            val selected = index == selectedIndex
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(if (selected) FocusOrange.copy(alpha = 0.85f) else Color.Transparent)
                    .padding(horizontal = 18.dp, vertical = 14.dp)
            ) {
                Text(
                    text = category.name,
                    color = if (selected) Color.White else Color(0xFFCCCCCC),
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    fontSize = 14.sp
                )
            }
        }
    }
}

@Composable
private fun TvPosterCard(
    item: ContentItem,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onClick: () -> Unit,
    focusRequester: FocusRequester? = null
) {
    var focused by remember { mutableStateOf(false) }

    Column(modifier = Modifier.padding(8.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(10.dp))
                .background(SurfaceDark)
                .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
                .border(
                    width = if (focused) 3.dp else 0.dp,
                    color = if (focused) FocusOrange else Color.Transparent,
                    shape = RoundedCornerShape(10.dp)
                )
                .onFocusChanged { focused = it.isFocused }
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

            // Calificación (esquina inferior-izquierda), igual que en la referencia
            if (!item.rating.isNullOrBlank()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(6.dp)
                        .background(FocusOrange, RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = item.rating,
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Favorito (esquina superior-derecha)
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.5f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onToggleFavorite
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isFavorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                    contentDescription = "Favorito",
                    tint = if (isFavorite) FocusOrange else Color.White,
                    modifier = Modifier.size(15.dp)
                )
            }
        }
        Text(
            text = item.name,
            color = Color.White,
            fontSize = 13.sp,
            maxLines = 1,
            modifier = Modifier.padding(top = 6.dp)
        )
    }
}
