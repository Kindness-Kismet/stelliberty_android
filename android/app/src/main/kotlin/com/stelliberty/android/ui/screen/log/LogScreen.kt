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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stelliberty.android.BuildConfig
import com.stelliberty.android.R
import com.stelliberty.android.domain.model.LogLevel
import com.stelliberty.android.domain.model.LogMessage
import com.stelliberty.android.platform.FilePicker
import com.stelliberty.android.platform.showToast
import com.stelliberty.android.ui.component.AdaptiveTopAppBar
import com.stelliberty.android.ui.component.ListPopupDefaults.MenuPositionProvider
import com.stelliberty.android.ui.component.blur.BlurredBar
import com.stelliberty.android.ui.component.blur.rememberBlurBackdrop
import com.stelliberty.android.ui.icon.AppIcons
import com.stelliberty.android.ui.theme.StatusColors
import com.stelliberty.android.ui.util.TestTags
import com.stelliberty.android.ui.util.horizontalCutoutPadding
import com.stelliberty.android.util.describe
import com.stelliberty.android.viewmodel.LogViewModel
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.DropdownImpl
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.ListPopupColumn
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.PopupPositionProvider
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.squircle.squircleBackground
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import top.yukonga.miuix.kmp.window.WindowListPopup

@Composable
fun LogScreen(
    viewModel: LogViewModel,
    filePicker: FilePicker? = null,
    onBack: () -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val logs by viewModel.logs.collectAsStateWithLifecycle()
    val scrollBehavior = MiuixScrollBehavior()
    val listState = rememberLazyListState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showLevelPopup by remember { mutableStateOf(false) }

    LifecycleStartEffect(viewModel) {
        viewModel.startObserving()
        onStopOrDispose { viewModel.stopObserving() }
    }

    var autoScrollEnabled by remember { mutableStateOf(true) }
    val autoScrollConnection = remember(listState) {
        object : NestedScrollConnection {
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                // 追加日志引起的布局变化不能关闭跟随，跟随状态只随滚动操作改变。
                if (consumed.y != 0f) autoScrollEnabled = !listState.canScrollForward
                return Offset.Zero
            }
        }
    }

    // 缓冲写满后长度不变，用单调递增的编号驱动自动滚动。
    val lastLogId = logs.lastOrNull()?.id
    val isScrolling = listState.isScrollInProgress
    LaunchedEffect(lastLogId, uiState.minimumLevel, autoScrollEnabled, isScrolling) {
        if (lastLogId == null) {
            autoScrollEnabled = true
        } else if (autoScrollEnabled && !isScrolling) {
            listState.requestScrollToItem(logs.size + 1)
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
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier.testTag(TestTags.Nav.BACK),
                        ) {
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
                        Box {
                            val selectedLevel = getLevelInfo(uiState.minimumLevel)
                            val filterDescription = stringResource(R.string.log_level_filter, selectedLevel.name)
                            IconButton(
                                onClick = { showLevelPopup = true },
                                holdDownState = showLevelPopup,
                                modifier = Modifier
                                    .testTag(TestTags.Log.LEVEL_FILTER)
                                    .semantics { contentDescription = filterDescription },
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = selectedLevel.name,
                                        fontSize = 14.sp,
                                        color = MiuixTheme.colorScheme.onSurface,
                                    )
                                    Icon(
                                        imageVector = AppIcons.MoveDown,
                                        contentDescription = null,
                                        tint = MiuixTheme.colorScheme.onSurface,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }
                            WindowListPopup(
                                show = showLevelPopup,
                                popupPositionProvider = MenuPositionProvider,
                                alignment = PopupPositionProvider.Align.TopEnd,
                                onDismissRequest = { showLevelPopup = false },
                            ) {
                                ListPopupColumn {
                                    LogLevel.entries.forEach { level ->
                                        Box(
                                            modifier = Modifier
                                                .semantics { testTagsAsResourceId = BuildConfig.DEBUG }
                                                .testTag(TestTags.Log.level(level.name)),
                                        ) {
                                            DropdownImpl(
                                                text = stringResource(R.string.log_level_and_above, getLevelInfo(level).name),
                                                optionSize = LogLevel.entries.size,
                                                isSelected = uiState.minimumLevel == level,
                                                index = level.ordinal,
                                                onSelectedIndexChange = {
                                                    viewModel.setMinimumLevel(level)
                                                    autoScrollEnabled = true
                                                    showLevelPopup = false
                                                },
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        IconButton(
                            enabled = filePicker != null && logs.isNotEmpty(),
                            onClick = {
                                val export = viewModel.exportLogs()
                                if (filePicker != null && export != null) {
                                    filePicker.createDocument(export.fileName, "text/plain") { uri ->
                                        if (uri != null) scope.launch {
                                            filePicker.writeTextDocument(uri, export.content)
                                                .onSuccess { showToast(context.getString(R.string.log_export_done)) }
                                                .onFailure {
                                                    showToast(context.getString(R.string.error_save_failed, it.describe()), long = true)
                                                }
                                        }
                                    }
                                }
                            },
                            modifier = Modifier.testTag(TestTags.Log.EXPORT),
                        ) {
                            Icon(
                                imageVector = AppIcons.Backup,
                                contentDescription = stringResource(R.string.log_export),
                                tint = MiuixTheme.colorScheme.onSurface,
                            )
                        }
                        IconButton(
                            onClick = {
                                autoScrollEnabled = true
                                viewModel.clearLogs()
                            },
                            modifier = Modifier.testTag(TestTags.Log.CLEAR),
                        ) {
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
                .testTag(TestTags.Log.LIST)
                .horizontalCutoutPadding()
                .then(if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier)
                .scrollEndHaptic()
                .overScrollVertical()
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .nestedScroll(autoScrollConnection),
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
                            modifier = Modifier.testTag(TestTags.Log.STATUS),
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
    val target: String = "",
    val rule: String = "",
    val proxy: String = "",
)

private fun parsePayload(payload: String): ParsedLog {
    val raw = payload.trim()

    val protocolMatch = Regex("^\\[(\\w+)]").find(raw)
    val protocol = protocolMatch?.groupValues?.get(1) ?: ""
    val rest = if (protocolMatch != null) raw.substring(protocolMatch.range.last + 1).trim() else raw

    val arrowIdx = rest.indexOf("-->")
    if (arrowIdx < 0) return ParsedLog()

    val afterArrow = rest.substring(arrowIdx + 3).trim()

    val matchIdx = afterArrow.indexOf(" match ")
    if (matchIdx < 0) {
        return ParsedLog(protocol = protocol, target = afterArrow)
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

    return ParsedLog(protocol = protocol, target = target, rule = rule, proxy = proxy)
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
private fun getLevelInfo(type: LogLevel): LevelInfo = when (type) {
    LogLevel.Error -> LevelInfo("E", stringResource(R.string.log_level_error), StatusColors.danger)
    LogLevel.Warning -> LevelInfo("W", stringResource(R.string.log_level_warning), StatusColors.warning)
    LogLevel.Info -> LevelInfo("I", stringResource(R.string.log_level_info), StatusColors.info)
    LogLevel.Debug -> LevelInfo("D", stringResource(R.string.log_level_debug), StatusColors.healthy)
}
