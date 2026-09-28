package com.stelliberty.android.ui.screen.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stelliberty.android.R
import com.stelliberty.android.ui.icon.AppIcons
import com.stelliberty.android.ui.theme.StatusColors
import com.stelliberty.android.util.FormatUtils
import com.stelliberty.android.viewmodel.HomeUiState
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

fun LazyListScope.latencySection(
    state: HomeUiState = HomeUiState(),
    onTestLatency: () -> Unit = {},
) {
    item(key = "latency_title") {
        LatencyHeader(state, onTestLatency)
    }
    item(key = "latency") {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .padding(bottom = 6.dp),
            insideMargin = PaddingValues(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val tested = state.latencyBaidu >= 0 ||
                    state.latencyCloudflare >= 0 ||
                    state.latencyGoogle >= 0
                LatencyItem("Baidu", state.latencyBaidu, tested)
                LatencyItem("Cloudflare", state.latencyCloudflare, tested)
                LatencyItem("Google", state.latencyGoogle, tested)
            }
        }
    }
}

@Composable
private fun LatencyHeader(
    state: HomeUiState,
    onTestLatency: () -> Unit,
) {
    // 三项全为 -1 才算没测过：只要有一项通了就说明测过，剩下不通的属于结果而非未测。
    val anyTested = state.latencyBaidu >= 0 || state.latencyCloudflare >= 0 || state.latencyGoogle >= 0
    val statusColor = when {
        !anyTested -> StatusColors.neutral
        state.latencyGoogle >= 0 -> StatusColors.healthy
        state.latencyCloudflare >= 0 -> StatusColors.warning
        else -> StatusColors.danger
    }
    val statusText = when {
        !anyTested -> stringResource(R.string.home_latency_untested)
        state.latencyGoogle >= 0 -> stringResource(R.string.home_latency_normal)
        state.latencyCloudflare >= 0 -> stringResource(R.string.home_latency_partial)
        else -> stringResource(R.string.home_latency_abnormal)
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SmallTitle(text = stringResource(R.string.home_latency))
        Row(
            modifier = Modifier.padding(end = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (state.isRunning && !state.latencyViaRules) {
                Text(
                    text = stringResource(R.string.home_latency_not_via_rules),
                    fontSize = 12.sp,
                    color = StatusColors.warning,
                )
            }
            if (state.isRunning) {
                Text(
                    text = statusText,
                    fontSize = 12.sp,
                    color = statusColor,
                )
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(statusColor),
                )
                IconButton(
                    onClick = onTestLatency,
                    modifier = Modifier.size(24.dp),
                ) {
                    Icon(
                        imageVector = AppIcons.Refresh,
                        contentDescription = stringResource(R.string.common_refresh),
                        modifier = Modifier.size(16.dp),
                        tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
            }
        }
    }
}

@Composable
private fun LatencyItem(name: String, delay: Int, tested: Boolean) {
    // -1 有两种含义：一次都没测显示占位符、灰色；测过没通才是超时、红色。整组是否测过由调用方判断。
    val untested = delay < 0 && !tested
    val color = StatusColors.delay(if (untested) null else delay)
    val text = when {
        delay >= 0 -> FormatUtils.formatLatency(delay)
        untested -> "--"
        else -> stringResource(R.string.proxy_timeout)
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = name,
                fontSize = 13.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(color),
            )
        }
        Text(
            text = text,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            color = MiuixTheme.colorScheme.onSurface,
        )
    }
}
