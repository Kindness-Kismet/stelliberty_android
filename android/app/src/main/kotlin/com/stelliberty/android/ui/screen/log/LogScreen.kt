package com.stelliberty.android.ui.screen.log

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stelliberty.android.R
import com.stelliberty.android.domain.model.LogMessage
import com.stelliberty.android.ui.component.AdaptiveTopAppBar
import com.stelliberty.android.ui.component.blur.BlurredBar
import com.stelliberty.android.ui.component.blur.rememberBlurBackdrop
import com.stelliberty.android.ui.icon.AppIcons
import com.stelliberty.android.ui.theme.StatusColors
import com.stelliberty.android.ui.util.horizontalCutoutPadding
import com.stelliberty.android.viewmodel.LogViewModel
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.squircle.squircleBackground
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

@Composable
fun LogScreen(
    viewModel: LogViewModel,
    onBack: () -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val logs by viewModel.logs.collectAsStateWithLifecycle()
    val scrollBehavior = MiuixScrollBehavior()
    val listState = rememberLazyListState()

    DisposableEffect(Unit) {
        viewModel.connect()
        onDispose { viewModel.disconnect() }
    }

    val autoScrollEnabled by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val visible = info.visibleItemsInfo
            if (visible.isEmpty()) true
            else {
                val lastVisible = visible.last().index
                lastVisible >= info.totalItemsCount - 2
            }
        }
    }

    // 触发条件必须用最后一条的编号（它只增不减），不能用列表长度：缓冲写满后长度就恒定不变了，
    // 自动滚动会永久停摆。
    val lastLogId = logs.lastOrNull()?.id
    LaunchedEffect(lastLogId) {
        if (lastLogId != null && autoScrollEnabled) {
            listState.animateScrollToItem(logs.lastIndex)
        }
    }

    val backdrop = rememberBlurBackdrop()
    val blurActive = backdrop != null
    val barColor = if (blurActive) Color.Transparent else MiuixTheme.colorScheme.surface

    Scaffold(
        topBar = {
            BlurredBar(backdrop = backdrop, blurActive = blurActive) {
                AdaptiveTopAppBar(
                    title = stringResource(R.string.log_title),
                    color = barColor,
                    scrollBehavior = scrollBehavior,
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            val layoutDirection = LocalLayoutDirection.current
                            Icon(
                                imageVector = AppIcons.Back,
                                contentDescription = stringResource(R.string.common_back),
                                tint = MiuixTheme.colorScheme.onSurface,
                                modifier = Modifier.graphicsLayer {
                                    scaleX = if (layoutDirection == LayoutDirection.Rtl) -1f else 1f
                                },
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = { viewModel.clearLogs() }) {
                            Icon(
                                imageVector = AppIcons.Delete,
                                contentDescription = stringResource(R.string.log_clear),
                                tint = MiuixTheme.colorScheme.onSurface,
                            )
                        }
                    },
                )
            }
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .horizontalCutoutPadding()
                .then(if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier)
                .scrollEndHaptic()
                .overScrollVertical()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            state = listState,
            contentPadding = PaddingValues(
                top = innerPadding.calculateTopPadding(),
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (logs.isEmpty()) {
                item(key = "empty", contentType = "empty") {
                    Column(
                        modifier = Modifier.fillParentMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            text = if (uiState.isConnected) {
                                stringResource(R.string.log_waiting)
                            } else {
                                stringResource(R.string.log_not_connected)
                            },
                            fontSize = 16.sp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    }
                }
            } else {
                item(key = "top_spacer", contentType = "spacer") {
                    Spacer(Modifier)
                }
            }

            items(
                items = logs,
                key = { it.id },
                contentType = { "log" },
            ) { indexedLog ->
                LogCard(indexedLog.message)
            }

            item(key = "bottom_spacer", contentType = "spacer") {
                Spacer(Modifier.navigationBarsPadding())
            }
        }
    }
}

