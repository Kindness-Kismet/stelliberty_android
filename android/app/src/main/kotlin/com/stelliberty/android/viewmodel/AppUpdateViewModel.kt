package com.stelliberty.android.viewmodel

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stelliberty.android.BuildConfig
import com.stelliberty.android.domain.model.AppUpdateResult
import com.stelliberty.android.domain.model.UpdateChannel
import com.stelliberty.android.domain.repository.AppUpdateRepository
import com.stelliberty.android.platform.PlatformStorage
import com.stelliberty.android.platform.StorageKeys
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Immutable
data class AppUpdateUiState(
    val channel: UpdateChannel = UpdateChannel.STABLE,
    val checkOnStartup: Boolean = false,
    val checking: Boolean = false,
    val result: AppUpdateResult? = null,
    val showDialog: Boolean = false,
)

class AppUpdateViewModel(
    private val repository: AppUpdateRepository,
    private val storage: PlatformStorage,
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        AppUpdateUiState(
            channel = UpdateChannel.entries.firstOrNull {
                it.name == storage.getString(StorageKeys.APP_UPDATE_CHANNEL, UpdateChannel.STABLE.name)
            } ?: UpdateChannel.STABLE,
            checkOnStartup = storage.getString(StorageKeys.APP_UPDATE_ON_STARTUP, "false").toBoolean(),
        ),
    )
    val uiState = _uiState.asStateFlow()

    private var checkJob: Job? = null
    private var startupHandled = false
    private var automaticCheck = false

    fun checkOnStartup() {
        if (startupHandled) return
        startupHandled = true
        if (_uiState.value.checkOnStartup) launchCheck(automatic = true)
    }

    fun checkForUpdates() {
        if (_uiState.value.checking) return
        launchCheck(automatic = false)
    }

    fun setChannel(channel: UpdateChannel) {
        if (_uiState.value.channel == channel) return
        checkJob?.cancel()
        storage.putString(StorageKeys.APP_UPDATE_CHANNEL, channel.name)
        _uiState.update { it.copy(channel = channel, checking = false, result = null, showDialog = false) }
    }

    fun setCheckOnStartup(enabled: Boolean) {
        storage.putString(StorageKeys.APP_UPDATE_ON_STARTUP, enabled.toString())
        if (!enabled && automaticCheck) {
            checkJob?.cancel()
            _uiState.update { it.copy(checking = false, showDialog = false) }
        }
        _uiState.update { it.copy(checkOnStartup = enabled) }
    }

    fun dismissUpdate() {
        _uiState.update { it.copy(showDialog = false) }
    }

    private fun launchCheck(automatic: Boolean) {
        checkJob?.cancel()
        automaticCheck = automatic
        val channel = _uiState.value.channel
        checkJob = viewModelScope.launch {
            // 启动检查让出首屏绘制；手动检查与自动检查共用任务，避免重复弹窗。
            if (automatic) delay(2_000)
            _uiState.update { it.copy(checking = true, result = null, showDialog = false) }
            val result = repository.checkForUpdate(BuildConfig.VERSION_NAME, channel)
            _uiState.update {
                it.copy(checking = false, result = result, showDialog = result is AppUpdateResult.Available)
            }
        }
    }
}
