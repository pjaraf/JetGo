package com.jetgo.tv.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun JetGoLogo(
    size: Dp = 36.dp,
    showText: Boolean = false,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(size)
                .shadow(elevation = 6.dp, shape = RoundedCornerShape(size * 0.3f), spotColor = Color(0xFFFF6B00))
                .clip(RoundedCornerShape(size * 0.3f))
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            Color(0xFFFF7A00), // Vibrant Orange
                            Color(0xFFE50914), // Red
                            Color(0xFF9C27B0)  // Purple
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.size(size * 0.55f)) {
                val w = this.size.width
                val h = this.size.height
                val path = Path().apply {
                    moveTo(w * 0.15f, h * 0.05f)
                    lineTo(w * 0.9f, h * 0.5f)
                    lineTo(w * 0.15f, h * 0.95f)
                    close()
                }
                drawPath(path = path, color = Color.White)
            }
        }

        if (showText) {
            Text(
                text = "JetGo",
                color = Color.White,
                fontSize = (size.value * 0.65f).sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 0.5.sp
            )
        }
    }
}
