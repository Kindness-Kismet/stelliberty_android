package com.stelliberty.android.ui.screen.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stelliberty.android.R
import com.stelliberty.android.data.backup.BackupManager
import com.stelliberty.android.data.backup.RestoreMode
import com.stelliberty.android.platform.FilePicker
import com.stelliberty.android.platform.PlatformStorage
import com.stelliberty.android.platform.StorageKeys
import com.stelliberty.android.ui.component.AdaptiveTopAppBar
import com.stelliberty.android.ui.component.CardItem
import com.stelliberty.android.ui.component.DialogButtons
import com.stelliberty.android.ui.component.blur.BlurredBar
import com.stelliberty.android.ui.component.blur.rememberBlurBackdrop
import com.stelliberty.android.ui.component.dialogTestTag
import com.stelliberty.android.ui.component.groupedCardItems
import com.stelliberty.android.ui.icon.AppIcons
import com.stelliberty.android.ui.util.TestTags
import com.stelliberty.android.ui.util.horizontalCutoutPadding
import com.stelliberty.android.viewmodel.BackupViewModel
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.RadioButtonLocation
import top.yukonga.miuix.kmp.preference.RadioButtonPreference
import top.yukonga.miuix.kmp.squircle.squircleClip
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import top.yukonga.miuix.kmp.window.WindowDialog

@Composable
fun DataManagementScreen(
    viewModel: BackupViewModel,
    storage: PlatformStorage,
    filePicker: FilePicker? = null,
    onRestartApp: () -> Unit = {},
    onBack: () -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scrollBehavior = MiuixScrollBehavior()
    var showLocalDialog by remember { mutableStateOf(false) }
    var showWebDavDialog by remember { mutableStateOf(false) }
    var pendingRestoreAction by remember { mutableStateOf<((RestoreMode) -> Unit)?>(null) }

    val backdrop = rememberBlurBackdrop()
    val blurActive = backdrop != null
    val barColor = if (blurActive) Color.Transparent else MiuixTheme.colorScheme.surface

    Scaffold(
        topBar = {
            BlurredBar(backdrop = backdrop, blurActive = blurActive) {
                AdaptiveTopAppBar(
                    title = stringResource(R.string.data_management_title),
                    color = barColor,
                    scrollBehavior = scrollBehavior,
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            val layoutDirection = LocalLayoutDirection.current
                            Icon(
                                imageVector = AppIcons.Back,
                                contentDescription = stringResource(R.string.common_back),
                                tint = MiuixTheme.colorScheme.onSurface,
                                modifier = Modifier.graphicsLayer {
                                    scaleX = if (layoutDirection == LayoutDirection.Rtl) -1f else 1f
                                },
                            )
                        }
                    },
                )
            }
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .horizontalCutoutPadding()
                .then(if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier)
                .scrollEndHaptic()
                .overScrollVertical()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = PaddingValues(
                top = innerPadding.calculateTopPadding(),
            ),
        ) {
            item(key = "top_spacer") {
                Spacer(Modifier.height(12.dp))
            }
            item(key = "hint") {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .padding(bottom = 12.dp),
                    insideMargin = PaddingValues(16.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            imageVector = AppIcons.Info,
                            contentDescription = null,
                            tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                        Text(
                            text = stringResource(R.string.backup_hint),
                            fontSize = 14.sp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    }
                }
            }
            groupedCardItems(
                keyPrefix = "backup_entries",
                outerBottomPadding = 12.dp,
                items = listOf(
                    CardItem("local") {
                        ArrowPreference(
                            title = stringResource(R.string.backup_section_local),
                            summary = stringResource(R.string.backup_local_row_summary),
                            enabled = filePicker != null && !uiState.isBusy,
                            holdDownState = showLocalDialog,
                            onClick = { showLocalDialog = true },
                        )
                    },
                    CardItem("webdav") {
                        ArrowPreference(
                            title = stringResource(R.string.backup_section_webdav),
                            summary = stringResource(R.string.backup_webdav_row_summary),
                            enabled = !uiState.isBusy,
                            holdDownState = showWebDavDialog,
                            onClick = { showWebDavDialog = true },
                        )
                    },
                ),
            )
            item(key = "bottom_spacer") {
                Spacer(
                    Modifier
                        .height(24.dp)
                        .navigationBarsPadding()
                )
            }
        }
    }

    if (filePicker != null) {
        LocalBackupDialog(
            show = showLocalDialog,
            isBusy = uiState.isBusy,
            onDismiss = { showLocalDialog = false },
            onExport = {
                showLocalDialog = false
                viewModel.exportBackup { onResult ->
                    // 按 zip 类型保存会让文档提供方给 .stelliberty 文件名补上 .zip。
                    filePicker.createDocument(BackupManager.newBackupFileName(), "application/octet-stream", onResult)
                }
            },
            onRestore = {
                showLocalDialog = false
                pendingRestoreAction = { mode ->
                    filePicker.pickZipDocument { uri ->
                        if (uri != null) viewModel.restoreFromDocument(uri, mode)
                    }
                }
            },
        )
    }

    WebDavDialog(
        show = showWebDavDialog,
        isBusy = uiState.isBusy,
        storage = storage,
        onDismiss = { showWebDavDialog = false },
        onTest = { viewModel.testConnection() },
        onBackup = { viewModel.backup() },
        onRestore = {
            showWebDavDialog = false
            pendingRestoreAction = { mode -> viewModel.restore(mode) }
        },
    )

    RestoreModeDialog(
        show = pendingRestoreAction != null,
        onDismiss = { pendingRestoreAction = null },
        onSelect = { mode ->
            val action = pendingRestoreAction
            pendingRestoreAction = null
            action?.invoke(mode)
        },
    )

    LaunchedEffect(uiState.restoreCompleted) {
        if (uiState.restoreCompleted) onRestartApp()
    }
}

