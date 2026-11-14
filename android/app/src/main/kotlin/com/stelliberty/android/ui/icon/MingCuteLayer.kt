// 由 scripts/gen_icons.py 从 MingCute 生成，勿手改。源：regular/design/layer.svg
package com.stelliberty.android.ui.icon

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

internal val MingCuteLayer: ImageVector by lazy {
    ImageVector.Builder(
        name = "Layer",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
        autoMirror = false,
    ).apply {
        addPath(
            pathData = PathParser().parsePathString("M12 18.202V21c-.056.827-.836 1.244-1.555.832l-5.554-3.703a2.2 2.2 0 0 1-.59-.617A2.1 2.1 0 0 1 4 16.465V6c.056-.827.836-1.244 1.555-.832L8 6.798V4c.056-.827.836-1.244 1.555-.832L12 4.798V2c.056-.827.836-1.244 1.555-.832l5.554 3.703c.234.169.438.37.59.617.197.318.287.676.301 1.047V17c-.056.827-.836 1.244-1.555.832L16 16.202V19c-.056.827-.836 1.244-1.555.832Zm-2.007-7.663-1.548-1.04L9 8.667l-.555.832L6 7.869l.007 8.592L10 19.131Zm4-2-1.548-1.04.555-.832-.555.832L10 5.869v2.262c.32.214 1.018.608 1.395.976.39.381.585.889.605 1.428v5.263l2 1.333ZM14 6.131c.32.214 1.018.608 1.395.976.39.381.585.889.605 1.428v5.263l2 1.333-.007-8.592L14 3.869Z").toNodes(),
            pathFillType = PathFillType.NonZero,
            fill = SolidColor(Color.Black),
        )
    }.build()
}
