package com.stelliberty.android.ui.screen.proxy

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.dp
import com.stelliberty.android.BuildConfig
import com.stelliberty.android.R
import com.stelliberty.android.platform.TunMode
import com.stelliberty.android.ui.component.SingleChoicePreference
import com.stelliberty.android.ui.util.TestTags
import kotlinx.collections.immutable.toImmutableList
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.squircle.squircleClip
import top.yukonga.miuix.kmp.window.WindowDialog

private val MODE_OPTIONS = listOf("rule" to "Rule", "global" to "Global", "direct" to "Direct")

private val TUN_STACK_OPTIONS = listOf(
    "mixed" to "Mixed",
    "gvisor" to "gVisor",
    "system" to "System",
    "mips" to "MIPS",
)

// 选路与显示改动即时生效，对话框只作为入口，不设确认草稿。
@Composable
internal fun ProxySettingsDialog(
    show: Boolean,
    onDismissRequest: () -> Unit,
    mode: String,
    onSwitchMode: (String) -> Unit,
    tunStack: String,
    tunMode: TunMode,
    isRunning: Boolean,
    onSwitchTunStack: (String) -> Unit,
    groupTabs: Boolean,
    onGroupTabsChange: (Boolean) -> Unit,
    singleColumn: Boolean,
    onSingleColumnChange: (Boolean) -> Unit,
    hideUnavailable: Boolean,
    onHideUnavailableChange: (Boolean) -> Unit,
    showGlobalGroup: Boolean,
    globalModeActive: Boolean,
    onShowGlobalGroupChange: (Boolean) -> Unit,
    sortOption: Int,
    onSortOptionChange: (Int) -> Unit,
    onClearIconCache: () -> Unit,
) {
    val modeIndex = MODE_OPTIONS.indexOfFirst { it.first == mode.lowercase() }.coerceAtLeast(0)
    val tunStackIndex = TUN_STACK_OPTIONS.indexOfFirst { it.first == tunStack.lowercase() }.coerceAtLeast(0)
    val sortIndex = (sortOption / 2).coerceIn(0, 2)
    val reverseSort = sortOption % 2 != 0
    val tunStackEnabled = isRunning && tunMode != TunMode.RootTproxy

    WindowDialog(
        show = show,
        title = stringResource(R.string.proxy_settings_title),
        modifier = Modifier
            .semantics { testTagsAsResourceId = BuildConfig.DEBUG }
            .testTag(TestTags.Proxy.SETTINGS_DIALOG),
        onDismissRequest = onDismissRequest,
    ) {
        Column(Modifier.heightIn(max = 500.dp)) {
            Column(
                Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState()),
            ) {
                Card(Modifier.fillMaxWidth()) {
                    SingleChoicePreference(
                        title = stringResource(R.string.proxy_mode),
                        items = MODE_OPTIONS.map { it.second }.toImmutableList(),
                        selectedIndex = modeIndex,
                        modifier = Modifier.testTag(TestTags.Proxy.MODE_ENTRY),
                        onSelectedIndexChange = { index ->
                            val value = MODE_OPTIONS[index].first
                            if (!mode.equals(value, ignoreCase = true)) onSwitchMode(value)
                        },
                    )
                    SingleChoicePreference(
                        title = stringResource(R.string.proxy_tun_stack),
                        items = TUN_STACK_OPTIONS.map { it.second }.toImmutableList(),
                        selectedIndex = tunStackIndex,
                        modifier = Modifier.testTag(TestTags.Proxy.TUN_STACK_ENTRY),
                        enabled = tunStackEnabled,
                        onSelectedIndexChange = { index ->
                            val value = TUN_STACK_OPTIONS[index].first
                            if (!tunStack.equals(value, ignoreCase = true)) onSwitchTunStack(value)
                        },
                    )
                }
                Spacer(Modifier.height(12.dp))
                Card(Modifier.fillMaxWidth()) {
                    SingleChoicePreference(
                        title = stringResource(R.string.proxy_layout_mode),
                        items = listOf(
                            stringResource(R.string.proxy_layout_horizontal),
                            stringResource(R.string.proxy_layout_vertical),
                        ).toImmutableList(),
                        selectedIndex = if (groupTabs) 0 else 1,
                        modifier = Modifier.testTag(TestTags.Proxy.GROUP_LAYOUT),
                        onSelectedIndexChange = { onGroupTabsChange(it == 0) },
                    )
                    SwitchPreference(
                        title = stringResource(R.string.proxy_single_column),
                        checked = singleColumn,
                        modifier = Modifier.squircleClip(16.dp).testTag(TestTags.Proxy.SINGLE_COLUMN),
                        onCheckedChange = onSingleColumnChange,
                    )
                    SwitchPreference(
                        title = stringResource(R.string.proxy_hide_unavailable),
                        checked = hideUnavailable,
                        modifier = Modifier.squircleClip(16.dp).testTag(TestTags.Proxy.HIDE_UNAVAILABLE),
                        onCheckedChange = onHideUnavailableChange,
                    )
                    SwitchPreference(
                        title = stringResource(R.string.proxy_show_global_group),
                        checked = showGlobalGroup || globalModeActive,
                        enabled = !globalModeActive,
                        modifier = Modifier.squircleClip(16.dp).testTag(TestTags.Proxy.SHOW_GLOBAL_GROUP),
                        onCheckedChange = onShowGlobalGroupChange,
                    )
                }
                Spacer(Modifier.height(12.dp))
                Card(Modifier.fillMaxWidth()) {
                    SingleChoicePreference(
                        title = stringResource(R.string.proxy_sort_title),
                        items = listOf(
                            stringResource(R.string.proxy_sort_default),
                            stringResource(R.string.proxy_sort_name),
                            stringResource(R.string.proxy_sort_delay),
                        ).toImmutableList(),
                        selectedIndex = sortIndex,
                        modifier = Modifier.testTag(TestTags.Proxy.SORT_ENTRY),
                        onSelectedIndexChange = { onSortOptionChange(it * 2 + if (reverseSort) 1 else 0) },
                    )
                    SwitchPreference(
                        title = stringResource(R.string.proxy_sort_reverse),
                        checked = reverseSort,
                        modifier = Modifier.squircleClip(16.dp).testTag(TestTags.Proxy.SORT_REVERSE),
                        onCheckedChange = { onSortOptionChange(sortIndex * 2 + if (it) 1 else 0) },
                    )
                }
                Spacer(Modifier.height(12.dp))
                Card(Modifier.fillMaxWidth()) {
                    ArrowPreference(
                        title = stringResource(R.string.proxy_refresh_icon),
                        modifier = Modifier.squircleClip(16.dp).testTag(TestTags.Proxy.REFRESH_ICONS),
                        onClick = onClearIconCache,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            TextButton(
                text = stringResource(R.string.proxy_settings_done),
                modifier = Modifier.fillMaxWidth().testTag(TestTags.Proxy.SETTINGS_DONE),
                onClick = onDismissRequest,
            )
        }
    }
}
