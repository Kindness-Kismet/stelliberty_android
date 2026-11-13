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

internal val MingCuteLanguage: ImageVector by lazy {
    ImageVector.Builder(
        name = "MingCute.Language",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
        autoMirror = false,
    ).apply {
        addPath(
            pathData = PathParser().parsePathString("M9.5 4H4v8h6m4-7h3a2 2 0 0 1 2 2v2m-9 10H7a2 2 0 0 1-2-2v-1M4 8h5m8 4v8m-2.5-2h5a.5.5 0 0 0 .5-.5v-3a.5.5 0 0 0-.5-.5h-5a.5.5 0 0 0-.5.5v3a.5.5 0 0 0 .5.5").toNodes(),
            pathFillType = PathFillType.NonZero,
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        )
    }.build()
}
