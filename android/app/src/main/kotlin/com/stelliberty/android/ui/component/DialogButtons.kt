package com.stelliberty.android.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.dp
import com.stelliberty.android.BuildConfig
import com.stelliberty.android.R
import com.stelliberty.android.ui.util.TestTags
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.TextButton

fun Modifier.dialogTestTag(tag: String): Modifier =
    semantics { testTagsAsResourceId = BuildConfig.DEBUG }.testTag(tag)

@Composable
fun DialogButtons(
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    onConfirm: (() -> Unit)? = null,
    confirmEnabled: Boolean = true,
    cancelTestTag: String = TestTags.Dialog.CANCEL,
    confirmTestTag: String = TestTags.Dialog.CONFIRM,
) {
    Row(
        modifier = modifier.fillMaxWidth().semantics { testTagsAsResourceId = BuildConfig.DEBUG },
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TextButton(
            text = stringResource(R.string.common_cancel),
            modifier = Modifier.weight(1f).testTag(cancelTestTag),
            colors = ButtonDefaults.textButtonColors(),
            onClick = onCancel,
        )
        if (onConfirm != null) {
            TextButton(
                text = stringResource(R.string.common_confirm),
                modifier = Modifier.weight(1f).testTag(confirmTestTag),
                colors = ButtonDefaults.textButtonColorsPrimary(),
                enabled = confirmEnabled,
                onClick = onConfirm,
            )
        }
    }
}
