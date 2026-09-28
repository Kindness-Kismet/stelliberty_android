// 由 scripts/gen_icons.py 从 MingCute 生成，勿手改。源：regular/buildings/home_2.svg
package com.stelliberty.android.ui.icon

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

internal val MingCuteHome2: ImageVector by lazy {
    ImageVector.Builder(
        name = "Home2",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
        autoMirror = false,
    ).apply {
        addPath(
            pathData = PathParser().parsePathString("M3.00291 10C3.00196 10 3.00155 9.9988 3.0023 9.99821L11.3861 3.47751C11.7472 3.19665 12.2528 3.19665 12.6139 3.47751L20.9977 9.99821C20.9985 9.9988 20.998 10 20.9971 10H19V19C19 19.5523 18.5523 20 18 20H6C5.44772 20 5 19.5523 5 19V10H3.00291Z").toNodes(),
            pathFillType = PathFillType.NonZero,
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2.0f,
            strokeLineCap = StrokeCap.Butt,
            strokeLineJoin = StrokeJoin.Round,
        )
    }.build()
}
