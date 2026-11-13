package com.stelliberty.android.ui.component

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.stelliberty.android.R
import com.stelliberty.android.ui.icon.AppIcons
import com.stelliberty.android.ui.util.TestTags
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun BackButton(onClick: () -> Unit) {
    val layoutDirection = LocalLayoutDirection.current
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(40.dp).testTag(TestTags.Nav.BACK),
    ) {
        Icon(
            imageVector = AppIcons.Back,
            contentDescription = stringResource(R.string.common_back),
            tint = MiuixTheme.colorScheme.onSurface,
            modifier = Modifier.size(24.dp).graphicsLayer {
                scaleX = if (layoutDirection == LayoutDirection.Rtl) -1f else 1f
            },
        )
    }
}
