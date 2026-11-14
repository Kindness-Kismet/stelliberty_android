package com.stelliberty.android.ui.screen.provider

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stelliberty.android.R
import com.stelliberty.android.ui.component.DialogButtons
import com.stelliberty.android.ui.component.dialogTestTag
import com.stelliberty.android.ui.util.TestTags
import com.stelliberty.android.viewmodel.RefreshProgress
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog

@Composable
fun ProviderRefreshDialog(
    show: Boolean,
    progress: RefreshProgress?,
    onCancel: () -> Unit,
) {
    WindowDialog(
        modifier = Modifier.dialogTestTag(TestTags.Dialog.PROVIDER_PROGRESS),
        show = show,
        title = stringResource(R.string.provider_updating_title),
        onDismissRequest = null,
        content = {
            val text = when {
                progress == null -> ""
                progress.singleName != null -> stringResource(
                    R.string.provider_updating_single,
                    progress.singleName,
                )

                else -> stringResource(
                    R.string.provider_updating_progress,
                    progress.completed,
                    progress.total,
                )
            }
            Column(Modifier.heightIn(max = 500.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                ) {
                    CircularProgressIndicator()
                    Spacer(Modifier.width(16.dp))
                    Text(
                        text = text,
                        fontSize = 15.sp,
                        color = MiuixTheme.colorScheme.onSurface,
                    )
                }
                Spacer(Modifier.height(12.dp))
                DialogButtons(onCancel = onCancel)
            }
        },
    )
}
