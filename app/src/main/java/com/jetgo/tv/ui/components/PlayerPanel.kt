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
import androidx.compose.runtime.remember
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
import androidx.media3.ui.PlayerView
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
            .clip(RoundedCornerShape(6.dp))
            .background(Color.Black)
            .border(
                width = if (isFocused) 3.dp else 0.dp,
                color = if (isFocused) FocusOrange else Color.Transparent,
                shape = RoundedCornerShape(6.dp)
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onTap
            )
    ) {
        if (showVideo) {
            AndroidView(
                factory = { context ->
                    PlayerView(context).apply {
                        player = playerManager.exoPlayer
                        useController = false
                        resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FILL
                    }
                },
                update = { view ->
                    // Vuelve a conectar el video cada vez (por si venía de pantalla completa):
                    // sin esto a veces se queda solo el audio con la imagen congelada.
                    if (view.player !== playerManager.exoPlayer) {
                        view.player = playerManager.exoPlayer
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