private data class ParsedLog(
    val protocol: String = "",
    val source: String = "",
    val target: String = "",
    val rule: String = "",
    val proxy: String = "",
    val raw: String = "",
)

private fun parsePayload(payload: String): ParsedLog {
    val raw = payload.trim()

    val protocolMatch = Regex("^\\[(\\w+)]").find(raw)
    val protocol = protocolMatch?.groupValues?.get(1) ?: ""
    val rest = if (protocolMatch != null) raw.substring(protocolMatch.range.last + 1).trim() else raw

    val arrowIdx = rest.indexOf("-->")
    if (arrowIdx < 0) return ParsedLog(raw = raw)

    val source = rest.substring(0, arrowIdx).trim()
    val afterArrow = rest.substring(arrowIdx + 3).trim()

    val matchIdx = afterArrow.indexOf(" match ")
    if (matchIdx < 0) {
        return ParsedLog(protocol = protocol, source = source, target = afterArrow, raw = raw)
    }

    val target = afterArrow.substring(0, matchIdx).trim()
    val matchPart = afterArrow.substring(matchIdx + 7).trim()

    val usingIdx = matchPart.indexOf(" using ")
    val rule: String
    val proxy: String
    if (usingIdx >= 0) {
        rule = matchPart.substring(0, usingIdx).trim()
        proxy = matchPart.substring(usingIdx + 7).trim()
    } else {
        rule = matchPart
        proxy = ""
    }

    return ParsedLog(protocol = protocol, source = source, target = target, rule = rule, proxy = proxy, raw = raw)
}

@Composable
private fun LogCard(log: LogMessage) {
    val levelInfo = getLevelInfo(log.type)
    val parsed = remember(log.payload) { parsePayload(log.payload) }
    val isParsed = parsed.target.isNotEmpty()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        insideMargin = PaddingValues(12.dp),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LevelBadge(levelInfo)

                Spacer(Modifier.width(8.dp))

                if (isParsed && parsed.protocol.isNotEmpty()) {
                    ProtocolBadge(parsed.protocol)
                    Spacer(Modifier.width(8.dp))
                }

                Text(
                    text = levelInfo.name,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = levelInfo.color,
                )
            }

            if (isParsed) {
                Text(
                    text = parsed.target,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Medium,
                    color = MiuixTheme.colorScheme.onSurface,
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    if (parsed.rule.isNotEmpty()) {
                        Text(
                            text = parsed.rule,
                            fontSize = 11.sp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    }
                    if (parsed.proxy.isNotEmpty()) {
                        Text(
                            text = parsed.proxy,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = levelInfo.color.copy(alpha = 0.8f),
                        )
                    }
                }
            } else {
                Text(
                    text = log.payload,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    color = MiuixTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun LevelBadge(levelInfo: LevelInfo) {
    Box(
        modifier = Modifier
            .size(width = 20.dp, height = 16.dp)
            .squircleBackground(levelInfo.color, 3.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = levelInfo.label,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            color = Color.White,
        )
    }
}

@Composable
private fun ProtocolBadge(protocol: String) {
    Box(
        modifier = Modifier
            .squircleBackground(MiuixTheme.colorScheme.onSurface.copy(alpha = 0.08f), 3.dp)
            .padding(horizontal = 5.dp, vertical = 1.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = protocol,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
    }
}

private data class LevelInfo(val label: String, val name: String, val color: Color)

@Composable
@ReadOnlyComposable
private fun getLevelInfo(type: String): LevelInfo = when (type.lowercase()) {
    "error" -> LevelInfo("E", "Error", StatusColors.danger)
    "warning" -> LevelInfo("W", "Warning", StatusColors.warning)
    "info" -> LevelInfo("I", "Info", StatusColors.info)
    "debug" -> LevelInfo("D", "Debug", StatusColors.healthy)
    else -> LevelInfo("V", "Verbose", StatusColors.neutral)
}
