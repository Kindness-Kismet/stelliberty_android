package com.stelliberty.android.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.stelliberty.android.R
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.window.WindowDialog

// 留空确定写回 null（使用默认值），与 PC 一致不区分空列表；右上角清除只清空输入，提交仍走确定。
@Composable
fun ListEditDialog(
    show: Boolean,
    title: String,
    textState: TextFieldState,
    onDismiss: () -> Unit,
    onConfirm: (List<String>?) -> Unit,
) {
    WindowDialog(
        show = show,
        onDismissRequest = onDismiss,
    ) {
        DialogHeader(title = title, summary = stringResource(R.string.override_empty_hint)) {
            DialogClearAction(onClick = textState::clearText)
        }
        TextField(
            state = textState,
            modifier = Modifier.fillMaxWidth(),
            label = stringResource(R.string.network_one_per_line),
        )
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TextButton(
                text = stringResource(R.string.common_cancel),
                modifier = Modifier.weight(1f),
                onClick = onDismiss,
            )
            TextButton(
                text = stringResource(R.string.common_confirm),
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.textButtonColorsPrimary(),
                onClick = {
                    val list = textState.text.lines().map { it.trim() }.filter { it.isNotEmpty() }
                    onConfirm(list.ifEmpty { null })
                    onDismiss()
                },
            )
        }
    }
}
