package com.stelliberty.android.viewmodel

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stelliberty.android.data.repository.OverrideJsonStore
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class ExternalControlViewModel(
    private val store: OverrideJsonStore,
) : ViewModel() {

    val state: StateFlow<ExternalControlUiState> = store.state
        .map { ExternalControlUiState(externalController = it.externalController, secret = it.secret) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = store.state.value.let {
                ExternalControlUiState(externalController = it.externalController, secret = it.secret)
            },
        )

    fun setExternalController(value: String?) {
        store.update { it.copy(externalController = value) }
    }

    fun setSecret(value: String?) {
        store.update { it.copy(secret = value) }
    }
}

@Immutable
data class ExternalControlUiState(
    val externalController: String? = null,
    val secret: String? = null,
)
