package com.stelliberty.android.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.rememberLifecycleOwner
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import com.stelliberty.android.DeepLinkImportRequest
import com.stelliberty.android.R
import com.stelliberty.android.platform.BootStartManager
import com.stelliberty.android.platform.FilePicker
import com.stelliberty.android.platform.PlatformStorage
import com.stelliberty.android.platform.StorageKeys
import com.stelliberty.android.platform.WifiPolicyController
import com.stelliberty.android.ui.component.blur.BlurredBar
import com.stelliberty.android.ui.component.blur.rememberBlurBackdrop
import com.stelliberty.android.ui.component.liquid.IosLiquidGlassNavigationBar
import com.stelliberty.android.ui.icon.AppIcons
import com.stelliberty.android.ui.screen.connection.ConnectionScreen
import com.stelliberty.android.ui.screen.dns.DnsQueryScreen
import com.stelliberty.android.ui.screen.home.HomeScreen
import com.stelliberty.android.ui.screen.log.LogScreen
import com.stelliberty.android.ui.screen.provider.ProviderScreen
import com.stelliberty.android.ui.screen.proxy.ProxyScreen
import com.stelliberty.android.ui.screen.settings.AboutScreen
import com.stelliberty.android.ui.screen.settings.AppProxyScreen
import com.stelliberty.android.ui.screen.settings.BackupRestoreScreen
import com.stelliberty.android.ui.screen.settings.ExternalControlScreen
import com.stelliberty.android.ui.screen.settings.FileManagerEditorScreen
import com.stelliberty.android.ui.screen.settings.FileManagerScreen
import com.stelliberty.android.ui.screen.settings.MetaSettingsScreen
import com.stelliberty.android.ui.screen.settings.NetworkSettingsScreen
import com.stelliberty.android.ui.screen.settings.RootSettingsScreen
import com.stelliberty.android.ui.screen.settings.SettingsScreen
import com.stelliberty.android.ui.screen.settings.ThemeSettingsScreen
import com.stelliberty.android.ui.screen.settings.VpnSettingsScreen
import com.stelliberty.android.ui.screen.settings.WifiPolicyScreen
import com.stelliberty.android.ui.screen.subscription.SubscriptionAddScreen
import com.stelliberty.android.ui.screen.subscription.SubscriptionAddUrlScreen
import com.stelliberty.android.ui.screen.subscription.RuleOverrideSummary
import com.stelliberty.android.ui.screen.subscription.SubscriptionEditScreen
import com.stelliberty.android.ui.screen.chainproxy.ChainProxyEditScreen
import com.stelliberty.android.ui.screen.chainproxy.ChainProxyListScreen
import com.stelliberty.android.ui.screen.ruleoverride.RuleOverrideEditScreen
import com.stelliberty.android.ui.screen.ruleoverride.RuleOverrideScreen
import com.stelliberty.android.ui.screen.overrides.OverrideEditScreen
import com.stelliberty.android.ui.screen.overrides.OverrideFileEditorScreen
import com.stelliberty.android.ui.screen.overrides.OverrideListScreen
import com.stelliberty.android.ui.screen.overrides.SubscriptionOverridesScreen
import com.stelliberty.android.ui.screen.subscription.SubscriptionScreen
import com.stelliberty.android.ui.screen.subscription.SubscriptionUpdateProgressDialog
import com.stelliberty.android.ui.theme.BottomBarMode
import com.stelliberty.android.ui.theme.FloatingBottomBarStyle
import com.stelliberty.android.ui.theme.LocalAppDarkMode
import com.stelliberty.android.ui.theme.ThemeConfig
import com.stelliberty.android.ui.theme.TopBarBlurStyle
import com.stelliberty.android.ui.util.TestTags
import com.stelliberty.android.ui.util.rememberIsWideScreen
import com.stelliberty.android.viewmodel.AppProxyViewModel
import com.stelliberty.android.viewmodel.BackupViewModel
import com.stelliberty.android.viewmodel.ConnectionViewModel
import com.stelliberty.android.viewmodel.DnsQueryViewModel
import com.stelliberty.android.viewmodel.ExternalControlViewModel
import com.stelliberty.android.viewmodel.HomeUiState
import com.stelliberty.android.viewmodel.HomeViewModel
import com.stelliberty.android.viewmodel.LogViewModel
import com.stelliberty.android.viewmodel.MetaSettingsViewModel
import com.stelliberty.android.viewmodel.NetworkSettingsViewModel
import com.stelliberty.android.viewmodel.ChainProxyViewModel
import com.stelliberty.android.viewmodel.RuleOverrideViewModel
import com.stelliberty.android.viewmodel.OverrideProfileViewModel
import com.stelliberty.android.viewmodel.ProviderViewModel
import com.stelliberty.android.viewmodel.ProxyViewModel
import com.stelliberty.android.viewmodel.SubscriptionViewModel
import java.util.UUID
import kotlin.math.abs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import top.yukonga.miuix.kmp.basic.FloatingNavigationBar
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarDisplayMode
import top.yukonga.miuix.kmp.basic.NavigationBarItem
import top.yukonga.miuix.kmp.basic.NavigationItem
import top.yukonga.miuix.kmp.basic.NavigationRail
import top.yukonga.miuix.kmp.basic.NavigationRailItem
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.rememberNavigationRailState
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurDefaults
import top.yukonga.miuix.kmp.blur.highlight.Highlight
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.nav.core.NavDisplay
import top.yukonga.miuix.kmp.nav.core.NavDisplayEffects
import top.yukonga.miuix.kmp.nav.core.NavKey
import top.yukonga.miuix.kmp.nav.core.rememberNavSystemCornerRadius
import top.yukonga.miuix.kmp.nav.transition.NavSwipeDirection
import top.yukonga.miuix.kmp.theme.MiuixTheme

