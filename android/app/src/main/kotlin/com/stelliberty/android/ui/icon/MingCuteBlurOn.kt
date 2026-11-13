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

internal val MingCuteBlurOn: ImageVector by lazy {
    ImageVector.Builder(
        name = "MingCute.BlurOn",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
        autoMirror = false,
    ).apply {
        addPath(
            pathData = PathParser().parsePathString("M14.15 3.258a9 9 0 0 1 2.511 1.04m3.04 3.04a9 9 0 0 1 1.04 2.512m0 4.298a9 9 0 0 1-1.04 2.512m-3.04 3.04a9 9 0 0 1-2.512 1.04m-4.298 0a9 9 0 0 1-2.512-1.04M4.3 16.66a9 9 0 0 1-1.04-2.512m0-4.299A9 9 0 0 1 4.3 7.338m3.04-3.04a9 9 0 0 1 2.512-1.04").toNodes(),
            pathFillType = PathFillType.NonZero,
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Miter,
        )
    }.build()
}
