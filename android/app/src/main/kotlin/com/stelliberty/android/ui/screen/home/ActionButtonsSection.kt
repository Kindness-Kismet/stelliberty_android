package com.stelliberty.android.ui.screen.home

import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stelliberty.android.R
import com.stelliberty.android.ui.theme.ActionKind
import com.stelliberty.android.ui.theme.StatusColors
import com.stelliberty.android.ui.util.TestTags
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonColors
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.ProgressIndicatorDefaults
import top.yukonga.miuix.kmp.basic.Text
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
    requestedAction: HomeControlAction? = null,
) {
    item(key = "actions") {
        ActionButtonsRow(onRestart, onStop, onReload, onStart, isRunning, isStarting, isStopping, requestedAction)
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
    requestedAction: HomeControlAction?,
) {
    val pending = if (isStarting || isStopping) {
        requestedAction ?: if (isStopping) HomeControlAction.Stop else HomeControlAction.Start
    } else null
    val reloading = pending == HomeControlAction.Reload
    val stopping = pending == HomeControlAction.Stop
    val restarting = pending == HomeControlAction.Start || pending == HomeControlAction.Restart
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
                isRunning || isStarting || isStopping -> {
                    FlatActionButton(
                        text = stringResource(if (reloading) R.string.home_reloading else R.string.home_reload),
                        tint = StatusColors.actionButton(ActionKind.Reload).content,
                        onClick = onReload,
                        modifier = Modifier.weight(1f).testTag(TestTags.Home.RELOAD),
                        enabled = !isStarting && !isStopping,
                        background = if (reloading) StatusColors.info.copy(alpha = 0.08f) else Color.Transparent,
                        loading = reloading,
                    )
                    ActionSeparator()
                    FlatActionButton(
                        text = stringResource(if (stopping) R.string.home_stopping else R.string.home_stop),
                        tint = if (stopping) StatusColors.warning else StatusColors.actionButton(ActionKind.Stop).content,
                        onClick = onStop,
                        modifier = Modifier.weight(1f).testTag(TestTags.Home.STOP),
                        enabled = !isStarting && !isStopping,
                        background = if (stopping) StatusColors.warning.copy(alpha = 0.08f) else Color.Transparent,
                        loading = stopping,
                    )
                    ActionSeparator()
                    FlatActionButton(
                        text = stringResource(when (pending) {
                            HomeControlAction.Start -> R.string.home_starting
                            HomeControlAction.Restart -> R.string.home_restarting
                            else -> R.string.home_restart
                        }),
                        tint = if (restarting) MiuixTheme.colorScheme.primary else StatusColors.actionButton(ActionKind.Restart).content,
                        onClick = onRestart,
                        modifier = Modifier.weight(1f).testTag(TestTags.Home.RESTART),
                        enabled = !isStarting && !isStopping,
                        background = if (restarting) MiuixTheme.colorScheme.primary.copy(alpha = 0.08f) else Color.Transparent,
                        loading = restarting,
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
    loading: Boolean = false,
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        cornerRadius = 12.dp,
        minHeight = ActionButtonHeight,
        insideMargin = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
        colors = ButtonColors(
            color = background,
            disabledColor = background,
            contentColor = tint,
            disabledContentColor = if (background != Color.Transparent) tint
            else MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = DisabledContentAlpha),
        ),
    ) {
        if (loading) {
            CircularProgressIndicator(
                size = 14.dp,
                strokeWidth = 1.5.dp,
                colors = ProgressIndicatorDefaults.progressIndicatorColors(
                    foregroundColor = tint,
                    backgroundColor = Color.Transparent,
                ),
            )
            Spacer(Modifier.width(6.dp))
        }
        Text(text, fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
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

private const val DisabledContentAlpha = 0.35f
