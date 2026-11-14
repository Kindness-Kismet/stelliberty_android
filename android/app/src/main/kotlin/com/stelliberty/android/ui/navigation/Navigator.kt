package com.stelliberty.android.ui.navigation

import androidx.compose.runtime.staticCompositionLocalOf
import top.yukonga.miuix.kmp.nav.core.NavBackStack
import top.yukonga.miuix.kmp.nav.core.NavKey

class Navigator(
    val backStack: NavBackStack,
) {
    fun push(key: NavKey) {
        if (key !in backStack) {
            backStack.add(key)
        }
    }

    // 搜索直达子页时补齐父路由，返回仍按设置页的真实层级逐级出栈。
    fun openSettingsPage(route: Route) {
        when (route) {
            Route.NetworkSettings, Route.PortControl, Route.SystemIntegration,
            Route.DnsSettings, Route.PerformanceSettings -> push(Route.ClashFeatures)
            Route.VpnSettings, Route.RootSettings, Route.AppProxy -> {
                push(Route.ClashFeatures)
                push(Route.SystemIntegration)
            }
            Route.WifiPolicy -> push(Route.AppBehavior)
            Route.OpenSourceLibraries -> push(Route.About)
            else -> Unit
        }
        push(route)
    }

    fun replace(key: NavKey) {
        if (backStack.isNotEmpty()) {
            backStack[backStack.lastIndex] = key
        } else {
            backStack.add(key)
        }
    }

    fun pop() {
        if (backStack.size > 1) {
            backStack.removeLastOrNull()
        }
    }

    fun popUntil(predicate: (NavKey) -> Boolean) {
        while (backStack.size > 1 && !predicate(backStack.last())) {
            backStack.removeAt(backStack.lastIndex)
        }
    }

    fun current() = backStack.lastOrNull()

    fun backStackSize() = backStack.size
}

val LocalNavigator = staticCompositionLocalOf<Navigator> {
    error("LocalNavigator not provided")
}
