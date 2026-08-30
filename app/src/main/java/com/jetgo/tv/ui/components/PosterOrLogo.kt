package com.jetgo.tv.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.jetgo.tv.R

/**
 * Muestra la carátula de la película/serie si el servidor la trae; si no tiene ninguna, en vez
 * de dejar el espacio vacío se muestra el logo de la app + el nombre del título.
 */
@Composable
fun PosterOrLogo(
    imageUrl: String?,
    name: String,
    modifier: Modifier = Modifier
) {
    if (!imageUrl.isNullOrBlank()) {
        AsyncImage(
            model = imageUrl,
            contentDescription = name,
            contentScale = ContentScale.Crop,
            modifier = modifier.fillMaxSize()
        )
    } else {
        Box(
            modifier = modifier.fillMaxSize().background(Color(0xFF1A1D24)),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                JetGoLogo(size = 40.dp)
                Text(
                    text = name,
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    maxLines = 3,
                    modifier = Modifier.padding(top = 8.dp, start = 8.dp, end = 8.dp)
                )
            }
        }
    }
}
