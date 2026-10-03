package com.stelliberty.android.ui.screen.subscription

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stelliberty.android.R
import com.stelliberty.android.domain.model.Subscription
import com.stelliberty.android.ui.component.AdaptiveTopAppBar
import com.stelliberty.android.ui.component.blur.BlurredBar
import com.stelliberty.android.ui.component.blur.rememberBlurBackdrop
import com.stelliberty.android.ui.icon.AppIcons
import com.stelliberty.android.ui.theme.StatusColors
import com.stelliberty.android.ui.util.TestTags
import com.stelliberty.android.ui.util.WideContentBox
import com.stelliberty.android.util.FormatUtils
import com.stelliberty.android.util.formatEpochMillisAsLocal
import com.stelliberty.android.viewmodel.ProfileOperation
import com.stelliberty.android.viewmodel.SubscriptionViewModel
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.squircle.squircleBackground
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import top.yukonga.miuix.kmp.window.WindowDialog

@Composable
fun SubscriptionScreen(
    viewModel: SubscriptionViewModel,
    bottomPadding: Dp = 0.dp,
    onNavigateAdd: () -> Unit = {},
    onNavigateEdit: (uuid: String) -> Unit = {},
    onActiveChanged: (() -> Unit)? = null,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scrollBehavior = MiuixScrollBehavior()

    val backdrop = rememberBlurBackdrop()
    val blurActive = backdrop != null
    val barColor = if (blurActive) Color.Transparent else MiuixTheme.colorScheme.surface

    Scaffold(
        topBar = {
            BlurredBar(backdrop = backdrop, blurActive = blurActive) {
                AdaptiveTopAppBar(
                    title = stringResource(R.string.subscription_title),
                    color = barColor,
                    scrollBehavior = scrollBehavior,
                    navigationIcon = {},
                    actions = {
                        if (uiState.subscriptions.any { !it.isLocalFile }) {
                            IconButton(
                                onClick = { viewModel.updateAllSubscriptions() },
                                modifier = Modifier.testTag(TestTags.Subscription.UPDATE_ALL),
                                enabled = !uiState.isLoading,
                            ) {
                                Icon(
                                    imageVector = AppIcons.Refresh,
                                    contentDescription = stringResource(R.string.subscription_update_all),
                                    tint = MiuixTheme.colorScheme.onSurface,
                                )
                            }
                        }
                        IconButton(
                            onClick = { viewModel.clearError(); onNavigateAdd() },
                            modifier = Modifier.testTag(TestTags.Subscription.ADD),
                        ) {
                            Icon(
                                imageVector = AppIcons.Add,
                                contentDescription = stringResource(R.string.subscription_add),
                                tint = MiuixTheme.colorScheme.onSurface,
                            )
                        }
                    },
                )
            }
        },
    ) { innerPadding ->
        WideContentBox { sidePadding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier)
                    .scrollEndHaptic()
                    .overScrollVertical()
                    .nestedScroll(scrollBehavior.nestedScrollConnection),
                contentPadding = PaddingValues(
                    top = innerPadding.calculateTopPadding(),
                    bottom = bottomPadding,
                    start = sidePadding,
                    end = sidePadding,
                ),
            ) {
                if (uiState.subscriptions.isNotEmpty()) {
                    item(key = "top_padding") {
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }

                if (uiState.error.isNotEmpty()) {
                    item(key = "error") {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp)
                                .padding(bottom = 12.dp),
                            insideMargin = PaddingValues(16.dp),
                        ) {
                            Text(
                                text = uiState.error,
                                color = StatusColors.danger,
                            )
                        }
                    }
                }

                if (uiState.subscriptions.isEmpty()) {
                    item(key = "empty") {
                        Column(
                            modifier = Modifier.fillParentMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Text(
                                text = stringResource(R.string.subscription_no_config),
                                fontSize = 16.sp,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                            Text(
                                text = stringResource(R.string.subscription_tap_add),
                                modifier = Modifier.padding(top = 6.dp),
                                fontSize = 14.sp,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                        }
                    }
                }

                items(uiState.subscriptions, key = { it.id }) { sub ->
                    SubscriptionItem(
                        subscription = sub,
                        isActive = sub.id == uiState.currentId,
                        isLoading = uiState.isLoading,
                        onSelect = {
                            viewModel.setActive(sub.id)
                            onActiveChanged?.invoke()
                        },
                        onRefresh = { viewModel.fetchSubscription(sub.id) },
                        onDelete = {
                            viewModel.removeSubscription(sub.id) { onActiveChanged?.invoke() }
                        },
                        onEdit = { onNavigateEdit(sub.id) },
                    )
                }
            }
        }
    }

    val title = when (uiState.operation) {
        ProfileOperation.Update -> stringResource(R.string.subscription_update_config)
        else -> stringResource(R.string.subscription_import_config)
    }
    ImportProgressDialog(
        show = uiState.importProgress != null,
        step = uiState.importProgress?.let { importStepLabel(it) } ?: stringResource(R.string.common_processing),
        title = title,
        onCancel = { viewModel.cancelCurrentUpdate() },
    )
}

