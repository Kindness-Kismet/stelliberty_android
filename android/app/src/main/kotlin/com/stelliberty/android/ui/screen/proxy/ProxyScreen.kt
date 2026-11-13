package com.stelliberty.android.ui.screen.proxy

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stelliberty.android.R
import com.stelliberty.android.platform.TunMode
import com.stelliberty.android.platform.showToast
import com.stelliberty.android.ui.component.AdaptiveTopAppBar
import com.stelliberty.android.ui.component.CardSegment
import com.stelliberty.android.ui.component.blur.BlurredBar
import com.stelliberty.android.ui.component.blur.rememberBlurBackdrop
import com.stelliberty.android.ui.icon.AppIcons
import com.stelliberty.android.ui.platform.IconLoader
import com.stelliberty.android.ui.theme.StatusColors
import com.stelliberty.android.ui.util.TestTags
import com.stelliberty.android.ui.util.WideContentBox
import com.stelliberty.android.viewmodel.ProxyGroupUi
import com.stelliberty.android.viewmodel.ProxyUiState
import com.stelliberty.android.viewmodel.ProxyViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

@Composable
fun ProxyScreen(
    modifier: Modifier = Modifier,
    bottomPadding: Dp = 0.dp,
    viewModel: ProxyViewModel? = null,
    isRunning: Boolean = false,
    mode: String = "",
    tunStack: String = "",
    tunMode: TunMode = TunMode.Vpn,
    onApplyRouting: (String?, String?) -> Unit = { _, _ -> },
) {
    val uiState = viewModel?.uiState?.collectAsStateWithLifecycle()?.value ?: ProxyUiState()
    val context = LocalContext.current
    LaunchedEffect(uiState.error) {
        if (uiState.error.isNotEmpty()) {
            showToast(context.getString(R.string.proxy_request_failed, uiState.error), long = true)
            viewModel?.clearError()
        }
    }
    val sortOption = viewModel?.sortOption?.collectAsStateWithLifecycle()?.value ?: 0
    val singleColumn = viewModel?.singleColumn?.collectAsStateWithLifecycle()?.value == true
    val groupTabs = viewModel?.groupTabs?.collectAsStateWithLifecycle()?.value != false
    val singleColumnProgress = animateFloatAsState(
        targetValue = if (singleColumn) 1f else 0f,
        animationSpec = tween(300),
        label = "singleColumnProgress",
    )
    val hideUnavailable = viewModel?.hideUnavailableNodes?.collectAsStateWithLifecycle()?.value == true
    val showGlobalGroup = viewModel?.showGlobalGroup?.collectAsStateWithLifecycle()?.value != false
    val scrollBehavior = MiuixScrollBehavior()
    val globalModeActive = uiState.mode == ProxyViewModel.MODE_GLOBAL
    val groups = remember(uiState.groups, globalModeActive, showGlobalGroup) {
        if (showGlobalGroup || globalModeActive) {
            uiState.groups
        } else {
            uiState.groups.filter { it.name != ProxyViewModel.GLOBAL_GROUP }.toPersistentList()
        }
    }

    val modeHintRes = when (mode.lowercase()) {
        ProxyViewModel.MODE_DIRECT -> R.string.proxy_mode_direct_hint
        ProxyViewModel.MODE_GLOBAL -> R.string.proxy_mode_global_hint
        else -> null
    }

    var showSettings by remember { mutableStateOf(false) }
    var iconCacheVersion by remember { mutableIntStateOf(0) }
    val coroutineScope = rememberCoroutineScope()

    val expandedGroups = viewModel?.expandedGroups?.collectAsStateWithLifecycle()?.value ?: persistentSetOf()

    val selectedGroupName = viewModel?.selectedGroupName?.collectAsStateWithLifecycle()?.value.orEmpty()
    var searchVisible by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    val visibleGroups = remember(groups, query) { filterGroups(groups, query) }
    val selectedGroup = visibleGroups.firstOrNull { it.name == selectedGroupName } ?: visibleGroups.firstOrNull()
    val groupNames = remember(visibleGroups) { visibleGroups.map { it.name }.toPersistentList() }
    val tabsState = rememberLazyListState()
    val scrollStates = rememberSaveableStateHolder()
    LaunchedEffect(globalModeActive) {
        if (globalModeActive) viewModel?.updateSelectedGroupName(ProxyViewModel.GLOBAL_GROUP)
    }
    val backdrop = rememberBlurBackdrop()
    val blurActive = backdrop != null
    val barColor = if (blurActive) Color.Transparent else MiuixTheme.colorScheme.surface

    Scaffold(
        modifier = modifier,
        topBar = {
            BlurredBar(backdrop = backdrop, blurActive = blurActive) {
                Column {
                    AdaptiveTopAppBar(
                        title = stringResource(R.string.proxy_title),
                        compact = searchVisible,
                        color = barColor,
                        scrollBehavior = scrollBehavior,
                        navigationIcon = {
                            if (groupTabs && selectedGroup != null) {
                                Row {
                                    if (selectedGroup.isFixed) {
                                        IconButton(
                                            onClick = { viewModel?.unfixProxy(selectedGroup.name) },
                                            modifier = Modifier.testTag(TestTags.Proxy.groupUnfix(selectedGroup.name)),
                                        ) {
                                            Icon(
                                                imageVector = AppIcons.Unpin,
                                                contentDescription = stringResource(R.string.proxy_unfix),
                                                tint = StatusColors.warning,
                                            )
                                        }
                                    }
                                    val isTesting = selectedGroup.name in uiState.testingGroups
                                    IconButton(
                                        onClick = { viewModel?.testGroupDelay(selectedGroup.name) },
                                        enabled = !isTesting,
                                        modifier = Modifier.testTag(TestTags.Proxy.groupTest(selectedGroup.name)),
                                    ) {
                                        if (isTesting) {
                                            CircularProgressIndicator(size = 20.dp, strokeWidth = 2.dp)
                                        } else {
                                            Icon(
                                                imageVector = AppIcons.TestDelay,
                                                contentDescription = stringResource(R.string.proxy_test_group_delay),
                                                modifier = Modifier.size(24.dp),
                                                tint = MiuixTheme.colorScheme.onSurface,
                                            )
                                        }
                                    }
                                }
                            }
                        },
                        actions = {
                            IconButton(
                                onClick = {
                                    searchVisible = !searchVisible
                                    if (!searchVisible) query = ""
                                },
                                modifier = Modifier.testTag(TestTags.Proxy.SEARCH),
                                holdDownState = searchVisible,
                            ) {
                                Icon(
                                    imageVector = AppIcons.Search,
                                    contentDescription = stringResource(R.string.proxy_search_groups),
                                    tint = MiuixTheme.colorScheme.onSurface,
                                )
                            }

                            IconButton(
                                onClick = { showSettings = true },
                                modifier = Modifier.testTag(TestTags.Proxy.SETTINGS),
                                holdDownState = showSettings,
                            ) {
                                Icon(
                                    imageVector = AppIcons.Settings,
                                    contentDescription = stringResource(R.string.proxy_settings_title),
                                    tint = MiuixTheme.colorScheme.onSurface,
                                )
                            }
                        },
                    )
                    ProxySettingsDialog(
                        show = showSettings,
                        onDismissRequest = { showSettings = false },
                        mode = mode,
                        onApplyRouting = onApplyRouting,
                        tunStack = tunStack,
                        tunMode = tunMode,
                        isRunning = isRunning,
                        groupTabs = groupTabs,
                        onGroupTabsChange = { viewModel?.updateGroupTabs(it) },
                        singleColumn = singleColumn,
                        onSingleColumnChange = { viewModel?.updateSingleColumn(it) },
                        hideUnavailable = hideUnavailable,
                        onHideUnavailableChange = { viewModel?.updateHideUnavailableNodes(it) },
                        showGlobalGroup = showGlobalGroup,
                        onShowGlobalGroupChange = { viewModel?.updateShowGlobalGroup(it) },
                        sortOption = sortOption,
                        onSortOptionChange = { viewModel?.updateSortOption(it) },
                        onClearIconCache = {
                            coroutineScope.launch {
                                IconLoader.clear()
                                iconCacheVersion++
                            }
                        },
                    )
                    if (groupTabs && selectedGroup != null) {
                        ProxyGroupTabs(
                            names = groupNames,
                            selectedName = selectedGroup.name,
                            listState = tabsState,
                            onSelect = { viewModel?.updateSelectedGroupName(it) },
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        WideContentBox(
            modifier = if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier,
        ) { sidePadding ->
            AnimatedContent(
                targetState = if (groupTabs) selectedGroup else null,
                contentKey = { it?.let { group -> "group:${group.name}" } ?: "list" },
                transitionSpec = {
                    (fadeIn(tween(200)) togetherWith fadeOut(tween(150))).using(null)
                },
                modifier = Modifier.fillMaxSize(),
                label = "proxyGroupContent",
            ) { contentGroup ->
                // 淡出内容保留自己的组快照，不能读取新选中组的节点行。
                val tabContent = contentGroup != null
                val displayedGroups = if (tabContent) listOf(contentGroup) else visibleGroups
                // 普通测速回填不应使默认排序失效。
                // 只给会渲染节点行的组分行：收起的组算了也没人读，而列表模式下切页会把全部可见组算一遍。
                val rowsByGroup = displayedGroups
                    .filter { tabContent || it.name in expandedGroups }
                    .associate { group ->
                        val rows = key(group.name) {
                            val relevantDelays = group.delays.takeIf { hideUnavailable || sortOption / 2 == 2 }
                            remember(group.all, relevantDelays, query, sortOption, hideUnavailable) {
                                val trimmed = query.trim()
                                val nodes = group.all.filter { name ->
                                    (!hideUnavailable || (group.delays[name] ?: 0) >= 0) &&
                                        (trimmed.isEmpty() || group.name.contains(trimmed, true) || name.contains(trimmed, true))
                                }
                                sortNodes(nodes, group.delays, sortOption).chunked(2)
                                    .map { it.toPersistentList() }.toPersistentList()
                            }
                        }
                        group.name to rows
                    }
                val scrollKey = if (tabContent) "group:${contentGroup.name}" else "list"
                scrollStates.SaveableStateProvider(scrollKey) {
                    val position = remember(scrollKey) { viewModel?.scrollPosition(scrollKey) ?: (0 to 0) }
                    val listState = rememberLazyListState(position.first, position.second)
                    LaunchedEffect(listState, scrollKey) {
                        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
                            .collect { (index, offset) -> viewModel?.updateScrollPosition(scrollKey, index, offset) }
                    }
                    LaunchedEffect(searchVisible) {
                        if (searchVisible) listState.scrollToItem(0)
                    }
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize()
                            .scrollEndHaptic().overScrollVertical()
                            .nestedScroll(scrollBehavior.nestedScrollConnection),
                        contentPadding = PaddingValues(
                            top = innerPadding.calculateTopPadding(),
                            bottom = bottomPadding,
                            start = sidePadding,
                            end = sidePadding,
                        ),
                    ) {
                        if (tabContent && !searchVisible) {
                            item(key = "top_spacer") { Spacer(Modifier.height(12.dp)) }
                        }
                        if (searchVisible) {
                            item(key = "search", contentType = "search") {
                                val focusRequester = remember { FocusRequester() }
                                TextField(
                                    value = query,
                                    onValueChange = { query = it },
                                    modifier = Modifier.fillMaxWidth().padding(12.dp)
                                        .focusRequester(focusRequester)
                                        .testTag(TestTags.Proxy.SEARCH_FIELD),
                                    label = stringResource(R.string.proxy_search_groups),
                                    useLabelAsPlaceholder = true,
                                )
                                LaunchedEffect(Unit) { focusRequester.requestFocus() }
                            }
                        }
                        if (modeHintRes != null) {
                            item(key = "mode_hint", contentType = "hint") {
                                Text(
                                    text = stringResource(modeHintRes),
                                    fontSize = 12.sp,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                                )
                            }
                        }
                        if (visibleGroups.isEmpty()) {
                            item(key = "empty") {
                                Column(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    Text(
                                        text = stringResource(
                                            if (groups.isEmpty()) R.string.proxy_no_groups else R.string.proxy_no_matching_groups,
                                        ),
                                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                    )
                                    if (groups.isEmpty() && !uiState.isProxyRunning) {
                                        Text(
                                            text = stringResource(R.string.proxy_start_first),
                                            modifier = Modifier.padding(top = 6.dp),
                                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                            fontSize = 14.sp,
                                        )
                                    }
                                }
                            }
                        }
                        displayedGroups.forEach { group ->
                            val expanded = tabContent || group.name in expandedGroups
                            if (!tabContent) {
                                item(key = "group:${group.name}", contentType = "group") {
                                    CardSegment(
                                        isFirst = true,
                                        isLast = true,
                                        outerTopPadding = 12.dp,
                                        outerBottomPadding = if (expanded) 10.dp else 0.dp,
                                        modifier = Modifier.animateItem(
                                            fadeInSpec = null, fadeOutSpec = null, placementSpec = tween(180),
                                        ),
                                    ) {
                                        ProxyGroupHeader(
                                            group = group,
                                            isExpanded = expanded,
                                            iconCacheVersion = iconCacheVersion,
                                            isTesting = group.name in uiState.testingGroups,
                                            onTestDelay = { viewModel?.testGroupDelay(group.name) },
                                            onUnfix = { viewModel?.unfixProxy(group.name) },
                                            onToggle = { viewModel?.toggleExpandedGroup(group.name) },
                                        )
                                    }
                                }
                            }
                            if (expanded) {
                                val rows = rowsByGroup.getValue(group.name)
                                if (rows.isEmpty()) {
                                    item(key = "empty:${group.name}", contentType = "hint") {
                                        Text(
                                            text = stringResource(R.string.proxy_no_matching_nodes),
                                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                            fontSize = 13.sp,
                                        )
                                    }
                                }
                                items(count = rows.size, key = { "nodes:${group.name}:$it" }, contentType = { "nodes" }) { index ->
                                    ProxyNodeRow(
                                        row = rows[index],
                                        group = group,
                                        singleColumnProgress = singleColumnProgress,
                                        testingNodes = uiState.testingNodes,
                                        onTestNodeDelay = { viewModel?.testNodeDelay(it) },
                                        onSelect = { viewModel?.selectProxy(group.name, it) },
                                        modifier = Modifier
                                            .animateItem(fadeInSpec = tween(150), fadeOutSpec = null, placementSpec = tween(180))
                                            .padding(horizontal = if (tabContent) 12.dp else 20.dp)
                                            .padding(bottom = 10.dp),
                                    )
                                }
                            }
                        }
                        item(key = "bottom_spacer") { Spacer(Modifier.height(12.dp)) }
                    }
                }
            }
        }
    }
}

private fun filterGroups(
    groups: ImmutableList<ProxyGroupUi>,
    query: String,
): List<ProxyGroupUi> {
    val trimmed = query.trim()
    if (trimmed.isEmpty()) return groups
    return groups.filter { group ->
        group.name.contains(trimmed, ignoreCase = true) ||
            group.all.any { it.contains(trimmed, ignoreCase = true) }
    }
}

private fun sortNodes(
    names: List<String>,
    delays: Map<String, Int>,
    sortOption: Int,
): List<String> {
    val key = sortOption / 2
    val reverse = sortOption % 2 != 0
    return when (key) {
        1 -> {
            val sorted = names.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it })
            if (reverse) sorted.reversed() else sorted
        }

        2 -> {
            val (valid, invalid) = names.partition {
                val d = delays[it]
                d != null && d > 0
            }
            val sortedValid = valid.sortedBy { delays[it] ?: Int.MAX_VALUE }
            val finalValid = if (reverse) sortedValid.reversed() else sortedValid
            finalValid + invalid
        }

        else -> if (reverse) names.reversed() else names
    }
}
