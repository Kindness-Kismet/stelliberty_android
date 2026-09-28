package com.stelliberty.android.ui.screen.subscription

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stelliberty.android.R
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog

@Composable
fun ImportProgressDialog(
    show: Boolean,
    step: String,
    title: String = stringResource(R.string.subscription_import_config),
    onCancel: (() -> Unit)? = null,
) {
    WindowDialog(
        show = show,
        title = title,
        onDismissRequest = null,
        content = {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator()
                    Spacer(Modifier.width(16.dp))
                    Text(
                        text = step,
                        fontSize = 15.sp,
                        color = MiuixTheme.colorScheme.onSurface,
                    )
                }
                if (onCancel != null) {
                    Spacer(Modifier.height(16.dp))
                    TextButton(
                        text = stringResource(R.string.common_cancel),
                        onClick = onCancel,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
    )
}
