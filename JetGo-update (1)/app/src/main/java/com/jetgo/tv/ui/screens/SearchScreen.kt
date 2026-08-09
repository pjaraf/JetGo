package com.jetgo.tv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.jetgo.tv.data.model.ContentItem
import com.jetgo.tv.ui.theme.BackgroundDark

@Composable
fun SearchScreen(
    isLoadingCatalog: Boolean,
    query: String,
    results: List<ContentItem>,
    isFavorite: (ContentItem) -> Boolean,
    onToggleFavorite: (ContentItem) -> Unit,
    onQueryChanged: (String) -> Unit,
    onEnterScreen: () -> Unit,
    onItemSelected: (ContentItem) -> Unit
) {
    LaunchedEffect(Unit) { onEnterScreen() }

    Column(modifier = Modifier.fillMaxSize().background(BackgroundDark).padding(16.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChanged,
            label = { Text("Buscar canales, películas o series") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(top = 12.dp))

        Box(modifier = Modifier.fillMaxSize()) {
            when {
                isLoadingCatalog -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator()
                        Text(
                            text = "Cargando catálogo por primera vez...",
                            color = androidx.compose.ui.graphics.Color.White,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
                query.isBlank() -> {
                    Text(
                        text = "Escribe para buscar en todo tu contenido",
                        color = androidx.compose.ui.graphics.Color.Gray,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                else -> {
                    ChannelListScreen(
                        isLoading = false,
                        items = results,
                        errorMessage = null,
                        isFavorite = isFavorite,
                        onToggleFavorite = onToggleFavorite,
                        onItemSelected = onItemSelected
                    )
                }
            }
        }
    }
}
