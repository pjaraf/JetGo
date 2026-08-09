package com.jetgo.tv.ui.screens

import androidx.compose.runtime.Composable
import com.jetgo.tv.data.model.ContentItem

@Composable
fun FavoritesScreen(
    favorites: List<ContentItem>,
    onToggleFavorite: (ContentItem) -> Unit,
    onItemSelected: (ContentItem) -> Unit
) {
    ChannelListScreen(
        isLoading = false,
        items = favorites,
        errorMessage = null,
        isFavorite = { true }, // todo lo que aparece aquí ya es favorito
        onToggleFavorite = onToggleFavorite,
        onItemSelected = onItemSelected
    )
}
