package com.stelliberty.android.ui.screen.settings

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import com.stelliberty.android.R
import com.stelliberty.android.platform.PlatformStorage
import com.stelliberty.android.platform.StorageKeys
import com.stelliberty.android.ui.component.AdaptiveTopAppBar
import com.stelliberty.android.ui.component.CardItem
import com.stelliberty.android.ui.component.PreferenceIcon
import com.stelliberty.android.ui.component.PreferenceLayout
import com.stelliberty.android.ui.component.SearchTopAppBar
import com.stelliberty.android.ui.component.SingleChoiceDialog
import com.stelliberty.android.ui.component.blur.BlurredBar
import com.stelliberty.android.ui.component.blur.rememberBlurBackdrop
import com.stelliberty.android.ui.component.densityScaleLabel
import com.stelliberty.android.ui.component.groupedCardItems
import com.stelliberty.android.ui.component.pageBottomPadding
import com.stelliberty.android.ui.icon.AppIcons
import com.stelliberty.android.ui.navigation.Route
import com.stelliberty.android.ui.platform.updateAppLanguage
import com.stelliberty.android.ui.theme.AppLanguage
import com.stelliberty.android.ui.theme.ThemeConfig
import com.stelliberty.android.ui.theme.label
import com.stelliberty.android.ui.theme.readAppLanguage
import com.stelliberty.android.ui.util.TestTags
import com.stelliberty.android.ui.util.WideContentBox
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.DropdownArrowEndAction
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

