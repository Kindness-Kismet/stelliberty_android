package com.stelliberty.android.ui.theme

import androidx.compose.ui.graphics.Color
import com.stelliberty.android.platform.PlatformStorage
import com.stelliberty.android.platform.StorageKeys
import top.yukonga.miuix.kmp.theme.ThemePaletteStyle

data class ThemeConfig(
    val colorMode: ThemeColorMode = ThemeColorMode.System,
    val pureBlack: Boolean = false,
    val useMonet: Boolean = false,
    val paletteStyle: ThemePaletteStyle = ThemePaletteStyle.TonalSpot,
    val accentColor: ThemeAccentColor = ThemeAccentColor.Default,
    val blurEnabled: Boolean = true,
    val topBarBlurStyle: TopBarBlurStyle = TopBarBlurStyle.Gaussian,
    val floatingBottomBar: Boolean = false,
    val floatingBottomBarStyle: FloatingBottomBarStyle = FloatingBottomBarStyle.Miuix,
    val bottomBarMode: BottomBarMode = BottomBarMode.IconAndText,
    val densityScale: Float = DefaultDensityScale,
)

const val MinDensityScale = 0.8f
const val MaxDensityScale = 1.1f
const val DefaultDensityScale = 1f

// 颜色模式到深浅色的唯一换算处。组合树内一律读 LocalAppDarkMode，别各自调 isSystemInDarkTheme，
// 否则用户强制深色而系统是浅色时，那一处不会跟着变。
fun ThemeConfig.resolveIsDark(systemDark: Boolean): Boolean = when (colorMode) {
    ThemeColorMode.Light -> false
    ThemeColorMode.Dark -> true
    ThemeColorMode.System -> systemDark
}

fun normalizeDensityScale(value: Float): Float =
    if (value.isFinite()) value.coerceIn(MinDensityScale, MaxDensityScale) else DefaultDensityScale

// 声明顺序即设置页选择器的排列顺序，靠 ordinal 对接下标，调整顺序会改动界面。
enum class ThemeColorMode(val storageValue: String) {
    System("system"),
    Light("light"),
    Dark("dark");

    companion object {
        fun fromStorage(value: String): ThemeColorMode =
            entries.firstOrNull { it.storageValue == value } ?: System
    }
}

enum class FloatingBottomBarStyle(val storageValue: String) {
    Miuix("miuix"),
    IosLike("ios_like");

    companion object {
        fun fromStorage(value: String): FloatingBottomBarStyle =
            entries.firstOrNull { it.storageValue == value } ?: Miuix
    }
}

enum class BottomBarMode(val storageValue: String) {
    IconAndText("icon_and_text"),
    IconOnly("icon_only");

    companion object {
        fun fromStorage(value: String): BottomBarMode =
            entries.firstOrNull { it.storageValue == value } ?: IconAndText
    }
}

enum class TopBarBlurStyle(val storageValue: String) {
    Gaussian("gaussian"),
    Progressive("progressive");

    companion object {
        fun fromStorage(value: String): TopBarBlurStyle =
            entries.firstOrNull { it.storageValue == value } ?: Gaussian
    }
}

enum class ThemeAccentColor(
    val storageValue: String,
    val seedColor: Color,
) {
    Default("default", Color(0xFF3482FF)),
    Blue("blue", Color(0xFF3482FF)),
    Purple("purple", Color(0xFF6750A4)),
    Pink("pink", Color(0xFFB0006D)),
    Red("red", Color(0xFFBA1A1A)),
    Orange("orange", Color(0xFFB65D00)),
    Yellow("yellow", Color(0xFF7D5700)),
    Green("green", Color(0xFF006D3B)),
    Teal("teal", Color(0xFF006A6A));

    companion object {
        fun fromStorage(value: String): ThemeAccentColor =
            entries.firstOrNull { it.storageValue == value } ?: Default
    }
}

val ThemePaletteStyles: List<ThemePaletteStyle> = ThemePaletteStyle.entries.toList()

fun themePaletteStyleFromStorage(value: String): ThemePaletteStyle =
    ThemePaletteStyles.firstOrNull { it.name == value } ?: ThemePaletteStyle.TonalSpot

fun readThemeConfig(storage: PlatformStorage): ThemeConfig {
    return ThemeConfig(
        colorMode = ThemeColorMode.fromStorage(
            storage.getString(StorageKeys.DARK_MODE, ThemeColorMode.System.storageValue),
        ),
        pureBlack = storage.getString(StorageKeys.THEME_PURE_BLACK, "false") == "true",
        useMonet = storage.getString(StorageKeys.THEME_MONET, "false") == "true",
        paletteStyle = themePaletteStyleFromStorage(
            storage.getString(StorageKeys.THEME_PALETTE_STYLE, ThemePaletteStyle.TonalSpot.name),
        ),
        accentColor = ThemeAccentColor.fromStorage(
            storage.getString(StorageKeys.THEME_ACCENT_COLOR, ThemeAccentColor.Default.storageValue),
        ),
        blurEnabled = storage.getString(StorageKeys.THEME_BLUR, "true") != "false",
        topBarBlurStyle = TopBarBlurStyle.fromStorage(
            storage.getString(StorageKeys.THEME_BLUR_STYLE, TopBarBlurStyle.Gaussian.storageValue),
        ),
        floatingBottomBar = storage.getString(StorageKeys.THEME_FLOATING_BOTTOM_BAR, "false") == "true",
        floatingBottomBarStyle = FloatingBottomBarStyle.fromStorage(
            storage.getString(StorageKeys.THEME_FLOATING_BOTTOM_BAR_STYLE, FloatingBottomBarStyle.Miuix.storageValue),
        ),
        bottomBarMode = BottomBarMode.fromStorage(
            storage.getString(StorageKeys.THEME_BOTTOM_BAR_MODE, BottomBarMode.IconAndText.storageValue),
        ),
        densityScale = normalizeDensityScale(
            storage.getString(StorageKeys.THEME_DENSITY_SCALE, DefaultDensityScale.toString()).toFloatOrNull()
                ?: DefaultDensityScale,
        ),
    )
}

fun writeThemeConfig(storage: PlatformStorage, config: ThemeConfig) {
    storage.putString(StorageKeys.DARK_MODE, config.colorMode.storageValue)
    storage.putString(StorageKeys.THEME_PURE_BLACK, config.pureBlack.toString())
    storage.putString(StorageKeys.THEME_MONET, config.useMonet.toString())
    storage.putString(StorageKeys.THEME_PALETTE_STYLE, config.paletteStyle.name)
    storage.putString(StorageKeys.THEME_ACCENT_COLOR, config.accentColor.storageValue)
    storage.putString(StorageKeys.THEME_BLUR, config.blurEnabled.toString())
    storage.putString(StorageKeys.THEME_BLUR_STYLE, config.topBarBlurStyle.storageValue)
    storage.putString(StorageKeys.THEME_FLOATING_BOTTOM_BAR, config.floatingBottomBar.toString())
    storage.putString(StorageKeys.THEME_FLOATING_BOTTOM_BAR_STYLE, config.floatingBottomBarStyle.storageValue)
    storage.putString(StorageKeys.THEME_BOTTOM_BAR_MODE, config.bottomBarMode.storageValue)
    storage.putString(StorageKeys.THEME_DENSITY_SCALE, normalizeDensityScale(config.densityScale).toString())
}
