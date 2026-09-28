package com.stelliberty.android.viewmodel

import android.content.Context
import androidx.annotation.StringRes
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
import com.stelliberty.android.util.AppLogger
import com.stelliberty.android.util.describe
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
    val error: String = "",
    val showAddDialog: Boolean = false,
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

    fun showAddDialog() {
        _uiState.value = _uiState.value.copy(showAddDialog = true)
    }

    fun hideAddDialog() {
        _uiState.value = _uiState.value.copy(showAddDialog = false)
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = "")
    }

    // 必须同步清掉进度状态让对话框立刻消失：协程取消是异步的，如果正卡在不可取消的提交阶段
    // 或者文件读写上，等收尾时再清会让用户觉得点了取消没反应。
    fun cancelCurrentUpdate() {
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
        hideAddDialog()
        runPipeline(ProfileOperation.Import, errorKey = R.string.error_import_failed) {
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
        runPipeline(ProfileOperation.Import, errorKey = R.string.error_import_failed) {
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
            viewModelScope.launch {
                _uiState.value = _uiState.value.copy(error = context.getString(R.string.subscription_file_only_no_update))
            }
            return
        }
        runPipeline(ProfileOperation.Update, errorKey = R.string.error_update_failed) {
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
        updateSubscriptions(
            repository.subscriptions.value.filter {
                !it.isLocalFile && it.autoUpdateMode == SubscriptionAutoUpdateMode.Startup
            }
        )
    }

    // 逐条等它做完，只为让进度条一次只前进一格。让配置生效的那次重启排在整轮结束之后且只做一次：
    // 中途重启会断网，后面还要下载的订阅会跟着一起失败。
    private fun updateSubscriptions(targets: List<Subscription>) {
        if (targets.isEmpty()) return
        if (isBusy()) return

        _uiState.value = _uiState.value.copy(operation = ProfileOperation.Update, error = "")
        currentJob = viewModelScope.launch {
            val failures = mutableListOf<String>()
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
                        val label = if (e is ConfigValidationException) {
                            context.getString(R.string.error_validation_failed, e.describe())
                        } else {
                            context.getString(R.string.error_update_failed, localizedMessage(e))
                        }
                        failures += "${sub.name}: $label"
                    }
                }
                _uiState.value = _uiState.value.copy(
                    updateAll = null,
                    operation = null,
                    error = if (failures.isEmpty()) "" else failures.joinToString("\n"),
                )
                updated.forEach { serviceController.restartAfterProfileUpdate(it) }
            } catch (e: CancellationException) {
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
        runPipeline(ProfileOperation.Edit, errorKey = R.string.error_save_failed) {
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
        @StringRes errorKey: Int,
        block: suspend () -> Unit,
    ) {
        if (isBusy()) return
        _uiState.value = _uiState.value.copy(
            isLoading = true,
            error = "",
            importProgress = null,
            operation = op,
        )
        currentJob = viewModelScope.launch {
            try {
                block()
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    importProgress = null,
                    operation = null,
                )
            } catch (e: CancellationException) {
                clearProgress()
                throw e
            } catch (e: Throwable) {
                AppLogger.error(TAG, "Profile operation $op failed", e)
                val message = if (e is ConfigValidationException) {
                    context.getString(R.string.error_validation_failed, e.describe())
                } else {
                    context.getString(errorKey, localizedMessage(e))
                }
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    importProgress = null,
                    operation = null,
                    error = message,
                )
            } finally {
                currentJob = null
            }
        }
    }

    private fun localizedMessage(e: Throwable): String = when (e) {
        is ImportError.HttpStatus -> context.getString(R.string.error_import_http_status, e.code)
        is ImportError.EmptyBody -> context.getString(R.string.error_import_empty_body)
        is ImportError.InvalidScheme -> context.getString(R.string.error_import_invalid_scheme, e.source)
        is ImportError.InvalidName -> context.getString(R.string.error_import_invalid_name)
        is ImportError.IntervalTooSmall -> context.getString(R.string.error_import_interval_too_small)
        else -> e.describe()
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
