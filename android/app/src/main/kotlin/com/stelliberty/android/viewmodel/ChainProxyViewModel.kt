package com.stelliberty.android.viewmodel

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stelliberty.android.R
import com.stelliberty.android.data.repository.ConfigValidationException
import com.stelliberty.android.domain.model.ChainProxyContext
import com.stelliberty.android.domain.model.SubscriptionCustomChainProxy
import com.stelliberty.android.domain.repository.ChainProxyRepository
import com.stelliberty.android.domain.repository.SubscriptionRepository
import com.stelliberty.android.platform.ProxyServiceController
import com.stelliberty.android.platform.showToast
import com.stelliberty.android.util.AppLogger
import com.stelliberty.android.util.describe
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Immutable
data class ChainProxyUiState(
    val subscriptionId: String? = null,
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val error: String = "",
    val context: ChainProxyContext = ChainProxyContext(),
    val disabledBuiltinNames: ImmutableList<String> = persistentListOf(),
    val customChainProxies: ImmutableList<SubscriptionCustomChainProxy> = persistentListOf(),
    val hasChanges: Boolean = false,
)

// 列表页与单条链的编辑页共用一份未保存的草稿，点保存才写入订阅；session 区分每次进入列表页。
class ChainProxyViewModel(
    private val repository: ChainProxyRepository,
    private val subscriptions: SubscriptionRepository,
    private val serviceController: ProxyServiceController,
    private val context: Context,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChainProxyUiState())
    val uiState: StateFlow<ChainProxyUiState> = _uiState.asStateFlow()

    private var session: String? = null
    private var loadJob: Job? = null

    fun open(subscriptionId: String, session: String) {
        if (this.session == session) return
        val subscription = subscriptions.subscriptions.value.find { it.id == subscriptionId } ?: return
        this.session = session
        loadJob?.cancel()
        _uiState.value = ChainProxyUiState(
            subscriptionId = subscriptionId,
            isLoading = true,
            disabledBuiltinNames = subscription.disabledBuiltinChainProxyNames.toPersistentList(),
            customChainProxies = subscription.customChainProxies.toPersistentList(),
        )
        loadJob = viewModelScope.launch {
            val (loaded, error) = try {
                repository.loadContext(subscriptionId) to ""
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                AppLogger.warn(TAG, "Chain proxy context failed for $subscriptionId", e)
                ChainProxyContext() to context.getString(R.string.chain_proxy_load_failed, e.describe())
            }
            _uiState.update { it.copy(isLoading = false, context = loaded, error = error) }
        }
    }

    fun toggleBuiltin(name: String) = edit { state ->
        val disabled = state.disabledBuiltinNames
        state.copy(disabledBuiltinNames = (if (name in disabled) disabled - name else disabled + name).toPersistentList())
    }

    fun toggleCustom(id: String) = edit { state ->
        state.copy(
            customChainProxies = state.customChainProxies
                .map { if (it.id == id) it.copy(isEnabled = !it.isEnabled) else it }
                .toPersistentList(),
        )
    }

    fun removeCustom(id: String) = edit { state ->
        state.copy(customChainProxies = state.customChainProxies.filterNot { it.id == id }.toPersistentList())
    }

    // 编辑已有链时保留原位置与启用状态。
    fun putCustom(chain: SubscriptionCustomChainProxy) = edit { state ->
        val list = state.customChainProxies
        val index = list.indexOfFirst { it.id == chain.id }
        val next = if (index < 0) list + chain else list.toMutableList().also {
            it[index] = chain.copy(isEnabled = list[index].isEnabled)
        }
        state.copy(customChainProxies = next.toPersistentList())
    }

    fun save(onComplete: () -> Unit) {
        val state = _uiState.value
        val subscriptionId = state.subscriptionId ?: return
        if (state.isSaving || state.isLoading) return
        _uiState.update { it.copy(isSaving = true, error = "") }
        viewModelScope.launch {
            val error = try {
                val hasCycle = repository.save(subscriptionId, state.disabledBuiltinNames, state.customChainProxies)
                if (hasCycle) showToast(context.getString(R.string.chain_proxy_cycle_warning))
                if (subscriptions.currentSubscriptionId.value == subscriptionId) {
                    serviceController.restartWhenReady(subscriptionId)
                }
                onComplete()
                ""
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                AppLogger.error(TAG, "Chain proxy save failed for $subscriptionId", e)
                if (e is ConfigValidationException) {
                    context.getString(R.string.error_validation_failed, e.describe())
                } else {
                    context.getString(R.string.error_save_failed, e.describe())
                }
            }
            _uiState.update { it.copy(isSaving = false, error = error) }
        }
    }

    private fun edit(transform: (ChainProxyUiState) -> ChainProxyUiState) {
        _uiState.update { state ->
            val next = transform(state)
            val saved = subscriptions.subscriptions.value.find { it.id == state.subscriptionId }
            next.copy(
                hasChanges = saved == null ||
                    next.disabledBuiltinNames.toSet() != saved.disabledBuiltinChainProxyNames.toSet() ||
                    next.customChainProxies != saved.customChainProxies,
            )
        }
    }

    private companion object {
        const val TAG = "ChainProxyViewModel"
    }
}
