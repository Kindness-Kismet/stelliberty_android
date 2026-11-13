package com.stelliberty.android.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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

// 原生标题没有动作槽，附加功能与标题共同测量，避免文字放大后重叠。
@Composable
fun DialogHeader(
    title: String,
    summary: String? = null,
    action: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = PreferenceLayout.ActionButtonSize).padding(bottom = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            color = DialogDefaults.titleColor(),
            fontSize = MiuixTheme.textStyles.title4.fontSize,
            fontWeight = FontWeight.Medium,
        )
        action()
    }
    if (summary != null) {
        Text(
            text = summary,
            modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
            color = DialogDefaults.summaryColor(),
            fontSize = MiuixTheme.textStyles.body1.fontSize,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
fun DialogClearAction(onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(PreferenceLayout.ActionButtonSize)) {
        Icon(
            imageVector = AppIcons.Delete,
            contentDescription = stringResource(R.string.common_clear),
            modifier = Modifier.size(PreferenceLayout.IconSize),
            tint = MiuixTheme.colorScheme.onSurface,
        )
    }
}
