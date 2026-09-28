package com.stelliberty.android.ui.screen.overrides

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.style.TextAlign
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
import com.stelliberty.android.viewmodel.OverrideProfileViewModel
import com.stelliberty.android.viewmodel.SubscriptionViewModel
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Checkbox
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

// 勾选决定用哪些覆写，列表顺序即应用顺序；保存时整份顺序写入 OverrideSortPreference，与 PC 一致。
@Composable
fun SubscriptionOverridesScreen(
    subscriptionId: String,
    subscriptionViewModel: SubscriptionViewModel,
    overrideViewModel: OverrideProfileViewModel,
    onBack: () -> Unit = {},
) {
    val subscriptionState by subscriptionViewModel.uiState.collectAsStateWithLifecycle()
    val overrideState by overrideViewModel.uiState.collectAsStateWithLifecycle()
    val subscription = subscriptionState.subscriptions.find { it.id == subscriptionId }
    if (subscription == null) {
        LaunchedEffect(Unit) { onBack() }
        return
    }
    val profiles = overrideState.profiles
    val profileIds = remember(profiles) { profiles.map { it.id }.toSet() }
    val initialOrder = remember(profiles, subscription.overrideSortPreference) {
        val rank = subscription.overrideSortPreference.withIndex().associate { it.value to it.index }
        profiles.sortedWith(compareBy<OverrideProfile> { rank[it.id] ?: Int.MAX_VALUE }.thenBy { it.name })
            .map { it.id }
    }
    val initialSelected = remember(profileIds, subscription.orderedOverrideIds) {
        subscription.orderedOverrideIds.filter { it in profileIds }
    }

    var order by rememberSaveable(subscriptionId) { mutableStateOf(initialOrder) }
    var selected by rememberSaveable(subscriptionId) { mutableStateOf(initialSelected) }
    // 页面打开期间覆写被删或新增时，按现有列表修正顺序。
    val displayed = order.filter { it in profileIds } + initialOrder.filter { it !in order }
    val profilesById = remember(profiles) { profiles.associateBy { it.id } }
    val selectedIds = displayed.filter { it in selected }
    val hasChanges = selectedIds != initialSelected || displayed != initialOrder

    LaunchedEffect(Unit) { overrideViewModel.clearError() }
    ClearErrorOnExit(overrideViewModel)

    val scrollBehavior = MiuixScrollBehavior()
    val backdrop = rememberBlurBackdrop()
    val blurActive = backdrop != null
    val barColor = if (blurActive) Color.Transparent else MiuixTheme.colorScheme.surface

    Scaffold(
        topBar = {
            BlurredBar(backdrop = backdrop, blurActive = blurActive) {
                AdaptiveTopAppBar(
                    title = stringResource(R.string.override_select_title),
                    color = barColor,
                    scrollBehavior = scrollBehavior,
                    navigationIcon = { BackButton(onBack) },
                    actions = {
                        val canSave = hasChanges && !overrideState.isLoading
                        IconButton(
                            enabled = canSave,
                            modifier = Modifier.testTag(TestTags.Overrides.SAVE),
                            onClick = {
                                overrideViewModel.setSelection(subscriptionId, selectedIds, displayed, onBack)
                            },
                        ) {
                            Icon(
                                imageVector = AppIcons.Check,
                                contentDescription = stringResource(R.string.common_save),
                                tint = if (canSave) MiuixTheme.colorScheme.onSurface
                                else MiuixTheme.colorScheme.disabledOnSecondaryVariant,
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

            if (overrideState.error.isNotEmpty()) {
                item(key = "error") {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp)
                            .padding(bottom = 12.dp),
                        insideMargin = PaddingValues(16.dp),
                    ) {
                        Text(text = overrideState.error, color = StatusColors.danger)
                    }
                }
            }

            if (displayed.isEmpty()) {
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

            itemsIndexed(displayed, key = { _, id -> id }) { index, id ->
                val profile = profilesById[id] ?: return@itemsIndexed
                val checked = id in selected
                SelectionItem(
                    profile = profile,
                    checked = checked,
                    canMoveUp = index > 0,
                    canMoveDown = index < displayed.lastIndex,
                    onToggle = { selected = if (checked) selected - id else selected + id },
                    onMoveUp = { order = displayed.swap(index, index - 1) },
                    onMoveDown = { order = displayed.swap(index, index + 1) },
                    modifier = Modifier.animateItem(),
                )
            }

            item(key = "bottom_spacer") {
                Spacer(Modifier.height(24.dp).navigationBarsPadding())
            }
        }
    }
}

@Composable
private fun SelectionItem(
    profile: OverrideProfile,
    checked: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onToggle: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .padding(horizontal = 12.dp)
            .padding(bottom = 12.dp)
            .testTag(TestTags.Overrides.select(profile.id)),
    ) {
        BasicComponent(
            title = profile.name,
            summary = overrideSummary(profile),
            startAction = {
                Checkbox(
                    state = if (checked) ToggleableState.On else ToggleableState.Off,
                    onClick = onToggle,
                    modifier = Modifier.padding(end = 12.dp),
                )
            },
            endActions = {
                MoveButton(AppIcons.MoveUp, stringResource(R.string.override_move_up), canMoveUp, onMoveUp, TestTags.Overrides.moveUp(profile.id))
                MoveButton(AppIcons.MoveDown, stringResource(R.string.override_move_down), canMoveDown, onMoveDown, TestTags.Overrides.moveDown(profile.id))
            },
            onClick = onToggle,
        )
    }
}

@Composable
private fun MoveButton(icon: ImageVector, contentDescription: String, enabled: Boolean, onClick: () -> Unit, testTag: String) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.testTag(testTag)) {
        Icon(
            modifier = Modifier.size(24.dp),
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (enabled) MiuixTheme.colorScheme.onSurfaceVariantSummary
            else MiuixTheme.colorScheme.disabledOnSecondaryVariant,
        )
    }
}

private fun List<String>.swap(from: Int, to: Int): List<String> =
    toMutableList().also { it[from] = this[to]; it[to] = this[from] }
