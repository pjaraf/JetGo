package com.jetgo.tv.ui.screens.phone

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
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
import com.jetgo.tv.ui.theme.BackgroundDark
import com.jetgo.tv.ui.theme.FocusOrange
import com.jetgo.tv.ui.theme.SurfaceDark

/**
 * Pantalla de Series/Películas para teléfono: categorías en un carrusel horizontal ARRIBA
 * (se mueve solo con el dedo, no automático), y abajo la grilla de carátulas de la
 * categoría elegida.
 */
@Composable
fun PhoneCategoryScreen(
    typeLabel: String,
    categories: List<Category>,
    categoriesLoading: Boolean,
    items: List<ContentItem>,
    itemsLoading: Boolean,
    onLoadCategories: () -> Unit,
    onCategorySelected: (String) -> Unit,
    onItemSelected: (ContentItem) -> Unit
) {
    var selectedCategoryId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) { onLoadCategories() }
    LaunchedEffect(categories) {
        if (selectedCategoryId == null && categories.isNotEmpty()) {
            selectedCategoryId = categories.first().id
            onCategorySelected(categories.first().id)
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(BackgroundDark)) {
        Text(
            text = typeLabel,
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(16.dp)
        )

        // ---- Carrusel de categorías (arriba, se mueve solo con el dedo) ----
        if (categoriesLoading && categories.isEmpty()) {
            CircularProgressIndicator(
                color = FocusOrange,
                modifier = Modifier.padding(start = 16.dp).size(24.dp)
            )
        } else {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(horizontal = 16.dp)
            ) {
                items(categories) { category ->
                    val isSelected = category.id == selectedCategoryId
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (isSelected) FocusOrange else SurfaceDark)
                            .clickable {
                                selectedCategoryId = category.id
                                onCategorySelected(category.id)
                            }
                            .padding(horizontal = 16.dp, vertical = 9.dp)
                    ) {
                        Text(
                            text = category.name,
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        }

        Box(modifier = Modifier.fillMaxSize().padding(top = 16.dp)) {
            when {
                itemsLoading -> CircularProgressIndicator(
                    color = FocusOrange,
                    modifier = Modifier.align(Alignment.Center)
                )
                items.isEmpty() -> Text(
                    text = "Sin contenido en esta categoría",
                    color = Color.Gray,
                    modifier = Modifier.align(Alignment.Center).padding(24.dp)
                )
                else -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 120.dp),
                    modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp)
                ) {
                    items(items, key = { item -> "${item.type}_${item.id}" }) { item ->
                        PhoneCategoryPosterCard(item = item, onClick = { onItemSelected(item) })
                    }
                }
            }
        }
    }
}

@Composable
private fun PhoneCategoryPosterCard(item: ContentItem, onClick: () -> Unit) {
    Column(modifier = Modifier.padding(6.dp).clickable(onClick = onClick)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(10.dp))
                .background(SurfaceDark)
        ) {
            if (!item.imageUrl.isNullOrBlank()) {
                AsyncImage(
                    model = item.imageUrl,
                    contentDescription = item.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        Text(
            text = item.name,
            color = Color.White,
            fontSize = 12.sp,
            maxLines = 1,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}
