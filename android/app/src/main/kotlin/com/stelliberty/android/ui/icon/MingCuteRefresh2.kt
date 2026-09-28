// 由 scripts/gen_icons.py 从 MingCute 生成，勿手改。源：regular/system/refresh_2.svg
package com.stelliberty.android.ui.icon

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

internal val MingCuteRefresh2: ImageVector by lazy {
    ImageVector.Builder(
        name = "Refresh2",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
        autoMirror = false,
    ).apply {
        addPath(
            pathData = PathParser().parsePathString("M3.81104 8.26367C5.59973 4.33101 10.0186 2.15182 14.3296 3.30695C17.9317 4.27213 20.4351 7.29097 20.9176 10.7662C20.9707 11.1491 20.9994 11.5375 21.0022 11.9293C21.0028 12.0087 20.9142 12.0553 20.8483 12.0109L18.1703 10.2065C18.0788 10.1449 18.1399 10.0023 18.2476 10.0259L20 10.4102M20.189 15.7367C18.4003 19.6693 13.9814 21.8485 9.67042 20.6934C6.06833 19.7282 3.56495 16.7094 3.08245 13.2341C3.02929 12.8513 3.00067 12.4629 2.9978 12.0711C2.99722 11.9916 3.08583 11.9451 3.15175 11.9895L5.8296 13.7938C5.92108 13.8554 5.86003 13.998 5.75229 13.9744L4.00002 13.5899").toNodes(),
            pathFillType = PathFillType.NonZero,
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2.0f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        )
    }.build()
}
