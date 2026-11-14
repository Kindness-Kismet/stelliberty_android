package com.stelliberty.android.ui.screen.proxy

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import com.stelliberty.android.BuildConfig
import com.stelliberty.android.R
import com.stelliberty.android.platform.TunMode
import com.stelliberty.android.ui.util.TestTags
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.window.WindowCascadingListPopup

private val MODE_OPTIONS = listOf("rule" to "Rule", "global" to "Global", "direct" to "Direct")
private val TUN_STACK_OPTIONS = listOf("mixed" to "Mixed", "gvisor" to "gVisor", "system" to "System", "mips" to "MIPS")

@Composable
internal fun ProxySettingsMenu(
    show: Boolean,
    onDismissRequest: () -> Unit,
    mode: String,
    onApplyRouting: (String?, String?) -> Unit,
    tunStack: String,
    tunMode: TunMode,
    isRunning: Boolean,
    groupTabs: Boolean,
    onGroupTabsChange: (Boolean) -> Unit,
    singleColumn: Boolean,
    onSingleColumnChange: (Boolean) -> Unit,
    hideUnavailable: Boolean,
    onHideUnavailableChange: (Boolean) -> Unit,
    showGlobalGroup: Boolean,
    onShowGlobalGroupChange: (Boolean) -> Unit,
    sortOption: Int,
    onSortOptionChange: (Int) -> Unit,
) {
    val modeIndex = MODE_OPTIONS.indexOfFirst { it.first == mode.lowercase() }.coerceAtLeast(0)
    val tunStackIndex = TUN_STACK_OPTIONS.indexOfFirst { it.first == tunStack.lowercase() }.coerceAtLeast(0)
    val sortIndex = (sortOption / 2).coerceIn(0, 2)
    val reverseSort = sortOption % 2 != 0
    val globalMode = mode.equals("global", ignoreCase = true)
    val tunStackEnabled = isRunning && tunMode != TunMode.RootTproxy
    val sortLabels = listOf(
        stringResource(R.string.proxy_sort_default),
        stringResource(R.string.proxy_sort_name),
        stringResource(R.string.proxy_sort_delay),
    )
    val layoutLabels = listOf(
        stringResource(R.string.proxy_layout_horizontal),
        stringResource(R.string.proxy_layout_vertical),
    )
    val entries = listOf(
        DropdownEntry(
            items = listOf(
                DropdownItem(
                    text = stringResource(R.string.proxy_mode),
                    summary = MODE_OPTIONS[modeIndex].second,
                    children = MODE_OPTIONS.mapIndexed { index, (value, label) ->
                        DropdownItem(
                            text = label,
                            selected = modeIndex == index,
                            onClick = { if (modeIndex != index) onApplyRouting(value, null) },
                        )
                    },
                ),
                DropdownItem(
                    text = stringResource(R.string.proxy_tun_stack),
                    summary = TUN_STACK_OPTIONS[tunStackIndex].second,
                    enabled = tunStackEnabled,
                    children = TUN_STACK_OPTIONS.mapIndexed { index, (value, label) ->
                        DropdownItem(
                            text = label,
                            selected = tunStackIndex == index,
                            onClick = { if (tunStackIndex != index) onApplyRouting(null, value) },
                        )
                    },
                ),
                DropdownItem(
                    text = stringResource(R.string.proxy_display_settings),
                    summary = layoutLabels[if (groupTabs) 0 else 1],
                    children = layoutLabels.mapIndexed { index, label ->
                        DropdownItem(
                            text = label,
                            selected = groupTabs == (index == 0),
                            onClick = { if (groupTabs != (index == 0)) onGroupTabsChange(index == 0) },
                        )
                    } + listOf(
                        DropdownItem(
                            text = stringResource(R.string.proxy_single_column),
                            selected = singleColumn,
                            onClick = { onSingleColumnChange(!singleColumn) },
                        ),
                        DropdownItem(
                            text = stringResource(R.string.proxy_hide_unavailable),
                            selected = hideUnavailable,
                            onClick = { onHideUnavailableChange(!hideUnavailable) },
                        ),
                        DropdownItem(
                            text = stringResource(R.string.proxy_show_global_group),
                            selected = showGlobalGroup || globalMode,
                            enabled = !globalMode,
                            onClick = { onShowGlobalGroupChange(!showGlobalGroup) },
                        ),
                    ),
                ),
                DropdownItem(
                    text = stringResource(R.string.proxy_sort_title),
                    summary = sortLabels[sortIndex],
                    children = sortLabels.mapIndexed { index, label ->
                        DropdownItem(
                            text = label,
                            selected = sortIndex == index,
                            onClick = {
                                if (sortIndex != index) onSortOptionChange(index * 2 + if (reverseSort) 1 else 0)
                            },
                        )
                    } + DropdownItem(
                        text = stringResource(R.string.proxy_sort_reverse),
                        selected = reverseSort,
                        onClick = { onSortOptionChange(sortIndex * 2 + if (reverseSort) 0 else 1) },
                    ),
                ),
            ),
        ),
    )
    WindowCascadingListPopup(
        show = show,
        entries = entries,
        onDismissRequest = onDismissRequest,
        popupModifier = Modifier
            .semantics { testTagsAsResourceId = BuildConfig.DEBUG }
            .testTag(TestTags.Proxy.SETTINGS_MENU),
    )
}
