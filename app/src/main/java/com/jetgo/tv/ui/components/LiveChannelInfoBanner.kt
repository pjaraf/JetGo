package com.jetgo.tv.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
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
 * Banner de información de canal profesional (OSD) con diseño glassmorphism,
 * indicador "EN VIVO", número de canal, calidad y EPG actual.
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
        modifier = modifier.padding(horizontal = 32.dp, vertical = 24.dp),
        enter = fadeIn() + slideInVertically(initialOffsetY = { it / 2 }),
        exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 2 })
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 560.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color(0xFF181B24).copy(alpha = 0.95f),
                            Color(0xFF0D0F14).copy(alpha = 0.98f)
                        )
                    )
                )
                .border(
                    width = 1.5.dp,
                    brush = Brush.linearGradient(
                        listOf(Color.White.copy(alpha = 0.15f), Color.White.copy(alpha = 0.05f))
                    ),
                    shape = RoundedCornerShape(20.dp)
                )
                .padding(horizontal = 18.dp, vertical = 14.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Logo del canal
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color.White.copy(alpha = 0.07f)),
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
                                fontSize = 16.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Número de canal
                            if (info.channelNumber != null) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(FocusOrange.copy(alpha = 0.2f))
                                        .padding(horizontal = 8.dp, vertical = 3.dp)
                                ) {
                                    Text(
                                        "CH ${info.channelNumber}",
                                        color = FocusOrange,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                            }

                            // Live Indicator Badge
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0xFFE53935).copy(alpha = 0.2f))
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFFE53935))
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    "EN VIVO",
                                    color = Color(0xFFFF5252),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        // Nombre del canal
                        Text(
                            text = info.channelName,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            maxLines = 1
                        )
                    }

                    // Calidad
                    if (!videoQuality.isNullOrBlank()) {
                        Spacer(modifier = Modifier.width(10.dp))
                        QualityBadge(videoQuality)
                    }
                }

                // EPG Program info (si existe)
                if (info.current != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color.White.copy(alpha = 0.04f))
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = info.current.title,
                                    color = Color.White.copy(alpha = 0.9f),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1
                                )
                                val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
                                val startTime = timeFormat.format(Date(info.current.startMs))
                                val endTime = timeFormat.format(Date(info.current.endMs))
                                Text(
                                    text = "🕒 $startTime - $endTime",
                                    color = Color.White.copy(alpha = 0.5f),
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QualityBadge(quality: String) {
    val (bgColor, textColor) = when (quality.uppercase()) {
        "4K" -> Color(0xFFB388FF).copy(alpha = 0.2f) to Color(0xFFB388FF)
        "FHD" -> FocusOrange.copy(alpha = 0.2f) to FocusOrange
        "HD" -> Color(0xFF4FE0B0).copy(alpha = 0.2f) to Color(0xFF4FE0B0)
        else -> Color.White.copy(alpha = 0.1f) to Color.White.copy(alpha = 0.7f)
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .border(1.dp, textColor.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(quality, color = textColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}
