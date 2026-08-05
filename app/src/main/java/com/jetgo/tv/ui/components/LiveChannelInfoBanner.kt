package com.jetgo.tv.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Banner de información de canal — diseño minimalista tipo "tarjeta flotante" translúcida con
 * bordes redondeados: logo, nombre y número de canal, calidad REAL detectada del video, y
 * la programación actual con barra de avance. Aparece unos segundos al cambiar de canal.
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
        val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
        var nowMs by remember { mutableStateOf(System.currentTimeMillis()) }

        LaunchedEffect(Unit) {
            while (true) {
                nowMs = System.currentTimeMillis()
                delay(15_000)
            }
        }

        val progress = info.current?.let { program ->
            val total = (program.endMs - program.startMs).coerceAtLeast(1)
            ((nowMs - program.startMs).toFloat() / total).coerceIn(0f, 1f)
        } ?: 0f

        Row(
            modifier = Modifier
                .widthIn(max = 620.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFF14161C).copy(alpha = 0.92f), Color(0xFF0A0B0E).copy(alpha = 0.96f))
                    )
                )
                .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(22.dp))
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // ---- Logo del canal ----
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.White.copy(alpha = 0.06f)),
                contentAlignment = Alignment.Center
            ) {
                if (!info.channelLogo.isNullOrBlank()) {
                    AsyncImage(
                        model = info.channelLogo,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize().padding(8.dp)
                    )
                } else {
                    Text(
                        info.channelName.take(2).uppercase(),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }
            }

            Column(modifier = Modifier.weight(1f).padding(start = 14.dp)) {
                // ---- Nombre, número y calidad, en una sola línea prolija ----
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (info.channelNumber != null) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color.White.copy(alpha = 0.10f))
                                .padding(horizontal = 7.dp, vertical = 2.dp)
                        ) {
                            Text("${info.channelNumber}", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        Box(modifier = Modifier.padding(start = 6.dp))
                    }
                    Text(
                        text = info.channelName,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        maxLines = 1,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (!videoQuality.isNullOrBlank()) {
                        QualityBadge(videoQuality, modifier = Modifier.padding(start = 8.dp))
                    }
                }

                Text(
                    text = info.current?.title ?: "Sin información de programación",
                    color = Color.White.copy(alpha = 0.85f),
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp,
                    maxLines = 1,
                    modifier = Modifier.padding(top = 6.dp)
                )

                if (info.current != null) {
                    LinearProgressIndicator(
                        progress = { progress },
                        color = FocusOrange,
                        trackColor = Color.White.copy(alpha = 0.12f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 7.dp)
                            .height(3.dp)
                            .clip(RoundedCornerShape(2.dp))
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(timeFormat.format(Date(info.current.startMs)), color = Color.White.copy(alpha = 0.45f), fontSize = 10.sp)
                        Text(timeFormat.format(Date(info.current.endMs)), color = Color.White.copy(alpha = 0.45f), fontSize = 10.sp)
                    }
                }

                if (info.next != null) {
                    Text(
                        text = "A continuación: ${info.next.title}",
                        color = Color.White.copy(alpha = 0.4f),
                        fontSize = 11.sp,
                        maxLines = 1,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
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
