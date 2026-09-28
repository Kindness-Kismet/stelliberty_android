package com.stelliberty.android.viewmodel

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stelliberty.android.R
import com.stelliberty.android.data.backup.BackupManager
import com.stelliberty.android.data.backup.RestoreMode
import com.stelliberty.android.data.backup.WebDavClient
import com.stelliberty.android.platform.PlatformStorage
import com.stelliberty.android.platform.ProxyServiceBridge
import com.stelliberty.android.platform.ProxyState
import com.stelliberty.android.platform.StorageKeys
import com.stelliberty.android.platform.showToast
import com.stelliberty.android.util.AppLogger
import com.stelliberty.android.util.describe
import kotlin.coroutines.resume
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine

@Immutable
data class BackupUiState(
    val isBusy: Boolean = false,
    val restoreCompleted: Boolean = false,
)

class BackupViewModel(
    private val backupManager: BackupManager,
    private val storage: PlatformStorage,
    private val context: Context,
) : ViewModel() {

    private val _uiState = MutableStateFlow(BackupUiState())
    val uiState: StateFlow<BackupUiState> = _uiState.asStateFlow()

    private fun client(): WebDavClient? {
        val url = storage.getString(StorageKeys.WEBDAV_URL, "").trim()
        val user = storage.getString(StorageKeys.WEBDAV_USERNAME, "")
        val pass = storage.getString(StorageKeys.WEBDAV_PASSWORD, "")
        if (url.isEmpty() || user.isEmpty() || pass.isEmpty()) {
            showToast(context.getString(R.string.backup_missing_credentials))
            return null
        }
        return WebDavClient(url, user, pass)
    }

    fun testConnection() = runBusy {
        val client = client() ?: return@runBusy
        client.testConnection()
        showToast(context.getString(R.string.backup_connection_ok))
    }

    fun backup() = runBusy {
        val client = client() ?: return@runBusy
        backupManager.withTransferFile { file ->
            backupManager.writeBackupTo(file)
            client.upload(file, BackupManager.newBackupFileName())
        }
        showToast(context.getString(R.string.backup_done))
    }

    fun restore(mode: RestoreMode) = runBusy {
        if (!ensureProxyStopped()) return@runBusy
        val client = client() ?: return@runBusy
        backupManager.withTransferFile { file ->
            if (!client.downloadLatest(file)) {
                showToast(context.getString(R.string.backup_not_found))
                return@withTransferFile
            }
            backupManager.restoreBackupFrom(file, mode)
            _uiState.update { it.copy(restoreCompleted = true) }
        }
    }

    fun exportBackup(pickTarget: (onResult: (Uri?) -> Unit) -> Unit) = runBusy {
        val uri = suspendCancellableCoroutine<Uri?> { cont ->
            pickTarget { result -> cont.resume(result) }
        } ?: return@runBusy
        backupManager.exportTo(uri)
        showToast(context.getString(R.string.backup_export_done))
    }

    fun restoreFromDocument(uri: Uri, mode: RestoreMode) = runBusy {
        if (!ensureProxyStopped()) return@runBusy
        backupManager.importFrom(uri, mode)
        _uiState.update { it.copy(restoreCompleted = true) }
    }

    private fun ensureProxyStopped(): Boolean {
        val state = ProxyServiceBridge.state.value.state
        if (state != ProxyState.Stopped && state != ProxyState.Error) {
            showToast(context.getString(R.string.backup_stop_proxy_first))
            return false
        }
        return true
    }

    private fun runBusy(block: suspend () -> Unit) {
        if (_uiState.value.isBusy) return
        _uiState.update { it.copy(isBusy = true) }
        viewModelScope.launch {
            try {
                block()
            } catch (e: Throwable) {
                AppLogger.error(TAG, "Backup operation failed", e)
                showToast(context.getString(R.string.backup_failed, e.describe()), long = true)
            } finally {
                _uiState.update { it.copy(isBusy = false) }
            }
        }
    }

    private companion object {
        const val TAG = "BackupViewModel"
    }
}
