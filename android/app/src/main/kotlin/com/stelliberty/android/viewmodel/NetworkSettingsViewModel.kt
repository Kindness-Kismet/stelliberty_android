package com.stelliberty.android.viewmodel

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stelliberty.android.data.repository.OverrideJsonStore
import com.stelliberty.android.domain.model.ConfigurationOverride
import com.stelliberty.android.domain.model.DnsOverride
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class NetworkSettingsViewModel(
    private val store: OverrideJsonStore,
) : ViewModel() {

    val state: StateFlow<NetworkSettingsUiState> = store.state
        .map { it.toNetworkUi() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = store.state.value.toNetworkUi(),
        )

    fun update(transform: (ConfigurationOverride) -> ConfigurationOverride) {
        store.update(transform)
    }

    fun updateDns(transform: (DnsOverride) -> DnsOverride) {
        store.update { state ->
            val current = state.dns ?: DnsOverride()
            val next = transform(current)
            val allNull = next == DnsOverride()
            state.copy(dns = if (allNull) null else next)
        }
    }
}

@Immutable
data class NetworkSettingsUiState(
    val httpPort: Int? = null,
    val socksPort: Int? = null,
    val redirPort: Int? = null,
    val tproxyPort: Int? = null,
    val mixedPort: Int? = null,
    val allowLan: Boolean? = null,
    val ipv6: Boolean? = null,
    val bindAddress: String? = null,
    val logLevel: String? = null,
    val dns: DnsOverride? = null,
)

private fun ConfigurationOverride.toNetworkUi() = NetworkSettingsUiState(
    httpPort = httpPort,
    socksPort = socksPort,
    redirPort = redirPort,
    tproxyPort = tproxyPort,
    mixedPort = mixedPort,
    allowLan = allowLan,
    ipv6 = ipv6,
    bindAddress = bindAddress,
    logLevel = logLevel,
    dns = dns,
)
