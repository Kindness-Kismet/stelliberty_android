package com.stelliberty.android.ui.screen.ruleoverride

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stelliberty.android.BuildConfig
import com.stelliberty.android.R
import com.stelliberty.android.domain.model.RuleTemplate
import com.stelliberty.android.ui.component.AdaptiveTopAppBar
import com.stelliberty.android.ui.component.CardSegment
import com.stelliberty.android.ui.component.DialogButtons
import com.stelliberty.android.ui.component.ListPopupDefaults.MenuPositionProvider
import com.stelliberty.android.ui.component.SearchBarFake
import com.stelliberty.android.ui.component.SearchBox
import com.stelliberty.android.ui.component.SearchPager
import com.stelliberty.android.ui.component.SearchResultStatusEffect
import com.stelliberty.android.ui.component.SearchStatus
import com.stelliberty.android.ui.component.blur.BlurredBar
import com.stelliberty.android.ui.component.blur.rememberBlurBackdrop
import com.stelliberty.android.ui.component.dialogTestTag
import com.stelliberty.android.ui.component.rememberSearchBarTopPadding
import com.stelliberty.android.ui.component.rememberSearchScreenStatus
import com.stelliberty.android.ui.icon.AppIcons
import com.stelliberty.android.ui.screen.chainproxy.HintText
import com.stelliberty.android.ui.screen.overrides.BackButton
import com.stelliberty.android.ui.theme.StatusColors
import com.stelliberty.android.ui.util.TestTags
import com.stelliberty.android.ui.util.horizontalCutoutPadding
import com.stelliberty.android.viewmodel.RuleOverrideRow
import com.stelliberty.android.viewmodel.RuleOverrideViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toPersistentList
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.BasicComponentDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.DropdownImpl
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.ListPopupColumn
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.PopupPositionProvider
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.preference.RadioButtonLocation
import top.yukonga.miuix.kmp.preference.RadioButtonPreference
import top.yukonga.miuix.kmp.squircle.squircleClip
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import top.yukonga.miuix.kmp.window.WindowDialog
import top.yukonga.miuix.kmp.window.WindowListPopup

