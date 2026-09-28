// 由 scripts/gen_icons.py 从 MingCute 生成，勿手改。源：regular/weather/moon_cloudy.svg
package com.stelliberty.android.ui.icon

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

internal val MingCuteMoonCloudy: ImageVector by lazy {
    ImageVector.Builder(
        name = "MoonCloudy",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
        autoMirror = false,
    ).apply {
        addPath(
            pathData = PathParser().parsePathString("M15.2692 9.39592C15.8574 8.50973 16.2 7.44644 16.2 6.30314C16.2 5.48142 16.023 4.70105 15.7051 3.99805C18.7131 4.52157 21.0001 7.14521 21.0001 10.303C21.0001 11.7133 20.5439 13.0171 19.771 14.0749V14.0789M19.771 14.0789C19.9196 14.5256 20 15.0034 20 15.5C20 17.9853 17.9853 20 15.5 20H6.5C4.567 20 3 18.433 3 16.5C3 14.7007 4.35773 13.2185 6.10455 13.0221C6.03602 12.6921 6 12.3503 6 12C6 9.23858 8.23858 7 11 7C12.8074 7 14.3908 7.959 15.2692 9.39592C15.5694 9.88712 15.7873 10.4342 15.9036 11.0179C17.7144 11.1788 19.2168 12.4124 19.771 14.0789Z").toNodes(),
            pathFillType = PathFillType.NonZero,
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2.0f,
            strokeLineCap = StrokeCap.Butt,
            strokeLineJoin = StrokeJoin.Round,
        )
    }.build()
}
