package com.jetgo.tv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import org.videolan.libvlc.util.VLCVideoLayout
import com.jetgo.tv.player.PlayerManager
import com.jetgo.tv.ui.theme.FocusOrange
import com.jetgo.tv.ui.theme.LiveGreen

@Composable
fun PlayerPanel(
    playerManager: PlayerManager,
    modifier: Modifier = Modifier,
    isFocused: Boolean = true,
    showFullscreenHint: Boolean = false,
    showVideo: Boolean = true,
    onTap: () -> Unit = {}
) {
    Box(
        modifier = modifier
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(14.dp))
            .background(Color.Black)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onTap
            )
    ) {
        if (showVideo) {
            var videoLayout by remember { mutableStateOf<VLCVideoLayout?>(null) }

            AndroidView(
                factory = { context ->
                    VLCVideoLayout(context).also { videoLayout = it }
                },
                modifier = Modifier.fillMaxSize()
            )

            // Se vincula/desvincula el reproductor de Vivo a esta vista chica cada vez que
            // entra o sale de composición (por ejemplo, al ir y volver de pantalla completa)
            // — sin esto a veces se queda solo el audio con la imagen congelada.
            DisposableEffect(videoLayout) {
                val layout = videoLayout
                if (layout != null) {
                    try {
                        playerManager.livePlayer.attachViews(layout, null, false, false)
                    } catch (e: Exception) { /* ignorar */ }
                }
                onDispose {
                    try { playerManager.livePlayer.detachViews() } catch (e: Exception) { /* ignorar */ }
                }
            }
        }
    }
}
