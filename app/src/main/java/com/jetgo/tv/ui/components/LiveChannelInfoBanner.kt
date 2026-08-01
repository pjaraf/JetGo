package com.jetgo.tv.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.jetgo.tv.ui.screens.HomeViewModel
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Banner tipo guía de canales: logo, programa actual con barra de avance, siguiente programa,
 * número de canal y hora. Aparece automáticamente unos segundos cada vez que se cambia de canal.
 */
@Composable
fun LiveChannelInfoBanner(
    info: HomeViewModel.LiveChannelInfo,
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
        modifier = modifier,
        enter = fadeIn(),
        exit = fadeOut()
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
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.82f))
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // ---- Logo del canal ----
            Box(
                modifier = Modifier
                    .size(width = 90.dp, height = 70.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF2A2E38)),
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
                    Text(info.channelName.take(2).uppercase(), color = Color.White, fontWeight = FontWeight.Bold)
                }
            }

            // ---- Programa actual + progreso ----
            Column(modifier = Modifier.weight(1f).padding(horizontal = 16.dp)) {
                Text(info.channelName, color = Color(0xFFFF7A2E), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Text(
                    text = info.current?.title ?: "Sin información de programación",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    maxLines = 1
                )
                if (info.current != null) {
                    LinearProgressIndicator(
                        progress = { progress },
                        color = Color(0xFFFF7A2E),
                        trackColor = Color(0xFF444444),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp)
                            .height(3.dp)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(timeFormat.format(Date(info.current.startMs)), color = Color.Gray, fontSize = 11.sp)
                        Text(timeFormat.format(Date(info.current.endMs)), color = Color.Gray, fontSize = 11.sp)
                    }
                }
                if (info.next != null) {
                    Text(
                        text = "Siguiente: ${info.next.title}  ${timeFormat.format(Date(info.next.startMs))}",
                        color = Color.Gray,
                        fontSize = 11.sp,
                        maxLines = 1,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }

            // ---- Número de canal + hora actual ----
            Column(horizontalAlignment = Alignment.End) {
                if (info.channelNumber != null) {
                    Text("${info.channelNumber}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                }
                Text(timeFormat.format(Date(nowMs)), color = Color.Gray, fontSize = 12.sp)
            }
        }
    }
}
