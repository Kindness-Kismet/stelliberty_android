package com.stelliberty.android.viewmodel

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stelliberty.android.data.repository.OverrideJsonStore
import com.stelliberty.android.domain.model.ConfigurationOverride
import com.stelliberty.android.domain.model.SnifferOverride
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class MetaSettingsViewModel(
    private val store: OverrideJsonStore,
) : ViewModel() {

    val state: StateFlow<MetaSettingsUiState> = store.state
        .map { it.toMetaUi() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = store.state.value.toMetaUi(),
        )

    fun update(transform: (ConfigurationOverride) -> ConfigurationOverride) {
        store.update(transform)
    }

    fun updateSniffer(transform: (SnifferOverride) -> SnifferOverride) {
        store.update { state ->
            val current = state.sniffer ?: SnifferOverride()
            val next = transform(current)
            val allNull = next == SnifferOverride()
            state.copy(sniffer = if (allNull) null else next)
        }
    }
}

@Immutable
data class MetaSettingsUiState(
    val unifiedDelay: Boolean? = null,
    val geodataMode: Boolean? = null,
    val tcpConcurrent: Boolean? = null,
    val findProcessMode: String? = null,
    val sniffer: SnifferOverride? = null,
)

private fun ConfigurationOverride.toMetaUi() = MetaSettingsUiState(
    unifiedDelay = unifiedDelay,
    geodataMode = geodataMode,
    tcpConcurrent = tcpConcurrent,
    findProcessMode = findProcessMode,
    sniffer = sniffer,
)