@Composable
private fun RestoreModeDialog(
    show: Boolean,
    onDismiss: () -> Unit,
    onSelect: (RestoreMode) -> Unit,
) {
    WindowDialog(
        modifier = Modifier.dialogTestTag(TestTags.Dialog.BACKUP_RESTORE_MODE),
        show = show,
        title = stringResource(R.string.backup_restore_confirm_title),
        onDismissRequest = onDismiss,
    ) {
        Card(Modifier.fillMaxWidth()) {
            ArrowPreference(
                title = stringResource(R.string.backup_restore_merge),
                summary = stringResource(R.string.backup_restore_merge_summary),
                onClick = { onSelect(RestoreMode.Merge) },
                modifier = Modifier.squircleClip(16.dp).testTag(TestTags.Backup.RESTORE_MERGE),
            )
            ArrowPreference(
                title = stringResource(R.string.backup_restore_overwrite),
                summary = stringResource(R.string.backup_restore_overwrite_summary),
                onClick = { onSelect(RestoreMode.Overwrite) },
                modifier = Modifier.squircleClip(16.dp).testTag(TestTags.Backup.RESTORE_OVERWRITE),
            )
        }
        Spacer(Modifier.height(12.dp))
        DialogButtons(onCancel = onDismiss)
    }
}

@Composable
private fun LocalBackupDialog(
    show: Boolean,
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onExport: () -> Unit,
    onRestore: () -> Unit,
) {
    WindowDialog(
        modifier = Modifier.dialogTestTag(TestTags.Dialog.BACKUP_LOCAL),
        show = show,
        title = stringResource(R.string.backup_section_local),
        onDismissRequest = onDismiss,
    ) {
        Card(Modifier.fillMaxWidth()) {
            ArrowPreference(
                title = stringResource(R.string.backup_export_local),
                startAction = {
                    Icon(
                        imageVector = AppIcons.Backup,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 16.dp),
                        tint = MiuixTheme.colorScheme.onSurface,
                    )
                },
                enabled = !isBusy,
                onClick = onExport,
                modifier = Modifier.squircleClip(16.dp).testTag(TestTags.Backup.EXPORT_LOCAL),
            )
            ArrowPreference(
                title = stringResource(R.string.backup_restore_local),
                startAction = {
                    Icon(
                        imageVector = AppIcons.Restore,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 16.dp),
                        tint = MiuixTheme.colorScheme.onSurface,
                    )
                },
                enabled = !isBusy,
                onClick = onRestore,
                modifier = Modifier.squircleClip(16.dp).testTag(TestTags.Backup.RESTORE_LOCAL),
            )
        }
        Spacer(Modifier.height(12.dp))
        DialogButtons(onCancel = onDismiss)
    }
}

@Composable
private fun WebDavDialog(
    show: Boolean,
    isBusy: Boolean,
    storage: PlatformStorage,
    onDismiss: () -> Unit,
    onTest: () -> Unit,
    onBackup: () -> Unit,
    onRestore: () -> Unit,
) {
    var url by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var action by remember { mutableStateOf<WebDavAction?>(null) }
    LaunchedEffect(show) {
        if (show) {
            url = storage.getString(StorageKeys.WEBDAV_URL, "")
            username = storage.getString(StorageKeys.WEBDAV_USERNAME, "")
            password = storage.getString(StorageKeys.WEBDAV_PASSWORD, "")
            action = null
        }
    }

    WindowDialog(
        modifier = Modifier.dialogTestTag(TestTags.Dialog.BACKUP_WEBDAV),
        show = show,
        title = stringResource(R.string.backup_section_webdav),
        onDismissRequest = onDismiss,
    ) {
        Column(Modifier.heightIn(max = 500.dp)) {
            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState()),
            ) {
                TextField(
                    value = url,
                    onValueChange = { url = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                        .testTag(TestTags.Backup.WEBDAV_URL),
                    label = stringResource(R.string.backup_server_url),
                )
                TextField(
                    value = username,
                    onValueChange = { username = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                        .testTag(TestTags.Backup.WEBDAV_USERNAME),
                    label = stringResource(R.string.backup_username),
                )
                TextField(
                    value = password,
                    onValueChange = { password = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                        .testTag(TestTags.Backup.WEBDAV_PASSWORD),
                    label = stringResource(R.string.backup_password),
                )
                Card(Modifier.fillMaxWidth()) {
                    WebDavAction.entries.forEach { item ->
                        RadioButtonPreference(
                            title = stringResource(item.titleRes),
                            selected = action == item,
                            radioButtonLocation = RadioButtonLocation.End,
                            enabled = !isBusy,
                            modifier = Modifier.squircleClip(16.dp).testTag(TestTags.Backup.webDavAction(item.name)),
                            onClick = { action = if (action == item) null else item },
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            DialogButtons(
                onCancel = onDismiss,
                confirmEnabled = !isBusy,
                onConfirm = {
                    storage.putString(StorageKeys.WEBDAV_URL, url)
                    storage.putString(StorageKeys.WEBDAV_USERNAME, username)
                    storage.putString(StorageKeys.WEBDAV_PASSWORD, password)
                    when (action) {
                        WebDavAction.Test -> onTest()
                        WebDavAction.Backup -> onBackup()
                        WebDavAction.Restore -> onRestore()
                        null -> Unit
                    }
                    onDismiss()
                },
            )
        }
    }
}

private enum class WebDavAction(val titleRes: Int) {
    Test(R.string.backup_test_connection),
    Backup(R.string.backup_now),
    Restore(R.string.backup_restore_now),
}
