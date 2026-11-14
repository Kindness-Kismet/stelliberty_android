package com.stelliberty.android.ui.component

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.areNavigationBarsVisible
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

object PreferenceLayout {
    val HomeHorizontalPadding = 12.dp
    val SubPageHorizontalPadding = 16.dp
    val TopSpacing = 10.dp
    val GroupSpacing = 12.dp
    val ItemSpacing = 8.dp
    val IconSize = 24.dp
    val IconEndSpacing = 12.dp
    val ActionButtonSize = 40.dp
}

val LocalPreferenceHorizontalPadding = staticCompositionLocalOf { PreferenceLayout.HomeHorizontalPadding }

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun pageBottomPadding(
    scaffoldBottom: Dp = 0.dp,
    rootBottomBarInset: Dp = 0.dp,
    hiddenNavigationExtra: Dp = 0.dp,
): Dp {
    val navigationBars = WindowInsets.navigationBars
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val hasNavigationInset = navigationBars.getLeft(density, layoutDirection) > 0 || navigationBars.getTop(density) > 0 ||
        navigationBars.getRight(density, layoutDirection) > 0 || navigationBars.getBottom(density) > 0
    val navigationVisible = WindowInsets.areNavigationBarsVisible && hasNavigationInset
    val spacing = when {
        navigationVisible -> 12.dp
        rootBottomBarInset > 0.dp -> 12.dp + hiddenNavigationExtra
        else -> 20.dp
    }
    return maxOf(scaffoldBottom, navigationBars.asPaddingValues().calculateBottomPadding(), rootBottomBarInset) + spacing
}
