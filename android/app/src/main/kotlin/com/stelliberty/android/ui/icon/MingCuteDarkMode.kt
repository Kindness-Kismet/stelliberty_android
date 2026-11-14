// SPDX-License-Identifier: Apache-2.0
// 来源：https://github.com/mingcute-design/mingcute-icons
package com.stelliberty.android.ui.icon

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

internal val MingCuteDarkMode: ImageVector by lazy {
    ImageVector.Builder(
        name = "MingCute.DarkMode",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
        autoMirror = false,
    ).apply {
        addPath(
            pathData = PathParser().parsePathString("m13.38 4.121-.91.412a1 1 0 0 1 1.082-1.397zM14 7h1zm-9.879 6.38-.985.171a1 1 0 0 1 1.397-1.082zM20 12h1zm-6.62-7.879.912-.412A8 8 0 0 1 15 7h-2c0-.88-.19-1.715-.53-2.467zM14 7h1a8 8 0 0 1-8 8v-2a6 6 0 0 0 6-6zm-7 7v1a8 8 0 0 1-3.29-.709l.411-.911.412-.911A6 6 0 0 0 7 13zm-2.879-.62.985-.171A7 7 0 0 0 12 19v2c-4.443 0-8.128-3.218-8.864-7.449zM12 20v-1a7 7 0 0 0 7-7h2a9 9 0 0 1-9 9zm8-8h-1a7 7 0 0 0-5.79-6.894l.17-.985.172-.985C17.782 3.872 21 7.558 21 12z").toNodes(),
            pathFillType = PathFillType.NonZero,
            fill = SolidColor(Color.Black),
            stroke = null,
        )
    }.build()
}
