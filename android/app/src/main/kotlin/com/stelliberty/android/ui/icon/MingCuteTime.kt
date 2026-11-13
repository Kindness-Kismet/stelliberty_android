// 由 scripts/gen_icons.py 从 MingCute 生成，勿手改。源：regular/system/time.svg
package com.stelliberty.android.ui.icon

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

internal val MingCuteTime: ImageVector by lazy {
    ImageVector.Builder(
        name = "Time",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
        autoMirror = false,
    ).apply {
        addPath(
            pathData = PathParser().parsePathString("m13 11.586 2.707 2.707c.359.409.404 1.01 0 1.414s-1.005.359-1.414 0l-3-3-.159-.207c-.101-.175-.121-.302-.134-.5V7c.03-.451.288-.844.741-.966.656-.176 1.217.327 1.259.966Zm-1.742 10.386c-1.75-.119-3.446-.731-4.895-1.712C1.054 16.668.538 9.075 5.166 4.701a9.8 9.8 0 0 1 3.148-1.999 10.2 10.2 0 0 1 4.428-.674c1.75.119 3.446.731 4.895 1.712 5.309 3.592 5.825 11.185 1.197 15.559a9.8 9.8 0 0 1-3.148 1.999 10.2 10.2 0 0 1-4.428.674m.116-17.947a8 8 0 0 0-4.377 1.733C2.03 9.744 3.51 17.709 9.585 19.629a8.006 8.006 0 0 0 7.418-1.387c4.967-3.985 3.487-11.951-2.588-13.871a8.1 8.1 0 0 0-3.041-.346").toNodes(),
            pathFillType = PathFillType.NonZero,
            fill = SolidColor(Color.Black),
        )
    }.build()
}
