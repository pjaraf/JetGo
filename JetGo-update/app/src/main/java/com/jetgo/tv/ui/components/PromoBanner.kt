package com.jetgo.tv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.jetgo.tv.ui.theme.SurfaceDark

@Composable
fun PromoBanner(
    imageUrl: String?,
    contentDescription: String,
    modifier: Modifier = Modifier
) {
    if (imageUrl.isNullOrBlank()) {
        Box(
            modifier = modifier
                .aspectRatio(0.7f)
                .clip(RoundedCornerShape(10.dp))
                .background(SurfaceDark)
        )
        return
    }
    AsyncImage(
        model = imageUrl,
        contentDescription = contentDescription,
        contentScale = ContentScale.Crop,
        modifier = modifier
            .aspectRatio(0.7f)
            .clip(RoundedCornerShape(10.dp))
            .background(SurfaceDark)
    )
}
