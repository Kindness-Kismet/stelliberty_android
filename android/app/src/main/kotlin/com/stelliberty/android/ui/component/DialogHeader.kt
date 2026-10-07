package com.stelliberty.android.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.stelliberty.android.R
import com.stelliberty.android.ui.icon.AppIcons
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.layout.DialogDefaults
import top.yukonga.miuix.kmp.theme.MiuixTheme

// miuix WindowDialog 的标题区没有操作位：需要右上角操作的弹窗不传 title / summary，
// 改在内容首行放本组件，标题与说明按原生样式重画（title4 Medium / body1，居中，下距 12dp）。
@Composable
fun DialogHeader(
    title: String,
    summary: String? = null,
    action: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
    ) {
        Text(
            text = title,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 40.dp),
            color = DialogDefaults.titleColor(),
            fontSize = MiuixTheme.textStyles.title4.fontSize,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
        )
        // 操作按钮不参与测量，标题行与没有操作的弹窗等高。
        Box(
            modifier = Modifier.matchParentSize(),
            contentAlignment = Alignment.CenterEnd,
        ) {
            Box(Modifier.wrapContentHeight(unbounded = true)) { action() }
        }
    }
    if (summary != null) {
        Text(
            text = summary,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            color = DialogDefaults.summaryColor(),
            fontSize = MiuixTheme.textStyles.body1.fontSize,
            textAlign = TextAlign.Center,
        )
    }
}

// 弹窗内的清除类操作统一用这个按钮，图标与页面顶栏的清除操作一致。
@Composable
fun DialogClearAction(onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(
            imageVector = AppIcons.Delete,
            contentDescription = stringResource(R.string.common_clear),
            tint = MiuixTheme.colorScheme.onSurface,
        )
    }
}
