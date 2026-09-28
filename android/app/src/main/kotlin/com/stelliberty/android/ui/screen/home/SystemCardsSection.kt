package com.stelliberty.android.ui.screen.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stelliberty.android.R
import com.stelliberty.android.ui.icon.AppIcons
import com.stelliberty.android.ui.theme.StatusColors
import com.stelliberty.android.ui.util.sheetContentSafePadding
import com.stelliberty.android.ui.util.sheetHeightTransition
import com.stelliberty.android.util.FormatUtils
import com.stelliberty.android.util.formatIsoTimeRelative
import com.stelliberty.android.viewmodel.MemorySnapshot
import com.stelliberty.android.viewmodel.ProviderTrafficInfo
import com.stelliberty.android.viewmodel.SystemInfoSnapshot
import kotlin.math.roundToInt
import kotlin.time.Instant
import kotlinx.collections.immutable.ImmutableList
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.squircle.squircleBackground
import top.yukonga.miuix.kmp.theme.LocalDismissState
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowBottomSheet

fun LazyListScope.systemCardsSection(
    memory: MemorySnapshot = MemorySnapshot(),
    systemInfo: SystemInfoSnapshot = SystemInfoSnapshot(),
) {
    item(key = "system_cards") {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .padding(vertical = 6.dp)
                .height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Card(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                insideMargin = PaddingValues(16.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "IP",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = MiuixTheme.colorScheme.onSurface,
                    )
                    val category = ipCategory(systemInfo.localIp)
                    BadgeLabel(category.label, category.color())
                }
                InfoRow(stringResource(R.string.home_address), systemInfo.localIp, Modifier.padding(top = 8.dp))
                InfoRow(stringResource(R.string.home_interface), systemInfo.interfaceName, Modifier.padding(top = 4.dp))
            }

            Card(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                insideMargin = PaddingValues(16.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.home_system),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = MiuixTheme.colorScheme.onSurface,
                    )
                    BadgeLabel("SYS", StatusColors.neutral)
                }
                InfoRow(
                    "CPU",
                    systemInfo.cpuUsage,
                    Modifier.padding(top = 8.dp),
                )
                InfoRow(
                    "RAM",
                    memory.ramUsage,
                    Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

private enum class IpCategory(val label: String) {
    Tun("TUN"),

    Lan("LAN"),

    Wan("WAN"),

    None("--"),
}

private fun ipCategory(ip: String): IpCategory {
    val parts = ip.split('.')
    if (parts.size != 4) return IpCategory.None
    val octets = parts.map { it.toIntOrNull() ?: return IpCategory.None }
    if (octets.all { it == 0 }) return IpCategory.None
    val (o1, o2) = octets
    return when (o1) {
        198 if (o2 == 18 || o2 == 19) -> IpCategory.Tun
        10 -> IpCategory.Lan
        192 if o2 == 168 -> IpCategory.Lan
        172 if o2 in 16..31 -> IpCategory.Lan
        100 if o2 in 64..127 -> IpCategory.Lan
        169 if o2 == 254 -> IpCategory.Lan
        else -> IpCategory.Wan
    }
}

@Composable
@ReadOnlyComposable
private fun IpCategory.color(): Color = when (this) {
    IpCategory.Tun -> StatusColors.healthy
    IpCategory.Lan, IpCategory.None -> StatusColors.neutral
    IpCategory.Wan -> StatusColors.trafficDownload
}

@Composable
internal fun SubscriptionTrafficDialog(
    show: Boolean,
    providers: ImmutableList<ProviderTrafficInfo>,
    isLoading: Boolean,
    loadFailed: Boolean,
    onUpdateAll: () -> Unit,
    onDismiss: () -> Unit,
) {
    WindowBottomSheet(
        show = show,
        title = stringResource(R.string.home_subscription_provider_traffic),
        onDismissRequest = onDismiss,
        startAction = {
            IconButton(onClick = onUpdateAll, enabled = !isLoading) {
                Icon(
                    imageVector = AppIcons.Refresh,
                    contentDescription = stringResource(R.string.home_provider_update_all),
                    tint = MiuixTheme.colorScheme.onBackground,
                )
            }
        },
        endAction = {
            val dismiss = LocalDismissState.current
            IconButton(onClick = { dismiss?.invoke() }) {
                Icon(
                    imageVector = AppIcons.Close,
                    contentDescription = stringResource(R.string.common_close),
                    tint = MiuixTheme.colorScheme.onBackground,
                )
            }
        },
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .sheetContentSafePadding()
                .heightIn(min = 200.dp)
                .sheetHeightTransition(),
        ) {
            when {
                isLoading -> CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                )

                loadFailed -> ProviderTrafficMessage(
                    text = stringResource(R.string.home_subscription_provider_traffic_load_failed),
                    modifier = Modifier.align(Alignment.Center),
                )

                providers.isEmpty() -> ProviderTrafficMessage(
                    text = stringResource(R.string.home_subscription_no_provider_traffic),
                    modifier = Modifier.align(Alignment.Center),
                )

                else -> LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(
                        items = providers,
                        key = { it.id },
                    ) { provider ->
                        ProviderTrafficCard(provider)
                    }
                }
            }
        }
    }
}

@Composable
private fun ProviderTrafficMessage(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        modifier = modifier.fillMaxWidth(),
        textAlign = TextAlign.Center,
        fontSize = 14.sp,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
    )
}

