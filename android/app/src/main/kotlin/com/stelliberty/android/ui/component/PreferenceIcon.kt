package com.stelliberty.android.ui.component

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun PreferenceIcon(imageVector: ImageVector) {
    Icon(
        imageVector = imageVector,
        contentDescription = null,
        tint = MiuixTheme.colorScheme.onSurface,
        modifier = Modifier.padding(end = PreferenceLayout.IconEndSpacing).size(PreferenceLayout.IconSize),
    )
}