private val NavBackStackSaver = Saver<SnapshotStateList<NavKey>, List<String>>(
    save = { backStack ->
        backStack.mapNotNull { key ->
            (key as? Route)?.let { Json.encodeToString<Route>(it) }
        }.ifEmpty {
            listOf(Json.encodeToString<Route>(Route.Main))
        }
    },
    restore = { savedRoutes ->
        val restoredRoutes = savedRoutes.mapNotNull { value ->
            runCatching { Json.decodeFromString<Route>(value) }.getOrNull()
        }
        mutableStateListOf<NavKey>().apply {
            if (restoredRoutes.isEmpty()) {
                add(Route.Main)
            } else {
                if (restoredRoutes.first() !is Route.Main) add(Route.Main)
                addAll(restoredRoutes)
            }
        }
    },
)

val LocalMainPagerState = staticCompositionLocalOf<MainPagerState> {
    error("LocalMainPagerState not provided")
}

@Composable
fun AppNavigation(
    themeConfig: ThemeConfig = ThemeConfig(),
    onThemeConfigChange: (ThemeConfig) -> Unit = {},
    homeViewModel: HomeViewModel? = null,
    subscriptionViewModel: SubscriptionViewModel? = null,
    proxyViewModel: ProxyViewModel? = null,
    logViewModel: LogViewModel? = null,
    providerViewModel: ProviderViewModel? = null,
    connectionViewModel: ConnectionViewModel? = null,
    dnsQueryViewModel: DnsQueryViewModel? = null,
    networkSettingsViewModel: NetworkSettingsViewModel? = null,
    metaSettingsViewModel: MetaSettingsViewModel? = null,
    externalControlViewModel: ExternalControlViewModel? = null,
    appProxyViewModel: AppProxyViewModel? = null,
    filePicker: FilePicker? = null,
    storage: PlatformStorage? = null,
    bootStartManager: BootStartManager? = null,
    mihomoVersion: String = "",
    onScanQR: ((callback: (String?) -> Unit) -> Unit)? = null,
    wifiPolicyController: WifiPolicyController? = null,
    onRequestWifiPermission: (((Boolean) -> Unit) -> Unit)? = null,
    onPredictiveBackChange: ((Boolean) -> Unit)? = null,
    onHideTaskCardChange: ((Boolean) -> Unit)? = null,
    hasRootPermission: Boolean = false,
    deepLinkImport: DeepLinkImportRequest? = null,
    onDeepLinkImportConsumed: () -> Unit = {},
    backupViewModel: BackupViewModel? = null,
    overrideViewModel: OverrideProfileViewModel? = null,
    chainProxyViewModel: ChainProxyViewModel? = null,
    ruleOverrideViewModel: RuleOverrideViewModel? = null,
    onRestartApp: () -> Unit = {},
) {
    val backStack = rememberSaveable(saver = NavBackStackSaver) { mutableStateListOf(Route.Main) }
    val navigator = remember { Navigator(backStack) }
    val pagerState = rememberPagerState(pageCount = { 4 })
    val mainPagerState = rememberMainPagerState(pagerState)

    LaunchedEffect(deepLinkImport) {
        if (deepLinkImport != null) {
            navigator.popUntil { key -> key is Route.Main }
            mainPagerState.animateToPage(2)
            navigator.push(
                Route.SubscriptionAddUrl(
                    initialUrl = deepLinkImport.url,
                    initialName = deepLinkImport.name,
                    initialIntervalMinutes = deepLinkImport.intervalMinutes,
                )
            )
            onDeepLinkImportConsumed()
        }
    }

    LaunchedEffect(Unit) {
        DebugNavBridge.requests.collect { pageId ->
            navigator.popUntil { key -> key is Route.Main }
            DebugNavBridge.mainTabIndexOf(pageId)?.let { index ->
                mainPagerState.animateToPage(index)
                return@collect
            }
            DebugNavBridge.routeOf(pageId)?.let { route ->
                if (pageId.startsWith("settings.")) mainPagerState.animateToPage(3)
                navigator.push(route)
            }
        }
    }

    LaunchedEffect(mainPagerState.pagerState.currentPage) {
        mainPagerState.syncPage()
    }
    // 滑到半页 currentPage 就会变成 1，那时翻页动画还在跑；等 settled 再拉列表，避免几千节点的刷新和动画抢主线程。
    // 代理已连上时 setRepository 会拉过一次；列表非空再拉只会用一份内容相等的新对象触发重组。
    LaunchedEffect(mainPagerState.pagerState.settledPage) {
        if (mainPagerState.pagerState.settledPage != 1) return@LaunchedEffect
        val vm = proxyViewModel ?: return@LaunchedEffect
        if (vm.uiState.value.groups.isEmpty()) vm.loadProxies()
    }

    MainScreenBackHandler(mainPagerState, navigator)

    var swipeDismissEnabled by remember {
        mutableStateOf(storage?.getString(StorageKeys.SWIPE_DISMISS, "true") == "true")
    }

    val swipeBackDirection = if (LocalLayoutDirection.current == LayoutDirection.Rtl) {
        NavSwipeDirection.RightToLeft
    } else {
        NavSwipeDirection.LeftToRight
    }
    val swipeDismiss = if (swipeDismissEnabled) swipeBackDirection else null

    CompositionLocalProvider(
        LocalNavigator provides navigator,
        LocalMainPagerState provides mainPagerState,
    ) {
        NavDisplay(
            backStack = backStack,
            onBack = { navigator.pop() },
            effects = NavDisplayEffects(
                cornerClipRadius = rememberNavSystemCornerRadius(),
            ),
        ) {
            entry<Route.Main>(swipeDismiss = swipeDismiss) {
                MainPage(
                    homeViewModel,
                    proxyViewModel,
                    subscriptionViewModel,
                    navigator,
                    mainPagerState,
                    bootStartManager,
                    themeConfig,
                    storage,
                    onHideTaskCardChange,
                    hasRootPermission,
                )
            }
            entry<Route.SubscriptionAdd>(swipeDismiss = swipeDismiss) {
                SubscriptionAddScreen(
                    viewModel = subscriptionViewModel,
                    onBack = { navigator.pop() },
                    onPickFile = {
                        filePicker?.pickYamlFile { result ->
                            if (result != null && subscriptionViewModel != null) {
                                subscriptionViewModel.addFromFile(
                                    fileName = result.fileName,
                                    content = result.content,
                                    onComplete = {
                                        navigator.popUntil { key -> key is Route.Main }
                                    },
                                )
                            }
                        }
                    },
                    onNavigateUrl = { navigator.push(Route.SubscriptionAddUrl()) },
                    onScanQR = if (onScanQR != null) {
                        {
                            onScanQR { url ->
                                if (url != null) {
                                    navigator.push(Route.SubscriptionAddUrl(initialUrl = url))
                                }
                            }
                        }
                    } else null,
                )
            }
            entry<Route.SubscriptionAddUrl>(swipeDismiss = swipeDismiss) { route ->
                subscriptionViewModel?.let {
                    SubscriptionAddUrlScreen(
                        viewModel = it,
                        initialUrl = route.initialUrl,
                        initialName = route.initialName,
                        initialIntervalMinutes = route.initialIntervalMinutes,
                        onBack = { navigator.pop() },
                        onSaved = { navigator.popUntil { key -> key is Route.Main } },
                    )
                }
            }
            entry<Route.SubscriptionEdit>(swipeDismiss = swipeDismiss) { route ->
                subscriptionViewModel?.let {
                    SubscriptionEditScreen(
                        uuid = route.uuid,
                        viewModel = it,
                        onBack = { navigator.pop() },
                        onSaved = { navigator.pop() },
                        onNavigateOverrides = { navigator.push(Route.SubscriptionOverrides(route.uuid)) },
                        onNavigateChainProxies = {
                            navigator.push(Route.SubscriptionChainProxies(route.uuid, UUID.randomUUID().toString()))
                        },
                        ruleOverrideSummary = ruleOverrideViewModel?.let { vm ->
                            val set by remember(route.uuid) { vm.observe(route.uuid) }
                                .collectAsStateWithLifecycle(initialValue = null)
                            set?.let { RuleOverrideSummary(it.customRules.size, it.disabledBuiltinRuleKeys.size) }
                        },
                        onNavigateRuleOverrides = {
                            navigator.push(Route.SubscriptionRuleOverrides(route.uuid, UUID.randomUUID().toString()))
                        },
                    )
                }
            }
            entry<Route.SubscriptionChainProxies>(swipeDismiss = swipeDismiss) { route ->
                chainProxyViewModel?.let {
                    ChainProxyListScreen(
                        subscriptionId = route.uuid,
                        session = route.session,
                        viewModel = it,
                        onBack = { navigator.pop() },
                        onAdd = { navigator.push(Route.ChainProxyEdit(route.uuid)) },
                        onEdit = { id -> navigator.push(Route.ChainProxyEdit(route.uuid, id)) },
                    )
                }
            }
            entry<Route.ChainProxyEdit>(swipeDismiss = swipeDismiss) { route ->
                chainProxyViewModel?.let {
                    ChainProxyEditScreen(
                        subscriptionId = route.uuid,
                        chainId = route.chainId,
                        viewModel = it,
                        onBack = { navigator.pop() },
                    )
                }
            }
            entry<Route.SubscriptionRuleOverrides>(swipeDismiss = swipeDismiss) { route ->
                ruleOverrideViewModel?.let {
                    RuleOverrideScreen(
                        subscriptionId = route.uuid,
                        session = route.session,
                        viewModel = it,
                        onBack = { navigator.pop() },
                        onAdd = { navigator.push(Route.RuleOverrideEdit(route.uuid)) },
                        onEdit = { id -> navigator.push(Route.RuleOverrideEdit(route.uuid, id)) },
                    )
                }
            }
            entry<Route.RuleOverrideEdit>(swipeDismiss = swipeDismiss) { route ->
                ruleOverrideViewModel?.let {
                    RuleOverrideEditScreen(
                        subscriptionId = route.uuid,
                        ruleId = route.ruleId,
                        viewModel = it,
                        onBack = { navigator.pop() },
                    )
                }
            }
            entry<Route.SubscriptionOverrides>(swipeDismiss = swipeDismiss) { route ->
                if (subscriptionViewModel != null && overrideViewModel != null) {
                    SubscriptionOverridesScreen(
                        subscriptionId = route.uuid,
                        subscriptionViewModel = subscriptionViewModel,
                        overrideViewModel = overrideViewModel,
                        onBack = { navigator.pop() },
                    )
                }
            }
            entry<Route.OverrideList>(swipeDismiss = swipeDismiss) {
                overrideViewModel?.let {
                    OverrideListScreen(
                        viewModel = it,
                        onBack = { navigator.pop() },
                        onAdd = { navigator.push(Route.OverrideEdit()) },
                        onEdit = { id -> navigator.push(Route.OverrideEdit(id)) },
                        onEditFile = { id -> navigator.push(Route.OverrideFileEditor(id)) },
                    )
                }
            }
            entry<Route.OverrideEdit>(swipeDismiss = swipeDismiss) { route ->
                overrideViewModel?.let {
                    OverrideEditScreen(
                        overrideId = route.id,
                        viewModel = it,
                        onBack = { navigator.pop() },
                        onSaved = { navigator.pop() },
                        onPickFile = { callback -> filePicker?.pickYamlFile(callback) },
                    )
                }
            }
            entry<Route.OverrideFileEditor>(swipeDismiss = swipeDismiss) { route ->
                overrideViewModel?.let {
                    OverrideFileEditorScreen(
                        overrideId = route.id,
                        viewModel = it,
                        onBack = { navigator.pop() },
                    )
                }
            }
            entry<Route.Log>(swipeDismiss = swipeDismiss) {
                logViewModel?.let {
                    LogScreen(
                        viewModel = it,
                        filePicker = filePicker,
                        onBack = { navigator.pop() },
                    )
                }
            }
            entry<Route.Provider>(swipeDismiss = swipeDismiss) {
                providerViewModel?.let {
                    ProviderScreen(
                        viewModel = it,
                        onBack = { navigator.pop() },
                    )
                }
            }
            entry<Route.Connection>(swipeDismiss = swipeDismiss) {
                connectionViewModel?.let {
                    ConnectionScreen(
                        viewModel = it,
                        onBack = { navigator.pop() },
                    )
                }
            }
            entry<Route.DnsQuery>(swipeDismiss = swipeDismiss) {
                dnsQueryViewModel?.let {
                    DnsQueryScreen(
                        viewModel = it,
                        onBack = { navigator.pop() },
                    )
                }
            }
            entry<Route.VpnSettings>(swipeDismiss = swipeDismiss) {
                storage?.let {
                    VpnSettingsScreen(
                        storage = it,
                        isSystemProxySupported = true,
                        onBack = { navigator.pop() },
                    )
                }
            }
            entry<Route.RootSettings>(swipeDismiss = swipeDismiss) {
                storage?.let {
                    val homeState = homeViewModel?.uiState?.collectAsStateWithLifecycle()?.value
                    RootSettingsScreen(
                        storage = it,
                        isProxyRunning = homeState?.isRunning == true || homeState?.isStarting == true,
                        onBack = { navigator.pop() },
                    )
                }
            }
            entry<Route.NetworkSettings>(swipeDismiss = swipeDismiss) {
                networkSettingsViewModel?.let {
                    NetworkSettingsScreen(
                        viewModel = it,
                        onBack = { navigator.pop() },
                    )
                }
            }
            entry<Route.MetaSettings>(swipeDismiss = swipeDismiss) {
                metaSettingsViewModel?.let {
                    MetaSettingsScreen(
                        viewModel = it,
                        onBack = { navigator.pop() },
                    )
                }
            }
            entry<Route.AppProxy>(swipeDismiss = swipeDismiss) {
                appProxyViewModel?.let {
                    AppProxyScreen(
                        viewModel = it,
                        onBack = { navigator.pop() },
                    )
                }
            }
            entry<Route.WifiPolicy>(swipeDismiss = swipeDismiss) {
                storage?.let {
                    WifiPolicyScreen(
                        storage = it,
                        controller = wifiPolicyController,
                        onRequestPermission = onRequestWifiPermission,
                        onBack = { navigator.pop() },
                    )
                }
            }
            entry<Route.ThemeSettings>(swipeDismiss = swipeDismiss) {
                storage?.let {
                    ThemeSettingsScreen(
                        storage = it,
                        themeConfig = themeConfig,
                        onThemeConfigChange = onThemeConfigChange,
                        onPredictiveBackChange = onPredictiveBackChange,
                        swipeDismissEnabled = swipeDismissEnabled,
                        onSwipeDismissChange = { enabled ->
                            swipeDismissEnabled = enabled
                            it.putString(StorageKeys.SWIPE_DISMISS, enabled.toString())
                        },
                        onBack = { navigator.pop() },
                    )
                }
            }
            entry<Route.ExternalControl>(swipeDismiss = swipeDismiss) {
                externalControlViewModel?.let {
                    ExternalControlScreen(
                        viewModel = it,
                        onBack = { navigator.pop() },
                    )
                }
            }
            entry<Route.BackupRestore>(swipeDismiss = swipeDismiss) {
                if (backupViewModel != null && storage != null) {
                    BackupRestoreScreen(
                        viewModel = backupViewModel,
                        storage = storage,
                        filePicker = filePicker,
                        onRestartApp = onRestartApp,
                        onBack = { navigator.pop() },
                    )
                }
            }
            entry<Route.FileManager>(swipeDismiss = swipeDismiss) {
                FileManagerScreen(
                    subscriptionViewModel = subscriptionViewModel,
                    onBack = { navigator.pop() },
                    onOpenFile = { uuid, relPath ->
                        navigator.push(Route.FileManagerEditor(uuid, relPath))
                    },
                )
            }
            entry<Route.FileManagerEditor>(swipeDismiss = swipeDismiss) { route ->
                FileManagerEditorScreen(
                    uuid = route.uuid,
                    relativePath = route.relativePath,
                    subscriptionViewModel = subscriptionViewModel,
                    onBack = { navigator.pop() },
                )
            }
            entry<Route.About>(swipeDismiss = swipeDismiss) {
                val uriHandler = LocalUriHandler.current
                AboutScreen(
                    onBack = { navigator.pop() },
                    mihomoVersion = mihomoVersion,
                    onOpenUrl = { url -> uriHandler.openUri(url) },
                )
            }
        }
    }

    subscriptionViewModel?.let { SubscriptionUpdateProgressDialog(it) }
}

