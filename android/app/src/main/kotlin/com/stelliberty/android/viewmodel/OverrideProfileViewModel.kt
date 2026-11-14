package com.stelliberty.android.viewmodel

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stelliberty.android.R
import com.stelliberty.android.data.repository.ConfigValidationException
import com.stelliberty.android.data.repository.OverrideInputError
import com.stelliberty.android.domain.model.OverrideFormat
import com.stelliberty.android.domain.model.OverrideProfile
import com.stelliberty.android.domain.model.SubscriptionUpdateProxyMode
import com.stelliberty.android.domain.repository.OverrideProfileRepository
import com.stelliberty.android.domain.repository.SubscriptionRepository
import com.stelliberty.android.platform.ProxyServiceController
import com.stelliberty.android.util.OperationFeedback
import com.stelliberty.android.util.AppLogger
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Immutable
data class OverrideProfileUiState(
    val profiles: ImmutableList<OverrideProfile> = persistentListOf(),
    val isLoading: Boolean = false,
)

// 当前订阅的覆写改动通过重启生效，独立于订阅自动更新的重启偏好。
class OverrideProfileViewModel(
    private val repository: OverrideProfileRepository,
    private val subscriptions: SubscriptionRepository,
    private val serviceController: ProxyServiceController,
) : ViewModel() {

    private val _uiState = MutableStateFlow(OverrideProfileUiState())
    val uiState: StateFlow<OverrideProfileUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.profiles.collect { list -> _uiState.update { it.copy(profiles = list) } }
        }
    }

    suspend fun readContent(id: String): String = repository.readContent(id)

    fun addRemote(
        name: String,
        url: String,
        format: OverrideFormat,
        updateProxyMode: SubscriptionUpdateProxyMode,
        onComplete: () -> Unit,
    ) = launchOperation(R.string.error_import_failed) {
        repository.addRemote(name, url, format, updateProxyMode)
        onComplete()
    }

    fun addLocal(name: String, fileName: String, format: OverrideFormat, content: String, onComplete: () -> Unit) =
        launchOperation(R.string.error_import_failed) {
            repository.addLocal(name, fileName, format, content)
            onComplete()
        }

    fun addBlank(name: String, format: OverrideFormat, onComplete: () -> Unit) =
        launchOperation(R.string.error_save_failed) {
            repository.addBlank(name, format)
            onComplete()
        }

    fun edit(
        profile: OverrideProfile,
        name: String,
        url: String,
        format: OverrideFormat,
        updateProxyMode: SubscriptionUpdateProxyMode,
        onComplete: () -> Unit,
    ) = launchOperation(R.string.error_save_failed) {
        repository.edit(profile.id, name, url, format, updateProxyMode)
        if (format != profile.format) restartIfUsed(profile.id)
        onComplete()
    }

    fun saveContent(id: String, content: String, onComplete: () -> Unit) =
        launchOperation(R.string.error_save_failed) {
            repository.saveContent(id, content)
            restartIfUsed(id)
            onComplete()
        }

    fun update(id: String) = launchOperation(R.string.error_update_failed) {
        repository.update(id)
        restartIfUsed(id)
        OperationFeedback.updateResult(1, 0)
    }

    // 逐条更新，最后只重启一次，免得中途断网让后面的下载一起失败。
    fun updateAll() = launchOperation(R.string.error_update_failed) {
        var succeeded = 0
        var failed = 0
        var restart = false
        _uiState.value.profiles.filter { it.isRemote }.forEach { profile ->
            try {
                repository.update(profile.id)
                succeeded++
                restart = restart || repository.isUsedByCurrent(profile.id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                AppLogger.warn(TAG, "Override update failed for ${profile.id}", e)
                failed++
            }
        }
        OperationFeedback.updateResult(succeeded, failed)
        if (restart) restartCurrent()
    }

    fun delete(id: String) = launchOperation(R.string.error_save_failed) {
        val used = repository.isUsedByCurrent(id)
        repository.delete(id)
        if (used) restartCurrent()
    }

    fun setSelection(
        subscriptionId: String,
        overrideIds: List<String>,
        sortPreference: List<String>,
        onComplete: () -> Unit,
    ) = launchOperation(R.string.error_save_failed) {
        val subscription = subscriptions.subscriptions.value.find { it.id == subscriptionId }
        repository.setSelection(subscriptionId, overrideIds, sortPreference)
        val nextOrder = subscription?.copy(overrideIds = overrideIds, overrideSortPreference = sortPreference)?.orderedOverrideIds
        if (subscription?.orderedOverrideIds != nextOrder && subscriptions.currentSubscriptionId.value == subscriptionId) {
            serviceController.restartWhenReady(subscriptionId)
        }
        onComplete()
    }

    private fun restartIfUsed(id: String) {
        if (repository.isUsedByCurrent(id)) restartCurrent()
    }

    private fun restartCurrent() {
        subscriptions.currentSubscriptionId.value?.let(serviceController::restartWhenReady)
    }

    private fun launchOperation(@StringRes errorKey: Int, block: suspend () -> Unit) {
        if (_uiState.value.isLoading) {
            OperationFeedback.show(R.string.operation_in_progress)
            return
        }
        _uiState.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            try {
                block()
                if (errorKey != R.string.error_update_failed) OperationFeedback.show(R.string.operation_success)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                AppLogger.error(TAG, "Override operation failed", e)
                OperationFeedback.show(
                    when (e) {
                        is OverrideInputError.NameRequired -> R.string.override_error_name_required
                        is OverrideInputError.InvalidUrl -> R.string.override_error_invalid_url
                        is ConfigValidationException -> R.string.operation_invalid_config
                        else -> when (errorKey) {
                            R.string.error_update_failed -> R.string.operation_update_failed
                            R.string.error_import_failed -> R.string.operation_import_failed
                            else -> R.string.operation_save_failed
                        }
                    },
                )
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    private companion object {
        const val TAG = "OverrideProfileViewModel"
    }
}
