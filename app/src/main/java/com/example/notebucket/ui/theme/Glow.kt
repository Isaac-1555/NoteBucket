package com.example.notebucket.ui.theme

import androidx.compose.animation.core.InfiniteRepeatableSpec
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun Modifier.glow(
    color: Color,
    radius: Dp = 20.dp,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(16.dp),
    durationMs: Int = 2000
): Modifier {
    val infiniteTransition = rememberInfiniteTransition(label = "glow")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.65f,
        animationSpec = InfiniteRepeatableSpec(
            animation = tween(durationMs),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowAlpha"
    )
    return this.shadow(
        elevation = radius,
        shape = shape,
        ambientColor = color.copy(alpha = alpha),
        spotColor = color.copy(alpha = alpha)
    )
}