@Composable
private fun MainPage(
    homeViewModel: HomeViewModel?,
    proxyViewModel: ProxyViewModel?,
    subscriptionViewModel: SubscriptionViewModel?,
    navigator: Navigator,
    mainPagerState: MainPagerState,
    bootStartManager: BootStartManager? = null,
    themeConfig: ThemeConfig = ThemeConfig(),
    storage: PlatformStorage? = null,
    onHideTaskCardChange: ((Boolean) -> Unit)? = null,
    hasRootPermission: Boolean = false,
) {
    val homeUiState = homeViewModel?.uiState?.collectAsStateWithLifecycle()?.value ?: HomeUiState()
    val selectedPage = mainPagerState.selectedPage

    val pagerContent: @Composable (Modifier, Dp) -> Unit = { pagerModifier, bottomPadding ->
        HorizontalPager(
            modifier = pagerModifier,
            state = mainPagerState.pagerState,
            verticalAlignment = Alignment.Top,
            // 四个主页面保留组合，避免翻页的动画帧重新创建节点与毛玻璃。
            beyondViewportPageCount = mainPagerState.pagerState.pageCount - 1,
        ) { page ->
            val pageVisible by remember(mainPagerState, page) {
                derivedStateOf {
                    mainPagerState.pagerState.layoutInfo.visiblePagesInfo.any { it.index == page }
                }
            }
            val lifecycleOwner = rememberLifecycleOwner(
                maxLifecycle = if (pageVisible) Lifecycle.State.RESUMED else Lifecycle.State.CREATED,
            )
            CompositionLocalProvider(LocalLifecycleOwner provides lifecycleOwner) {
                when (page) {
                    0 -> HomeScreen(
                        bottomPadding = bottomPadding,
                        uiState = homeUiState,
                        viewModel = homeViewModel,
                        onRestart = { homeViewModel?.restartProxy() },
                        onStop = { homeViewModel?.stopProxy() },
                        onReload = { homeViewModel?.reloadConfig() },
                        onTestLatency = { homeViewModel?.testLatency() },
                        onNavigateLog = { navigator.push(Route.Log) },
                        onNavigateProvider = { navigator.push(Route.Provider) },
                        onNavigateConnection = { navigator.push(Route.Connection) },
                        onNavigateDnsQuery = { navigator.push(Route.DnsQuery) },
                        onStartProxy = { homeViewModel?.startProxy() },
                    )

                    1 -> ProxyScreen(
                        bottomPadding = bottomPadding,
                        viewModel = proxyViewModel,
                        isRunning = homeUiState.isRunning,
                        mode = homeUiState.mode,
                        tunStack = homeUiState.tunStack,
                        tunMode = homeUiState.tunMode,
                        onSwitchMode = { homeViewModel?.switchMode(it) },
                        onSwitchTunStack = { homeViewModel?.switchTunStack(it) },
                    )
                    2 -> subscriptionViewModel?.let {
                        SubscriptionScreen(
                            viewModel = it,
                            bottomPadding = bottomPadding,
                            onNavigateAdd = { navigator.push(Route.SubscriptionAdd) },
                            onNavigateEdit = { uuid -> navigator.push(Route.SubscriptionEdit(uuid)) },
                            onActiveChanged = { homeViewModel?.onActiveSubscriptionChanged() },
                        )
                    }

                    3 -> SettingsScreen(
                        bottomPadding = bottomPadding,
                        onNavigateVpnSettings = { navigator.push(Route.VpnSettings) },
                        onNavigateRootSettings = { navigator.push(Route.RootSettings) },
                        onNavigateNetworkSettings = { navigator.push(Route.NetworkSettings) },
                        onNavigateMetaSettings = { navigator.push(Route.MetaSettings) },
                        onNavigateExternalControl = { navigator.push(Route.ExternalControl) },
                        onNavigateAppProxy = { navigator.push(Route.AppProxy) },
                        onNavigateWifiPolicy = { navigator.push(Route.WifiPolicy) },
                        onNavigateThemeSettings = { navigator.push(Route.ThemeSettings) },
                        onNavigateFileManager = { navigator.push(Route.FileManager) },
                        onNavigateOverrides = { navigator.push(Route.OverrideList) },
                        onNavigateBackup = { navigator.push(Route.BackupRestore) },
                        onNavigateAbout = { navigator.push(Route.About) },
                        bootStartManager = bootStartManager,
                        storage = storage,
                        onHideTaskCardChange = onHideTaskCardChange,
                        hasRootPermission = hasRootPermission,
                        isProxyRunning = homeUiState.isRunning || homeUiState.isStarting,
                    )
                }
            }
        }
    }

    if (rememberIsWideScreen()) {
        Scaffold(modifier = Modifier.fillMaxSize()) { _ ->
            Row(Modifier.fillMaxSize()) {
                NavigationRail(state = rememberNavigationRailState()) {
                    NAV_TABS.forEachIndexed { index, tab ->
                        NavigationRailItem(
                            modifier = Modifier.testTag(TestTags.Nav.tab(index)),
                            selected = selectedPage == index,
                            onClick = { mainPagerState.animateToPage(index) },
                            icon = tab.icon(selectedPage == index),
                            label = stringResource(tab.labelRes),
                        )
                    }
                }
                pagerContent(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .consumeWindowInsets(
                            WindowInsets.displayCutout.union(WindowInsets.navigationBars)
                                .only(WindowInsetsSides.Start),
                        )
                        .windowInsetsPadding(
                            WindowInsets.systemBars.union(WindowInsets.displayCutout)
                                .only(WindowInsetsSides.End),
                        ),
                    WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
                )
            }
        }
    } else {
        val bottomBarBackdrop = rememberBlurBackdrop(themeConfig.blurEnabled)
        val bottomBarBlurActive = bottomBarBackdrop != null
        val barColor = if (bottomBarBlurActive) Color.Transparent else MiuixTheme.colorScheme.surface
        val floatingBarColor = if (bottomBarBlurActive) Color.Transparent else MiuixTheme.colorScheme.surfaceContainer
        val floatingPillRadius = 50.dp
        val floatingBarShape = RoundedCornerShape(floatingPillRadius)
        val isDark = LocalAppDarkMode.current
        val floatingHighlight = remember(isDark) {
            if (isDark) Highlight.GlassStrokeMiddleDark else Highlight.GlassStrokeMiddleLight
        }
        val floatingBarModifier = if (bottomBarBackdrop != null) {
            Modifier.textureBlur(
                backdrop = bottomBarBackdrop,
                shape = floatingBarShape,
                blurRadius = 25f,
                colors = BlurDefaults.blurColors(
                    blendColors = listOf(
                        BlendColorEntry(
                            color = MiuixTheme.colorScheme.surfaceContainer.copy(alpha = 0.6f),
                        ),
                    ),
                ),
                highlight = floatingHighlight,
            )
        } else {
            Modifier
        }
        val bottomBarDisplayMode = when (themeConfig.bottomBarMode) {
            BottomBarMode.IconAndText -> NavigationBarDisplayMode.IconAndText
            BottomBarMode.IconOnly -> NavigationBarDisplayMode.IconOnly
        }
        val showBottomBarLabels = themeConfig.bottomBarMode == BottomBarMode.IconAndText
        val navigationItems = NAV_TABS.mapIndexed { index, tab ->
            NavigationItem(label = stringResource(tab.labelRes), icon = tab.icon(selectedPage == index))
        }

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            bottomBar = {
                if (themeConfig.floatingBottomBar) {
                    if (themeConfig.floatingBottomBarStyle == FloatingBottomBarStyle.IosLike) {
                        IosLiquidGlassNavigationBar(
                            items = navigationItems,
                            selectedIndex = selectedPage,
                            onItemClick = { index -> mainPagerState.animateToPage(index) },
                            backdrop = bottomBarBackdrop,
                            isBlurActive = bottomBarBlurActive,
                            isDark = isDark,
                            showLabels = showBottomBarLabels,
                        )
                    } else {
                        FloatingNavigationBar(
                            modifier = floatingBarModifier,
                            color = floatingBarColor,
                            cornerRadius = floatingPillRadius,
                        ) {
                            navigationItems.forEachIndexed { index, item ->
                                MiuixFloatingNavigationBarItem(
                                    item = item,
                                    selected = selectedPage == index,
                                    onClick = { mainPagerState.animateToPage(index) },
                                    showLabel = showBottomBarLabels,
                                    modifier = Modifier.testTag(TestTags.Nav.tab(index)),
                                )
                            }
                        }
                    }
                } else {
                    BlurredBar(
                        backdrop = bottomBarBackdrop,
                        blurActive = bottomBarBlurActive,
                        blurStyle = TopBarBlurStyle.Gaussian,
                    ) {
                        NavigationBar(
                            color = barColor,
                            mode = bottomBarDisplayMode,
                        ) {
                            navigationItems.forEachIndexed { index, item ->
                                NavigationBarItem(
                                    modifier = Modifier.testTag(TestTags.Nav.tab(index)),
                                    selected = selectedPage == index,
                                    onClick = { mainPagerState.animateToPage(index) },
                                    icon = item.icon,
                                    label = item.label,
                                )
                            }
                        }
                    }
                }
            },
        ) { padding ->
            pagerContent(
                if (bottomBarBackdrop != null) {
                    Modifier
                        .fillMaxSize()
                        .layerBackdrop(bottomBarBackdrop)
                } else {
                    Modifier.fillMaxSize()
                },
                padding.calculateBottomPadding(),
            )
        }
    }
}

