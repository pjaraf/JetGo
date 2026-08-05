package com.jetgo.tv.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.jetgo.tv.ui.screens.HomeViewModel
import com.jetgo.tv.ui.theme.FocusOrange
import kotlinx.coroutines.delay

/**
 * Banner de información de canal — versión compacta: solo identidad del canal (logo, número,
 * nombre y calidad real detectada), SIN información de programación. Aparece unos segundos
 * al cambiar de canal.
 */
@Composable
fun LiveChannelInfoBanner(
    info: HomeViewModel.LiveChannelInfo,
    videoQuality: String? = null,
    modifier: Modifier = Modifier
) {
    var visible by remember(info.channelName) { mutableStateOf(true) }

    LaunchedEffect(info.channelName) {
        visible = true
        delay(6000)
        visible = false
    }

    AnimatedVisibility(
        visible = visible,
        modifier = modifier.padding(horizontal = 24.dp, vertical = 20.dp),
        enter = fadeIn() + slideInVertically(initialOffsetY = { it / 3 }),
        exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 3 })
    ) {
        Row(
            modifier = Modifier
                .widthIn(max = 420.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFF14161C).copy(alpha = 0.92f), Color(0xFF0A0B0E).copy(alpha = 0.96f))
                    )
                )
                .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(18.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // ---- Logo del canal ----
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White.copy(alpha = 0.06f)),
                contentAlignment = Alignment.Center
            ) {
                if (!info.channelLogo.isNullOrBlank()) {
                    AsyncImage(
                        model = info.channelLogo,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize().padding(6.dp)
                    )
                } else {
                    Text(
                        info.channelName.take(2).uppercase(),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }

            // ---- Número de canal ----
            if (info.channelNumber != null) {
                Box(
                    modifier = Modifier
                        .padding(start = 12.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.White.copy(alpha = 0.10f))
                        .padding(horizontal = 7.dp, vertical = 2.dp)
                ) {
                    Text("${info.channelNumber}", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }

            // ---- Nombre del canal ----
            Text(
                text = info.channelName,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                maxLines = 1,
                modifier = Modifier.padding(start = 10.dp).weight(1f, fill = false)
            )

            // ---- Calidad real detectada ----
            if (!videoQuality.isNullOrBlank()) {
                QualityBadge(videoQuality, modifier = Modifier.padding(start = 10.dp))
            }
        }
    }
}

/** Etiqueta chica de calidad (SD/HD/FHD/4K) con color según la nitidez detectada */
@Composable
private fun QualityBadge(quality: String, modifier: Modifier = Modifier) {
    val color = when (quality) {
        "4K" -> Color(0xFFB388FF)
        "FHD" -> FocusOrange
        "HD" -> Color(0xFF4FE0B0)
        else -> Color.White.copy(alpha = 0.5f)
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .border(1.dp, color, RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(quality, color = color, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}
