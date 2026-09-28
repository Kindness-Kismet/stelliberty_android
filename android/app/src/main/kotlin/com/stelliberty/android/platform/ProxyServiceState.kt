package com.stelliberty.android.platform

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class ProxyState {
    Stopped,
    Starting,
    Running,
    Stopping,
    Error,
}

// storageValue 是存进设置里的字符串，和枚举名故意不同形。写错了不会报错，只会悄悄退回 VPN 模式，
// 所以任何读写这个设置项的地方都要走这里，别自己另写一套对应关系。
enum class TunMode(val storageValue: String) {
    Vpn("vpn"),
    RootTun("root_tun"),
    RootTproxy("root_tproxy"),
    ;

    companion object {
        fun fromStorage(value: String?): TunMode = entries.firstOrNull { it.storageValue == value } ?: Vpn
    }
}

data class ProxyServiceStatus(
    val state: ProxyState = ProxyState.Stopped,
    val secret: String = "",
    val externalController: String = "127.0.0.1:9090",
    val errorMessage: String = "",
    val tunMode: TunMode = TunMode.Vpn,
    val startTime: Long = 0L,
    val mihomoPid: Int = -1,
    val errorNotified: Boolean = false,
)

object ProxyServiceBridge {
    private val _state = MutableStateFlow(ProxyServiceStatus())
    val state: StateFlow<ProxyServiceStatus> = _state.asStateFlow()

    private val _notificationRefresh = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val notificationRefresh: SharedFlow<Unit> = _notificationRefresh.asSharedFlow()

    fun updateState(status: ProxyServiceStatus) {
        _state.value = status
    }

    // 冷启动填初始值、停止态改模式两处共用。只动 tunMode 一个字段：整体覆盖会把 Error 的错误
    // 信息一起抹掉，错误是终态；运行中的 tunMode 是「正在跑的那个」，不接受同步。
    fun setSelectedTunMode(tunMode: TunMode) {
        _state.update { current ->
            val settled = current.state == ProxyState.Stopped || current.state == ProxyState.Error
            if (settled) current.copy(tunMode = tunMode) else current
        }
    }

    // 一定要带上当前模式。读的人在已停止状态下只能回头查设置项，而设置项是「用户现在选的」，
    // 不是「刚才在跑的那个」，用户改了模式还没重启时这两者并不相等。
    fun markStopped(tunMode: TunMode) {
        _state.value = ProxyServiceStatus(ProxyState.Stopped, tunMode = tunMode)
    }

    // 服务销毁时专用：失败路径刚写完错误就走到销毁，无条件写「已停止」会把它抹掉，用户只见
    // 「启动中 → 未运行」。错误是终态，不能被覆盖。
    fun markStoppedUnlessError(tunMode: TunMode) {
        _state.update { current ->
            if (current.state == ProxyState.Error) current
            else ProxyServiceStatus(ProxyState.Stopped, tunMode = tunMode)
        }
    }

    fun requestNotificationRefresh() {
        _notificationRefresh.tryEmit(Unit)
    }
}
