package com.jetgo.tv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.jetgo.tv.data.model.Category
import com.jetgo.tv.ui.theme.BackgroundDark
import com.jetgo.tv.ui.theme.SurfaceDark

@Composable
fun CategoryPickerScreen(
    isLoading: Boolean,
    categories: List<Category>,
    errorMessage: String?,
    onCategorySelected: (Category) -> Unit
) {
    Box(modifier = Modifier.fillMaxSize().background(BackgroundDark)) {
        when {
            isLoading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            errorMessage != null -> Text(
                text = errorMessage,
                color = Color.Red,
                modifier = Modifier.align(Alignment.Center).padding(24.dp)
            )
            categories.isEmpty() -> Text(
                text = "No hay categorías disponibles aquí",
                color = Color.White,
                modifier = Modifier.align(Alignment.Center).padding(24.dp)
            )
            else -> {
                LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                    items(categories) { category ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(SurfaceDark)
                                .clickable { onCategorySelected(category) }
                                .padding(16.dp)
                        ) {
                            Text(text = category.name, color = Color.White)
                        }
                    }
                }
            }
        }
    }
}
