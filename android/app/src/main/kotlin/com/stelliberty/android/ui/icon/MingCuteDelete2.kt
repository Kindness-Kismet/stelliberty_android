// 由 scripts/gen_icons.py 从 MingCute 生成，勿手改。源：regular/system/delete_2.svg
package com.stelliberty.android.ui.icon

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

internal val MingCuteDelete2: ImageVector by lazy {
    ImageVector.Builder(
        name = "Delete2",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
        autoMirror = false,
    ).apply {
        addPath(
            pathData = PathParser().parsePathString("M5 6L5.87554 19.133C5.94558 20.1836 6.81818 21 7.87111 21H16.1289C17.1818 21 18.0544 20.1836 18.1245 19.133L19 6M8 6L8.77208 3.68377C8.90819 3.27543 9.29033 3 9.72076 3H14.2792C14.7097 3 15.0918 3.27543 15.2279 3.68377L16 6M10 11V16M14 11V16M4 6H20").toNodes(),
            pathFillType = PathFillType.NonZero,
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2.0f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Miter,
        )
    }.build()
}
