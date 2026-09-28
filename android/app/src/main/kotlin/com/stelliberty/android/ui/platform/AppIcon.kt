package com.stelliberty.android.ui.platform

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.stelliberty.android.util.AppIconCache
import top.yukonga.miuix.kmp.squircle.squircleBackground
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun AppIcon(
    packageName: String,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
) {
    val context = LocalContext.current
    val sizePx = with(LocalDensity.current) { size.roundToPx() }

    val cached = remember(packageName) { AppIconCache.getFromCache(packageName) }
    var bitmap by remember(packageName) { mutableStateOf(cached) }

    if (cached == null) {
        LaunchedEffect(packageName) {
            // 取不到图标多半是应用刚被卸载，保持占位方块即可。
            runCatching { bitmap = AppIconCache.loadIcon(context, packageName, sizePx) }
        }
    }

    val image = remember(bitmap) { bitmap?.asImageBitmap() }

    Box(modifier = modifier.size(size)) {
        if (cached != null) {
            IconOrPlaceholder(image, size)
        } else {
            Crossfade(
                targetState = image,
                animationSpec = tween(durationMillis = 150),
                label = "AppIconFade",
            ) { icon -> IconOrPlaceholder(icon, size) }
        }
    }
}

@Composable
private fun IconOrPlaceholder(image: ImageBitmap?, size: Dp) {
    if (image != null) {
        Image(
            bitmap = image,
            contentDescription = null,
            modifier = Modifier.size(size),
        )
    } else {
        Box(
            modifier = Modifier
                .size(size)
                .squircleBackground(MiuixTheme.colorScheme.secondaryContainer, 8.dp),
        )
    }
}
