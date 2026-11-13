package com.stelliberty.android.ui.screen.home

import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stelliberty.android.R
import com.stelliberty.android.ui.theme.ActionKind
import com.stelliberty.android.ui.theme.StatusColors
import com.stelliberty.android.ui.util.TestTags
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextButtonColors
import top.yukonga.miuix.kmp.basic.VerticalDivider
import top.yukonga.miuix.kmp.theme.MiuixTheme

enum class HomeControlAction(val labelRes: Int) {
    Start(R.string.home_starting),
    Stop(R.string.home_stopping),
    Restart(R.string.home_restarting),
    Reload(R.string.home_reloading),
}

fun LazyListScope.actionButtonsSection(
    onRestart: () -> Unit = {},
    onStop: () -> Unit = {},
    onReload: () -> Unit = {},
    onStart: () -> Unit = {},
    isRunning: Boolean = false,
    isStarting: Boolean = false,
    isStopping: Boolean = false,
) {
    item(key = "actions") {
        ActionButtonsRow(onRestart, onStop, onReload, onStart, isRunning, isStarting, isStopping)
    }
}

@Composable
private fun ActionButtonsRow(
    onRestart: () -> Unit,
    onStop: () -> Unit,
    onReload: () -> Unit,
    onStart: () -> Unit,
    isRunning: Boolean,
    isStarting: Boolean,
    isStopping: Boolean,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .padding(vertical = 6.dp),
        cornerRadius = 16.dp,
        insideMargin = PaddingValues(4.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
        ) {
            when {
                isStopping -> FlatActionButton(
                    text = stringResource(R.string.home_stopping_btn),
                    tint = MiuixTheme.colorScheme.primary,
                    onClick = {},
                    modifier = Modifier.weight(1f),
                    enabled = false,
                )

                isStarting -> FlatActionButton(
                    text = stringResource(R.string.home_starting_btn),
                    tint = StatusColors.warning,
                    onClick = {},
                    modifier = Modifier.weight(1f),
                    enabled = false,
                )

                isRunning -> {
                    FlatActionButton(
                        text = stringResource(R.string.home_reload),
                        tint = StatusColors.actionButton(ActionKind.Reload).content,
                        onClick = onReload,
                        modifier = Modifier.weight(1f).testTag(TestTags.Home.RELOAD),
                    )
                    ActionSeparator()
                    FlatActionButton(
                        text = stringResource(R.string.home_stop),
                        tint = StatusColors.actionButton(ActionKind.Stop).content,
                        onClick = onStop,
                        modifier = Modifier.weight(1f).testTag(TestTags.Home.STOP),
                    )
                    ActionSeparator()
                    FlatActionButton(
                        text = stringResource(R.string.home_restart),
                        tint = StatusColors.actionButton(ActionKind.Restart).content,
                        onClick = onRestart,
                        modifier = Modifier.weight(1f).testTag(TestTags.Home.RESTART),
                    )
                }

                else -> FlatActionButton(
                    text = stringResource(R.string.home_start),
                    tint = MiuixTheme.colorScheme.onPrimary,
                    onClick = onStart,
                    modifier = Modifier.weight(1f).testTag(TestTags.Home.START),
                    background = MiuixTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun FlatActionButton(
    text: String,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    background: Color = Color.Transparent,
) {
    TextButton(
        text = text,
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        cornerRadius = 12.dp,
        minHeight = ActionButtonHeight,
        insideMargin = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
        textStyle = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold),
        colors = TextButtonColors(
            color = background,
            disabledColor = background,
            textColor = tint,
            disabledTextColor = tint.copy(alpha = DisabledContentAlpha),
        ),
    )
}

@Composable
private fun ActionSeparator() {
    VerticalDivider(
        modifier = Modifier
            .fillMaxHeight()
            .padding(vertical = ActionSeparatorInset),
        thickness = 1.dp,
    )
}

private val ActionButtonHeight = 38.dp

private val ActionSeparatorInset = 6.dp

private const val DisabledContentAlpha = 0.5f
