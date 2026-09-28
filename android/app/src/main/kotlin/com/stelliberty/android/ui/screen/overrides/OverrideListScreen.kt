package com.stelliberty.android.ui.screen.overrides

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stelliberty.android.R
import com.stelliberty.android.domain.model.OverrideProfile
import com.stelliberty.android.ui.component.AdaptiveTopAppBar
import com.stelliberty.android.ui.component.blur.BlurredBar
import com.stelliberty.android.ui.component.blur.rememberBlurBackdrop
import com.stelliberty.android.ui.icon.AppIcons
import com.stelliberty.android.ui.theme.StatusColors
import com.stelliberty.android.ui.util.TestTags
import com.stelliberty.android.ui.util.horizontalCutoutPadding
import com.stelliberty.android.util.formatEpochMillisAsLocal
import com.stelliberty.android.viewmodel.OverrideProfileViewModel
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
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import top.yukonga.miuix.kmp.window.WindowDialog

@Composable
fun OverrideListScreen(
    viewModel: OverrideProfileViewModel,
    onBack: () -> Unit = {},
    onAdd: () -> Unit = {},
    onEdit: (id: String) -> Unit = {},
    onEditFile: (id: String) -> Unit = {},
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
                    title = stringResource(R.string.override_title),
                    color = barColor,
                    scrollBehavior = scrollBehavior,
                    navigationIcon = { BackButton(onBack) },
                    actions = {
                        if (uiState.profiles.any { it.isRemote }) {
                            IconButton(
                                onClick = viewModel::updateAll,
                                modifier = Modifier.testTag(TestTags.Overrides.UPDATE_ALL),
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
                            onClick = { viewModel.clearError(); onAdd() },
                            modifier = Modifier.testTag(TestTags.Overrides.ADD),
                        ) {
                            Icon(
                                imageVector = AppIcons.Add,
                                contentDescription = stringResource(R.string.override_add),
                                tint = MiuixTheme.colorScheme.onSurface,
                            )
                        }
                    },
                )
            }
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .horizontalCutoutPadding()
                .then(if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier)
                .scrollEndHaptic()
                .overScrollVertical()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = PaddingValues(top = innerPadding.calculateTopPadding()),
        ) {
            item(key = "top_padding") { Spacer(Modifier.height(12.dp)) }

            if (uiState.error.isNotEmpty()) {
                item(key = "error") {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp)
                            .padding(bottom = 12.dp),
                        insideMargin = PaddingValues(16.dp),
                    ) {
                        Text(text = uiState.error, color = StatusColors.danger)
                    }
                }
            }

            if (uiState.profiles.isEmpty()) {
                item(key = "empty") {
                    Text(
                        text = stringResource(R.string.override_empty),
                        modifier = Modifier
                            .fillParentMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 48.dp),
                        fontSize = 14.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        textAlign = TextAlign.Center,
                    )
                }
            }

            items(uiState.profiles, key = { it.id }) { profile ->
                OverrideItem(
                    profile = profile,
                    isLoading = uiState.isLoading,
                    onClick = { viewModel.clearError(); onEditFile(profile.id) },
                    onEdit = { viewModel.clearError(); onEdit(profile.id) },
                    onRefresh = { viewModel.update(profile.id) },
                    onDelete = { viewModel.delete(profile.id) },
                )
            }

            item(key = "bottom_spacer") {
                Spacer(Modifier.height(24.dp).navigationBarsPadding())
            }
        }
    }
}

@Composable
private fun OverrideItem(
    profile: OverrideProfile,
    isLoading: Boolean,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onRefresh: () -> Unit,
    onDelete: () -> Unit,
) {
    var showDeleteDialog by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .padding(bottom = 12.dp)
            .testTag(TestTags.Overrides.item(profile.id)),
        insideMargin = PaddingValues(16.dp),
        onClick = onClick,
        pressFeedbackType = PressFeedbackType.Sink,
    ) {
        Text(
            text = profile.name,
            fontSize = 17.sp,
            fontWeight = FontWeight(550),
            color = MiuixTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = overrideSummary(profile),
            modifier = Modifier.padding(top = 2.dp),
            fontSize = 12.sp,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                if (profile.sourceLocation.isNotEmpty()) {
                    Text(
                        text = profile.sourceLocation,
                        fontSize = 12.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                profile.lastUpdatedAt?.let {
                    Text(
                        text = stringResource(
                            R.string.subscription_updated_at,
                            formatEpochMillisAsLocal(it.toEpochMilliseconds()),
                        ),
                        modifier = Modifier.padding(top = 2.dp),
                        fontSize = 12.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            ItemAction(
                AppIcons.More,
                stringResource(R.string.common_edit),
                enabled = !isLoading,
                onClick = onEdit,
                modifier = Modifier.testTag(TestTags.Overrides.edit(profile.id)),
            )
            if (profile.isRemote) {
                Spacer(Modifier.width(8.dp))
                ItemAction(
                    AppIcons.Refresh,
                    stringResource(R.string.common_update),
                    enabled = !isLoading,
                    onClick = onRefresh,
                    modifier = Modifier.testTag(TestTags.Overrides.update(profile.id)),
                )
            }
            Spacer(Modifier.width(8.dp))
            ItemAction(
                AppIcons.Delete,
                stringResource(R.string.common_delete),
                enabled = !isLoading,
                onClick = { showDeleteDialog = true },
                modifier = Modifier.testTag(TestTags.Overrides.delete(profile.id)),
            )
        }
    }

    WindowDialog(
        show = showDeleteDialog,
        title = stringResource(R.string.override_delete_title),
        summary = stringResource(R.string.override_delete_summary, profile.name),
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

@Composable
private fun ItemAction(
    icon: ImageVector,
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    IconButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        minHeight = 35.dp,
        minWidth = 35.dp,
        backgroundColor = MiuixTheme.colorScheme.secondaryContainer,
    ) {
        Icon(
            modifier = Modifier.size(20.dp),
            imageVector = icon,
            contentDescription = contentDescription,
            tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
    }
}

@Composable
internal fun BackButton(onBack: () -> Unit) {
    IconButton(onClick = onBack, modifier = Modifier.testTag(TestTags.Nav.BACK)) {
        val layoutDirection = LocalLayoutDirection.current
        Icon(
            imageVector = AppIcons.Back,
            contentDescription = stringResource(R.string.common_back),
            tint = MiuixTheme.colorScheme.onSurface,
            modifier = Modifier.graphicsLayer {
                scaleX = if (layoutDirection == LayoutDirection.Rtl) -1f else 1f
            },
        )
    }
}

// 子页面共用这个 ViewModel，错误只属于出错的那一页，离开时清掉，免得带回列表。
@Composable
internal fun ClearErrorOnExit(viewModel: OverrideProfileViewModel) {
    DisposableEffect(viewModel) {
        onDispose { viewModel.clearError() }
    }
}

// 「远程 · YAML」这类来源与格式标签，列表与选择页共用。
@Composable
internal fun overrideSummary(profile: OverrideProfile): String {
    val source = stringResource(
        if (profile.isRemote) R.string.override_source_remote else R.string.override_source_local
    )
    return "$source · ${profile.format.label}"
}
