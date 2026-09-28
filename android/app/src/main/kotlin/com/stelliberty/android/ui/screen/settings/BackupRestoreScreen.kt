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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.DpSize
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
import com.stelliberty.android.ui.component.blur.BlurredBar
import com.stelliberty.android.ui.component.blur.rememberBlurBackdrop
import com.stelliberty.android.ui.component.groupedCardItems
import com.stelliberty.android.ui.icon.AppIcons
import com.stelliberty.android.ui.util.horizontalCutoutPadding
import com.stelliberty.android.viewmodel.BackupViewModel
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import top.yukonga.miuix.kmp.window.WindowDialog

@Composable
fun BackupRestoreScreen(
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
                    title = stringResource(R.string.backup_title),
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
                    filePicker.createZipDocument(BackupManager.newBackupFileName(), onResult)
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

    WindowDialog(
        show = uiState.restoreCompleted,
        title = stringResource(R.string.backup_restore_done_title),
        summary = stringResource(R.string.backup_restore_done_summary),
        onDismissRequest = {},
    ) {
        TextButton(
            text = stringResource(R.string.backup_restart_now),
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.textButtonColorsPrimary(),
            onClick = onRestartApp,
        )
    }
}

@Composable
private fun RestoreModeDialog(
    show: Boolean,
    onDismiss: () -> Unit,
    onSelect: (RestoreMode) -> Unit,
) {
    WindowDialog(
        show = show,
        title = stringResource(R.string.backup_restore_confirm_title),
        insideMargin = DpSize(0.dp, 24.dp),
        onDismissRequest = onDismiss,
    ) {
        ArrowPreference(
            title = stringResource(R.string.backup_restore_merge),
            summary = stringResource(R.string.backup_restore_merge_summary),
            onClick = { onSelect(RestoreMode.Merge) },
            insideMargin = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
        )
        ArrowPreference(
            title = stringResource(R.string.backup_restore_overwrite),
            summary = stringResource(R.string.backup_restore_overwrite_summary),
            onClick = { onSelect(RestoreMode.Overwrite) },
            insideMargin = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
        )
        TextButton(
            text = stringResource(R.string.common_cancel),
            onClick = onDismiss,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp)
                .padding(horizontal = 24.dp),
        )
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
        show = show,
        title = stringResource(R.string.backup_section_local),
        insideMargin = DpSize(0.dp, 24.dp),
        onDismissRequest = onDismiss,
    ) {
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
            insideMargin = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
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
            insideMargin = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
        )
        TextButton(
            text = stringResource(R.string.common_cancel),
            onClick = onDismiss,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp)
                .padding(horizontal = 24.dp),
        )
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
    var url by remember { mutableStateOf(storage.getString(StorageKeys.WEBDAV_URL, "")) }
    var username by remember { mutableStateOf(storage.getString(StorageKeys.WEBDAV_USERNAME, "")) }
    var password by remember { mutableStateOf(storage.getString(StorageKeys.WEBDAV_PASSWORD, "")) }

    WindowDialog(
        show = show,
        title = stringResource(R.string.backup_section_webdav),
        insideMargin = DpSize(0.dp, 24.dp),
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
                    onValueChange = {
                        url = it
                        storage.putString(StorageKeys.WEBDAV_URL, it)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .padding(bottom = 8.dp),
                    label = stringResource(R.string.backup_server_url),
                )
                TextField(
                    value = username,
                    onValueChange = {
                        username = it
                        storage.putString(StorageKeys.WEBDAV_USERNAME, it)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .padding(bottom = 8.dp),
                    label = stringResource(R.string.backup_username),
                )
                TextField(
                    value = password,
                    onValueChange = {
                        password = it
                        storage.putString(StorageKeys.WEBDAV_PASSWORD, it)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .padding(bottom = 12.dp),
                    label = stringResource(R.string.backup_password),
                )
                ArrowPreference(
                    title = stringResource(R.string.backup_now),
                    startAction = {
                        Icon(
                            imageVector = AppIcons.Backup,
                            contentDescription = null,
                            modifier = Modifier.padding(end = 16.dp),
                            tint = MiuixTheme.colorScheme.onSurface,
                        )
                    },
                    enabled = !isBusy,
                    onClick = onBackup,
                    insideMargin = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
                )
                ArrowPreference(
                    title = stringResource(R.string.backup_restore_now),
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
                    insideMargin = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
                    .padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TextButton(
                    text = stringResource(R.string.backup_test_connection),
                    modifier = Modifier.weight(1f),
                    enabled = !isBusy,
                    onClick = onTest,
                )
                TextButton(
                    text = stringResource(R.string.common_close),
                    modifier = Modifier.weight(1f),
                    onClick = onDismiss,
                )
            }
        }
    }
}
