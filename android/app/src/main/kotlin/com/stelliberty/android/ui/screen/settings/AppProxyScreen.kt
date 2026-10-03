package com.stelliberty.android.ui.screen.settings

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stelliberty.android.R
import com.stelliberty.android.domain.model.AppProxyMode
import com.stelliberty.android.platform.showToast
import com.stelliberty.android.ui.component.AdaptiveTopAppBar
import com.stelliberty.android.ui.component.ListPopupDefaults.MenuPositionProvider
import com.stelliberty.android.ui.component.SearchBarFake
import com.stelliberty.android.ui.component.SearchBox
import com.stelliberty.android.ui.component.SearchPager
import com.stelliberty.android.ui.component.SearchResultStatusEffect
import com.stelliberty.android.ui.component.SearchStatus
import com.stelliberty.android.ui.component.blur.BlurredBar
import com.stelliberty.android.ui.component.blur.rememberBlurBackdrop
import com.stelliberty.android.ui.component.rememberSearchBarTopPadding
import com.stelliberty.android.ui.component.rememberSearchScreenStatus
import com.stelliberty.android.ui.icon.AppIcons
import com.stelliberty.android.ui.platform.AppIcon
import com.stelliberty.android.ui.platform.getPlainText
import com.stelliberty.android.ui.platform.setPlainText
import com.stelliberty.android.ui.util.TestTags
import com.stelliberty.android.ui.util.horizontalCutoutPadding
import com.stelliberty.android.viewmodel.AppProxyViewModel
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Checkbox
import top.yukonga.miuix.kmp.basic.DropdownImpl
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.ListPopupColumn
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.PopupPositionProvider
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.preference.RadioButtonLocation
import top.yukonga.miuix.kmp.preference.RadioButtonPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import top.yukonga.miuix.kmp.window.WindowListPopup

