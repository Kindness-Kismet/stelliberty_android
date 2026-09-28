// 由 scripts/gen_icons.py 从 MingCute 生成，勿手改。源：regular/files/pin.svg
package com.stelliberty.android.ui.icon

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

internal val MingCutePin: ImageVector by lazy {
    ImageVector.Builder(
        name = "Pin",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
        autoMirror = false,
    ).apply {
        addPath(
            pathData = PathParser().parsePathString("M9.87858 14.1223C12.387 16.6307 14.1539 17.9032 15.1792 18.5244C15.7128 18.8477 16.3238 18.5035 16.412 17.8859L16.5873 16.6585C16.825 14.9946 17.4783 13.4174 18.4868 12.0727L20.6719 9.15928C20.9705 8.76116 20.9309 8.20407 20.579 7.85218L16.1487 3.42186C15.7968 3.06997 15.2397 3.03038 14.8416 3.32897L11.9282 5.51406C10.5835 6.52256 9.00631 7.17584 7.34237 7.41355L6.115 7.58889C5.49737 7.67712 5.15317 8.28806 5.47645 8.82167C6.09765 9.84701 7.37017 11.6139 9.87858 14.1223ZM9.87858 14.1223L4.92889 19.0722").toNodes(),
            pathFillType = PathFillType.NonZero,
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2.0f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Miter,
        )
    }.build()
}
