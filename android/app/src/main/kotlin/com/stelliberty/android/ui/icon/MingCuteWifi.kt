// 由 scripts/gen_icons.py 从 MingCute 生成，勿手改。源：regular/devices/wifi.svg
package com.stelliberty.android.ui.icon

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

internal val MingCuteWifi: ImageVector by lazy {
    ImageVector.Builder(
        name = "Wifi",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
        autoMirror = false,
    ).apply {
        addPath(
            pathData = PathParser().parsePathString("M12 18H12.01M9.17139 15.1716C9.89524 14.4477 10.8952 14 11.9998 14C13.1044 14 14.1044 14.4477 14.8282 15.1716M6.34302 12.3431C7.79073 10.8954 9.79073 10 11.9999 10C14.209 10 16.209 10.8954 17.6567 12.3431M3.51465 9.51472C5.68622 7.34315 8.68622 6 11.9999 6C15.3136 6 18.3136 7.34315 20.4852 9.51472").toNodes(),
            pathFillType = PathFillType.NonZero,
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2.0f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Miter,
        )
    }.build()
}