@Composable
private fun MiuixFloatingNavigationBarItem(
    item: NavigationItem,
    selected: Boolean,
    onClick: () -> Unit,
    showLabel: Boolean,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val onSurfaceContainerColor = MiuixTheme.colorScheme.onSurfaceContainer
    val tint = when {
        isPressed -> onSurfaceContainerColor.copy(alpha = if (selected) 0.7f else 0.5f)
        selected -> onSurfaceContainerColor
        else -> onSurfaceContainerColor.copy(alpha = 0.6f)
    }

    Column(
        modifier = modifier
            .defaultMinSize(minWidth = if (showLabel) 56.dp else 48.dp, minHeight = 48.dp)
            .selectable(
                selected = selected,
                onClick = onClick,
                role = Role.Tab,
                interactionSource = interactionSource,
                indication = null,
            )
            .padding(horizontal = if (showLabel) 8.dp else 6.dp, vertical = 5.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            modifier = Modifier.size(22.dp),
            imageVector = item.icon,
            contentDescription = if (showLabel) null else item.label,
            tint = tint,
        )
        if (showLabel) {
            Text(
                text = item.label,
                color = tint,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Stable
class MainPagerState(
    val pagerState: PagerState,
    private val coroutineScope: CoroutineScope,
) {
    var selectedPage by mutableIntStateOf(pagerState.currentPage)
        private set

    var isNavigating by mutableStateOf(false)
        private set

    private var navJob: Job? = null

    fun animateToPage(targetIndex: Int) {
        if (targetIndex == selectedPage) return

        navJob?.cancel()
        selectedPage = targetIndex
        isNavigating = true

        navJob = coroutineScope.launch {
            val myJob = coroutineContext.job
            try {
                pagerState.scroll(MutatePriority.UserInput) {
                    val distance = abs(targetIndex - pagerState.currentPage).coerceAtLeast(2)
                    val duration = 100 * distance + 100
                    val layoutInfo = pagerState.layoutInfo
                    val pageSize = layoutInfo.pageSize + layoutInfo.pageSpacing
                    val currentDistanceInPages =
                        targetIndex - pagerState.currentPage - pagerState.currentPageOffsetFraction
                    val scrollPixels = currentDistanceInPages * pageSize

                    var previousValue = 0f
                    animate(
                        initialValue = 0f,
                        targetValue = scrollPixels,
                        animationSpec = tween(easing = EaseInOut, durationMillis = duration),
                    ) { currentValue, _ ->
                        previousValue += scrollBy(currentValue - previousValue)
                    }
                }

                if (pagerState.currentPage != targetIndex) {
                    pagerState.scrollToPage(targetIndex)
                }
            } finally {
                if (navJob == myJob) {
                    isNavigating = false
                    if (pagerState.currentPage != targetIndex) {
                        selectedPage = pagerState.currentPage
                    }
                }
            }
        }
    }

    fun syncPage() {
        if (!isNavigating && selectedPage != pagerState.currentPage) {
            selectedPage = pagerState.currentPage
        }
    }
}

@Composable
fun rememberMainPagerState(
    pagerState: PagerState,
    coroutineScope: CoroutineScope = rememberCoroutineScope(),
): MainPagerState = remember(pagerState, coroutineScope) {
    MainPagerState(pagerState, coroutineScope)
}

@Composable
private fun MainScreenBackHandler(
    mainState: MainPagerState,
    navigator: Navigator,
) {
    val isPagerBackHandlerEnabled by remember {
        derivedStateOf {
            navigator.current() is Route.Main &&
                    navigator.backStackSize() == 1 &&
                    mainState.selectedPage != 0
        }
    }

    val navEventState = rememberNavigationEventState(NavigationEventInfo.None)

    NavigationBackHandler(
        state = navEventState,
        isBackEnabled = isPagerBackHandlerEnabled,
        onBackCompleted = {
            mainState.animateToPage(0)
        },
    )
}

private class NavTab(
    @StringRes val labelRes: Int,
    private val idle: ImageVector,
    private val active: ImageVector,
) {
    fun icon(selected: Boolean): ImageVector = if (selected) active else idle
}

private val NAV_TABS = listOf(
    NavTab(R.string.nav_home, AppIcons.NavHome, AppIcons.NavHomeActive),
    NavTab(R.string.nav_proxy, AppIcons.NavProxy, AppIcons.NavProxyActive),
    NavTab(R.string.nav_subscription, AppIcons.NavSubscription, AppIcons.NavSubscriptionActive),
    NavTab(R.string.nav_settings, AppIcons.NavSettings, AppIcons.NavSettingsActive),
)
