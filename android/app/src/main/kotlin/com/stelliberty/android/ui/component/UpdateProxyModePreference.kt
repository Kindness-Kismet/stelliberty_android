package com.stelliberty.android.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.stelliberty.android.R
import com.stelliberty.android.domain.model.SubscriptionUpdateProxyMode
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference

// 订阅与覆写共用，选项顺序与 PC 一致。
@Composable
fun UpdateProxyModePreference(
    mode: SubscriptionUpdateProxyMode,
    onModeChange: (SubscriptionUpdateProxyMode) -> Unit,
) {
    val modes = SubscriptionUpdateProxyMode.entries
    OverlayDropdownPreference(
        title = stringResource(R.string.update_proxy_mode),
        summary = stringResource(
            when (mode) {
                SubscriptionUpdateProxyMode.Direct -> R.string.update_proxy_direct_summary
                SubscriptionUpdateProxyMode.SystemProxy -> R.string.update_proxy_system_summary
                SubscriptionUpdateProxyMode.Core -> R.string.update_proxy_core_summary
            }
        ),
        items = listOf(
            stringResource(R.string.update_proxy_direct),
            stringResource(R.string.update_proxy_system),
            stringResource(R.string.update_proxy_core),
        ),
        selectedIndex = modes.indexOf(mode),
        onSelectedIndexChange = { onModeChange(modes[it]) },
    )
}
