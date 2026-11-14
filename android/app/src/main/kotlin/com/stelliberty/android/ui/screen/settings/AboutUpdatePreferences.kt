package com.stelliberty.android.ui.screen.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stelliberty.android.R
import com.stelliberty.android.domain.model.AppUpdateResult
import com.stelliberty.android.domain.model.UpdateChannel
import com.stelliberty.android.ui.component.SingleChoicePreference
import com.stelliberty.android.ui.util.TestTags
import com.stelliberty.android.viewmodel.AppUpdateViewModel
import kotlinx.collections.immutable.toImmutableList
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.squircle.squircleClip

@Composable
internal fun AboutUpdatePreferences(viewModel: AppUpdateViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val summary = when {
        state.checking -> stringResource(R.string.app_update_checking)
        else -> when (val result = state.result) {
            is AppUpdateResult.Available -> stringResource(R.string.app_update_available, result.info.latestVersion)
            AppUpdateResult.UpToDate -> stringResource(R.string.app_update_up_to_date)
            AppUpdateResult.Failed -> stringResource(R.string.app_update_failed)
            null -> null
        }
    }
    ArrowPreference(
        title = stringResource(R.string.app_update_check),
        summary = summary,
        modifier = Modifier.squircleClip(16.dp).testTag(TestTags.About.CHECK_UPDATE),
        holdDownState = state.showDialog,
        enabled = !state.checking,
        onClick = viewModel::checkForUpdates,
    )
    SingleChoicePreference(
        title = stringResource(R.string.app_update_channel),
        items = UpdateChannel.entries.map { stringResource(channelLabel(it)) }.toImmutableList(),
        selectedIndex = state.channel.ordinal,
        modifier = Modifier.testTag(TestTags.About.UPDATE_CHANNEL),
        optionTestTag = { TestTags.About.channel(UpdateChannel.entries[it].name) },
        onSelectedIndexChange = { viewModel.setChannel(UpdateChannel.entries[it]) },
    )
    SwitchPreference(
        title = stringResource(R.string.app_update_on_startup),
        modifier = Modifier.squircleClip(16.dp).testTag(TestTags.About.UPDATE_STARTUP),
        checked = state.checkOnStartup,
        onCheckedChange = viewModel::setCheckOnStartup,
    )
}

private fun channelLabel(channel: UpdateChannel): Int = when (channel) {
    UpdateChannel.STABLE -> R.string.app_update_channel_stable
    UpdateChannel.TEST -> R.string.app_update_channel_test
}
