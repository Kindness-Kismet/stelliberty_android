package com.stelliberty.android.viewmodel

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stelliberty.android.R
import com.stelliberty.android.data.repository.ConfigValidationException
import com.stelliberty.android.data.repository.ImportError
import com.stelliberty.android.data.repository.ImportProgress
import com.stelliberty.android.data.repository.ProfileProcessor
import com.stelliberty.android.domain.model.Subscription
import com.stelliberty.android.domain.model.SubscriptionAutoUpdateMode
import com.stelliberty.android.domain.model.SubscriptionUpdateProxyMode
import com.stelliberty.android.domain.repository.SubscriptionRepository
import com.stelliberty.android.platform.ProfileFileManager
import com.stelliberty.android.platform.ProxyServiceController
import com.stelliberty.android.service.ProfileWorker
import com.stelliberty.android.util.AppLogger
import com.stelliberty.android.util.OperationFeedback
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class ProfileOperation { Import, Update, Edit }

@Immutable
data class SubscriptionUiState(
    val subscriptions: ImmutableList<Subscription> = persistentListOf(),
    val currentId: String? = null,
    val isLoading: Boolean = false,
    val importProgress: ImportProgress? = null,
    val updateAll: UpdateAllProgress? = null,
    val operation: ProfileOperation? = null,
)

@Immutable
data class UpdateAllProgress(
    val completed: Int,
    val total: Int,
    val currentName: String,
    val currentStep: ImportProgress? = null,
)

