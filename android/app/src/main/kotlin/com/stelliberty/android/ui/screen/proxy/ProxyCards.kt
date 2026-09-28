package com.stelliberty.android.ui.screen.proxy

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import com.stelliberty.android.R
import com.stelliberty.android.ui.icon.AppIcons
import com.stelliberty.android.ui.platform.IconLoader
import com.stelliberty.android.ui.theme.StatusColors
import com.stelliberty.android.ui.util.TestTags
import com.stelliberty.android.util.FormatUtils
import com.stelliberty.android.viewmodel.ProxyGroupUi
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.squircle.squircleBackground
import top.yukonga.miuix.kmp.squircle.squircleClip
import top.yukonga.miuix.kmp.squircle.squircleSurface
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun ProxyGroupTabs(
    names: ImmutableList<String>,
    selectedName: String,
    listState: LazyListState,
    onSelect: (String) -> Unit,
) {
    LaunchedEffect(selectedName, names) {
        val index = names.indexOf(selectedName)
        if (index >= 0 && listState.layoutInfo.visibleItemsInfo.none {
                it.index == index && it.offset >= 0 &&
                    it.offset + it.size <= listState.layoutInfo.viewportEndOffset
            }
        ) {
            listState.animateScrollToItem(index)
        }
    }
    LazyRow(
        state = listState,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.fillMaxWidth().background(MiuixTheme.colorScheme.surface)
            .nestedScroll(GroupTabsScrollConnection).selectableGroup(),
    ) {
        items(names, key = { it }) { name ->
            val selected = name == selectedName
            Box(
                modifier = Modifier
                    .widthIn(max = 220.dp)
                    .selectable(
                        selected = selected,
                        interactionSource = null,
                        indication = null,
                        role = Role.Tab,
                        onClick = { onSelect(name) },
                    )
                    .testTag(TestTags.Proxy.groupTab(name))
                    .padding(horizontal = 12.dp),
            ) {
                Text(
                    text = name,
                    fontSize = 15.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    color = if (selected) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 12.dp, bottom = 15.dp),
                )
                if (selected) {
                    Box(
                        Modifier.matchParentSize().wrapContentHeight(Alignment.Bottom).height(3.dp)
                            .squircleBackground(MiuixTheme.colorScheme.primary, 3.dp),
                    )
                }
            }
        }
    }
}

// 标签到达边缘后仍消费横向余量，避免外层主页 Pager 接管拖动与惯性。
private object GroupTabsScrollConnection : NestedScrollConnection {
    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset =
        Offset(available.x, 0f)

    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity =
        Velocity(available.x, 0f)
}

