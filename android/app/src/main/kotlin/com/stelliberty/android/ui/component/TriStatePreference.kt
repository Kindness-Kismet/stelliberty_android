package com.stelliberty.android.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.stelliberty.android.R
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference

@Composable
fun TriStatePreference(
    title: String,
    value: Boolean?,
    onValueChange: (Boolean?) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val notModified = stringResource(R.string.common_not_modified)
    val enabledLabel = stringResource(R.string.common_enabled)
    val disabledLabel = stringResource(R.string.common_disabled)
    val items = remember(notModified, enabledLabel, disabledLabel) {
        listOf(notModified, enabledLabel, disabledLabel)
    }
    val selectedIndex = when (value) {
        null -> 0
        true -> 1
        false -> 2
    }

    OverlayDropdownPreference(
        title = title,
        modifier = modifier,
        items = items,
        selectedIndex = selectedIndex,
        onSelectedIndexChange = { index ->
            onValueChange(
                when (index) {
                    1 -> true
                    2 -> false
                    else -> null
                }
            )
        },
        enabled = enabled,
    )
}
