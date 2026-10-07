package com.stelliberty.android.ui.screen.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.stelliberty.android.R
import com.stelliberty.android.platform.BootStartManager
import com.stelliberty.android.platform.PlatformStorage
import com.stelliberty.android.platform.ProxyServiceBridge
import com.stelliberty.android.platform.StorageKeys
import com.stelliberty.android.platform.TunMode
import com.stelliberty.android.ui.component.CardItem
import com.stelliberty.android.ui.component.groupedCardItems
import com.stelliberty.android.util.AppLogger
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference

@Composable
fun AppBehaviorScreen(
    storage: PlatformStorage,
    bootStartManager: BootStartManager?,
    onHideTaskCardChange: ((Boolean) -> Unit)?,
    onBack: () -> Unit = {},
    onNavigateWifiPolicy: () -> Unit = {},
) {
    var isAutoConnectEnabled by remember {
        mutableStateOf(storage.getString(StorageKeys.AUTO_CONNECT_ON_LAUNCH, "false") == "true")
    }
    var isAutoStartEnabled by remember { mutableStateOf(bootStartManager?.isEnabled() ?: false) }
    var isRestartAfterUpdateEnabled by remember {
        mutableStateOf(storage.getString(StorageKeys.RESTART_AFTER_PROFILE_UPDATE, "true") != "false")
    }
    var isDynamicNotificationEnabled by remember {
        mutableStateOf(storage.getString(StorageKeys.DYNAMIC_NOTIFICATION, "true") != "false")
    }
    var isHideTaskCardEnabled by remember {
        mutableStateOf(storage.getString(StorageKeys.HIDE_TASK_CARD, "false") == "true")
    }
    var appLogLevel by remember {
        mutableStateOf(storage.getString(StorageKeys.APP_LOG_LEVEL, AppLogger.DEFAULT_LEVEL))
    }
    // ROOT 模式的通知固定为静态，动态通知只在 VPN 模式可选。
    val isVpnMode = remember {
        TunMode.fromStorage(storage.getString(StorageKeys.TUN_MODE, TunMode.Vpn.storageValue)) == TunMode.Vpn
    }

    SettingsSubPage(title = stringResource(R.string.app_behavior_title), onBack = onBack) {
        item { SmallTitle(text = stringResource(R.string.settings_group_automation)) }
        groupedCardItems(
            keyPrefix = "app_behavior_automation",
            items = buildList {
                add(CardItem("autoConnect") {
                    SwitchPreference(
                        title = stringResource(R.string.settings_auto_connect),
                        summary = stringResource(R.string.settings_auto_connect_summary),
                        checked = isAutoConnectEnabled,
                        onCheckedChange = { checked ->
                            storage.putString(StorageKeys.AUTO_CONNECT_ON_LAUNCH, checked.toString())
                            isAutoConnectEnabled = checked
                        },
                    )
                })
                if (bootStartManager != null) {
                    add(CardItem("autoRestart") {
                        SwitchPreference(
                            title = stringResource(R.string.settings_auto_restart),
                            summary = stringResource(R.string.settings_auto_restart_summary),
                            checked = isAutoStartEnabled,
                            onCheckedChange = { checked ->
                                bootStartManager.setEnabled(checked)
                                isAutoStartEnabled = checked
                            },
                        )
                    })
                }
                add(CardItem("wifiPolicy") {
                    ArrowPreference(
                        title = stringResource(R.string.settings_wifi_policy),
                        summary = stringResource(R.string.settings_wifi_policy_summary),
                        onClick = onNavigateWifiPolicy,
                    )
                })
                add(CardItem("restartAfterUpdate") {
                    SwitchPreference(
                        title = stringResource(R.string.settings_restart_after_update),
                        summary = stringResource(R.string.settings_restart_after_update_summary),
                        checked = isRestartAfterUpdateEnabled,
                        onCheckedChange = { checked ->
                            storage.putString(StorageKeys.RESTART_AFTER_PROFILE_UPDATE, checked.toString())
                            isRestartAfterUpdateEnabled = checked
                        },
                    )
                })
            },
        )

        item { SmallTitle(text = stringResource(R.string.settings_general)) }
        groupedCardItems(
            keyPrefix = "app_behavior_general",
            items = buildList {
                add(CardItem("dynamicNotification") {
                    SwitchPreference(
                        title = stringResource(R.string.settings_dynamic_notification),
                        summary = stringResource(
                            if (isVpnMode) R.string.settings_dynamic_notification_summary
                            else R.string.settings_dynamic_notification_summary_root_unsupported
                        ),
                        checked = isDynamicNotificationEnabled && isVpnMode,
                        enabled = isVpnMode,
                        onCheckedChange = { checked ->
                            storage.putString(StorageKeys.DYNAMIC_NOTIFICATION, checked.toString())
                            isDynamicNotificationEnabled = checked
                            ProxyServiceBridge.requestNotificationRefresh()
                        },
                    )
                })
                if (onHideTaskCardChange != null) {
                    add(CardItem("hideTaskCard") {
                        SwitchPreference(
                            title = stringResource(R.string.settings_hide_task_card),
                            summary = stringResource(R.string.settings_hide_task_card_summary),
                            checked = isHideTaskCardEnabled,
                            onCheckedChange = { checked ->
                                storage.putString(StorageKeys.HIDE_TASK_CARD, checked.toString())
                                isHideTaskCardEnabled = checked
                                onHideTaskCardChange(checked)
                            },
                        )
                    })
                }
                add(CardItem("appLogLevel") {
                    OverlayDropdownPreference(
                        title = stringResource(R.string.log_source_application),
                        summary = stringResource(R.string.settings_app_log_summary),
                        items = LOG_LEVEL_CHOICES.labels,
                        selectedIndex = LOG_LEVEL_CHOICES.values.indexOf(appLogLevel).coerceAtLeast(0),
                        onSelectedIndexChange = { index ->
                            val level = LOG_LEVEL_CHOICES.values[index]
                            storage.putString(StorageKeys.APP_LOG_LEVEL, level)
                            AppLogger.setLevel(level)
                            appLogLevel = level
                        },
                    )
                })
            },
        )
    }
}
