// 由 scripts/gen_icons.py 从 MingCute 生成，勿手改。源：filled/shapes/shield_shape.svg
package com.stelliberty.android.ui.icon

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

internal val MingCuteShieldShapeFilled: ImageVector by lazy {
    ImageVector.Builder(
        name = "ShieldShapeFilled",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
        autoMirror = false,
    ).apply {
        addPath(
            pathData = PathParser().parsePathString("M11.2978 2.1957C11.7505 2.02591 12.2495 2.02591 12.7022 2.1957L19.7022 4.8207C20.4829 5.11343 21 5.85967 21 6.69336V12.0561C21 15.465 19.074 18.5814 16.0249 20.1059L12.6708 21.783C12.2485 21.9941 11.7515 21.9941 11.3292 21.783L7.97508 20.1059C4.92602 18.5814 3 15.465 3 12.0561V6.69336C3 5.85967 3.51715 5.11343 4.29775 4.8207L11.2978 2.1957Z").toNodes(),
            pathFillType = PathFillType.EvenOdd,
            fill = SolidColor(Color.Black),
        )
    }.build()
}
