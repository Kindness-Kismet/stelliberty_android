package com.stelliberty.android.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.stelliberty.android.R
import top.yukonga.miuix.kmp.theme.ThemePaletteStyle

@Composable
fun ThemeColorMode.label(): String = stringResource(
    when (this) {
        ThemeColorMode.System -> R.string.settings_theme_system
        ThemeColorMode.Light -> R.string.settings_theme_light
        ThemeColorMode.Dark -> R.string.settings_theme_dark
    },
)

@Composable
fun ThemeAccentColor.label(): String = stringResource(
    when (this) {
        ThemeAccentColor.Default -> R.string.settings_theme_accent_default
        ThemeAccentColor.Blue -> R.string.settings_theme_accent_blue
        ThemeAccentColor.Purple -> R.string.settings_theme_accent_purple
        ThemeAccentColor.Pink -> R.string.settings_theme_accent_pink
        ThemeAccentColor.Red -> R.string.settings_theme_accent_red
        ThemeAccentColor.Orange -> R.string.settings_theme_accent_orange
        ThemeAccentColor.Yellow -> R.string.settings_theme_accent_yellow
        ThemeAccentColor.Green -> R.string.settings_theme_accent_green
        ThemeAccentColor.Teal -> R.string.settings_theme_accent_teal
    },
)

@Composable
fun ThemePaletteStyle.label(): String = stringResource(
    when (this) {
        ThemePaletteStyle.Neutral -> R.string.settings_theme_palette_neutral
        ThemePaletteStyle.Vibrant -> R.string.settings_theme_palette_vibrant
        ThemePaletteStyle.Expressive -> R.string.settings_theme_palette_expressive
        ThemePaletteStyle.Rainbow -> R.string.settings_theme_palette_rainbow
        ThemePaletteStyle.FruitSalad -> R.string.settings_theme_palette_fruit_salad
        ThemePaletteStyle.Monochrome -> R.string.settings_theme_palette_monochrome
        ThemePaletteStyle.Fidelity -> R.string.settings_theme_palette_fidelity
        ThemePaletteStyle.Content -> R.string.settings_theme_palette_content
        else -> R.string.settings_theme_palette_tonal_spot
    },
)

@Composable
fun FloatingBottomBarStyle.label(): String = stringResource(
    when (this) {
        FloatingBottomBarStyle.Miuix -> R.string.settings_theme_floating_bottom_bar_style_miuix
        FloatingBottomBarStyle.IosLike -> R.string.settings_theme_floating_bottom_bar_style_ios_like
    },
)

@Composable
fun BottomBarMode.label(): String = stringResource(
    when (this) {
        BottomBarMode.IconAndText -> R.string.settings_theme_bottom_bar_icon_and_text
        BottomBarMode.IconOnly -> R.string.settings_theme_bottom_bar_icon_only
    },
)

@Composable
fun TopBarBlurStyle.label(): String = stringResource(
    when (this) {
        TopBarBlurStyle.Gaussian -> R.string.settings_theme_blur_style_gaussian
        TopBarBlurStyle.Progressive -> R.string.settings_theme_blur_style_progressive
    },
)

@Composable
fun TopBarBlurStyle.summary(): String = stringResource(
    when (this) {
        TopBarBlurStyle.Gaussian -> R.string.settings_theme_blur_style_gaussian_summary
        TopBarBlurStyle.Progressive -> R.string.settings_theme_blur_style_progressive_summary
    },
)
