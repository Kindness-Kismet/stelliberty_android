package com.stelliberty.android.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.stelliberty.android.BuildConfig
import com.stelliberty.android.ui.util.TestTags
import kotlinx.collections.immutable.ImmutableList
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.DropdownArrowEndAction
import top.yukonga.miuix.kmp.preference.RadioButtonLocation
import top.yukonga.miuix.kmp.preference.RadioButtonPreference
import top.yukonga.miuix.kmp.squircle.squircleClip
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog

@Composable
fun SingleChoicePreference(
    title: String,
    items: ImmutableList<String>,
    selectedIndex: Int,
    onSelectedIndexChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    startAction: @Composable (() -> Unit)? = null,
    optionTestTag: (Int) -> String = TestTags.Settings::option,
) {
    var show by rememberSaveable { mutableStateOf(false) }
    var holdDown by remember { mutableStateOf(false) }
    BasicComponent(
        title = title,
        summary = items[selectedIndex],
        modifier = modifier.squircleClip(16.dp),
        startAction = startAction,
        endActions = {
            DropdownArrowEndAction(
                actionColor = if (enabled) MiuixTheme.colorScheme.onSurfaceVariantActions
                else MiuixTheme.colorScheme.disabledOnSecondaryVariant,
            )
        },
        onClick = {
            holdDown = true
            show = true
        },
        holdDownState = holdDown,
        enabled = enabled,
    )
    SingleChoiceDialog(
        show = show,
        title = title,
        items = items,
        selectedIndex = selectedIndex,
        onSelectedIndexChange = {
            show = false
            onSelectedIndexChange(it)
        },
        onDismiss = { show = false },
        onDismissFinished = { holdDown = false },
        optionTestTag = optionTestTag,
    )
}

@Composable
fun SingleChoiceDialog(
    show: Boolean,
    title: String,
    items: ImmutableList<String>,
    selectedIndex: Int,
    onSelectedIndexChange: (Int) -> Unit,
    onDismiss: () -> Unit,
    onDismissFinished: () -> Unit = {},
    optionTestTag: (Int) -> String = TestTags.Settings::option,
) {
    WindowDialog(
        show = show,
        title = title,
        modifier = Modifier
            .semantics { testTagsAsResourceId = BuildConfig.DEBUG }
            .testTag(TestTags.Settings.CHOICE_DIALOG),
        onDismissRequest = onDismiss,
        onDismissFinished = onDismissFinished,
    ) {
        Column(Modifier.heightIn(max = 500.dp)) {
            Card(Modifier.fillMaxWidth().weight(1f, fill = false)) {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    // 候选标记固定在视觉右侧，文本仍按自身内容决定书写方向。
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        items.forEachIndexed { index, label ->
                            RadioButtonPreference(
                                title = label,
                                selected = index == selectedIndex,
                                radioButtonLocation = RadioButtonLocation.End,
                                modifier = Modifier.squircleClip(16.dp).testTag(optionTestTag(index)),
                                onClick = { onSelectedIndexChange(index) },
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            DialogButtons(onCancel = onDismiss)
        }
    }
}
