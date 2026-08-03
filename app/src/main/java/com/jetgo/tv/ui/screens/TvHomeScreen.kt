package com.jetgo.tv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.jetgo.tv.data.model.Channel
import com.jetgo.tv.player.PlayerManager
import com.jetgo.tv.ui.components.PlayerPanel
import com.jetgo.tv.ui.theme.BackgroundDark
import com.jetgo.tv.ui.theme.FocusOrange
import com.jetgo.tv.ui.theme.SurfaceDark
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay

/**
 * Pantalla principal de TV con menú lateral (como en la referencia): TV / Película / Series /
 * Seguir viendo a la izquierda, video en vivo grande al centro y la lista de canales a la derecha.
 */
@Composable
fun TvHomeScreen(
    playerManager: PlayerManager,
    liveChannels: List<Channel>,
    onChannelSelected: (Channel) -> Unit,
    onMovieClick: () -> Unit,
    onSeriesClick: () -> Unit,
    onContinueWatchingClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onSearchClick: () -> Unit,
    isFullscreen: Boolean
) {
    var currentTime by remember { mutableStateOf(formatNow()) }
    LaunchedEffect(Unit) {
        while (true) {
            currentTime = formatNow()
            delay(30_000)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .padding(20.dp)
    ) {
        // ---- Barra superior: buscar | ajustes + hora ----
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onSearchClick) {
                Icon(Icons.Default.Search, contentDescription = "Buscar", tint = Color.White)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onSettingsClick) {
                    Icon(Icons.Default.AccountCircle, contentDescription = "Ajustes", tint = Color.White)
                }
                Text(
                    text = currentTime,
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 8.dp, end = 4.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(modifier = Modifier.fillMaxSize()) {
            // ---- Menú lateral ----
            Column(
                modifier = Modifier
                    .width(190.dp)
                    .fillMaxHeight()
                    .padding(end = 16.dp)
            ) {
                SidebarItem(icon = Icons.Default.Tv, label = "TV", selected = true, onClick = {})
                SidebarItem(icon = Icons.Default.Movie, label = "PELÍCULA", selected = false, onClick = onMovieClick)
                SidebarItem(icon = Icons.Default.Tv, label = "SERIES", selected = false, onClick = onSeriesClick)
                SidebarItem(icon = Icons.Default.History, label = "SEGUIR VIENDO", selected = false, onClick = onContinueWatchingClick)
            }

            // ---- Video en vivo grande ----
            PlayerPanel(
                playerManager = playerManager,
                showVideo = !isFullscreen,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(end = 16.dp)
            )

            // ---- Lista de canales ----
            LazyColumn(
                modifier = Modifier
                    .width(300.dp)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(liveChannels) { channel ->
                    ChannelListRow(channel = channel, onClick = { onChannelSelected(channel) })
                }
            }
        }
    }
}

@Composable
private fun SidebarItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) FocusOrange else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
        Text(
            text = label,
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 10.dp)
        )
    }
}

@Composable
private fun ChannelListRow(channel: Channel, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(SurfaceDark)
            .clickable(onClick = onClick)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0xFF2A2E38)),
            contentAlignment = Alignment.Center
        ) {
            if (!channel.logoUrl.isNullOrBlank()) {
                AsyncImage(
                    model = channel.logoUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().padding(4.dp)
                )
            } else {
                Text(channel.name.take(2).uppercase(), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
        Text(
            text = channel.name,
            color = Color.White,
            fontSize = 13.sp,
            maxLines = 1,
            modifier = Modifier.padding(start = 10.dp)
        )
    }
}

private fun formatNow(): String = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