@Composable
private fun ProviderTrafficCard(provider: ProviderTrafficInfo) {
    val upload = provider.upload.coerceAtLeast(0)
    val download = provider.download.coerceAtLeast(0)
    val total = provider.total.coerceAtLeast(0)
    val used = if (Long.MAX_VALUE - upload < download) Long.MAX_VALUE else upload + download
    val remaining = (total - used).coerceAtLeast(0)
    val hasQuota = total > 0
    val progress = if (hasQuota) {
        (used.toFloat() / total.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    val percent = if (hasQuota) "${(progress * 100).roundToInt()}%" else "--"
    val updatedText = remember(provider.updatedAt) { formatIsoTimeRelative(provider.updatedAt) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = provider.name,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MiuixTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f, fill = false),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (provider.nodeCount > 0) {
                    Spacer(Modifier.width(6.dp))
                    NodeCountBadge(provider.nodeCount)
                }
            }
            Spacer(Modifier.width(8.dp))
            if (updatedText.isNotEmpty()) {
                Text(
                    text = updatedText,
                    fontSize = 12.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    maxLines = 1,
                )
            }
            if (provider.hasTraffic) {
                Spacer(Modifier.width(6.dp))
                UsageBadge(percent, StatusColors.usage(progress))
            }
        }

        if (provider.hasTraffic) {
            TrafficDetailPanel(
                progress = progress.takeIf { hasQuota },
                used = FormatUtils.formatBytes(used),
                remaining = if (hasQuota) FormatUtils.formatBytes(remaining) else "--",
                total = if (hasQuota) FormatUtils.formatBytes(total) else "--",
                expire = expireText(provider.expire),
            )
        }
    }
}

@Composable
private fun NodeCountBadge(count: Int) {
    Text(
        text = count.toString(),
        modifier = Modifier
            .squircleBackground(MiuixTheme.colorScheme.onSurface.copy(alpha = 0.08f), 3.dp)
            .padding(horizontal = 4.dp, vertical = 1.dp),
        fontSize = 9.sp,
        fontWeight = FontWeight.Bold,
        fontFamily = FontFamily.Monospace,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
    )
}

@Composable
private fun TrafficDetailPanel(
    progress: Float?,
    used: String,
    remaining: String,
    total: String,
    expire: String,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth(),
        cornerRadius = 12.dp,
        insideMargin = PaddingValues(
            start = 12.dp,
            top = 12.dp,
            end = 12.dp,
            bottom = 14.dp,
        ),
        colors = CardDefaults.defaultColors(
            color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.04f),
        ),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (progress != null) {
                TrafficProgressBar(progress)
            }
            UsedRemainingRow(
                used = used,
                remaining = remaining,
            )
            TotalExpireRow(
                total = total,
                expire = expire,
            )
        }
    }
}

@Composable
private fun UsedRemainingRow(
    used: String,
    remaining: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .padding(end = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TrafficMetaLabel(stringResource(R.string.home_used))
            TrafficMetaValue(used)
        }
        Box(
            modifier = Modifier
                .width(1.dp)
                .height(20.dp)
                .background(MiuixTheme.colorScheme.onSurface.copy(alpha = 0.12f)),
        )
        Row(
            modifier = Modifier
                .weight(1f)
                .padding(start = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TrafficMetaLabel(stringResource(R.string.home_remaining))
            TrafficMetaValue(remaining)
        }
    }
}

@Composable
private fun TotalExpireRow(
    total: String,
    expire: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "${stringResource(R.string.home_total)} $total",
            modifier = Modifier.weight(0.42f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontSize = 12.sp,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
        Text(
            text = expire,
            modifier = Modifier.weight(0.58f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.End,
            fontSize = 12.sp,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
    }
}

@Composable
private fun TrafficMetaLabel(text: String) {
    Text(
        text = text,
        maxLines = 1,
        fontSize = 12.sp,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
    )
}

@Composable
private fun TrafficMetaValue(text: String) {
    Text(
        text = text,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        textAlign = TextAlign.End,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        color = MiuixTheme.colorScheme.onSurface,
    )
}

@Composable
private fun UsageBadge(text: String, color: Color) {
    Text(
        text = text,
        modifier = Modifier
            .squircleBackground(color.copy(alpha = 0.12f), 6.dp)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        color = color,
    )
}

@Composable
private fun TrafficProgressBar(progress: Float) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(7.dp)
            .clip(RoundedCornerShape(50))
            .background(MiuixTheme.colorScheme.onSurface.copy(alpha = 0.08f)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(progress)
                .height(7.dp)
                .clip(RoundedCornerShape(50))
                .background(StatusColors.usage(progress)),
        )
    }
}

@Composable
private fun expireText(expire: Long): String {
    if (expire <= 0) return stringResource(R.string.home_no_expire)
    val formatted = runCatching {
        Instant.fromEpochSeconds(expire)
            .toLocalDateTime(TimeZone.currentSystemDefault())
            .formatDateTime()
    }.getOrNull() ?: stringResource(R.string.home_expire_unknown)
    return "${stringResource(R.string.home_expire)} $formatted"
}

private fun LocalDateTime.formatDateTime(): String = buildString {
    append(year.toString().padStart(4, '0'))
    append('-')
    append(month.number.toString().padStart(2, '0'))
    append('-')
    append(day.toString().padStart(2, '0'))
    append(' ')
    append(hour.toString().padStart(2, '0'))
    append(':')
    append(minute.toString().padStart(2, '0'))
}
