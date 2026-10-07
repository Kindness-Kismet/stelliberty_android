package com.stelliberty.android

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.Density
import com.stelliberty.android.platform.BootStartManager
import com.stelliberty.android.platform.FilePicker
import com.stelliberty.android.platform.PlatformStorage
import com.stelliberty.android.platform.WifiPolicyController
import com.stelliberty.android.ui.component.blur.LocalBlurEnabled
import com.stelliberty.android.ui.component.blur.LocalTopBarBlurStyle
import com.stelliberty.android.ui.navigation.AppNavigation
import com.stelliberty.android.ui.theme.LocalAppDarkMode
import com.stelliberty.android.ui.theme.LocalAppMonetEnabled
import com.stelliberty.android.ui.theme.LocalPlatformDensity
import com.stelliberty.android.ui.theme.ThemeAccentColor
import com.stelliberty.android.ui.theme.ThemeColorMode
import com.stelliberty.android.ui.theme.ThemeConfig
import com.stelliberty.android.ui.theme.resolveIsDark
import com.stelliberty.android.viewmodel.AppProxyViewModel
import com.stelliberty.android.viewmodel.BackupViewModel
import com.stelliberty.android.viewmodel.ChainProxyViewModel
import com.stelliberty.android.viewmodel.ClashFeaturesViewModel
import com.stelliberty.android.viewmodel.RuleOverrideViewModel
import com.stelliberty.android.viewmodel.ConnectionViewModel
import com.stelliberty.android.viewmodel.DnsQueryViewModel
import com.stelliberty.android.viewmodel.HomeViewModel
import com.stelliberty.android.viewmodel.LogViewModel
import com.stelliberty.android.viewmodel.OverrideProfileViewModel
import com.stelliberty.android.viewmodel.ProviderViewModel
import com.stelliberty.android.viewmodel.ProxyViewModel
import com.stelliberty.android.viewmodel.SubscriptionViewModel
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.LocalContentColor
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeColorSpec
import top.yukonga.miuix.kmp.theme.ThemeController
import top.yukonga.miuix.kmp.theme.platformDynamicColors

@Composable
fun App(
    themeConfig: ThemeConfig = ThemeConfig(),
    onThemeConfigChange: (ThemeConfig) -> Unit = {},
    homeViewModel: HomeViewModel? = null,
    subscriptionViewModel: SubscriptionViewModel? = null,
    proxyViewModel: ProxyViewModel? = null,
    logViewModel: LogViewModel? = null,
    providerViewModel: ProviderViewModel? = null,
    connectionViewModel: ConnectionViewModel? = null,
    dnsQueryViewModel: DnsQueryViewModel? = null,
    clashFeaturesViewModel: ClashFeaturesViewModel? = null,
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
    val colorSchemeMode = if (themeConfig.useMonet) {
        when (themeConfig.colorMode) {
            ThemeColorMode.System -> ColorSchemeMode.MonetSystem
            ThemeColorMode.Light -> ColorSchemeMode.MonetLight
            ThemeColorMode.Dark -> ColorSchemeMode.MonetDark
        }
    } else {
        when (themeConfig.colorMode) {
            ThemeColorMode.System -> ColorSchemeMode.System
            ThemeColorMode.Light -> ColorSchemeMode.Light
            ThemeColorMode.Dark -> ColorSchemeMode.Dark
        }
    }
    val isDark = themeConfig.resolveIsDark(isSystemInDarkTheme())
    val systemSeedColor = if (themeConfig.useMonet && themeConfig.accentColor == ThemeAccentColor.Default) {
        platformDynamicColors(isDark).primary
    } else {
        null
    }
    val keyColor = when {
        !themeConfig.useMonet -> null
        themeConfig.accentColor == ThemeAccentColor.Default -> systemSeedColor
        else -> themeConfig.accentColor.seedColor
    }
    val controller = remember(themeConfig, colorSchemeMode, keyColor, isDark) {
        ThemeController(
            colorSchemeMode = colorSchemeMode,
            keyColor = keyColor,
            colorSpec = ThemeColorSpec.Spec2025,
            paletteStyle = themeConfig.paletteStyle,
        )
    }
    val colors = controller.currentColors()
    val themedColors = remember(colors, isDark, themeConfig.pureBlack) {
        if (themeConfig.useMonet && themeConfig.pureBlack && isDark) {
            colors.copy(
                background = Color.Black,
                surface = Color.Black,
            )
        } else {
            colors
        }
    }

    MiuixTheme(colors = themedColors) {
        val currentDensity = LocalDensity.current
        val appDensity = remember(currentDensity, themeConfig.densityScale) {
            Density(
                density = currentDensity.density * themeConfig.densityScale,
                fontScale = currentDensity.fontScale,
            )
        }
        CompositionLocalProvider(
            LocalAppDarkMode provides isDark,
            LocalAppMonetEnabled provides themeConfig.useMonet,
            LocalPlatformDensity provides currentDensity,
            LocalDensity provides appDensity,
            LocalBlurEnabled provides themeConfig.blurEnabled,
            LocalTopBarBlurStyle provides themeConfig.topBarBlurStyle,
            LocalContentColor provides MiuixTheme.colorScheme.onBackground,
        ) {
            // testTag 默认只在 Compose 语义树里，adb 看不到；开这项才写进无障碍树的
            // resource-id 字段，调试脚本靠它定位控件。
            Box(
                modifier = Modifier.semantics {
                    if (BuildConfig.DEBUG) testTagsAsResourceId = true
                },
            ) {
                AppNavigation(
                    themeConfig = themeConfig,
                    onThemeConfigChange = onThemeConfigChange,
                    homeViewModel = homeViewModel,
                    subscriptionViewModel = subscriptionViewModel,
                    proxyViewModel = proxyViewModel,
                    logViewModel = logViewModel,
                    providerViewModel = providerViewModel,
                    connectionViewModel = connectionViewModel,
                    dnsQueryViewModel = dnsQueryViewModel,
                    clashFeaturesViewModel = clashFeaturesViewModel,
                    appProxyViewModel = appProxyViewModel,
                    filePicker = filePicker,
                    storage = storage,
                    bootStartManager = bootStartManager,
                    mihomoVersion = mihomoVersion,
                    onScanQR = onScanQR,
                    wifiPolicyController = wifiPolicyController,
                    onRequestWifiPermission = onRequestWifiPermission,
                    onPredictiveBackChange = onPredictiveBackChange,
                    onHideTaskCardChange = onHideTaskCardChange,
                    hasRootPermission = hasRootPermission,
                    deepLinkImport = deepLinkImport,
                    onDeepLinkImportConsumed = onDeepLinkImportConsumed,
                    backupViewModel = backupViewModel,
                    overrideViewModel = overrideViewModel,
                    chainProxyViewModel = chainProxyViewModel,
                    ruleOverrideViewModel = ruleOverrideViewModel,
                    onRestartApp = onRestartApp,
                )
            }
        }
    }
}
