package com.stelliberty.android.ui.screen.proxy

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.stelliberty.android.BuildConfig
import com.stelliberty.android.R
import com.stelliberty.android.platform.TunMode
import com.stelliberty.android.ui.component.DialogButtons
import com.stelliberty.android.ui.component.SingleChoicePreference
import com.stelliberty.android.ui.util.TestTags
import kotlinx.collections.immutable.toImmutableList
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Checkbox
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.squircle.squircleClip
import top.yukonga.miuix.kmp.window.WindowDialog

private val MODE_OPTIONS = listOf("rule" to "Rule", "global" to "Global", "direct" to "Direct")

private val TUN_STACK_OPTIONS = listOf(
    "mixed" to "Mixed",
    "gvisor" to "gVisor",
    "system" to "System",
    "mips" to "MIPS",
)

@Immutable
private data class ProxySettingsDraft(
    val modeIndex: Int,
    val tunStackIndex: Int,
    val groupTabs: Boolean,
    val singleColumn: Boolean,
    val hideUnavailable: Boolean,
    val showGlobalGroup: Boolean,
    val sortIndex: Int,
    val reverseSort: Boolean,
    val subscriptionId: String?,
    val autoDelayInterval: String,
    val clearIconCache: Boolean = false,
)

@Composable
internal fun ProxySettingsDialog(
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
    activeSubscriptionId: String?,
    autoDelayIntervalMinutes: Int,
    onAutoDelayIntervalChange: (Int) -> Unit,
    onClearIconCache: () -> Unit,
) {
    val modeIndex = MODE_OPTIONS.indexOfFirst { it.first == mode.lowercase() }.coerceAtLeast(0)
    val tunStackIndex = TUN_STACK_OPTIONS.indexOfFirst { it.first == tunStack.lowercase() }.coerceAtLeast(0)
    val sortIndex = (sortOption / 2).coerceIn(0, 2)
    val reverseSort = sortOption % 2 != 0
    val tunStackEnabled = isRunning && tunMode != TunMode.RootTproxy
    val initial = ProxySettingsDraft(
        modeIndex, tunStackIndex, groupTabs, singleColumn, hideUnavailable, showGlobalGroup, sortIndex, reverseSort,
        activeSubscriptionId, autoDelayIntervalMinutes.takeIf { it > 0 }?.toString().orEmpty(),
    )
    var draft by remember { mutableStateOf(initial) }
    LaunchedEffect(show, activeSubscriptionId) {
        if (show) draft = initial
    }
    val draftGlobalMode = MODE_OPTIONS[draft.modeIndex].first == "global"

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
                        selectedIndex = draft.modeIndex,
                        modifier = Modifier.testTag(TestTags.Proxy.MODE_ENTRY),
                        onSelectedIndexChange = { draft = draft.copy(modeIndex = it) },
                    )
                    SingleChoicePreference(
                        title = stringResource(R.string.proxy_tun_stack),
                        items = TUN_STACK_OPTIONS.map { it.second }.toImmutableList(),
                        selectedIndex = draft.tunStackIndex,
                        modifier = Modifier.testTag(TestTags.Proxy.TUN_STACK_ENTRY),
                        enabled = tunStackEnabled,
                        onSelectedIndexChange = { draft = draft.copy(tunStackIndex = it) },
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
                        selectedIndex = if (draft.groupTabs) 0 else 1,
                        modifier = Modifier.testTag(TestTags.Proxy.GROUP_LAYOUT),
                        onSelectedIndexChange = { draft = draft.copy(groupTabs = it == 0) },
                    )
                    SwitchPreference(
                        title = stringResource(R.string.proxy_single_column),
                        checked = draft.singleColumn,
                        modifier = Modifier.squircleClip(16.dp).testTag(TestTags.Proxy.SINGLE_COLUMN),
                        onCheckedChange = { draft = draft.copy(singleColumn = it) },
                    )
                    SwitchPreference(
                        title = stringResource(R.string.proxy_hide_unavailable),
                        checked = draft.hideUnavailable,
                        modifier = Modifier.squircleClip(16.dp).testTag(TestTags.Proxy.HIDE_UNAVAILABLE),
                        onCheckedChange = { draft = draft.copy(hideUnavailable = it) },
                    )
                    SwitchPreference(
                        title = stringResource(R.string.proxy_show_global_group),
                        checked = draft.showGlobalGroup || draftGlobalMode,
                        enabled = !draftGlobalMode,
                        modifier = Modifier.squircleClip(16.dp).testTag(TestTags.Proxy.SHOW_GLOBAL_GROUP),
                        onCheckedChange = { draft = draft.copy(showGlobalGroup = it) },
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
                        selectedIndex = draft.sortIndex,
                        modifier = Modifier.testTag(TestTags.Proxy.SORT_ENTRY),
                        onSelectedIndexChange = { draft = draft.copy(sortIndex = it) },
                    )
                    SwitchPreference(
                        title = stringResource(R.string.proxy_sort_reverse),
                        checked = draft.reverseSort,
                        modifier = Modifier.squircleClip(16.dp).testTag(TestTags.Proxy.SORT_REVERSE),
                        onCheckedChange = { draft = draft.copy(reverseSort = it) },
                    )
                }
                Spacer(Modifier.height(12.dp))
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(bottom = 12.dp)) {
                        SmallTitle(text = stringResource(R.string.subscription_auto_delay_interval))
                        TextField(
                            value = draft.autoDelayInterval,
                            onValueChange = { input ->
                                draft = draft.copy(autoDelayInterval = input.filter { it in '0'..'9' }.take(6))
                            },
                            label = stringResource(R.string.proxy_auto_delay_placeholder),
                            useLabelAsPlaceholder = true,
                            enabled = activeSubscriptionId != null,
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp)
                                .testTag(TestTags.Proxy.AUTO_DELAY_INTERVAL),
                        )
                        if (activeSubscriptionId == null) {
                            Text(
                                text = stringResource(R.string.proxy_no_active_subscription),
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Card(Modifier.fillMaxWidth()) {
                    BasicComponent(
                        title = stringResource(R.string.proxy_refresh_icon),
                        modifier = Modifier.squircleClip(16.dp).testTag(TestTags.Proxy.REFRESH_ICONS),
                        onClick = { draft = draft.copy(clearIconCache = !draft.clearIconCache) },
                        endActions = {
                            Checkbox(
                                state = if (draft.clearIconCache) ToggleableState.On else ToggleableState.Off,
                                onClick = { draft = draft.copy(clearIconCache = !draft.clearIconCache) },
                            )
                        },
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            DialogButtons(
                onCancel = onDismissRequest,
                cancelTestTag = TestTags.Proxy.SETTINGS_CANCEL,
                confirmTestTag = TestTags.Proxy.SETTINGS_CONFIRM,
                onConfirm = {
                    onApplyRouting(
                        MODE_OPTIONS[draft.modeIndex].first.takeIf { draft.modeIndex != modeIndex },
                        TUN_STACK_OPTIONS[draft.tunStackIndex].first.takeIf { tunStackEnabled && draft.tunStackIndex != tunStackIndex },
                    )
                    if (draft.groupTabs != groupTabs) onGroupTabsChange(draft.groupTabs)
                    if (draft.singleColumn != singleColumn) onSingleColumnChange(draft.singleColumn)
                    if (draft.hideUnavailable != hideUnavailable) onHideUnavailableChange(draft.hideUnavailable)
                    if (draft.showGlobalGroup != showGlobalGroup) onShowGlobalGroupChange(draft.showGlobalGroup)
                    val nextSort = draft.sortIndex * 2 + if (draft.reverseSort) 1 else 0
                    if (nextSort != sortOption) onSortOptionChange(nextSort)
                    if (activeSubscriptionId != null && draft.subscriptionId == activeSubscriptionId) {
                        val nextInterval = draft.autoDelayInterval.toIntOrNull() ?: 0
                        if (nextInterval != autoDelayIntervalMinutes) onAutoDelayIntervalChange(nextInterval)
                    }
                    if (draft.clearIconCache) onClearIconCache()
                    onDismissRequest()
                },
            )
        }
    }
}
