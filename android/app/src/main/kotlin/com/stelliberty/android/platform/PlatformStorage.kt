package com.stelliberty.android.platform

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

object StorageKeys {
    const val SERVICE_WAS_RUNNING = "service_was_running"
    const val TUN_MODE = "tun_mode"
    const val HAS_ROOT = "has_root"

    const val AUTO_CONNECT_ON_LAUNCH = "auto_connect_on_launch"

    const val ROOT_MIHOMO_PID = "root_mihomo_pid"
    const val ROOT_MIHOMO_SECRET = "root_mihomo_secret"
    const val ROOT_START_TIME = "root_start_time"
    const val ROOT_ACTIVE_SUBSCRIPTION_ID = "root_active_subscription_id"

    const val ROOT_BOOT_COUNT = "root_boot_count"

    const val ACTIVE_PROFILE_NAME = "active_profile_name"

    const val RESTART_AFTER_PROFILE_UPDATE = "restart_after_profile_update"

    const val WIFI_POLICY_ENABLED = "wifi_policy_enabled"
    const val WIFI_POLICY_SSIDS = "wifi_policy_ssids"
    const val WIFI_POLICY_ACTION = "wifi_policy_action"
    const val WIFI_POLICY_MATCHED = "wifi_policy_matched"
    const val WIFI_POLICY_MATCHED_ACTION = "wifi_policy_matched_action"
    const val WIFI_POLICY_PENDING_RESTART = "wifi_policy_pending_restart"
    const val WIFI_POLICY_RUNTIME_MODE = "wifi_policy_runtime_mode"
    const val WIFI_POLICY_NOTIFY_SWITCH = "wifi_policy_notify_switch"
    const val WIFI_POLICY_HIDE_MONITOR_NOTIFICATION = "wifi_policy_hide_monitor_notification"

    const val VPN_BYPASS_PRIVATE_NETWORK = "vpn_bypass_private_network"
    const val VPN_ALLOW_BYPASS = "vpn_allow_bypass"
    const val VPN_DNS_HIJACKING = "vpn_dns_hijacking"
    const val VPN_SYSTEM_PROXY = "vpn_system_proxy"
    const val VPN_ALLOW_IPV6 = "vpn_allow_ipv6"

    const val APP_PROXY_MODE = "app_proxy_mode"
    const val APP_PROXY_PACKAGES = "app_proxy_packages"

    const val ROOT_TUN_DEVICE = "root_tun_device"
    const val ROOT_TETHER_MODE = "root_tether_mode"
    const val ROOT_TETHER_IFACES = "root_tether_ifaces"

    const val ROOT_TETHER_MODE_ACTIVE = "root_tether_mode_active"

    const val ROOT_SUBMODE_ACTIVE = "root_submode_active"

    const val ROOT_TUN_JUMBO_MTU = "root_tun_jumbo_mtu"

    const val ROOT_ATTACH_FORCE_REAPPLY = "root_attach_force_reapply"

    const val ROOT_TPROXY_KERNEL_CAPABLE = "root_tproxy_kernel_capable"

    const val PROXY_NODE_SORT_OPTION = "proxy_node_sort_option"

    const val PROXY_NODE_SINGLE_COLUMN = "proxy_node_single_column"

    const val PROXY_GROUP_TABS = "proxy_group_tabs"
    const val PROXY_SELECTED_GROUP = "proxy_selected_group"

    const val PROXY_SHOW_GLOBAL_GROUP = "proxy_show_global_group"

    const val PROXY_HIDE_UNAVAILABLE_NODES = "proxy_hide_unavailable_nodes"

    const val DARK_MODE = "dark_mode"
    const val THEME_PURE_BLACK = "theme_pure_black"
    const val THEME_MONET = "theme_monet"
    const val THEME_PALETTE_STYLE = "theme_palette_style"
    const val THEME_ACCENT_COLOR = "theme_accent_color"
    const val THEME_BLUR = "theme_blur"
    const val THEME_BLUR_STYLE = "theme_blur_style"
    const val THEME_FLOATING_BOTTOM_BAR = "theme_floating_bottom_bar"
    const val THEME_FLOATING_BOTTOM_BAR_STYLE = "theme_floating_bottom_bar_style"
    const val THEME_BOTTOM_BAR_MODE = "theme_bottom_bar_mode"
    const val THEME_DENSITY_SCALE = "theme_density_scale"
    const val DYNAMIC_NOTIFICATION = "dynamic_notification"
    const val PREDICTIVE_BACK = "predictive_back"
    const val SWIPE_DISMISS = "swipe_dismiss"
    const val HIDE_TASK_CARD = "hide_task_card"
    const val APP_LOG_LEVEL = "app_log_level"
    const val APP_UPDATE_CHANNEL = "app_update_channel"
    const val APP_UPDATE_ON_STARTUP = "app_update_on_startup"

    const val WEBDAV_URL = "webdav_url"
    const val WEBDAV_USERNAME = "webdav_username"
    const val WEBDAV_PASSWORD = "webdav_password"
}

class PlatformStorage(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("stelliberty_prefs", Context.MODE_PRIVATE)

    // 偏好文件里存的类型和读的对不上时 SharedPreferences 直接抛 ClassCastException，从前台服务
    // 逸出会杀进程，系统拉起后还是同一个坏值，成崩溃循环；退默认值是唯一自愈手段。
    fun getString(key: String, default: String): String =
        runCatching { prefs.getString(key, default) }.getOrNull() ?: default

    fun putString(key: String, value: String) {
        prefs.edit { putString(key, value) }
    }

    fun getStringSet(key: String, default: Set<String>): Set<String> =
        runCatching { prefs.getStringSet(key, default) }.getOrNull() ?: default

    fun putStringSet(key: String, value: Set<String>) {
        prefs.edit { putStringSet(key, value) }
    }

    fun remove(key: String) {
        prefs.edit { remove(key) }
    }

    fun dumpAll(): Map<String, Any?> = prefs.all
}
