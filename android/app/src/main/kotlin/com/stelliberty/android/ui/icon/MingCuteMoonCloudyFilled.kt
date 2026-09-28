// 由 scripts/gen_icons.py 从 MingCute 生成，勿手改。源：filled/weather/moon_cloudy.svg
package com.stelliberty.android.ui.icon

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

internal val MingCuteMoonCloudyFilled: ImageVector by lazy {
    ImageVector.Builder(
        name = "MoonCloudyFilled",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
        autoMirror = false,
    ).apply {
        addPath(
            pathData = PathParser().parsePathString("M11 5.99927C13.6623 5.99927 15.9177 7.73297 16.7031 10.1321C19.1622 10.6812 20.9999 12.875 21 15.4993C21 18.5368 18.5376 20.9993 15.5 20.9993H6.5C4.01472 20.9993 2 18.9846 2 16.4993C2.00011 14.5379 3.25437 12.8707 5.00488 12.2542C5.00135 12.1697 5 12.0845 5 11.9993C5.00014 8.68568 7.68638 5.99927 11 5.99927ZM15.8936 3.01587C19.3637 3.62834 22 6.65679 22 10.303C21.9999 11.2495 21.8191 12.1536 21.4951 12.9856C20.7609 11.2372 19.2846 9.8774 17.46 9.30103C16.9753 8.14235 16.1884 7.14145 15.1973 6.39771C15.1979 6.36629 15.2002 6.33455 15.2002 6.30298C15.2002 5.63063 15.0561 4.99424 14.7988 4.42114C14.4643 3.67571 15.1051 2.87692 15.8936 3.01587Z").toNodes(),
            pathFillType = PathFillType.NonZero,
            fill = SolidColor(Color.Black),
        )
    }.build()
}
