package com.stelliberty.android.ui.screen.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.stelliberty.android.R
import com.stelliberty.android.platform.PlatformStorage
import com.stelliberty.android.platform.StorageKeys
import com.stelliberty.android.ui.component.AppScalePreference
import com.stelliberty.android.ui.component.CardItem
import com.stelliberty.android.ui.component.PreferenceIcon
import com.stelliberty.android.ui.component.PreferenceLayout
import com.stelliberty.android.ui.component.SingleChoicePreference
import com.stelliberty.android.ui.component.groupedCardItems
import com.stelliberty.android.ui.icon.AppIcons
import com.stelliberty.android.ui.theme.BottomBarMode
import com.stelliberty.android.ui.theme.FloatingBottomBarStyle
import com.stelliberty.android.ui.theme.ThemeAccentColor
import com.stelliberty.android.ui.theme.ThemeColorMode
import com.stelliberty.android.ui.theme.ThemeConfig
import com.stelliberty.android.ui.theme.ThemePaletteStyles
import com.stelliberty.android.ui.theme.label
import com.stelliberty.android.ui.theme.writeThemeConfig
import kotlinx.collections.immutable.toImmutableList
import top.yukonga.miuix.kmp.blur.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.preference.SwitchPreference

@Composable
fun ThemeSettingsScreen(
    storage: PlatformStorage,
    themeConfig: ThemeConfig,
    onThemeConfigChange: (ThemeConfig) -> Unit,
    onPredictiveBackChange: ((Boolean) -> Unit)? = null,
    swipeDismissEnabled: Boolean = true,
    onSwipeDismissChange: ((Boolean) -> Unit)? = null,
    onBack: () -> Unit = {},
) {
    var isPredictiveBackEnabled by rememberSaveable {
        mutableStateOf(storage.getString(StorageKeys.PREDICTIVE_BACK, "false") == "true")
    }
    fun updateTheme(next: ThemeConfig) {
        writeThemeConfig(storage, next)
        onThemeConfigChange(next)
    }
    val colorModes = ThemeColorMode.entries
    val themeItems = colorModes.map { it.label() }.toImmutableList()
    val paletteStyles = ThemePaletteStyles
    val paletteItems = paletteStyles.map { it.label() }.toImmutableList()
    val accentOptions = ThemeAccentColor.entries
    val accentItems = accentOptions.map { it.label() }.toImmutableList()
    val floatingStyles = FloatingBottomBarStyle.entries
    val floatingItems = floatingStyles.map { it.label() }.toImmutableList()
    val bottomBarModes = BottomBarMode.entries
    val bottomBarItems = bottomBarModes.map { it.label() }.toImmutableList()
    val isBlurSupported = isRuntimeShaderSupported()
    val blurUnavailable = if (isBlurSupported) null else stringResource(R.string.settings_blur_unavailable)

    SettingsSubPage(
        title = stringResource(R.string.settings_theme_title),
        onBack = onBack,
        topSpacing = PreferenceLayout.TopSpacing,
    ) {
        groupedCardItems(
            keyPrefix = "theme_color",
            items = buildList {
                add(CardItem("mode") {
                    SingleChoicePreference(
                        title = stringResource(R.string.settings_theme_mode),
                        items = themeItems,
                        selectedIndex = themeConfig.colorMode.ordinal,
                        startAction = { PreferenceIcon(AppIcons.DarkMode) },
                        onSelectedIndexChange = { updateTheme(themeConfig.copy(colorMode = colorModes[it])) },
                    )
                })
                add(CardItem("monet") {
                    SwitchPreference(
                        title = stringResource(R.string.settings_theme_monet),
                        checked = themeConfig.useMonet,
                        startAction = { PreferenceIcon(AppIcons.Appearance) },
                        onCheckedChange = { updateTheme(themeConfig.copy(useMonet = it)) },
                    )
                })
                if (themeConfig.useMonet) {
                    add(CardItem("paletteStyle") {
                        SingleChoicePreference(
                            title = stringResource(R.string.settings_theme_palette_style),
                            items = paletteItems,
                            selectedIndex = paletteStyles.indexOf(themeConfig.paletteStyle),
                            onSelectedIndexChange = { updateTheme(themeConfig.copy(paletteStyle = paletteStyles[it])) },
                        )
                    })
                    add(CardItem("accentColor") {
                        SingleChoicePreference(
                            title = stringResource(R.string.settings_theme_accent_title),
                            items = accentItems,
                            selectedIndex = themeConfig.accentColor.ordinal,
                            onSelectedIndexChange = { updateTheme(themeConfig.copy(accentColor = accentOptions[it])) },
                        )
                    })
                    add(CardItem("pureBlack") {
                        SwitchPreference(
                            title = stringResource(R.string.settings_theme_pure_black),
                            checked = themeConfig.pureBlack,
                            onCheckedChange = { updateTheme(themeConfig.copy(pureBlack = it)) },
                        )
                    })
                }
            },
        )
        groupedCardItems(
            keyPrefix = "theme_blur",
            items = listOf(
                CardItem("blur") {
                    SwitchPreference(
                        title = stringResource(R.string.settings_theme_blur),
                        summary = blurUnavailable,
                        checked = themeConfig.blurEnabled,
                        enabled = isBlurSupported,
                        startAction = { PreferenceIcon(AppIcons.BlurOn) },
                        onCheckedChange = { updateTheme(themeConfig.copy(blurEnabled = it)) },
                    )
                },
                CardItem("bottomBarBlur") {
                    SwitchPreference(
                        title = stringResource(R.string.settings_theme_bottom_bar_blur),
                        summary = blurUnavailable,
                        checked = themeConfig.bottomBarBlurEnabled,
                        enabled = isBlurSupported,
                        startAction = { PreferenceIcon(AppIcons.BlurOn) },
                        onCheckedChange = { updateTheme(themeConfig.copy(bottomBarBlurEnabled = it)) },
                    )
                },
            ),
        )
        groupedCardItems(
            keyPrefix = "theme_gestures",
            items = buildList {
                if (onSwipeDismissChange != null) {
                    add(CardItem("swipeDismiss") {
                        SwitchPreference(
                            title = stringResource(R.string.settings_swipe_dismiss),
                            checked = swipeDismissEnabled,
                            startAction = { PreferenceIcon(AppIcons.SwipeHorizontal) },
                            onCheckedChange = onSwipeDismissChange,
                        )
                    })
                }
                add(CardItem("predictiveBack") {
                    SwitchPreference(
                        title = stringResource(R.string.settings_predictive_back),
                        summary = if (onPredictiveBackChange == null) stringResource(R.string.settings_predictive_back_unavailable) else null,
                        checked = isPredictiveBackEnabled,
                        enabled = onPredictiveBackChange != null,
                        startAction = { PreferenceIcon(AppIcons.SwipeHorizontal) },
                        onCheckedChange = {
                            storage.putString(StorageKeys.PREDICTIVE_BACK, it.toString())
                            isPredictiveBackEnabled = it
                            onPredictiveBackChange?.invoke(it)
                        },
                    )
                })
            },
        )
        groupedCardItems(
            keyPrefix = "theme_scale",
            items = listOf(CardItem("densityScale") {
                AppScalePreference(
                    scale = themeConfig.densityScale,
                    onScaleChange = { updateTheme(themeConfig.copy(densityScale = it)) },
                )
            }),
        )
        groupedCardItems(
            keyPrefix = "theme_navigation",
            outerBottomPadding = 0.dp,
            items = buildList {
                add(CardItem("floating") {
                    SwitchPreference(
                        title = stringResource(R.string.settings_theme_floating_bottom_bar),
                        checked = themeConfig.floatingBottomBar,
                        onCheckedChange = { updateTheme(themeConfig.copy(floatingBottomBar = it)) },
                    )
                })
                if (themeConfig.floatingBottomBar) {
                    add(CardItem("floatingStyle") {
                        SingleChoicePreference(
                            title = stringResource(R.string.settings_theme_floating_bottom_bar_style),
                            items = floatingItems,
                            selectedIndex = themeConfig.floatingBottomBarStyle.ordinal,
                            onSelectedIndexChange = { updateTheme(themeConfig.copy(floatingBottomBarStyle = floatingStyles[it])) },
                        )
                    })
                }
                add(CardItem("bottomBarMode") {
                    SingleChoicePreference(
                        title = stringResource(R.string.settings_theme_bottom_bar_mode),
                        items = bottomBarItems,
                        selectedIndex = themeConfig.bottomBarMode.ordinal,
                        onSelectedIndexChange = { updateTheme(themeConfig.copy(bottomBarMode = bottomBarModes[it])) },
                    )
                })
            },
        )
    }
}
