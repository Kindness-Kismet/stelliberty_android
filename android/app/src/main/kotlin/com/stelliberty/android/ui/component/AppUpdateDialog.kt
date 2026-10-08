package com.stelliberty.android.ui.component

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stelliberty.android.BuildConfig
import com.stelliberty.android.R
import com.stelliberty.android.domain.model.AppUpdateResult
import com.stelliberty.android.ui.util.TestTags
import com.stelliberty.android.viewmodel.AppUpdateViewModel
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.window.WindowDialog

@Composable
fun AppUpdateDialog(viewModel: AppUpdateViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val info = (state.result as? AppUpdateResult.Available)?.info
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val language = LocalConfiguration.current.locales[0].language
    val notes = remember(info?.releaseNotes, language) {
        localizedReleaseNotes(info?.releaseNotes.orEmpty(), language)
    }

    LaunchedEffect(viewModel) { viewModel.checkOnStartup() }

    WindowDialog(
        show = state.showDialog && info != null,
        modifier = Modifier
            .semantics { testTagsAsResourceId = BuildConfig.DEBUG }
            .testTag(TestTags.About.UPDATE_DIALOG),
        title = stringResource(R.string.app_update_dialog_title, info?.latestVersion.orEmpty()),
        onDismissRequest = viewModel::dismissUpdate,
    ) {
        Column(Modifier.heightIn(max = 500.dp)) {
            Text(
                text = notes.ifBlank { stringResource(R.string.app_update_no_notes) },
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .testTag(TestTags.About.RELEASE_NOTES),
            )
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    text = stringResource(R.string.app_update_not_now),
                    modifier = Modifier.weight(1f).testTag(TestTags.About.UPDATE_DISMISS),
                    onClick = viewModel::dismissUpdate,
                )
                TextButton(
                    text = stringResource(R.string.app_update_download),
                    modifier = Modifier.weight(1f).testTag(TestTags.About.UPDATE_DOWNLOAD),
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                    onClick = {
                        if (info != null) {
                            try {
                                uriHandler.openUri(info.releaseUrl)
                                viewModel.dismissUpdate()
                            } catch (_: IllegalArgumentException) {
                                Toast.makeText(context, R.string.app_update_open_failed, Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                )
            }
        }
    }
}

private fun localizedReleaseNotes(body: String, language: String): String {
    // 发布日志固定为英文、分隔线、中文；测试版的单语日志保留原文。
    val sections = body.split(Regex("""(?m)^---[ \t]*\r?$"""), limit = 2)
    val notes = if (language == "zh") sections.last() else sections.first()
    return notes.trim().lineSequence().joinToString("\n") { line ->
        val text = line.replace(Regex("""\[([^]]+)]\([^)]+\)"""), "$1").replace("`", "")
        when {
            text.startsWith("- ") -> "• ${text.removePrefix("- ")}"
            text.startsWith("## ") -> text.removePrefix("## ")
            else -> text
        }
    }
}
