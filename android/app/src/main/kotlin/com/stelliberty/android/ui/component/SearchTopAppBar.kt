package com.stelliberty.android.ui.component

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.ime
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.stelliberty.android.R
import com.stelliberty.android.ui.icon.AppIcons
import com.stelliberty.android.ui.util.TestTags
import com.stelliberty.android.ui.util.horizontalCutoutPadding
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.InputField
import top.yukonga.miuix.kmp.basic.SearchBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.squircle.squircleClip
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun SearchTopAppBar(
    isSearching: Boolean,
    isActive: Boolean,
    query: String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit,
    content: @Composable () -> Unit,
) {
    val transition = updateTransition(isSearching, label = "SettingsSearch")
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val imeInsets = WindowInsets.ime
    val density = LocalDensity.current
    fun close() {
        keyboard?.hide()
        focusManager.clearFocus()
        onClose()
    }
    LaunchedEffect(isSearching, transition.currentState, transition.isRunning) {
        if (!isSearching && !transition.isRunning) onQueryChange("")
    }
    transition.AnimatedContent(
        modifier = Modifier.fillMaxWidth(),
        transitionSpec = {
            (slideInVertically(tween(300, easing = FastOutSlowInEasing)) { it / 12 } +
                fadeIn(tween(180, delayMillis = 60))) togetherWith
                (slideOutVertically(tween(300, easing = FastOutSlowInEasing)) { -it / 12 } +
                    fadeOut(tween(120))) using
                SizeTransform(sizeAnimationSpec = { _, _ -> tween(300, easing = FastOutSlowInEasing) })
        },
    ) { searching ->
        if (!searching) {
            content()
        } else {
            val inputActive = isActive && transition.currentState && transition.targetState && !transition.isRunning
            val label = stringResource(R.string.settings_search)
            SearchBar(
                expanded = isActive && isSearching,
                modifier = Modifier
                    .horizontalCutoutPadding()
                    .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Top))
                    .padding(vertical = 6.dp),
                onExpandedChange = { expanded ->
                    if (!expanded && isActive) {
                        if (imeInsets.getBottom(density) > 0) keyboard?.hide() else close()
                    }
                },
                inputField = {
                    Box {
                        // 重建输入节点可取消内置的延迟焦点与清空任务，后台页不保留输入会话。
                        key(inputActive) {
                            InputField(
                                query = query,
                                onQueryChange = { if (inputActive) onQueryChange(it) },
                                onSearch = { keyboard?.hide() },
                                expanded = inputActive,
                                enabled = inputActive,
                                onExpandedChange = {},
                                label = "",
                                modifier = Modifier
                                    .testTag(TestTags.Settings.SEARCH_FIELD)
                                    .semantics { contentDescription = label },
                                leadingIcon = {
                                    Icon(
                                        imageVector = AppIcons.Search,
                                        contentDescription = null,
                                        modifier = Modifier.padding(start = 16.dp, end = 8.dp).size(24.dp),
                                        tint = MiuixTheme.colorScheme.onSurfaceContainerHigh,
                                    )
                                },
                                trailingIcon = {
                                    if (query.isNotEmpty()) {
                                        Icon(
                                            imageVector = AppIcons.SearchCleanup,
                                            contentDescription = stringResource(R.string.common_clear),
                                            tint = MiuixTheme.colorScheme.onSurfaceContainerHigh,
                                            modifier = Modifier
                                                .padding(start = 8.dp, end = 16.dp)
                                                .size(24.dp)
                                                .squircleClip(16.dp)
                                                .testTag(TestTags.Settings.SEARCH_CLEAR)
                                                .clickable(enabled = inputActive) { onQueryChange("") },
                                        )
                                    }
                                },
                            )
                        }
                        if (query.isEmpty()) {
                            Text(
                                text = label,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                modifier = Modifier
                                    .align(Alignment.CenterStart)
                                    .padding(start = 48.dp, end = 16.dp)
                                    .clearAndSetSemantics {},
                            )
                        }
                    }
                },
                outsideEndAction = {
                    IconButton(
                        onClick = ::close,
                        modifier = Modifier.padding(end = 16.dp).size(40.dp).testTag(TestTags.Settings.SEARCH_CLOSE),
                    ) {
                        Icon(
                            imageVector = AppIcons.Close,
                            contentDescription = stringResource(R.string.common_close),
                            modifier = Modifier.size(24.dp),
                            tint = MiuixTheme.colorScheme.onSurface,
                        )
                    }
                },
                content = {},
            )
        }
    }
}
