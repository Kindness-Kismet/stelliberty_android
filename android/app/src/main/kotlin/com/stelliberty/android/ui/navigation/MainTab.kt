package com.stelliberty.android.ui.navigation

import androidx.annotation.StringRes
import com.stelliberty.android.R

enum class MainTab(val storageValue: String, @param:StringRes val labelRes: Int) {
    Home("home", R.string.nav_home),
    Proxy("proxy", R.string.nav_proxy),
    Subscription("subscription", R.string.nav_subscription),
    Settings("settings", R.string.nav_settings);

    companion object {
        fun fromStorage(value: String?): MainTab = entries.firstOrNull { it.storageValue == value } ?: Home
    }
}
