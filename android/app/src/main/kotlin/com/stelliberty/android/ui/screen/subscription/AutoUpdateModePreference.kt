package com.stelliberty.android.ui.screen.subscription

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.stelliberty.android.R
import com.stelliberty.android.domain.model.SubscriptionAutoUpdateMode
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference

@Composable
internal fun AutoUpdateModePreference(
    mode: SubscriptionAutoUpdateMode,
    onModeChange: (SubscriptionAutoUpdateMode) -> Unit,
) {
    val modes = SubscriptionAutoUpdateMode.entries
    OverlayDropdownPreference(
        title = stringResource(R.string.subscription_auto_update),
        items = listOf(
            stringResource(R.string.subscription_auto_update_disabled),
            stringResource(R.string.subscription_auto_update_startup),
            stringResource(R.string.subscription_auto_update_interval),
        ),
        selectedIndex = modes.indexOf(mode),
        onSelectedIndexChange = { onModeChange(modes[it]) },
    )
}
