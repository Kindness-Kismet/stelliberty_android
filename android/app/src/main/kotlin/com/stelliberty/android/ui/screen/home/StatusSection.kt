package com.stelliberty.android.ui.screen.home

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.stelliberty.android.R
import com.stelliberty.android.ui.theme.RunState
import com.stelliberty.android.ui.theme.StatusColors
import com.stelliberty.android.ui.util.TestTags
import com.stelliberty.android.ui.util.label
import com.stelliberty.android.viewmodel.HomeUiState
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.ProgressIndicatorDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.squircle.squircleBackground
import top.yukonga.miuix.kmp.theme.MiuixTheme

fun LazyListScope.statusSection(
    state: HomeUiState = HomeUiState(),
    uptime: String = "00:00:00",
) {
    item(key = "status") {
        StatusContent(state, uptime)
    }
}

@Composable
private fun StatusContent(
    state: HomeUiState,
    uptime: String,
) {
    val isRunning = state.isRunning
    val isStarting = state.isStarting
    val isStopping = state.isStopping

    val runState = when {
        isStarting || isStopping -> RunState.Pending
        isRunning -> RunState.Running
        else -> RunState.Stopped
    }
    val statusTint = StatusColors.runState(runState)

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
        insideMargin = PaddingValues(20.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).squircleBackground(statusTint, 4.dp))
                Spacer(Modifier.width(10.dp))
                Text(
                    text = when {
                        isStopping -> stringResource(R.string.home_stopping)
                        isStarting -> stringResource(R.string.home_starting)
                        isRunning -> stringResource(R.string.home_running)
                        else -> stringResource(R.string.home_stopped)
                    },
                    modifier = Modifier.weight(1f),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MiuixTheme.colorScheme.onSurface,
                )
                Text(
                    text = state.tunMode.label(),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
            Spacer(Modifier.height(24.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                StatusDetailLine(stringResource(R.string.home_uptime), Modifier.weight(1f))
                StatusDetailLine(stringResource(R.string.home_active_profile), Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                Text(
                    text = if (isRunning) uptime else "00:00:00",
                    modifier = Modifier.weight(1f).alignByBaseline(),
                    style = StatusValueStyle,
                    autoSize = TextAutoSize.StepBased(minFontSize = 18.sp, maxFontSize = 20.sp, stepSize = 1.sp),
                    color = MiuixTheme.colorScheme.onSurface,
                    maxLines = 1,
                )
                Text(
                    text = state.profileName.ifEmpty { NO_VALUE },
                    modifier = Modifier.weight(1f).alignByBaseline(),
                    style = StatusValueStyle,
                    color = MiuixTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(20.dp))
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                StatusDetailLine(
                    "$CORE_NAME${state.version.prefixedOrEmpty(" ")}",
                    Modifier.weight(1f).alignByBaseline(),
                )
                if (portsText.isNotEmpty()) {
                    StatusDetailLine(portsText, Modifier.alignByBaseline())
                }
            }
            if (isStarting || isStopping) {
                Spacer(Modifier.height(16.dp))
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    colors = ProgressIndicatorDefaults.progressIndicatorColors(
                        foregroundColor = statusTint,
                        disabledForegroundColor = statusTint,
                        backgroundColor = statusTint.copy(alpha = ProgressTrackAlpha),
                    ),
                )
            }
        }
    }
}

@Composable
private fun StatusDetailLine(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier,
        fontSize = 12.sp,
        letterSpacing = 0.sp,
        style = StatusDetailStyle,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

private fun String.prefixedOrEmpty(prefix: String): String = if (isEmpty()) "" else prefix + this

private const val CORE_NAME = "Mihomo"

private const val NO_VALUE = "--"

private const val ProgressTrackAlpha = 0.24f

private val StatusValueStyle = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontSize = 20.sp,
    lineHeight = 28.sp,
    fontWeight = FontWeight.Medium,
    fontFeatureSettings = "tnum",
    letterSpacing = 0.sp,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both),
)

private val StatusDetailStyle = StatusValueStyle.copy(
    fontSize = 12.sp,
    lineHeight = 18.sp,
    fontWeight = FontWeight.Normal,
)
