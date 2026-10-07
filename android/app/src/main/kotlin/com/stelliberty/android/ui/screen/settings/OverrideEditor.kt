package com.stelliberty.android.ui.screen.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.stelliberty.android.R
import com.stelliberty.android.ui.component.ListEditDialog
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.window.WindowDialog

internal enum class OverrideEditorKind { Port, Text, List }

// 覆写字段的编辑弹窗同一时刻只开一个，标题、初值与写回函数随入口切换。
@Stable
internal class OverrideEditorState {
    val textState = TextFieldState()
    var kind by mutableStateOf<OverrideEditorKind?>(null)
        private set
    var title by mutableStateOf("")
        private set
    private var portSetter: (Int?) -> Unit = {}
    private var textSetter: (String?) -> Unit = {}
    private var listSetter: (List<String>?) -> Unit = {}

    fun editPort(title: String, value: Int?, setter: (Int?) -> Unit) {
        portSetter = setter
        open(OverrideEditorKind.Port, title, value?.toString())
    }

    fun editText(title: String, value: String?, setter: (String?) -> Unit) {
        textSetter = setter
        open(OverrideEditorKind.Text, title, value)
    }

    fun editList(title: String, value: List<String>?, setter: (List<String>?) -> Unit) {
        listSetter = setter
        open(OverrideEditorKind.List, title, value?.joinToString("\n"))
    }

    fun dismiss() {
        kind = null
    }

    fun applyPort(value: Int?) = portSetter(value)

    fun applyText(value: String?) = textSetter(value)

    fun applyList(value: List<String>?) = listSetter(value)

    private fun open(kind: OverrideEditorKind, title: String, initial: String?) {
        this.title = title
        textState.edit { replace(0, length, initial ?: "") }
        this.kind = kind
    }
}

@Composable
internal fun rememberOverrideEditorState(): OverrideEditorState = remember { OverrideEditorState() }

// 三种弹窗都以留空表示使用默认值，写回 null 后该字段不进入覆写。
@Composable
internal fun OverrideEditorDialogs(state: OverrideEditorState) {
    PortEditDialog(
        show = state.kind == OverrideEditorKind.Port,
        title = state.title,
        textState = state.textState,
        onDismiss = state::dismiss,
        onConfirm = state::applyPort,
    )
    TextEditDialog(
        show = state.kind == OverrideEditorKind.Text,
        title = state.title,
        textState = state.textState,
        onDismiss = state::dismiss,
        onConfirm = state::applyText,
    )
    ListEditDialog(
        show = state.kind == OverrideEditorKind.List,
        title = state.title,
        textState = state.textState,
        onDismiss = state::dismiss,
        onConfirm = state::applyList,
    )
}

// 覆写下拉项：首项「未修改」对应 null，其余 labels 与 values 一一对应。
@Immutable
internal class OverrideChoices(val labels: List<String>, val values: List<String>)

internal val LOG_LEVEL_CHOICES = OverrideChoices(
    labels = listOf("Info", "Warning", "Error", "Debug", "Silent"),
    values = listOf("info", "warning", "error", "debug", "silent"),
)

internal val DNS_ENHANCED_MODE_CHOICES = OverrideChoices(
    labels = listOf("Normal", "FakeIP", "Redir-Host"),
    values = listOf("normal", "fake-ip", "redir-host"),
)

internal val FIND_PROCESS_MODE_CHOICES = OverrideChoices(
    labels = listOf("Off", "Strict", "Always"),
    values = listOf("off", "strict", "always"),
)

@Composable
internal fun OverrideChoicePreference(
    title: String,
    choices: OverrideChoices,
    value: String?,
    onValueChange: (String?) -> Unit,
    summary: String? = null,
    enabled: Boolean = true,
) {
    OverlayDropdownPreference(
        title = title,
        summary = summary,
        items = listOf(stringResource(R.string.common_not_modified)) + choices.labels,
        selectedIndex = choices.values.indexOf(value) + 1,
        onSelectedIndexChange = { index -> onValueChange(choices.values.getOrNull(index - 1)) },
        enabled = enabled,
    )
}

@Composable
internal fun portSummary(port: Int?): String =
    port?.toString() ?: stringResource(R.string.common_not_modified)

@Composable
internal fun listSummary(list: List<String>?): String =
    if (list == null) {
        stringResource(R.string.common_not_modified)
    } else {
        pluralStringResource(R.plurals.common_items_count, list.size, list.size)
    }

@Composable
private fun PortEditDialog(
    show: Boolean,
    title: String,
    textState: TextFieldState,
    onDismiss: () -> Unit,
    onConfirm: (Int?) -> Unit,
) {
    WindowDialog(
        show = show,
        title = title,
        summary = stringResource(R.string.network_port_zero_hint),
        onDismissRequest = onDismiss,
    ) {
        TextField(
            state = textState,
            modifier = Modifier.fillMaxWidth(),
            label = stringResource(R.string.network_port_label),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
        Spacer(Modifier.height(12.dp))
        EditDialogButtons(
            onDismiss = onDismiss,
            onConfirm = {
                val text = textState.text.toString().trim()
                val port = text.toIntOrNull()
                when {
                    text.isEmpty() -> onConfirm(null)
                    port != null && port in 0..65535 -> onConfirm(port)
                }
            },
        )
    }
}

@Composable
private fun TextEditDialog(
    show: Boolean,
    title: String,
    textState: TextFieldState,
    onDismiss: () -> Unit,
    onConfirm: (String?) -> Unit,
) {
    WindowDialog(
        show = show,
        title = title,
        summary = stringResource(R.string.override_empty_hint),
        onDismissRequest = onDismiss,
    ) {
        TextField(
            state = textState,
            modifier = Modifier.fillMaxWidth(),
            label = stringResource(R.string.network_input_value),
        )
        Spacer(Modifier.height(12.dp))
        EditDialogButtons(
            onDismiss = onDismiss,
            onConfirm = { onConfirm(textState.text.toString().trim().ifEmpty { null }) },
        )
    }
}

@Composable
private fun EditDialogButtons(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
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
                onConfirm()
                onDismiss()
            },
        )
    }
}