class SubscriptionViewModel(
    private val repository: SubscriptionRepository,
    val fileManager: ProfileFileManager,
    private val processor: ProfileProcessor,
    private val serviceController: ProxyServiceController,
    private val context: Context,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SubscriptionUiState())
    val uiState: StateFlow<SubscriptionUiState> = _uiState.asStateFlow()

    private var currentJob: Job? = null

    private var startupUpdatesStarted = false

    init {
        viewModelScope.launch {
            combine(repository.subscriptions, repository.currentSubscriptionId, ::Pair).collect { (subs, id) ->
                _uiState.value = _uiState.value.copy(subscriptions = subs, currentId = id)
            }
        }
    }

    // 必须同步清掉进度状态让对话框立刻消失：协程取消是异步的，如果正卡在不可取消的提交阶段
    // 或者文件读写上，等收尾时再清会让用户觉得点了取消没反应。
    fun cancelCurrentUpdate() {
        if (isBusy()) OperationFeedback.show(R.string.operation_cancelled)
        clearProgress()
        currentJob?.cancel()
    }

    fun addSubscription(
        name: String,
        url: String,
        autoUpdateMode: SubscriptionAutoUpdateMode = SubscriptionAutoUpdateMode.Disabled,
        intervalMinutes: Int = 0,
        userAgent: String = "",
        ageSecretKey: String = "",
        updateProxyMode: SubscriptionUpdateProxyMode = SubscriptionUpdateProxyMode.Core,
        autoTestDelayMinutes: Int = 0,
        onComplete: () -> Unit = {},
    ) {
        runPipeline(ProfileOperation.Import) {
            val sub = repository.create(
                name = name,
                sourceLocation = url,
                isLocalFile = false,
                autoUpdateMode = autoUpdateMode,
                autoUpdateIntervalMinutes = intervalMinutes,
                userAgent = userAgent,
                ageSecretKey = ageSecretKey,
                updateProxyMode = updateProxyMode,
                autoTestDelayIntervalMinutes = autoTestDelayMinutes,
            )
            pendingOnFailure(sub.id) {
                processor.apply(sub.id, ::reportProgress)
            }
            onComplete()
        }
    }

    fun addFromFile(fileName: String, content: String, onComplete: () -> Unit = {}) {
        runPipeline(ProfileOperation.Import) {
            val name = fileName.removeSuffix(".yaml").removeSuffix(".yml")
            val sub = repository.create(name = name, sourceLocation = "", isLocalFile = true)
            pendingOnFailure(sub.id) {
                fileManager.savePendingConfig(sub.id, content)
                processor.apply(sub.id, ::reportProgress)
            }
            onComplete()
        }
    }

    fun fetchSubscription(id: String) {
        val sub = _uiState.value.subscriptions.find { it.id == id } ?: return
        if (sub.isLocalFile) {
            OperationFeedback.show(R.string.subscription_file_only_no_update)
            return
        }
        runPipeline(ProfileOperation.Update) {
            processor.update(id, ::reportProgress)
            serviceController.restartAfterProfileUpdate(id)
        }
    }

    fun removeSubscription(id: String, onActiveChanged: () -> Unit = {}) {
        viewModelScope.launch {
            val wasActive = repository.currentSubscriptionId.value == id
            repository.delete(id)
            fileManager.deleteDirs(id)
            if (wasActive) onActiveChanged()
        }
    }

    fun setActive(id: String) {
        repository.setActive(id)
    }

    fun updateAllSubscriptions() {
        updateSubscriptions(_uiState.value.subscriptions.filterNot { it.isLocalFile })
    }

    // 「启动时更新」按打开应用计：ViewModel 是进程级单例，每个进程只跑一次。
    fun runStartupUpdates() {
        if (startupUpdatesStarted) return
        startupUpdatesStarted = true
        ProfileWorker.startUpdates(
            context,
            repository.subscriptions.value.filter {
                !it.isLocalFile && it.autoUpdateMode == SubscriptionAutoUpdateMode.Startup
            }.map { it.id },
        )
    }

    // 逐条等它做完，只为让进度条一次只前进一格。让配置生效的那次重启排在整轮结束之后且只做一次：
    // 中途重启会断网，后面还要下载的订阅会跟着一起失败。
    private fun updateSubscriptions(targets: List<Subscription>) {
        if (targets.isEmpty()) {
            OperationFeedback.updateResult(0, 0)
            return
        }
        if (isBusy()) {
            OperationFeedback.show(R.string.operation_in_progress)
            return
        }

        _uiState.value = _uiState.value.copy(operation = ProfileOperation.Update)
        currentJob = viewModelScope.launch {
            var failed = 0
            val updated = mutableListOf<String>()
            val total = targets.size
            try {
                targets.forEachIndexed { index, sub ->
                    _uiState.value = _uiState.value.copy(
                        updateAll = UpdateAllProgress(index, total, sub.name),
                    )
                    try {
                        processor.update(sub.id) { progress ->
                            val state = _uiState.value.updateAll ?: return@update
                            _uiState.value = _uiState.value.copy(
                                updateAll = state.copy(currentStep = progress),
                            )
                        }
                        updated += sub.id
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Throwable) {
                        AppLogger.warn(TAG, "Batch update failed for ${sub.id}", e)
                        failed++
                    }
                }
                _uiState.value = _uiState.value.copy(
                    updateAll = null,
                    operation = null,
                )
                updated.forEach { serviceController.restartAfterProfileUpdate(it) }
                OperationFeedback.updateResult(updated.size, failed)
            } catch (e: CancellationException) {
                AppLogger.info(TAG, "Batch profile update cancelled")
                clearProgress()
                throw e
            } finally {
                currentJob = null
            }
        }
    }

    fun editSubscription(
        uuid: String,
        name: String,
        source: String,
        autoUpdateMode: SubscriptionAutoUpdateMode,
        intervalMinutes: Int,
        userAgent: String,
        ageSecretKey: String,
        updateProxyMode: SubscriptionUpdateProxyMode,
        autoTestDelayMinutes: Int,
        onComplete: () -> Unit = {},
    ) {
        runPipeline(ProfileOperation.Edit) {
            repository.patch(
                uuid, name, source, autoUpdateMode, intervalMinutes, userAgent, ageSecretKey, updateProxyMode,
                autoTestDelayMinutes,
            )
            if (repository.validatePendingForCommit(uuid)) {
                processor.apply(uuid, ::reportProgress)
            } else {
                repository.commitPendingProfile(uuid)
            }
            onComplete()
        }
    }

    private fun runPipeline(
        op: ProfileOperation,
        block: suspend () -> Unit,
    ) {
        if (isBusy()) {
            OperationFeedback.show(R.string.operation_in_progress)
            return
        }
        _uiState.value = _uiState.value.copy(
            isLoading = true,
            importProgress = null,
            operation = op,
        )
        currentJob = viewModelScope.launch {
            AppLogger.info(TAG, "Profile operation $op started")
            try {
                block()
                AppLogger.info(TAG, "Profile operation $op completed")
                OperationFeedback.show(
                    when (op) {
                        ProfileOperation.Import -> R.string.operation_import_success
                        ProfileOperation.Update -> R.string.operation_update_success
                        ProfileOperation.Edit -> R.string.operation_save_success
                    },
                )
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    importProgress = null,
                    operation = null,
                )
            } catch (e: CancellationException) {
                AppLogger.info(TAG, "Profile operation $op cancelled")
                clearProgress()
                throw e
            } catch (e: Throwable) {
                AppLogger.error(TAG, "Profile operation $op failed", e)
                OperationFeedback.show(
                    when (e) {
                        is ImportError.InvalidName -> R.string.error_import_invalid_name
                        is ImportError.IntervalTooSmall -> R.string.error_import_interval_too_small
                        is ImportError.InvalidScheme -> R.string.operation_invalid_url
                        is ConfigValidationException -> R.string.operation_invalid_config
                        else -> when (op) {
                            ProfileOperation.Import -> R.string.operation_import_failed
                            ProfileOperation.Update -> R.string.operation_update_failed
                            ProfileOperation.Edit -> R.string.operation_save_failed
                        }
                    },
                )
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    importProgress = null,
                    operation = null,
                )
            } finally {
                currentJob = null
            }
        }
    }

    private suspend fun pendingOnFailure(uuid: String, block: suspend () -> Unit) {
        try {
            block()
        } catch (e: Throwable) {
            withContext(NonCancellable) {
                runCatching {
                    repository.release(uuid)
                    fileManager.releasePending(uuid)
                }
            }
            throw e
        }
    }

    private fun isBusy(): Boolean = currentJob?.isActive == true

    private fun clearProgress() {
        _uiState.value = _uiState.value.copy(
            isLoading = false,
            importProgress = null,
            updateAll = null,
            operation = null,
        )
    }

    private fun reportProgress(p: ImportProgress) {
        _uiState.value = _uiState.value.copy(importProgress = p)
    }

    private companion object {
        const val TAG = "SubscriptionViewModel"
    }
}
