// 由 scripts/gen_icons.py 从 MingCute 生成，勿手改。源：regular/files/upload.svg
package com.stelliberty.android.ui.icon

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

internal val MingCuteUpload: ImageVector by lazy {
    ImageVector.Builder(
        name = "Upload",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
        autoMirror = false,
    ).apply {
        addPath(
            pathData = PathParser().parsePathString("M8 9H6C5.44772 9 5 9.44772 5 10V20C5 20.5523 5.44772 21 6 21H18C18.5523 21 19 20.5523 19 20V10C19 9.44772 18.5523 9 18 9H16M12 3V14M14.5 5.5L12 3L9.5 5.5").toNodes(),
            pathFillType = PathFillType.NonZero,
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2.0f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        )
    }.build()
}
