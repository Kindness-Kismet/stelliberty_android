package com.stelliberty.android.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.stelliberty.android.R
import com.stelliberty.android.platform.TunMode

@Composable
fun TunMode.label(): String = stringResource(
    when (this) {
        TunMode.Vpn -> R.string.settings_tun_mode_vpn
        TunMode.RootTun -> R.string.settings_tun_mode_root_tun
        TunMode.RootTproxy -> R.string.settings_tun_mode_root_tproxy
    },
)
