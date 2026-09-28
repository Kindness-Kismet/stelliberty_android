package com.stelliberty.android.ui.screen.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stelliberty.android.R
import com.stelliberty.android.domain.model.SubscriptionInfo
import com.stelliberty.android.ui.theme.StatusColors
import com.stelliberty.android.util.FormatUtils
import com.stelliberty.android.viewmodel.HomeUiState
import com.stelliberty.android.viewmodel.SpeedSnapshot
import kotlin.math.roundToInt
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType

fun LazyListScope.overviewCardsSection(
    speed: SpeedSnapshot = SpeedSnapshot(),
    state: HomeUiState = HomeUiState(),
    onSpeedClick: () -> Unit = {},
    onSubscriptionClick: () -> Unit = {},
) {
    item(key = "overview_title") {
        SmallTitle(text = stringResource(R.string.home_overview))
    }
    item(key = "overview_cards") {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .padding(bottom = 6.dp)
                .height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SpeedCard(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                speed = speed,
                isRunning = state.isRunning,
                onClick = onSpeedClick,
            )
            SubscriptionCard(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                subscription = state.subscription,
                isRunning = state.isRunning,
                onClick = onSubscriptionClick,
            )
        }
    }
}

@Composable
private fun SpeedCard(
    modifier: Modifier = Modifier,
    speed: SpeedSnapshot,
    isRunning: Boolean,
    onClick: () -> Unit,
) {
    Card(
        modifier = modifier,
        insideMargin = PaddingValues(0.dp),
        onClick = if (isRunning) onClick else null,
        showIndication = isRunning,
        pressFeedbackType = if (isRunning) PressFeedbackType.Sink else PressFeedbackType.None,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            TrafficSparkline(
                modifier = Modifier.matchParentSize(),
                history = speed.history,
                uploadColor = StatusColors.trafficUpload,
                downloadColor = StatusColors.trafficDownload,
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.home_speed),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = MiuixTheme.colorScheme.onSurface
                    )
                    BadgeLabel("NET", StatusColors.trafficDownload)
                }
                InfoRow(stringResource(R.string.home_upload), speed.uploadSpeed, Modifier.padding(top = 8.dp))
                InfoRow(stringResource(R.string.home_download), speed.downloadSpeed, Modifier.padding(top = 4.dp))
            }
        }
    }
}

@Composable
private fun SubscriptionCard(
    modifier: Modifier = Modifier,
    subscription: SubscriptionInfo?,
    isRunning: Boolean,
    onClick: () -> Unit,
) {
    val total = subscription?.Total?.coerceAtLeast(0) ?: 0
    val used = subscription?.let { (it.Upload + it.Download).coerceAtLeast(0) } ?: 0
    val hasQuota = total > 0
    val progress = if (hasQuota) (used.toFloat() / total.toFloat()).coerceIn(0f, 1f) else 0f

    Card(
        modifier = modifier,
        insideMargin = PaddingValues(0.dp),
        onClick = if (isRunning) onClick else null,
        showIndication = isRunning,
        pressFeedbackType = if (isRunning) PressFeedbackType.Sink else PressFeedbackType.None,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (hasQuota) {
                SubscriptionUsageBar(
                    modifier = Modifier.matchParentSize(),
                    progress = progress,
                    color = StatusColors.usage(progress),
                )
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.home_subscription),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = MiuixTheme.colorScheme.onSurface,
                    )
                    BadgeLabel(
                        text = if (hasQuota) "${(progress * 100).roundToInt()}%" else "SUB",
                        color = StatusColors.usage(progress),
                    )
                }
                InfoRow(
                    stringResource(R.string.home_used),
                    FormatUtils.formatBytes(used),
                    Modifier.padding(top = 8.dp)
                )
                InfoRow(
                    stringResource(R.string.home_total),
                    if (hasQuota) FormatUtils.formatBytes(total) else "--",
                    Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}
