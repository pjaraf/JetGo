package com.jetgo.tv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.jetgo.tv.player.TrackOption
import com.jetgo.tv.ui.theme.FocusOrange
import com.jetgo.tv.ui.theme.SurfaceDark

@Composable
fun LanguageTracksDialog(
    audioTracks: List<TrackOption>,
    subtitleTracks: List<TrackOption>,
    onSelectAudio: (TrackOption) -> Unit,
    onSelectSubtitle: (TrackOption) -> Unit,
    onDisableSubtitles: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(SurfaceDark, RoundedCornerShape(16.dp))
                .padding(20.dp)
        ) {
            Text("Idioma y subtítulos", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)

            if (audioTracks.isNotEmpty()) {
                Text(
                    "Audio",
                    color = Color.Gray,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 16.dp, bottom = 6.dp)
                )
                audioTracks.forEach { track ->
                    TrackRow(label = track.label, selected = track.isSelected) { onSelectAudio(track) }
                }
            }

            if (subtitleTracks.isNotEmpty()) {
                Text(
                    "Subtítulos",
                    color = Color.Gray,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 16.dp, bottom = 6.dp)
                )
                TrackRow(label = "Desactivados", selected = subtitleTracks.none { it.isSelected }) { onDisableSubtitles() }
                subtitleTracks.forEach { track ->
                    TrackRow(label = track.label, selected = track.isSelected) { onSelectSubtitle(track) }
                }
            }

            if (audioTracks.isEmpty() && subtitleTracks.isEmpty()) {
                Text(
                    "Este contenido no ofrece más de una pista de audio ni subtítulos.",
                    color = Color.Gray,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 16.dp)
                )
            }
        }
    }
}

@Composable
private fun TrackRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = if (selected) FocusOrange else Color.White, fontSize = 14.sp)
        if (selected) {
            Icon(Icons.Default.Check, contentDescription = null, tint = FocusOrange)
        }
    }
}
