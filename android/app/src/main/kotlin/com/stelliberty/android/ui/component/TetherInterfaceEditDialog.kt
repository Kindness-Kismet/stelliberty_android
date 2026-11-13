package com.stelliberty.android.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import com.stelliberty.android.R
import com.stelliberty.android.platform.scanTetherInterfacesAsRoot
import com.stelliberty.android.platform.showToast
import com.stelliberty.android.ui.icon.AppIcons
import com.stelliberty.android.ui.util.TestTags
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Checkbox
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.squircle.squircleClip
import top.yukonga.miuix.kmp.window.WindowDialog

@Composable
fun TetherInterfaceEditDialog(
    show: Boolean,
    title: String,
    initialValue: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    val textState = rememberTextFieldState()
    val scanningMsg = stringResource(R.string.root_tether_scan_scanning)
    val emptyMsg = stringResource(R.string.root_tether_scan_empty)
    val scope = rememberCoroutineScope()

    var showScanDialog by remember { mutableStateOf(false) }
    var scanResult by remember { mutableStateOf<ImmutableList<String>>(persistentListOf()) }
    val selectedMap = remember { mutableStateMapOf<String, Boolean>() }
    var scanJob by remember { mutableStateOf<Job?>(null) }
    fun dismiss() {
        scanJob?.cancel()
        showScanDialog = false
        onDismiss()
    }
    fun scan() {
        scanJob?.cancel()
        scanJob = scope.launch {
            showToast(scanningMsg)
            val found = withContext(Dispatchers.IO) { scanTetherInterfacesAsRoot() }
            if (found.isEmpty()) {
                showToast(emptyMsg)
            } else {
                scanResult = found.toPersistentList()
                selectedMap.clear()
                found.forEach { selectedMap[it] = isLikelyTetherInterface(it) }
                showScanDialog = true
            }
        }
    }

    LaunchedEffect(show, initialValue) {
        if (show) {
            textState.setTextAndPlaceCursorAtEnd(
                initialValue.split(',').map { it.trim() }.filter { it.isNotEmpty() }.joinToString("\n")
            )
        }
    }

    WindowDialog(
        modifier = Modifier.dialogTestTag(TestTags.Dialog.TETHER_EDITOR),
        show = show,
        onDismissRequest = ::dismiss,
    ) {
        Column(Modifier.heightIn(max = 500.dp)) {
            DialogHeader(title = title, summary = stringResource(R.string.root_tether_ifaces_hint)) {
                IconButton(onClick = ::scan, modifier = Modifier.size(40.dp).testTag(TestTags.Dialog.SCAN_INTERFACES)) {
                    Icon(
                        imageVector = AppIcons.Search,
                        contentDescription = stringResource(R.string.root_tether_scan),
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
            TextField(
                state = textState,
                modifier = Modifier.fillMaxWidth().weight(1f, fill = false).testTag(TestTags.Dialog.INPUT),
            )
            Spacer(Modifier.height(12.dp))
            DialogButtons(
                onCancel = ::dismiss,
                onConfirm = {
                    val csv = textState.text.toString()
                        .lines()
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }
                        .distinct()
                        .joinToString(",")
                    onConfirm(csv)
                    dismiss()
                },
            )
        }
    }

    ScanResultDialog(
        show = showScanDialog,
        candidates = scanResult,
        selected = selectedMap,
        onDismiss = { showScanDialog = false },
        onApply = { picked ->
            textState.setTextAndPlaceCursorAtEnd(picked.joinToString("\n"))
            showScanDialog = false
        },
    )
}

@Composable
private fun ScanResultDialog(
    show: Boolean,
    candidates: ImmutableList<String>,
    selected: SnapshotStateMap<String, Boolean>,
    onDismiss: () -> Unit,
    onApply: (List<String>) -> Unit,
) {
    WindowDialog(
        modifier = Modifier.dialogTestTag(TestTags.Dialog.TETHER_SCAN),
        show = show,
        title = stringResource(R.string.root_tether_scan_result_title),
        onDismissRequest = onDismiss,
    ) {
        Column(Modifier.heightIn(max = 500.dp)) {
            Card(modifier = Modifier.fillMaxWidth().weight(1f, fill = false)) {
                LazyColumn {
                    items(candidates) { iface ->
                        val checked = selected[iface] == true
                        BasicComponent(
                            title = iface,
                            modifier = Modifier.squircleClip(16.dp).testTag(TestTags.Dialog.tetherInterface(iface)),
                            endActions = {
                                Checkbox(
                                    state = if (checked) ToggleableState.On else ToggleableState.Off,
                                    onClick = { selected[iface] = !checked },
                                )
                            },
                            onClick = { selected[iface] = !checked },
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            DialogButtons(
                onCancel = onDismiss,
                onConfirm = {
                    val picked = candidates.filter { selected[it] == true }
                    onApply(picked)
                },
            )
        }
    }
}

@Composable
fun tetherInterfaceSummary(value: String): String {
    val items = value.split(',').map { it.trim() }.filter { it.isNotEmpty() }
    return if (items.isEmpty()) "—" else items.joinToString(", ")
}

private fun isLikelyTetherInterface(name: String): Boolean {
    if (name == "wlan0") return false
    if (name.startsWith("ap")) return true
    if (name == "wlan0_AP") return true
    if (name.startsWith("wlan") && name.length > 4 && name[4].isDigit() && name[4] != '0') return true
    if (name.startsWith("swlan")) return true
    if (name.startsWith("rndis")) return true
    if (name.startsWith("usb")) return true
    if (name == "bt-pan") return true
    return false
}
