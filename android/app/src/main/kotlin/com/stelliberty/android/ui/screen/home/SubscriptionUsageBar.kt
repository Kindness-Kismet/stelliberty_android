package com.stelliberty.android.ui.screen.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap

@Composable
internal fun SubscriptionUsageBar(
    modifier: Modifier = Modifier,
    progress: Float,
    color: Color,
) {
    val animated = remember { Animatable(0f) }
    LaunchedEffect(progress) {
        animated.animateTo(progress, tween(durationMillis = ProgressAnimationMillis))
    }

    Spacer(
        modifier = modifier
            .clipToBounds()
            .drawWithCache {
                val baseline = size.height
                val fillTop = size.height * (1f - WatermarkHeightFraction)
                val strokeWidth = WatermarkStrokeWidth.toPx()
                val brush = Brush.verticalGradient(
                    colors = listOf(color.copy(alpha = WatermarkFillAlpha), Color.Transparent),
                    startY = fillTop,
                    endY = baseline,
                )
                onDrawBehind {
                    val width = size.width * animated.value
                    if (width <= 0f) return@onDrawBehind
                    drawRect(
                        brush = brush,
                        topLeft = Offset(0f, fillTop),
                        size = Size(width, baseline - fillTop),
                    )
                    drawLine(
                        color = color,
                        start = Offset(0f, fillTop),
                        end = Offset(width, fillTop),
                        strokeWidth = strokeWidth,
                        cap = StrokeCap.Round,
                    )
                }
            },
    )
}

private const val ProgressAnimationMillis = 700