// 规则改动留在页面草稿里，点保存才写入；模板自身的保存与删除由对应弹窗确认。
@Composable
fun RuleOverrideScreen(
    subscriptionId: String,
    session: String,
    viewModel: RuleOverrideViewModel,
    onBack: () -> Unit = {},
    onAdd: () -> Unit = {},
    onEdit: (String) -> Unit = {},
) {
    LaunchedEffect(subscriptionId, session) { viewModel.open(subscriptionId, session) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val ready = state.subscriptionId == subscriptionId && state.isReady
    val editable = ready && !state.isSaving
    val rows = state.rows

    val scrollBehavior = MiuixScrollBehavior()
    val density = LocalDensity.current
    val searchStatusState = rememberSearchScreenStatus(stringResource(R.string.rule_override_search))
    var searchStatus by searchStatusState
    val query = searchStatus.searchText.trim()
    val allIndices = remember(rows) { rows.indices.toPersistentList() }
    val matches = remember(rows, query) {
        if (query.isEmpty()) allIndices else rows.indices.filter { rows[it].matches(query) }.toPersistentList()
    }
    SearchResultStatusEffect(searchStatusState, matches.isEmpty())
    val dynamicTopPadding = rememberSearchBarTopPadding(scrollBehavior)

    var showMenu by remember { mutableStateOf(false) }
    var showTemplatePicker by remember { mutableStateOf(false) }
    var showTemplateSave by remember { mutableStateOf(false) }

    val backdrop = rememberBlurBackdrop()
    val blurActive = backdrop != null
    val barColor = if (blurActive) Color.Transparent else MiuixTheme.colorScheme.surface

    Scaffold(
        topBar = {
            BlurredBar(backdrop = backdrop, blurActive = blurActive) {
                searchStatus.TopAppBarAnim(backgroundColor = barColor) {
                    AdaptiveTopAppBar(
                        title = stringResource(R.string.rule_override_title),
                        color = barColor,
                        scrollBehavior = scrollBehavior,
                        navigationIcon = { BackButton(onBack) },
                        actions = {
                            BarAction(AppIcons.Add, stringResource(R.string.rule_override_add), editable, onAdd, TestTags.RuleOverride.ADD)
                            Box {
                                IconButton(
                                    enabled = editable,
                                    onClick = { showMenu = true },
                                    holdDownState = showMenu,
                                    modifier = Modifier.testTag(TestTags.RuleOverride.MORE),
                                ) {
                                    Icon(
                                        imageVector = AppIcons.More,
                                        contentDescription = stringResource(R.string.common_more),
                                        tint = barIconTint(editable),
                                    )
                                }
                                MoreMenu(
                                    show = showMenu,
                                    canApplyTemplate = state.templates.isNotEmpty(),
                                    canSaveTemplate = state.customRules.isNotEmpty(),
                                    canResetOrder = state.hasCustomOrder,
                                    onDismiss = { showMenu = false },
                                    onApplyTemplate = { showTemplatePicker = true },
                                    onSaveTemplate = { showTemplateSave = true },
                                    onResetOrder = viewModel::resetOrder,
                                )
                            }
                            BarAction(
                                AppIcons.Check,
                                stringResource(R.string.common_save),
                                editable && state.hasChanges,
                                { viewModel.save(onBack) },
                                TestTags.RuleOverride.SAVE,
                            )
                        },
                        bottomContent = {
                            Box(
                                modifier = Modifier
                                    .alpha(if (searchStatus.isCollapsed()) 1f else 0f)
                                    .onGloballyPositioned { coordinates ->
                                        with(density) {
                                            val offsetY = coordinates.positionInWindow().y.toDp()
                                            if (searchStatus.offsetY != offsetY) {
                                                searchStatus = searchStatus.copy(offsetY = offsetY)
                                            }
                                        }
                                    }
                                    .then(
                                        if (searchStatus.isCollapsed() && ready) {
                                            Modifier.pointerInput(Unit) {
                                                detectTapGestures {
                                                    searchStatus = searchStatus.copy(current = SearchStatus.Status.EXPANDING)
                                                }
                                            }
                                        } else Modifier,
                                    ),
                            ) {
                                SearchBarFake(searchStatus.label, dynamicTopPadding)
                            }
                        },
                    )
                }
            }
        },
        popupHost = {
            searchStatus.SearchPager(
                onSearchStatusChange = { searchStatus = it },
                searchBarTopPadding = dynamicTopPadding,
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .overScrollVertical()
                        .imePadding(),
                ) {
                    item(key = "top") { Spacer(Modifier.height(6.dp)) }
                    ruleRows(rows, matches, editable, viewModel, onEdit)
                    item(key = "bottom_spacer") { Spacer(Modifier.height(24.dp).navigationBarsPadding()) }
                }
            }
        },
    ) { innerPadding ->
        searchStatus.SearchBox {
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
                if (state.error.isNotEmpty()) {
                    item(key = "error") {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp)
                                .padding(bottom = 12.dp),
                            insideMargin = PaddingValues(16.dp),
                        ) {
                            Text(text = state.error, color = StatusColors.danger)
                        }
                    }
                }
                when {
                    state.isLoading || state.subscriptionId != subscriptionId -> item(key = "loading") {
                        HintText(stringResource(R.string.rule_override_loading))
                    }
                    !state.isReady -> Unit
                    rows.isEmpty() -> item(key = "empty") { HintText(stringResource(R.string.rule_override_empty)) }
                    else -> {
                        item(key = "hint") { HintText(stringResource(R.string.rule_override_hint)) }
                        ruleRows(rows, allIndices, editable, viewModel, onEdit)
                    }
                }
                item(key = "bottom_spacer") { Spacer(Modifier.height(24.dp).navigationBarsPadding()) }
            }
        }
    }

    TemplatePickerDialog(
        show = showTemplatePicker,
        templates = state.templates,
        onDismiss = { showTemplatePicker = false },
        onApply = { template ->
            showTemplatePicker = false
            viewModel.applyTemplate(template)
        },
        onDelete = { viewModel.deleteTemplate(it.id) },
    )
    TemplateSaveDialog(
        show = showTemplateSave,
        onDismiss = { showTemplateSave = false },
        onConfirm = { name ->
            showTemplateSave = false
            viewModel.saveTemplate(name)
        },
    )
}

