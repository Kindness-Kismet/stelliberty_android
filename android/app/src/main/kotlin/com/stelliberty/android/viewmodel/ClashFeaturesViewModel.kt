package com.stelliberty.android.viewmodel

import androidx.lifecycle.ViewModel
import com.stelliberty.android.data.repository.OverrideJsonStore
import com.stelliberty.android.domain.model.ConfigurationOverride
import com.stelliberty.android.domain.model.DnsOverride
import com.stelliberty.android.domain.model.SnifferOverride
import kotlinx.coroutines.flow.StateFlow

// 「Clash 特性」下各页共用：字段都在 override.user.json 里，按页拆分只影响展示。
class ClashFeaturesViewModel(
    private val store: OverrideJsonStore,
) : ViewModel() {

    val state: StateFlow<ConfigurationOverride> = store.state

    fun update(transform: (ConfigurationOverride) -> ConfigurationOverride) {
        store.update(transform)
    }

    // 子对象全部字段回到未修改时整体置空，避免写出空的 dns / sniffer 段。
    fun updateDns(transform: (DnsOverride) -> DnsOverride) {
        store.update { state ->
            val next = transform(state.dns ?: DnsOverride())
            state.copy(dns = next.takeIf { it != DnsOverride() })
        }
    }

    fun updateSniffer(transform: (SnifferOverride) -> SnifferOverride) {
        store.update { state ->
            val next = transform(state.sniffer ?: SnifferOverride())
            state.copy(sniffer = next.takeIf { it != SnifferOverride() })
        }
    }
}