@Composable
fun SettingsScreen(
    storage: PlatformStorage,
    themeConfig: ThemeConfig,
    modifier: Modifier = Modifier,
    bottomPadding: Dp = 0.dp,
    isActive: Boolean = true,
    onLanguageChange: () -> Unit = {},
    onNavigateSearchResult: (Route) -> Unit = {},
    onNavigateThemeSettings: () -> Unit = {},
    onNavigateClashFeatures: () -> Unit = {},
    onNavigateOverrides: () -> Unit = {},
    onNavigateFileManager: () -> Unit = {},
    onNavigateAppBehavior: () -> Unit = {},
    onNavigateAbout: () -> Unit = {},
    onNavigateDataManagement: () -> Unit = {},
    onNavigateAgeKey: () -> Unit = {},
) {
    var isSearching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var showLanguages by rememberSaveable { mutableStateOf(false) }
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val pageActive = isActive && lifecycleState.isAtLeast(Lifecycle.State.RESUMED)
    val context = LocalContext.current
    val language = readAppLanguage(context, storage)
    val languageOptions = AppLanguage.entries.map { it.label() }.toImmutableList()
    val scrollBehavior = MiuixScrollBehavior(canScroll = { !isSearching })
    val listState = rememberLazyListState()
    val searchListState = rememberLazyListState()
    val backdrop = rememberBlurBackdrop()
    val blurActive = backdrop != null
    val barColor = if (blurActive) Color.Transparent else MiuixTheme.colorScheme.surface
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val searchItems = settingsSearchItems(themeConfig, language)
    val normalizedQuery = query.trim()
    val results = remember(searchItems, normalizedQuery) {
        searchItems.filter {
            "${it.title} ${it.currentValue.orEmpty()} ${it.keywords}".contains(normalizedQuery, ignoreCase = true)
        }.toImmutableList()
    }
    val showResults = isSearching && normalizedQuery.isNotEmpty()
    LaunchedEffect(normalizedQuery) {
        if (normalizedQuery.isNotEmpty()) searchListState.scrollToItem(0)
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            BlurredBar(backdrop = backdrop, blurActive = blurActive) {
                SearchTopAppBar(
                    isSearching = isSearching,
                    isActive = pageActive,
                    query = query,
                    onQueryChange = { query = it },
                    onClose = { isSearching = false },
                ) {
                    AdaptiveTopAppBar(
                        title = stringResource(R.string.settings_title),
                        color = barColor,
                        scrollBehavior = scrollBehavior,
                        actions = {
                            IconButton(
                                onClick = { isSearching = true },
                                modifier = Modifier.size(40.dp).testTag(TestTags.Settings.SEARCH),
                            ) {
                                Icon(
                                    imageVector = AppIcons.Search,
                                    contentDescription = stringResource(R.string.settings_search),
                                    modifier = Modifier.size(24.dp),
                                    tint = MiuixTheme.colorScheme.onSurface,
                                )
                            }
                        },
                    )
                }
            }
        },
    ) { innerPadding ->
        val contentBottom = pageBottomPadding(innerPadding.calculateBottomPadding(), bottomPadding)
        WideContentBox { sidePadding ->
            LazyColumn(
                state = if (showResults) searchListState else listState,
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier)
                    .scrollEndHaptic()
                    .overScrollVertical()
                    .nestedScroll(scrollBehavior.nestedScrollConnection),
                contentPadding = PaddingValues(
                    top = innerPadding.calculateTopPadding() + PreferenceLayout.TopSpacing,
                    start = sidePadding,
                    end = sidePadding,
                    bottom = contentBottom,
                ),
            ) {
                if (showResults) {
                    if (results.isEmpty()) {
                        item {
                            Text(
                                text = stringResource(R.string.settings_search_empty),
                                modifier = Modifier.fillMaxWidth().padding(24.dp),
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                    results.forEachIndexed { index, entry ->
                        groupedCardItems(
                            keyPrefix = "settings_search_${entry.key}",
                            outerBottomPadding = if (index == results.lastIndex) 0.dp else PreferenceLayout.ItemSpacing,
                            items = listOf(CardItem("search_${entry.key}") {
                                ArrowPreference(
                                    title = entry.title,
                                    summary = entry.currentValue,
                                    modifier = Modifier.testTag(TestTags.Settings.searchResult(entry.key)),
                                    onClick = {
                                        keyboard?.hide()
                                        focusManager.clearFocus()
                                        if (entry.route == null) showLanguages = true else onNavigateSearchResult(entry.route)
                                    },
                                )
                            }),
                        )
                    }
                } else {
                    groupedCardItems(
                        keyPrefix = "settings_personalization",
                        items = listOf(
                            CardItem("theme") {
                                ArrowPreference(
                                    title = stringResource(R.string.settings_theme_title),
                                    startAction = { PreferenceIcon(AppIcons.Appearance) },
                                    onClick = onNavigateThemeSettings,
                                )
                            },
                            CardItem("language") {
                                BasicComponent(
                                    title = stringResource(R.string.settings_language),
                                    summary = language.label(),
                                    startAction = { PreferenceIcon(AppIcons.Language) },
                                    endActions = { DropdownArrowEndAction(MiuixTheme.colorScheme.onSurfaceVariantActions) },
                                    holdDownState = showLanguages,
                                    onClick = { showLanguages = true },
                                )
                            },
                        ),
                    )
                    groupedCardItems(
                        keyPrefix = "settings_clash",
                        items = listOf(
                            CardItem("clashFeatures") {
                                ArrowPreference(
                                    title = stringResource(R.string.clash_features_title),
                                    startAction = { PreferenceIcon(AppIcons.NavProxy) },
                                    onClick = onNavigateClashFeatures,
                                )
                            },
                            CardItem("overrides") {
                                ArrowPreference(
                                    title = stringResource(R.string.override_title),
                                    startAction = { PreferenceIcon(AppIcons.NavSubscription) },
                                    onClick = onNavigateOverrides,
                                )
                            },
                            CardItem("fileManager") {
                                ArrowPreference(
                                    title = stringResource(R.string.settings_file_manager),
                                    startAction = { PreferenceIcon(AppIcons.Backup) },
                                    onClick = onNavigateFileManager,
                                )
                            },
                        ),
                    )
                    groupedCardItems(
                        keyPrefix = "settings_application",
                        items = listOf(CardItem("appBehavior") {
                            ArrowPreference(
                                title = stringResource(R.string.app_behavior_title),
                                startAction = { PreferenceIcon(AppIcons.NavSettings) },
                                onClick = onNavigateAppBehavior,
                            )
                        }),
                    )
                    groupedCardItems(
                        keyPrefix = "settings_maintenance",
                        items = listOf(
                            CardItem("dataManagement") {
                                ArrowPreference(
                                    title = stringResource(R.string.data_management_title),
                                    startAction = { PreferenceIcon(AppIcons.Restore) },
                                    onClick = onNavigateDataManagement,
                                )
                            },
                            CardItem("ageKey") {
                                ArrowPreference(
                                    title = stringResource(R.string.age_key_title),
                                    startAction = { PreferenceIcon(AppIcons.NavProxy) },
                                    onClick = onNavigateAgeKey,
                                )
                            },
                        ),
                    )
                    groupedCardItems(
                        keyPrefix = "settings_about",
                        outerBottomPadding = 0.dp,
                        items = listOf(CardItem("about") {
                            ArrowPreference(
                                title = stringResource(R.string.settings_about),
                                startAction = { PreferenceIcon(AppIcons.Info) },
                                onClick = onNavigateAbout,
                            )
                        }),
                    )
                }
            }
        }
    }
    SingleChoiceDialog(
        show = showLanguages,
        title = stringResource(R.string.settings_language),
        items = languageOptions,
        selectedIndex = language.ordinal,
        onDismiss = { showLanguages = false },
        onSelectedIndexChange = {
            showLanguages = false
            val selected = AppLanguage.entries[it]
            if (selected != language) {
                storage.putString(StorageKeys.APP_LANGUAGE, selected.languageTag)
                updateAppLanguage(context, selected)
                onLanguageChange()
            }
        },
    )
}

@Immutable
private data class SettingsSearchItem(
    val key: String,
    val title: String,
    val route: Route?,
    val keywords: String = "",
    val currentValue: String? = null,
)

@Composable
private fun settingsSearchItems(theme: ThemeConfig, language: AppLanguage): ImmutableList<SettingsSearchItem> = persistentListOf(
    SettingsSearchItem("theme", stringResource(R.string.settings_theme_title), Route.ThemeSettings, stringResource(R.string.settings_theme_summary)),
    SettingsSearchItem("language", stringResource(R.string.settings_language), null, currentValue = language.label()),
    SettingsSearchItem("themeMode", stringResource(R.string.settings_theme_mode), Route.ThemeSettings, currentValue = theme.colorMode.label()),
    SettingsSearchItem("monet", stringResource(R.string.settings_theme_monet), Route.ThemeSettings, stringResource(R.string.settings_theme_monet_summary)),
    SettingsSearchItem("blur", stringResource(R.string.settings_theme_blur), Route.ThemeSettings),
    SettingsSearchItem("bottomBarBlur", stringResource(R.string.settings_theme_bottom_bar_blur), Route.ThemeSettings),
    SettingsSearchItem("densityScale", stringResource(R.string.settings_theme_density_scale), Route.ThemeSettings, currentValue = densityScaleLabel(theme.densityScale, stringResource(R.string.common_default))),
    SettingsSearchItem("predictiveBack", stringResource(R.string.settings_predictive_back), Route.ThemeSettings),
    SettingsSearchItem("clashFeatures", stringResource(R.string.clash_features_title), Route.ClashFeatures, stringResource(R.string.clash_features_summary)),
    SettingsSearchItem("network", stringResource(R.string.network_settings_title), Route.NetworkSettings),
    SettingsSearchItem("ports", stringResource(R.string.port_control_title), Route.PortControl),
    SettingsSearchItem("systemIntegration", stringResource(R.string.system_integration_title), Route.SystemIntegration),
    SettingsSearchItem("dns", stringResource(R.string.dns_settings_title), Route.DnsSettings, "DNS"),
    SettingsSearchItem("performance", stringResource(R.string.performance_settings_title), Route.PerformanceSettings),
    SettingsSearchItem("vpn", stringResource(R.string.vpn_settings_title), Route.VpnSettings, "VPN TUN"),
    SettingsSearchItem("root", stringResource(R.string.root_settings_title), Route.RootSettings, "ROOT TPROXY"),
    SettingsSearchItem("appProxy", stringResource(R.string.app_proxy_title), Route.AppProxy),
    SettingsSearchItem("overrides", stringResource(R.string.override_title), Route.OverrideList, stringResource(R.string.settings_overrides_summary)),
    SettingsSearchItem("fileManager", stringResource(R.string.settings_file_manager), Route.FileManager, stringResource(R.string.settings_file_manager_summary)),
    SettingsSearchItem("appBehavior", stringResource(R.string.app_behavior_title), Route.AppBehavior, stringResource(R.string.app_behavior_summary)),
    SettingsSearchItem("appNotifications", stringResource(R.string.settings_app_notifications), Route.AppBehavior),
    SettingsSearchItem("wifiPolicy", stringResource(R.string.wifi_policy_title), Route.WifiPolicy, "Wi-Fi"),
    SettingsSearchItem("dataManagement", stringResource(R.string.data_management_title), Route.DataManagement, stringResource(R.string.data_management_summary)),
    SettingsSearchItem("ageKey", stringResource(R.string.age_key_title), Route.AgeKey, stringResource(R.string.age_key_summary)),
    SettingsSearchItem("about", stringResource(R.string.settings_about), Route.About),
)