@Composable
fun AppProxyScreen(
    viewModel: AppProxyViewModel,
    onBack: () -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scrollBehavior = MiuixScrollBehavior()
    val showPopup = remember { mutableStateOf(false) }
    val density = LocalDensity.current
    val clipboard = LocalClipboard.current
    val clipboardScope = rememberCoroutineScope()

    val appliedMsg = stringResource(R.string.app_proxy_applied)
    val searchStatusState = rememberSearchScreenStatus(stringResource(R.string.app_proxy_search))
    var searchStatus by searchStatusState

    val searchText = searchStatus.searchText

    LaunchedEffect(searchText) {
        viewModel.setSearchQuery(searchText)
    }

    val filteredApps by viewModel.filteredAppsFlow.collectAsStateWithLifecycle()
    SearchResultStatusEffect(searchStatusState, filteredApps.isEmpty())

    val dynamicTopPadding = rememberSearchBarTopPadding(scrollBehavior)

    val backdrop = rememberBlurBackdrop()
    val blurActive = backdrop != null
    val barColor = if (blurActive) Color.Transparent else MiuixTheme.colorScheme.surface

    Scaffold(
        topBar = {
            BlurredBar(backdrop = backdrop, blurActive = blurActive) {
                searchStatus.TopAppBarAnim(
                    backgroundColor = if (blurActive) Color.Transparent else MiuixTheme.colorScheme.surface,
                ) {
                    AdaptiveTopAppBar(
                        title = stringResource(R.string.app_proxy_title),
                        color = barColor,
                        scrollBehavior = scrollBehavior,
                        navigationIcon = {
                            IconButton(onClick = {
                                if (viewModel.applyIfChanged()) {
                                    showToast(appliedMsg)
                                }
                                onBack()
                            }) {
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
                            if (uiState.mode != AppProxyMode.AllowAll) {
                                IconButton(
                                    onClick = { showPopup.value = true },
                                    holdDownState = showPopup.value,
                                ) {
                                    Icon(
                                        imageVector = AppIcons.More,
                                        contentDescription = stringResource(R.string.common_more),
                                        tint = MiuixTheme.colorScheme.onSurface,
                                    )
                                }

                                WindowListPopup(
                                    show = showPopup.value,
                                    popupPositionProvider = MenuPositionProvider,
                                    alignment = PopupPositionProvider.Align.TopEnd,
                                    onDismissRequest = { showPopup.value = false },
                                ) {
                                    ListPopupColumn {
                                        DropdownImpl(
                                            text = stringResource(R.string.app_proxy_select_all),
                                            optionSize = 6,
                                            isSelected = false,
                                            index = 0,
                                            onSelectedIndexChange = {
                                                viewModel.selectAll()
                                                showPopup.value = false
                                            },
                                        )
                                        DropdownImpl(
                                            text = stringResource(R.string.app_proxy_deselect_all),
                                            optionSize = 6,
                                            isSelected = false,
                                            index = 1,
                                            onSelectedIndexChange = {
                                                viewModel.deselectAll()
                                                showPopup.value = false
                                            },
                                        )
                                        DropdownImpl(
                                            text = stringResource(R.string.app_proxy_invert),
                                            optionSize = 6,
                                            isSelected = false,
                                            index = 2,
                                            onSelectedIndexChange = {
                                                viewModel.invertSelection()
                                                showPopup.value = false
                                            },
                                        )
                                        HorizontalDivider(
                                            modifier = Modifier
                                                .padding(horizontal = 20.dp, vertical = 4.dp),
                                            thickness = 1.5.dp,
                                        )
                                        DropdownImpl(
                                            text = if (uiState.showSystemApps) stringResource(R.string.app_proxy_hide_system) else stringResource(
                                                R.string.app_proxy_show_system
                                            ),
                                            optionSize = 6,
                                            isSelected = uiState.showSystemApps,
                                            index = 3,
                                            onSelectedIndexChange = {
                                                viewModel.setShowSystemApps(!uiState.showSystemApps)
                                                showPopup.value = false
                                            },
                                        )
                                        HorizontalDivider(
                                            modifier = Modifier
                                                .padding(horizontal = 20.dp, vertical = 4.dp),
                                            thickness = 1.5.dp,
                                        )
                                        DropdownImpl(
                                            text = stringResource(R.string.app_proxy_import),
                                            optionSize = 6,
                                            isSelected = false,
                                            index = 4,
                                            onSelectedIndexChange = {
                                                clipboardScope.launch {
                                                    val text = clipboard.getPlainText()
                                                    if (!text.isNullOrBlank()) {
                                                        viewModel.importPackages(text)
                                                    }
                                                }
                                                showPopup.value = false
                                            },
                                        )
                                        DropdownImpl(
                                            text = stringResource(R.string.app_proxy_export),
                                            optionSize = 6,
                                            isSelected = false,
                                            index = 5,
                                            onSelectedIndexChange = {
                                                val exported = viewModel.exportPackages()
                                                if (exported.isNotEmpty()) {
                                                    clipboardScope.launch { clipboard.setPlainText(exported) }
                                                }
                                                showPopup.value = false
                                            },
                                        )
                                    }
                                }
                            }
                        },
                        bottomContent = {
                            Box(
                                modifier = Modifier
                                    .alpha(if (searchStatus.isCollapsed()) 1f else 0f)
                                    .onGloballyPositioned { coordinates ->
                                        with(density) {
                                            val newOffsetY = coordinates.positionInWindow().y.toDp()
                                            if (searchStatus.offsetY != newOffsetY) {
                                                searchStatus = searchStatus.copy(offsetY = newOffsetY)
                                            }
                                        }
                                    }
                                    .then(
                                        if (searchStatus.isCollapsed()) {
                                            Modifier.pointerInput(Unit) {
                                                detectTapGestures {
                                                    searchStatus = searchStatus.copy(current = SearchStatus.Status.EXPANDING)
                                                }
                                            }
                                        } else Modifier,
                                    ),
                            ) {
                                SearchBarFake(searchStatus.label, dynamicTopPadding)
                            }
                        },
                    )
                }
            }
        },
        popupHost = {
            searchStatus.SearchPager(
                onSearchStatusChange = { searchStatus = it },
                defaultResult = {},
                searchBarTopPadding = dynamicTopPadding,
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .overScrollVertical()
                        .imePadding(),
                ) {
                    item {
                        Spacer(Modifier.height(6.dp))
                    }

                    if (filteredApps.isEmpty()) {
                        item(key = "search_empty") {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text(
                                    text = stringResource(R.string.app_proxy_no_match),
                                    fontSize = 16.sp,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                )
                            }
                        }
                    } else {
                        items(
                            items = filteredApps,
                            key = { it.packageName },
                            contentType = { "app" },
                        ) { app ->
                            AppItem(
                                appName = app.appName,
                                packageName = app.packageName,
                                isSelected = app.packageName in uiState.selectedPackages,
                                onToggle = { viewModel.toggleApp(app.packageName) },
                            )
                        }
                    }

                    item {
                        Spacer(
                            Modifier
                                .height(24.dp)
                                .navigationBarsPadding()
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        searchStatus.SearchBox {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .horizontalCutoutPadding()
                    .then(if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier)
                    .scrollEndHaptic()
                    .overScrollVertical()
                    .nestedScroll(scrollBehavior.nestedScrollConnection),
                contentPadding = PaddingValues(
                    top = innerPadding.calculateTopPadding(),
                ),
            ) {
                item(key = "mode_card") {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    ) {
                        RadioButtonPreference(
                            title = stringResource(R.string.app_proxy_allow_all),
                            summary = stringResource(R.string.app_proxy_allow_all_summary),
                            selected = uiState.mode == AppProxyMode.AllowAll,
                            onClick = { viewModel.setMode(AppProxyMode.AllowAll) },
                            radioButtonLocation = RadioButtonLocation.End,
                            modifier = Modifier.testTag(TestTags.AppProxy.mode(AppProxyMode.AllowAll.name)),
                        )
                        RadioButtonPreference(
                            title = stringResource(R.string.app_proxy_allow_selected),
                            summary = stringResource(R.string.app_proxy_allow_selected_summary),
                            selected = uiState.mode == AppProxyMode.AllowSelected,
                            onClick = { viewModel.setMode(AppProxyMode.AllowSelected) },
                            radioButtonLocation = RadioButtonLocation.End,
                            modifier = Modifier.testTag(TestTags.AppProxy.mode(AppProxyMode.AllowSelected.name)),
                        )
                        RadioButtonPreference(
                            title = stringResource(R.string.app_proxy_deny_selected),
                            summary = stringResource(R.string.app_proxy_deny_selected_summary),
                            selected = uiState.mode == AppProxyMode.DenySelected,
                            onClick = { viewModel.setMode(AppProxyMode.DenySelected) },
                            radioButtonLocation = RadioButtonLocation.End,
                            modifier = Modifier.testTag(TestTags.AppProxy.mode(AppProxyMode.DenySelected.name)),
                        )
                    }
                }

                if (uiState.mode != AppProxyMode.AllowAll) {
                    if (uiState.isLoading) {
                        item(key = "loading") {
                            Box(
                                modifier = Modifier
                                    .fillParentMaxSize(),
                                contentAlignment = Alignment.Center,
                            ) {
                                InfiniteProgressIndicator()
                            }
                        }
                    } else {
                        item(key = "apps_title") {
                            SmallTitle(
                                text = stringResource(
                                    R.string.app_proxy_app_list,
                                    filteredApps.count { it.packageName in uiState.selectedPackages },
                                    filteredApps.size,
                                ),
                                modifier = Modifier.testTag(TestTags.AppProxy.APP_LIST),
                            )
                        }
                        items(
                            items = filteredApps,
                            key = { it.packageName },
                            contentType = { "app" },
                        ) { app ->
                            AppItem(
                                appName = app.appName,
                                packageName = app.packageName,
                                isSelected = app.packageName in uiState.selectedPackages,
                                onToggle = { viewModel.toggleApp(app.packageName) },
                            )
                        }
                    }
                }

                item {
                    Spacer(
                        Modifier
                            .height(24.dp)
                            .navigationBarsPadding()
                    )
                }
            }
        }
    }
}

@Composable
private fun AppItem(
    appName: String,
    packageName: String,
    isSelected: Boolean,
    onToggle: () -> Unit,
) {
    Card(
        modifier = Modifier
            .padding(horizontal = 12.dp)
            .padding(bottom = 12.dp)
            .testTag(TestTags.AppProxy.app(packageName)),
    ) {
        BasicComponent(
            title = appName,
            summary = packageName,
            startAction = {
                AppIcon(
                    packageName = packageName,
                    modifier = Modifier.padding(end = 6.dp),
                    size = 40.dp,
                )
            },
            endActions = {
                Checkbox(
                    state = if (isSelected) ToggleableState.On else ToggleableState.Off,
                    onClick = onToggle,
                )
            },
            onClick = onToggle,
        )
    }
}
