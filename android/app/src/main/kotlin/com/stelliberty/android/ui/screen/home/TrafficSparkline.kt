package com.stelliberty.android.ui.screen.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.stelliberty.android.viewmodel.HomeViewModel.Companion.TRAFFIC_HISTORY_CAPACITY
import com.stelliberty.android.viewmodel.TrafficHistory

@Composable
internal fun TrafficSparkline(
    modifier: Modifier = Modifier,
    history: TrafficHistory,
    uploadColor: Color,
    downloadColor: Color,
) {
    val up = history.up
    val down = history.down
    if (up.size < 2) return

    val targetScale = remember(history.seq) {
        var peak = 0L
        for (value in up) if (value > peak) peak = value
        for (value in down) if (value > peak) peak = value
        peak.toFloat().coerceAtLeast(MinScaleBytes)
    }

    val scroll = remember { Animatable(history.seq.toFloat()) }
    val scale = remember { Animatable(targetScale) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    LaunchedEffect(history.seq, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            scroll.animateTo(history.seq.toFloat(), tween(durationMillis = ScrollAnimationMillis, easing = LinearEasing))
        }
    }
    LaunchedEffect(targetScale, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            scale.animateTo(targetScale, tween(durationMillis = ScaleAnimationMillis))
        }
    }

    val upLine = remember { Path() }
    val upFill = remember { Path() }
    val downLine = remember { Path() }
    val downFill = remember { Path() }

    Spacer(
        modifier = modifier
            .clipToBounds()
            .drawWithCache {
                val baseline = size.height
                val chartTop = size.height * (1f - WatermarkHeightFraction)
                val step = size.width / (TRAFFIC_HISTORY_CAPACITY - 2)
                val strokeWidth = WatermarkStrokeWidth.toPx()
                val uploadFillBrush = fillBrush(uploadColor, chartTop, baseline)
                val downloadFillBrush = fillBrush(downloadColor, chartTop, baseline)
                val seq = history.seq
                onDrawBehind {
                    val progress = (1f - (seq - scroll.value)).coerceIn(0f, 1f)
                    val maxValue = scale.value
                    drawSeries(
                        downLine,
                        downFill,
                        down,
                        maxValue,
                        step,
                        progress,
                        chartTop,
                        baseline,
                        downloadColor,
                        downloadFillBrush,
                        strokeWidth
                    )
                    drawSeries(upLine, upFill, up, maxValue, step, progress, chartTop, baseline, uploadColor, uploadFillBrush, strokeWidth)
                }
            },
    )
}

private fun fillBrush(color: Color, chartTop: Float, baseline: Float): Brush = Brush.verticalGradient(
    colors = listOf(color.copy(alpha = WatermarkFillAlpha), Color.Transparent),
    startY = chartTop,
    endY = baseline,
)

private fun DrawScope.drawSeries(
    linePath: Path,
    fillPath: Path,
    values: List<Long>,
    maxValue: Float,
    step: Float,
    progress: Float,
    chartTop: Float,
    baseline: Float,
    color: Color,
    fillBrush: Brush,
    strokeWidth: Float,
) {
    val count = values.size
    if (count < 2) return
    val range = baseline - chartTop
    val offset = (1f - progress) * step
    fun x(index: Int) = size.width - (count - 1 - index) * step + offset
    fun y(index: Int) = baseline - (values[index] / maxValue).coerceIn(0f, 1f) * range

    linePath.reset()
    linePath.moveTo(x(0), y(0))
    for (i in 0 until count - 1) {
        val x0 = x(i)
        val y0 = y(i)
        val x1 = x(i + 1)
        val y1 = y(i + 1)
        val prev = (i - 1).coerceAtLeast(0)
        val next = (i + 2).coerceAtMost(count - 1)
        linePath.cubicTo(
            x0 + (x1 - x(prev)) / 6f,
            (y0 + (y1 - y(prev)) / 6f).coerceIn(chartTop, baseline),
            x1 - (x(next) - x0) / 6f,
            (y1 - (y(next) - y0) / 6f).coerceIn(chartTop, baseline),
            x1,
            y1,
        )
    }

    fillPath.reset()
    fillPath.addPath(linePath)
    fillPath.lineTo(x(count - 1), baseline)
    fillPath.lineTo(x(0), baseline)
    fillPath.close()

    drawPath(fillPath, fillBrush)
    drawPath(
        path = linePath,
        color = color,
        style = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round),
    )
}

private const val MinScaleBytes = 32f * 1024f

// 比每秒一次的数据推送略长一点，让动画总在跑完之前被下一帧数据接上，从而吸收推送的抖动。
// 取整 1000 毫秒的话，每个周期末尾会有一小段静止等待。
private const val ScrollAnimationMillis = 1100

private const val ScaleAnimationMillis = 600
