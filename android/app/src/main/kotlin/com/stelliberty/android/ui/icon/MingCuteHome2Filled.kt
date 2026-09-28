// 由 scripts/gen_icons.py 从 MingCute 生成，勿手改。源：filled/buildings/home_2.svg
package com.stelliberty.android.ui.icon

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

internal val MingCuteHome2Filled: ImageVector by lazy {
    ImageVector.Builder(
        name = "Home2Filled",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
        autoMirror = false,
    ).apply {
        addPath(
            pathData = PathParser().parsePathString("M10.7722 2.68839C11.4944 2.12666 12.5057 2.12666 13.2279 2.68839L21.6117 9.20909C22.3648 9.79487 21.9492 11.0002 20.9971 11.0002H20.0001V19.0002C20.0001 20.1048 19.1046 21.0002 18.0001 21.0002H6.00005C4.89548 21.0002 4.00005 20.1048 4.00005 19.0002V11.0002H3.00297C2.04989 11.0002 1.63605 9.79426 2.38841 9.20909L10.7722 2.68839Z").toNodes(),
            pathFillType = PathFillType.EvenOdd,
            fill = SolidColor(Color.Black),
        )
    }.build()
}
