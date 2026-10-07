package com.stelliberty.android.ui.screen.settings

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.stelliberty.android.R
import com.stelliberty.android.platform.PlatformStorage
import com.stelliberty.android.platform.ProxyServiceBridge
import com.stelliberty.android.platform.StorageKeys
import com.stelliberty.android.platform.TunMode
import com.stelliberty.android.ui.component.CardItem
import com.stelliberty.android.ui.component.groupedCardItems
import com.stelliberty.android.ui.util.label
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference

@Composable
fun SystemIntegrationScreen(
    storage: PlatformStorage,
    hasRootPermission: Boolean,
    isProxyRunning: Boolean,
    onBack: () -> Unit = {},
    onNavigateVpnSettings: () -> Unit = {},
    onNavigateRootSettings: () -> Unit = {},
    onNavigateAppProxy: () -> Unit = {},
) {
    var tunMode by remember {
        mutableIntStateOf(TunMode.fromStorage(storage.getString(StorageKeys.TUN_MODE, TunMode.Vpn.storageValue)).ordinal)
    }

    SettingsSubPage(title = stringResource(R.string.system_integration_title), onBack = onBack) {
        item { Spacer(Modifier.height(12.dp)) }
        groupedCardItems(
            keyPrefix = "system_integration",
            items = buildList {
                if (hasRootPermission) {
                    add(CardItem("tunMode") {
                        OverlayDropdownPreference(
                            title = stringResource(R.string.settings_tun_mode),
                            summary = when (TunMode.entries[tunMode]) {
                                TunMode.Vpn -> stringResource(R.string.settings_tun_vpn_summary)
                                TunMode.RootTun -> stringResource(R.string.settings_tun_root_tun_summary)
                                TunMode.RootTproxy -> stringResource(R.string.settings_tun_root_tproxy_summary)
                            },
                            items = TunMode.entries.map { it.label() },
                            selectedIndex = tunMode,
                            onSelectedIndexChange = { index ->
                                val mode = TunMode.entries[index]
                                storage.putString(StorageKeys.TUN_MODE, mode.storageValue)
                                ProxyServiceBridge.setSelectedTunMode(mode)
                                tunMode = index
                            },
                            enabled = !isProxyRunning,
                        )
                    })
                }
                if (TunMode.entries[tunMode] == TunMode.Vpn) {
                    add(CardItem("vpnSettings") {
                        ArrowPreference(
                            title = stringResource(R.string.settings_vpn_settings),
                            summary = stringResource(R.string.settings_vpn_summary),
                            onClick = onNavigateVpnSettings,
                        )
                    })
                } else {
                    add(CardItem("rootSettings") {
                        ArrowPreference(
                            title = stringResource(R.string.root_settings_title),
                            summary = stringResource(R.string.root_settings_summary),
                            onClick = onNavigateRootSettings,
                        )
                    })
                }
                add(CardItem("appProxy") {
                    ArrowPreference(
                        title = stringResource(R.string.settings_app_proxy),
                        summary = stringResource(R.string.settings_app_proxy_summary),
                        onClick = onNavigateAppProxy,
                    )
                })
            },
        )
    }
}
