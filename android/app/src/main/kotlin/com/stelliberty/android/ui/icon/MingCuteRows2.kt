// 由 scripts/gen_icons.py 从 MingCute 生成，勿手改。源：regular/design/rows_2.svg
package com.stelliberty.android.ui.icon

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

internal val MingCuteRows2: ImageVector by lazy {
    ImageVector.Builder(
        name = "Rows2",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
        autoMirror = false,
    ).apply {
        addPath(
            pathData = PathParser().parsePathString("M21 19.057c-.063 1.1-.843 1.88-1.943 1.943H4.943c-.445-.026-.846-.153-1.198-.435-.47-.377-.711-.915-.745-1.508V4.943c.026-.445.153-.846.435-1.198.377-.47.915-.711 1.508-.745h14.114c.593.034 1.131.275 1.508.745.282.352.409.753.435 1.198ZM5 13l.006 5.994h13.988L19 13Zm.006-7.994L5 11h14l-.006-5.994Z").toNodes(),
            pathFillType = PathFillType.NonZero,
            fill = SolidColor(Color.Black),
        )
    }.build()
}
