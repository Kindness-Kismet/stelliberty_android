package com.stelliberty.android.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import kotlin.math.roundToInt
import com.stelliberty.android.platform.PlatformStorage
import com.stelliberty.android.platform.StorageKeys
import top.yukonga.miuix.kmp.theme.ThemePaletteStyle

@Immutable
data class ThemeConfig(
    val colorMode: ThemeColorMode = ThemeColorMode.System,
    val pureBlack: Boolean = false,
    val useMonet: Boolean = false,
    val paletteStyle: ThemePaletteStyle = ThemePaletteStyle.TonalSpot,
    val accentColor: ThemeAccentColor = ThemeAccentColor.Default,
    val blurEnabled: Boolean = true,
    val bottomBarBlurEnabled: Boolean = blurEnabled,
    val floatingBottomBar: Boolean = false,
    val floatingBottomBarStyle: FloatingBottomBarStyle = FloatingBottomBarStyle.Miuix,
    val bottomBarMode: BottomBarMode = BottomBarMode.IconAndText,
    val densityScale: Float = DefaultDensityScale,
)

const val MinDensityScale = 0.8f
const val MaxDensityScale = 1.2f
const val DefaultDensityScale = 1f
const val DensityScaleStep = 0.05f
const val DensityScaleSteps = 7

// 颜色模式到深浅色的唯一换算处。组合树内一律读 LocalAppDarkMode，别各自调 isSystemInDarkTheme，
// 否则用户强制深色而系统是浅色时，那一处不会跟着变。
fun ThemeConfig.resolveIsDark(systemDark: Boolean): Boolean = when (colorMode) {
    ThemeColorMode.Light -> false
    ThemeColorMode.Dark -> true
    ThemeColorMode.System -> systemDark
}

fun normalizeDensityScale(value: Float): Float {
    if (!value.isFinite()) return DefaultDensityScale
    val step = ((value.coerceIn(MinDensityScale, MaxDensityScale) - MinDensityScale) / DensityScaleStep).roundToInt()
    return (80 + step * 5) / 100f
}

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
        // 尚未单独设置底栏模糊的用户沿用原全局开关，迁移不改变现有视觉效果。
        bottomBarBlurEnabled = storage.getString(
            StorageKeys.THEME_BOTTOM_BAR_BLUR,
            storage.getString(StorageKeys.THEME_BLUR, "true"),
        ) != "false",
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
    storage.putString(StorageKeys.THEME_BOTTOM_BAR_BLUR, config.bottomBarBlurEnabled.toString())
    storage.putString(StorageKeys.THEME_FLOATING_BOTTOM_BAR, config.floatingBottomBar.toString())
    storage.putString(StorageKeys.THEME_FLOATING_BOTTOM_BAR_STYLE, config.floatingBottomBarStyle.storageValue)
    storage.putString(StorageKeys.THEME_BOTTOM_BAR_MODE, config.bottomBarMode.storageValue)
    storage.putString(StorageKeys.THEME_DENSITY_SCALE, normalizeDensityScale(config.densityScale).toString())
}