@Composable
private fun SubscriptionItem(
    subscription: Subscription,
    isActive: Boolean,
    isLoading: Boolean,
    onSelect: () -> Unit,
    onRefresh: () -> Unit,
    onDelete: () -> Unit,
    onEdit: () -> Unit,
) {
    var showDeleteDialog by remember { mutableStateOf(false) }
    val displayName = subscription.name.ifBlank { stringResource(R.string.subscription_config) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .padding(bottom = 12.dp)
            .testTag(TestTags.Subscription.item(subscription.id)),
        insideMargin = PaddingValues(16.dp),
        onClick = onSelect,
        pressFeedbackType = PressFeedbackType.Sink,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = subscription.name.ifBlank { stringResource(R.string.subscription_config) },
                modifier = Modifier.weight(1f),
                fontSize = 17.sp,
                fontWeight = FontWeight(550),
                color = MiuixTheme.colorScheme.onSurface,
            )
            if (isActive) {
                val activeColor = StatusColors.healthy
                Text(
                    text = stringResource(R.string.subscription_in_use),
                    fontSize = 12.sp,
                    fontWeight = FontWeight(750),
                    color = activeColor,
                    modifier = Modifier
                        .squircleBackground(activeColor.copy(alpha = 0.15f), 6.dp)
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        Row {

            Column(
                modifier = Modifier.wrapContentSize()
            ) {
                val traffic = subscription.trafficInfo
                if (traffic != null && traffic.total > 0) {
                    Text(
                        text = stringResource(
                            R.string.subscription_used_traffic,
                            FormatUtils.formatBytes(traffic.used),
                            FormatUtils.formatBytes(traffic.total)
                        ),
                        modifier = Modifier.padding(top = 2.dp),
                        fontSize = 12.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                } else {
                    Text(
                        text = stringResource(R.string.subscription_no_traffic),
                        modifier = Modifier.padding(top = 2.dp),
                        fontSize = 12.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }

                Spacer(Modifier.weight(1f))

                val updatedAt = (subscription.lastUpdatedAt ?: subscription.createdAt).toEpochMilliseconds()
                if (updatedAt > 0) {
                    Text(
                        text = stringResource(R.string.subscription_updated_at, formatEpochMillisAsLocal(updatedAt)),
                        modifier = Modifier.padding(top = 2.dp),
                        fontSize = 12.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
            }

            Spacer(Modifier.weight(1f))

            IconButton(
                onClick = onEdit,
                minHeight = 35.dp,
                minWidth = 35.dp,
                backgroundColor = MiuixTheme.colorScheme.secondaryContainer,
            ) {
                Icon(
                    modifier = Modifier.size(20.dp),
                    imageVector = AppIcons.More,
                    contentDescription = stringResource(R.string.common_edit),
                    tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
            Spacer(Modifier.width(8.dp))
            IconButton(
                onClick = onRefresh,
                enabled = !isLoading && !subscription.isLocalFile,
                minHeight = 35.dp,
                minWidth = 35.dp,
                backgroundColor = MiuixTheme.colorScheme.secondaryContainer,
            ) {
                Icon(
                    modifier = Modifier.size(20.dp),
                    imageVector = AppIcons.Refresh,
                    contentDescription = stringResource(R.string.common_update),
                    tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
            Spacer(Modifier.width(8.dp))
            IconButton(
                onClick = { showDeleteDialog = true },
                minHeight = 35.dp,
                minWidth = 35.dp,
                backgroundColor = MiuixTheme.colorScheme.secondaryContainer,
            ) {
                Icon(
                    modifier = Modifier.size(20.dp),
                    imageVector = AppIcons.Delete,
                    contentDescription = stringResource(R.string.common_delete),
                    tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
        }

        subscription.lastError?.takeIf { it.isNotBlank() }?.let { error ->
            Text(
                text = stringResource(R.string.subscription_last_error, error),
                modifier = Modifier.padding(top = 8.dp),
                fontSize = 12.sp,
                color = StatusColors.danger,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }

    WindowDialog(
        show = showDeleteDialog,
        title = stringResource(R.string.subscription_delete_title),
        summary = stringResource(R.string.subscription_delete_summary, displayName),
        onDismissRequest = { showDeleteDialog = false },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TextButton(
                text = stringResource(R.string.common_cancel),
                modifier = Modifier.weight(1f),
                onClick = { showDeleteDialog = false },
            )
            TextButton(
                text = stringResource(R.string.common_confirm),
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.textButtonColorsPrimary(),
                onClick = {
                    showDeleteDialog = false
                    onDelete()
                },
            )
        }
    }
}
