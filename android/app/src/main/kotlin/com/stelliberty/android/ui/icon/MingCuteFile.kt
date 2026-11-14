// 由 scripts/gen_icons.py 从 MingCute 生成，勿手改。源：regular/files/file.svg
package com.stelliberty.android.ui.icon

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

internal val MingCuteFile: ImageVector by lazy {
    ImageVector.Builder(
        name = "File",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
        autoMirror = false,
    ).apply {
        addPath(
            pathData = PathParser().parsePathString("M20 20.057c-.063 1.1-.843 1.88-1.943 1.943H5.943c-.593-.034-1.131-.275-1.508-.745-.282-.352-.409-.753-.435-1.198V3.943c.034-.593.275-1.131.745-1.508.352-.282.753-.409 1.198-.435h7.692c.514.025.949.205 1.33.551l4.484 4.484c.346.381.526.816.551 1.33ZM12 4l-5.994.006v15.988h11.988L18 10h-4.58c-.808-.065-1.355-.612-1.42-1.42Zm2 4h3.586L14 4.414Z").toNodes(),
            pathFillType = PathFillType.NonZero,
            fill = SolidColor(Color.Black),
        )
    }.build()
}
