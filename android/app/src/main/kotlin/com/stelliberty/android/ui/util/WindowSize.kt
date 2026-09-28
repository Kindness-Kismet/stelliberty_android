package com.stelliberty.android.ui.util

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.stelliberty.android.ui.theme.LocalPlatformDensity
import top.yukonga.miuix.kmp.anim.folmeSpring

private val WideScreenMinWidth = 600.dp

val MaxContentWidth: Dp = 800.dp

@Composable
fun rememberIsWideScreen(): Boolean {
    val containerSize = LocalWindowInfo.current.containerSize
    val density = LocalPlatformDensity.current ?: LocalDensity.current
    return with(density) { containerSize.width.toDp() >= WideScreenMinWidth }
}

// 是否居中必须沿用宽屏判定的结果：外层导航栏按缩放前的密度量 600dp，而这里在缩放后的密度下量宽度，
// 各自比一次会出现「手机版外壳配上内容莫名内缩」这种错搭。留白量本身仍在缩放后的空间里算。
@Composable
fun WideContentBox(
    modifier: Modifier = Modifier,
    content: @Composable (sidePadding: Dp) -> Unit,
) {
    val isWideScreen = rememberIsWideScreen()
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val sidePadding = if (isWideScreen) {
            ((maxWidth - MaxContentWidth) / 2).coerceAtLeast(0.dp)
        } else {
            0.dp
        }
        content(sidePadding)
    }
}

@Composable
fun Modifier.horizontalCutoutPadding(): Modifier = windowInsetsPadding(
    WindowInsets.displayCutout.union(WindowInsets.navigationBars).only(WindowInsetsSides.Horizontal),
)

// 两处有意排除，别顺手补全：不含输入法（弹窗自己已经加了，并进来会是双倍间距）；
// 不含左右方向（弹窗本身居中且有最大宽度，单侧的缺口间距会把内容推偏）。
@Composable
fun Modifier.sheetContentSafePadding(): Modifier = windowInsetsPadding(
    WindowInsets.systemBars.union(WindowInsets.displayCutout).only(WindowInsetsSides.Bottom),
)

// 弹窗高度完全跟随内容且自身没有动画，从加载中切到列表、条数增减都会整体硬跳。
fun Modifier.sheetHeightTransition(): Modifier = animateContentSize(
    folmeSpring(damping = 0.9f, response = 0.38f, visibilityThreshold = IntSize(1, 1)),
)
