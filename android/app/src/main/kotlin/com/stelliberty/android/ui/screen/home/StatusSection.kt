package com.stelliberty.android.ui.screen.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.stelliberty.android.R
import com.stelliberty.android.ui.icon.AppIcons
import com.stelliberty.android.ui.theme.RunState
import com.stelliberty.android.ui.theme.StatusColors
import com.stelliberty.android.ui.util.TestTags
import com.stelliberty.android.ui.util.label
import com.stelliberty.android.viewmodel.HomeUiState
import kotlinx.coroutines.isActive
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.ProgressIndicatorDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.squircle.squircleBackground
import top.yukonga.miuix.kmp.theme.MiuixTheme

fun LazyListScope.statusSection(
    state: HomeUiState = HomeUiState(),
    uptime: String = "00:00:00",
    requestedAction: HomeControlAction? = null,
) {
    item(key = "status") {
        StatusContent(state, uptime, requestedAction)
    }
}

@Composable
private fun StatusContent(
    state: HomeUiState,
    uptime: String,
    requestedAction: HomeControlAction?,
) {
    val isRunning = state.isRunning
    val isStarting = state.isStarting
    val isStopping = state.isStopping

    val runState = when {
        isStarting || isStopping -> RunState.Pending
        isRunning -> RunState.Running
        else -> RunState.Stopped
    }
    val transitioning = isStarting || isStopping
    var lastRunningUptime by remember { mutableStateOf("00:00:00") }
    LaunchedEffect(isRunning, uptime) {
        if (isRunning) lastRunningUptime = uptime
    }
    val pending = if (transitioning) {
        requestedAction ?: if (isStopping) HomeControlAction.Stop else HomeControlAction.Start
    } else null
    val statusTint = when {
        pending == HomeControlAction.Stop -> StatusColors.warning
        pending != null -> MiuixTheme.colorScheme.primary
        runState == RunState.Stopped -> StatusColors.neutral
        else -> StatusColors.runState(runState)
    }
    val phase = remember { Animatable(0f) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(isRunning, transitioning, lifecycle) {
        if (isRunning || transitioning) {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (isActive) {
                    phase.snapTo(0f)
                    phase.animateTo(1f, tween(1450, easing = FastOutSlowInEasing))
                }
            }
        }
    }

    val mixedPort = state.config?.mixedPort ?: 0
    val tproxyPort = state.config?.tproxyPort ?: 0
    val mixedLabel = if (mixedPort > 0) stringResource(R.string.home_port_mixed, mixedPort) else ""
    val tproxyLabel = if (tproxyPort > 0) stringResource(R.string.home_port_tproxy, tproxyPort) else ""
    val portsText = listOf(mixedLabel, tproxyLabel).filter { it.isNotEmpty() }.joinToString(" · ")

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .padding(top = 12.dp, bottom = 6.dp)
            .testTag(TestTags.Home.STATUS_CARD),
        cornerRadius = 22.dp,
        insideMargin = PaddingValues(0.dp),
    ) {
        Box(Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Spacer(Modifier.size(10.dp).drawWithCache {
                        onDrawBehind {
                            if (isRunning || transitioning) {
                                drawCircle(statusTint.copy(alpha = (1f - phase.value) * 0.35f), radius = size.width * (0.5f + phase.value * 0.55f))
                            }
                            drawCircle(statusTint)
                        }
                    })
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = when {
                            pending != null -> stringResource(pending.labelRes)
                            isRunning -> stringResource(R.string.home_running)
                            else -> stringResource(R.string.home_stopped)
                        },
                        modifier = Modifier.weight(1f),
                        fontSize = 19.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MiuixTheme.colorScheme.onSurface,
                    )
                    Row(
                        modifier = Modifier
                            .squircleBackground(MiuixTheme.colorScheme.surfaceContainerHigh, 50.dp)
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(AppIcons.NavProxy, null, Modifier.size(12.dp), tint = StatusColors.healthy)
                        Text(
                            text = state.tunMode.label(),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 0.sp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    StatusFieldLabel(AppIcons.StatusClock, stringResource(R.string.home_uptime), Modifier.weight(1f))
                    StatusFieldLabel(AppIcons.StatusSubscription, stringResource(R.string.home_active_profile), Modifier.weight(1f))
                }
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = when {
                            isRunning -> uptime
                            transitioning -> lastRunningUptime
                            else -> "00:00:00"
                        },
                        modifier = Modifier.weight(1f).alignByBaseline(),
                        style = StatusValueStyle,
                        autoSize = TextAutoSize.StepBased(minFontSize = 18.sp, maxFontSize = 20.sp, stepSize = 1.sp),
                        color = MiuixTheme.colorScheme.onSurface,
                        maxLines = 1,
                    )
                    Text(
                        text = state.profileName.ifEmpty { NO_VALUE },
                        modifier = Modifier.weight(1f).alignByBaseline(),
                        style = StatusValueStyle.copy(fontSize = 15.sp),
                        color = MiuixTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(14.dp))
                HorizontalDivider()
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().height(18.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (transitioning) {
                        CircularProgressIndicator(
                            size = 14.dp,
                            strokeWidth = 1.5.dp,
                            colors = ProgressIndicatorDefaults.progressIndicatorColors(
                                foregroundColor = statusTint,
                                backgroundColor = Color.Transparent,
                            ),
                        )
                        StatusDetailLine(
                            stringResource(if (isStopping) R.string.home_releasing_resources else R.string.home_establishing_connection),
                            Modifier.weight(1f),
                            tint = statusTint,
                        )
                    } else {
                        StatusDetailLine(
                            "$CORE_NAME${state.version.prefixedOrEmpty(" ")}",
                            Modifier.weight(1f).alignByBaseline(),
                        )
                        if (portsText.isNotEmpty()) {
                            StatusDetailLine(portsText, Modifier.alignByBaseline())
                        }
                    }
                }
            }
            if (transitioning) {
                Spacer(Modifier.matchParentSize().drawWithCache {
                    val beamWidth = size.width * 0.4f
                    val beam = Brush.horizontalGradient(listOf(Color.Transparent, statusTint, Color.Transparent), 0f, beamWidth)
                    onDrawBehind {
                        val height = 3.dp.toPx()
                        drawRect(statusTint.copy(alpha = 0.08f), topLeft = Offset(0f, size.height - height), size = Size(size.width, height))
                        val left = -beamWidth + (size.width + beamWidth) * phase.value
                        translate(left, size.height - height) {
                            drawRect(beam, size = Size(beamWidth, height))
                        }
                    }
                })
            }
        }
    }
}

@Composable
private fun StatusFieldLabel(icon: ImageVector, text: String, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(icon, null, Modifier.size(12.dp), tint = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        StatusDetailLine(text)
    }
}

@Composable
private fun StatusDetailLine(text: String, modifier: Modifier = Modifier, tint: Color = Color.Unspecified) {
    Text(
        text = text,
        modifier = modifier,
        fontSize = 11.sp,
        letterSpacing = 0.sp,
        style = StatusDetailStyle,
        color = if (tint == Color.Unspecified) MiuixTheme.colorScheme.onSurfaceVariantSummary else tint,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

private fun String.prefixedOrEmpty(prefix: String): String = if (isEmpty()) "" else prefix + this

private const val CORE_NAME = "Mihomo"

private const val NO_VALUE = "--"

private val StatusValueStyle = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontSize = 20.sp,
    lineHeight = 28.sp,
    fontWeight = FontWeight.SemiBold,
    fontFeatureSettings = "tnum",
    letterSpacing = 0.sp,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both),
)

private val StatusDetailStyle = StatusValueStyle.copy(
    fontSize = 11.sp,
    lineHeight = 18.sp,
    fontWeight = FontWeight.Medium,
)
