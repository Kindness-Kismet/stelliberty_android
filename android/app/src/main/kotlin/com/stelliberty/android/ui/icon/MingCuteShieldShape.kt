// 由 scripts/gen_icons.py 从 MingCute 生成，勿手改。源：regular/shapes/shield_shape.svg
package com.stelliberty.android.ui.icon

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

internal val MingCuteShieldShape: ImageVector by lazy {
    ImageVector.Builder(
        name = "ShieldShape",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
        autoMirror = false,
    ).apply {
        addPath(
            pathData = PathParser().parsePathString("M11.6489 3.13167L4.64888 5.75667C4.25857 5.90304 4 6.27616 4 6.693V12.0557C4 15.0859 5.71202 17.856 8.42229 19.2111L11.7764 20.8882C11.9172 20.9586 12.0828 20.9586 12.2236 20.8882L15.5777 19.2111C18.288 17.856 20 15.0859 20 12.0557V6.693C20 6.27616 19.7414 5.90304 19.3511 5.75667L12.3511 3.13167C12.1247 3.04678 11.8753 3.04678 11.6489 3.13167Z").toNodes(),
            pathFillType = PathFillType.NonZero,
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2.0f,
            strokeLineCap = StrokeCap.Butt,
            strokeLineJoin = StrokeJoin.Miter,
        )
    }.build()
}
