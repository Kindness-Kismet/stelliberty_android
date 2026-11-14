// SPDX-License-Identifier: Apache-2.0
// 来源：https://github.com/mingcute-design/mingcute-icons
package com.stelliberty.android.ui.icon

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

internal val MingCuteSwipeHorizontal: ImageVector by lazy {
    ImageVector.Builder(
        name = "MingCute.SwipeHorizontal",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
        autoMirror = false,
    ).apply {
        addPath(
            pathData = PathParser().parsePathString("M3 6s.643-.883 2-1.691m9-.512c2.042.889 3 2.202 3 2.202M8 13V4.5a1.5 1.5 0 1 1 3 0v4.605a1 1 0 0 0 .89.994l5.441.604A3 3 0 0 1 20 13.686V14a7 7 0 0 1-7 7h-.674a7 7 0 0 1-6.26-3.87L3 11c2-1 3-.5 5 2").toNodes(),
            pathFillType = PathFillType.NonZero,
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        )
    }.build()
}
