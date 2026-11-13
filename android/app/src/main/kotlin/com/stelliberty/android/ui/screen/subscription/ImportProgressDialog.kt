package com.stelliberty.android.ui.screen.subscription

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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stelliberty.android.BuildConfig
import com.stelliberty.android.R
import com.stelliberty.android.ui.component.DialogButtons
import com.stelliberty.android.ui.util.TestTags
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog

@Composable
fun ImportProgressDialog(
    show: Boolean,
    step: String,
    title: String = stringResource(R.string.subscription_import_config),
    onCancel: () -> Unit,
) {
    WindowDialog(
        show = show,
        modifier = Modifier
            .semantics { testTagsAsResourceId = BuildConfig.DEBUG }
            .testTag(TestTags.Subscription.PROGRESS),
        title = title,
        onDismissRequest = null,
        content = {
            Column(Modifier.heightIn(max = 500.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                ) {
                    CircularProgressIndicator()
                    Spacer(Modifier.width(16.dp))
                    Text(
                        text = step,
                        fontSize = 15.sp,
                        color = MiuixTheme.colorScheme.onSurface,
                    )
                }
                Spacer(Modifier.height(12.dp))
                DialogButtons(onCancel = onCancel, cancelTestTag = TestTags.Subscription.CANCEL_UPDATE)
            }
        },
    )
}
