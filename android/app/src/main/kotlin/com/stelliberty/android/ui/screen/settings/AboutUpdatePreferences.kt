package com.stelliberty.android.ui.screen.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stelliberty.android.BuildConfig
import com.stelliberty.android.R
import com.stelliberty.android.domain.model.AppUpdateResult
import com.stelliberty.android.domain.model.UpdateChannel
import com.stelliberty.android.ui.util.TestTags
import com.stelliberty.android.viewmodel.AppUpdateViewModel
import top.yukonga.miuix.kmp.basic.DropdownImpl
import top.yukonga.miuix.kmp.basic.ListPopupColumn
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.window.WindowListPopup

@Composable
internal fun AboutUpdatePreferences(viewModel: AppUpdateViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showChannels by remember { mutableStateOf(false) }
    val summary = when {
        state.checking -> stringResource(R.string.app_update_checking)
        else -> when (val result = state.result) {
            is AppUpdateResult.Available -> stringResource(R.string.app_update_available, result.info.latestVersion)
            AppUpdateResult.UpToDate -> stringResource(R.string.app_update_up_to_date)
            AppUpdateResult.Failed -> stringResource(R.string.app_update_failed)
            null -> stringResource(R.string.app_update_idle)
        }
    }

    ArrowPreference(
        title = stringResource(R.string.app_update_check),
        summary = summary,
        modifier = Modifier.testTag(TestTags.About.CHECK_UPDATE),
        holdDownState = state.showDialog,
        enabled = !state.checking,
        onClick = viewModel::checkForUpdates,
    )
    Box {
        ArrowPreference(
            title = stringResource(R.string.app_update_channel),
            summary = stringResource(channelLabel(state.channel)),
            modifier = Modifier.testTag(TestTags.About.UPDATE_CHANNEL),
            holdDownState = showChannels,
            onClick = { showChannels = true },
        )
        WindowListPopup(show = showChannels, onDismissRequest = { showChannels = false }) {
            ListPopupColumn {
                UpdateChannel.entries.forEach { channel ->
                    Box(
                        Modifier
                            .semantics { testTagsAsResourceId = BuildConfig.DEBUG }
                            .testTag(TestTags.About.channel(channel.name)),
                    ) {
                        DropdownImpl(
                            text = stringResource(channelLabel(channel)),
                            optionSize = UpdateChannel.entries.size,
                            isSelected = state.channel == channel,
                            index = channel.ordinal,
                            onSelectedIndexChange = {
                                viewModel.setChannel(channel)
                                showChannels = false
                            },
                        )
                    }
                }
            }
        }
    }
    SwitchPreference(
        title = stringResource(R.string.app_update_on_startup),
        summary = stringResource(R.string.app_update_on_startup_summary),
        modifier = Modifier.testTag(TestTags.About.UPDATE_STARTUP),
        checked = state.checkOnStartup,
        onCheckedChange = viewModel::setCheckOnStartup,
    )
}

private fun channelLabel(channel: UpdateChannel): Int = when (channel) {
    UpdateChannel.STABLE -> R.string.app_update_channel_stable
    UpdateChannel.TEST -> R.string.app_update_channel_test
}
