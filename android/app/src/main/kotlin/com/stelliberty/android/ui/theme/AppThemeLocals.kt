package com.stelliberty.android.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Density

// 组合树里判断深浅色一律读这个值，页面和组件都不要直接问系统：
// 用户强制选了深色或浅色时，直接问系统的那处不会跟着变。
val LocalAppDarkMode = staticCompositionLocalOf { false }
val LocalAppMonetEnabled = staticCompositionLocalOf { false }
val LocalPlatformDensity = staticCompositionLocalOf<Density?> { null }