// indices 指向 rows，序号按完整列表计算，搜索结果里也显示规则在运行配置中的位置。
private fun LazyListScope.ruleRows(
    rows: ImmutableList<RuleOverrideRow>,
    indices: ImmutableList<Int>,
    enabled: Boolean,
    viewModel: RuleOverrideViewModel,
    onEdit: (String) -> Unit,
) {
    itemsIndexed(indices, key = { _, index -> rows[index].orderId }, contentType = { _, _ -> "rule" }) { position, index ->
        val row = rows[index]
        RuleRowItem(
            number = index + 1,
            row = row,
            isFirst = position == 0,
            isLast = position == indices.lastIndex,
            enabled = enabled,
            onToggle = {
                val id = row.customId
                if (id == null) viewModel.toggleBuiltin(row.key) else viewModel.toggleCustom(id)
            },
            onEdit = { row.customId?.let(onEdit) },
        )
    }
}

@Composable
private fun RuleRowItem(
    number: Int,
    row: RuleOverrideRow,
    isFirst: Boolean,
    isLast: Boolean,
    enabled: Boolean,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
) {
    val title = if (row.isBuiltin) "$number. ${row.type}"
    else "$number. ${row.type} · ${stringResource(R.string.rule_override_custom_tag)}"
    val summary = buildString {
        if (row.payload.isNotEmpty()) append(row.payload).append(" → ")
        append(row.proxy)
        if (row.options.isNotEmpty()) append(" · ").append(row.options)
    }
    CardSegment(
        isFirst = isFirst,
        isLast = isLast,
        outerBottomPadding = if (isLast) 12.dp else 0.dp,
        modifier = Modifier.testTag(TestTags.RuleOverride.row(number)),
    ) {
        BasicComponent(
            title = title,
            summary = if (row.missingTarget) "$summary\n${stringResource(R.string.rule_override_missing_target)}" else summary,
            summaryColor = if (row.missingTarget) BasicComponentDefaults.summaryColor(color = StatusColors.danger)
            else BasicComponentDefaults.summaryColor(),
            endActions = {
                Switch(
                    checked = row.isEnabled,
                    onCheckedChange = { onToggle() },
                    enabled = enabled,
                    modifier = Modifier.testTag(TestTags.RuleOverride.toggle(number)),
                )
            },
            onClick = if (row.isBuiltin || !enabled) null else onEdit,
        )
    }
}

@Composable
private fun MoreMenu(
    show: Boolean,
    canApplyTemplate: Boolean,
    canSaveTemplate: Boolean,
    canResetOrder: Boolean,
    onDismiss: () -> Unit,
    onApplyTemplate: () -> Unit,
    onSaveTemplate: () -> Unit,
    onResetOrder: () -> Unit,
) {
    WindowListPopup(
        show = show,
        popupPositionProvider = MenuPositionProvider,
        alignment = PopupPositionProvider.Align.TopEnd,
        onDismissRequest = onDismiss,
    ) {
        val entries = listOf(
            Triple(R.string.rule_override_apply_template, canApplyTemplate, onApplyTemplate),
            Triple(R.string.rule_override_save_template, canSaveTemplate, onSaveTemplate),
            Triple(R.string.rule_override_reset_order, canResetOrder, onResetOrder),
        )
        ListPopupColumn {
            entries.forEachIndexed { index, (text, enabled, onClick) ->
                Box(
                    Modifier
                        .semantics { testTagsAsResourceId = BuildConfig.DEBUG }
                        .testTag(TestTags.RuleOverride.menu(index)),
                ) {
                    DropdownImpl(
                        text = stringResource(text),
                        optionSize = entries.size,
                        isSelected = false,
                        index = index,
                        enabled = enabled,
                        onSelectedIndexChange = {
                            onDismiss()
                            onClick()
                        },
                    )
                }
            }
        }
    }
}