@Composable
internal fun ProxyGroupHeader(
    group: ProxyGroupUi,
    isExpanded: Boolean,
    iconCacheVersion: Int,
    isTesting: Boolean,
    onTestDelay: () -> Unit,
    onUnfix: () -> Unit,
    onToggle: () -> Unit,
) {
    val rotation = animateFloatAsState(
        targetValue = if (isExpanded) 270f else 90f,
        animationSpec = tween(180),
        label = "groupArrow",
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .testTag(TestTags.Proxy.group(group.name))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (group.icon.isNotEmpty()) GroupIcon(group.icon, iconCacheVersion)
        Column(Modifier.weight(1f)) {
            Text(
                text = group.name,
                fontSize = 18.sp,
                lineHeight = 24.sp,
                fontWeight = FontWeight.SemiBold,
                color = MiuixTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val availableCount = remember(group.delays) {
                group.delays.values.count { it > 0 }
            }
            val count = stringResource(R.string.proxy_node_count, availableCount, group.all.size)
            Text(
                text = listOf(group.type, group.now, count).filter { it.isNotEmpty() }.joinToString(" · "),
                modifier = Modifier.padding(top = 2.dp),
                fontSize = 13.sp,
                lineHeight = 18.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(10.dp))
        if (group.isFixed) {
            IconButton(
                onClick = onUnfix,
                modifier = Modifier.size(36.dp).testTag(TestTags.Proxy.groupUnfix(group.name)),
            ) {
                Icon(
                    imageVector = AppIcons.Unpin,
                    contentDescription = stringResource(R.string.proxy_unfix),
                    modifier = Modifier.size(18.dp),
                    tint = StatusColors.warning,
                )
            }
        }
        val delay = group.delays[group.now]
        val delayColor = StatusColors.delay(delay)
        Box(
            modifier = Modifier
                .height(28.dp)
                .widthIn(min = 36.dp)
                .clickable(
                    interactionSource = null,
                    indication = null,
                    enabled = !isTesting,
                    role = Role.Button,
                    onClickLabel = stringResource(R.string.proxy_test_group_delay),
                    onClick = onTestDelay,
                )
                .testTag(TestTags.Proxy.groupTest(group.name))
                .padding(horizontal = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (isTesting) {
                CircularProgressIndicator(size = 14.dp, strokeWidth = 2.dp)
            } else {
                Text(
                    text = when {
                        delay == null -> "—"
                        delay < 0 -> "-1"
                        else -> FormatUtils.formatLatency(delay)
                    },
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = delayColor,
                    maxLines = 1,
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        IconButton(
            onClick = onToggle,
            minWidth = 36.dp,
            minHeight = 36.dp,
            backgroundColor = MiuixTheme.colorScheme.surface,
            modifier = Modifier.testTag(TestTags.Proxy.groupToggle(group.name)),
        ) {
            Icon(
                imageVector = AppIcons.ChevronRight,
                contentDescription = stringResource(
                    if (isExpanded) R.string.proxy_collapse_group else R.string.proxy_expand_group,
                ),
                tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.size(20.dp).graphicsLayer { rotationZ = rotation.value },
            )
        }
    }
}

@Composable
internal fun ProxyNodeRow(
    row: ImmutableList<String>,
    group: ProxyGroupUi,
    singleColumnProgress: State<Float>,
    testingNodes: ImmutableSet<String>,
    onTestNodeDelay: (String) -> Unit,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Layout(
        modifier = modifier,
        content = {
            row.forEach { name ->
                ProxyNodeCard(
                    name = name,
                    type = group.nodeTypes[name].orEmpty(),
                    delay = group.delays[name],
                    isSelected = name == group.now,
                    isFixed = name == group.fixed,
                    isSelectable = group.isSelectable,
                    isTesting = name in testingNodes,
                    onTestDelay = { onTestNodeDelay(name) },
                    onClick = { onSelect(name) },
                )
            }
        },
    ) { measurables, constraints ->
        val fraction = singleColumnProgress.value
        val gap = 10.dp.roundToPx()
        val halfWidth = (constraints.maxWidth - gap) / 2
        val cellWidth = lerp(halfWidth, constraints.maxWidth, fraction)
        val placeables = measurables.map {
            it.measure(constraints.copy(minWidth = cellWidth, maxWidth = cellWidth, minHeight = 0))
        }
        val first = placeables.first()
        val second = placeables.getOrNull(1)
        val secondX = lerp(halfWidth + gap, 0, fraction)
        val secondY = lerp(0, first.height + gap, fraction)
        // 行始终占完整高度；从零高度展开会让懒列表在首帧组合整组节点。
        val height = maxOf(first.height, if (second != null) secondY + second.height else 0)
        layout(constraints.maxWidth, height) {
            first.placeRelative(0, 0)
            second?.placeRelative(secondX, secondY)
        }
    }
}

@Composable
private fun ProxyNodeCard(
    name: String,
    type: String,
    delay: Int?,
    isSelected: Boolean,
    isFixed: Boolean,
    isSelectable: Boolean,
    isTesting: Boolean,
    onTestDelay: () -> Unit,
    onClick: () -> Unit,
) {
    val primary = MiuixTheme.colorScheme.primary
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .squircleSurface(MiuixTheme.colorScheme.surfaceContainer, 14.dp)
            .selectable(selected = isSelected, enabled = isSelectable, role = Role.RadioButton, onClick = onClick)
            .testTag(TestTags.Proxy.node(name)),
    ) {
        if (isSelected) {
            Box(
                Modifier.align(Alignment.CenterStart).width(3.dp).height(28.dp)
                    .squircleBackground(if (isFixed) StatusColors.warning else primary, 2.dp),
            )
        }
        Column(Modifier.padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = name,
                    modifier = Modifier.weight(1f),
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MiuixTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (isSelected || isFixed) {
                    Spacer(Modifier.width(6.dp))
                    Icon(
                        imageVector = if (isFixed) AppIcons.Pin else AppIcons.Check,
                        contentDescription = if (isFixed) stringResource(R.string.proxy_fixed) else null,
                        modifier = Modifier.size(14.dp),
                        tint = if (isFixed) StatusColors.warning else primary,
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp).heightIn(min = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = type,
                    modifier = Modifier.weight(1f).alignByBaseline(),
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val delayColor = StatusColors.delay(delay)
                val label = stringResource(R.string.proxy_test_node_delay)
                Box(
                    modifier = Modifier
                        .widthIn(min = 44.dp)
                        .alignByBaseline()
                        .clickable(
                            interactionSource = null,
                            indication = null,
                            enabled = !isTesting,
                            onClickLabel = label,
                            role = Role.Button,
                            onClick = onTestDelay,
                        )
                        .testTag(TestTags.Proxy.nodeTest(name)),
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    when {
                        isTesting -> CircularProgressIndicator(size = 14.dp, strokeWidth = 2.dp)
                        else -> Text(
                            text = when {
                                delay == null -> "—"
                                delay < 0 -> "-1"
                                else -> FormatUtils.formatLatency(delay)
                            },
                            fontSize = 11.sp,
                            lineHeight = 16.sp,
                            fontWeight = FontWeight.Medium,
                            color = delayColor,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GroupIcon(
    icon: String,
    cacheVersion: Int,
) {
    var bitmap by remember(icon, cacheVersion) { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(icon, cacheVersion) {
        bitmap = IconLoader.loadIcon(icon)
    }

    bitmap?.let { current ->
        Image(
            bitmap = current,
            contentDescription = null,
            modifier = Modifier
                .size(32.dp)
                .squircleClip(8.dp),
        )
        Spacer(Modifier.width(12.dp))
    }
}