// 模板选择与删除先记录草稿，确定后再分别合并规则与写回模板列表。
@Composable
private fun TemplatePickerDialog(
    show: Boolean,
    templates: ImmutableList<RuleTemplate>,
    onDismiss: () -> Unit,
    onApply: (RuleTemplate) -> Unit,
    onDelete: (RuleTemplate) -> Unit,
) {
    var selectedId by remember { mutableStateOf<String?>(null) }
    var deletedIds by remember { mutableStateOf(persistentSetOf<String>()) }
    LaunchedEffect(show) {
        if (show) {
            selectedId = null
            deletedIds = persistentSetOf()
        }
    }
    WindowDialog(
        modifier = Modifier.dialogTestTag(TestTags.Dialog.TEMPLATE_PICKER),
        show = show,
        title = stringResource(R.string.rule_override_apply_template),
        onDismissRequest = onDismiss,
    ) {
        Column(Modifier.heightIn(max = 500.dp)) {
            Card(Modifier.fillMaxWidth().weight(1f, fill = false)) {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    templates.filter { it.id !in deletedIds }.forEach { template ->
                        RadioButtonPreference(
                            title = template.name,
                            selected = selectedId == template.id,
                            radioButtonLocation = RadioButtonLocation.End,
                            summary = pluralStringResource(R.plurals.rule_override_template_count, template.rules.size, template.rules.size),
                            modifier = Modifier.squircleClip(16.dp).testTag(TestTags.RuleOverride.template(template.id)),
                            endActions = {
                                IconButton(
                                    onClick = {
                                        deletedIds = deletedIds.adding(template.id)
                                        if (selectedId == template.id) selectedId = null
                                    },
                                    modifier = Modifier.testTag(TestTags.RuleOverride.deleteTemplate(template.id)),
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
                            },
                            onClick = { selectedId = template.id },
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            DialogButtons(
                onCancel = onDismiss,
                confirmEnabled = selectedId != null || deletedIds.isNotEmpty(),
                onConfirm = {
                    templates.filter { it.id in deletedIds }.forEach(onDelete)
                    templates.firstOrNull { it.id == selectedId }?.let(onApply)
                    onDismiss()
                },
            )
        }
    }
}

@Composable
private fun TemplateSaveDialog(show: Boolean, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(show) {
        if (show) name = ""
    }
    WindowDialog(
        modifier = Modifier.dialogTestTag(TestTags.Dialog.TEMPLATE_SAVE),
        show = show,
        title = stringResource(R.string.rule_override_save_template),
        summary = stringResource(R.string.rule_override_save_template_summary),
        onDismissRequest = onDismiss,
    ) {
        TextField(
            value = name,
            onValueChange = { name = it },
            label = stringResource(R.string.rule_override_template_name),
            useLabelAsPlaceholder = true,
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .semantics { testTagsAsResourceId = BuildConfig.DEBUG }
                .testTag(TestTags.RuleOverride.TEMPLATE_NAME),
        )
        Spacer(Modifier.height(12.dp))
        DialogButtons(
            onCancel = onDismiss,
            onConfirm = { onConfirm(name) },
            confirmEnabled = name.isNotBlank(),
        )
    }
}

@Composable
private fun BarAction(
    icon: ImageVector,
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit,
    testTag: String,
) {
    IconButton(enabled = enabled, onClick = onClick, modifier = Modifier.testTag(testTag)) {
        Icon(imageVector = icon, contentDescription = contentDescription, tint = barIconTint(enabled))
    }
}

@Composable
private fun barIconTint(enabled: Boolean): Color =
    if (enabled) MiuixTheme.colorScheme.onSurface else MiuixTheme.colorScheme.disabledOnSecondaryVariant

// 同 PC：在类型、匹配内容、出站目标与附加参数里不区分大小写地查找。
private fun RuleOverrideRow.matches(query: String): Boolean =
    listOf(type, payload, proxy, options).joinToString(" ").contains(query, ignoreCase = true)
