package com.stelliberty.android.ui.screen.chainproxy

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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stelliberty.android.R
import com.stelliberty.android.domain.model.ChainProxyContext
import com.stelliberty.android.domain.model.ChainProxyOption
import com.stelliberty.android.domain.model.SubscriptionChainProxyHop
import com.stelliberty.android.domain.model.SubscriptionChainProxyHopKind
import com.stelliberty.android.domain.model.SubscriptionCustomChainProxy
import com.stelliberty.android.ui.component.AdaptiveTopAppBar
import com.stelliberty.android.ui.component.CardItem
import com.stelliberty.android.ui.component.blur.BlurredBar
import com.stelliberty.android.ui.component.blur.rememberBlurBackdrop
import com.stelliberty.android.ui.component.groupedCardItems
import com.stelliberty.android.ui.icon.AppIcons
import com.stelliberty.android.ui.screen.overrides.BackButton
import com.stelliberty.android.ui.theme.StatusColors
import com.stelliberty.android.ui.util.TestTags
import com.stelliberty.android.ui.util.horizontalCutoutPadding
import com.stelliberty.android.viewmodel.ChainProxyViewModel
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Checkbox
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

// 规则同 PC：至少两个跳点；代理组只能作为首跳，且不能是这条链所属的代理组，否则链会绕回自身。
@OptIn(ExperimentalUuidApi::class)
@Composable
fun ChainProxyEditScreen(
    subscriptionId: String,
    chainId: String?,
    viewModel: ChainProxyViewModel,
    onBack: () -> Unit = {},
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val existing = chainId?.let { id -> state.customChainProxies.find { it.id == id } }
    if (state.subscriptionId != subscriptionId || state.isLoading || (chainId != null && existing == null)) {
        LaunchedEffect(Unit) { onBack() }
        return
    }
    val context = state.context
    val groupNames = context.proxyGroups.map { it.name }

    val draftId = rememberSaveable { chainId ?: Uuid.random().toHexString() }
    var name by rememberSaveable { mutableStateOf(existing?.displayName.orEmpty()) }
    var groupName by rememberSaveable {
        mutableStateOf(existing?.proxyGroupName?.takeIf { it in groupNames } ?: groupNames.firstOrNull())
    }
    var hopKeys by rememberSaveable {
        mutableStateOf(
            existing?.hops.orEmpty()
                .filter { it.name.isNotBlank() }
                .filterNot { it.kind == SubscriptionChainProxyHopKind.ProxyGroup && it.name == existing?.proxyGroupName }
                .map { it.key },
        )
    }
    var attempted by rememberSaveable { mutableStateOf(false) }

    val hops = hopKeys.map(::hopOf)
    val nameError = if (attempted) nameError(name, draftId, state.customChainProxies, context) else null
    val groupError = if (attempted && groupName == null) R.string.chain_proxy_error_group else null
    val hopsError = if (attempted && hops.drop(1).any { it.kind != SubscriptionChainProxyHopKind.Proxy }) {
        R.string.chain_proxy_error_group_position
    } else null
    val canSave = hops.size >= MIN_HOPS
    val available = context.candidates.filterNot {
        it.kind == SubscriptionChainProxyHopKind.ProxyGroup && it.name == groupName
    }
    val typeOf = context.candidates.associate { it.hop to it.type }

    val scrollBehavior = MiuixScrollBehavior()
    val backdrop = rememberBlurBackdrop()
    val blurActive = backdrop != null
    val barColor = if (blurActive) Color.Transparent else MiuixTheme.colorScheme.surface

    Scaffold(
        topBar = {
            BlurredBar(backdrop = backdrop, blurActive = blurActive) {
                AdaptiveTopAppBar(
                    title = stringResource(if (existing == null) R.string.chain_proxy_add else R.string.chain_proxy_edit),
                    color = barColor,
                    scrollBehavior = scrollBehavior,
                    navigationIcon = { BackButton(onBack) },
                    actions = {
                        IconButton(
                            enabled = canSave,
                            modifier = Modifier.testTag(TestTags.ChainProxy.SAVE),
                            onClick = {
                                attempted = true
                                val group = groupName
                                if (group == null ||
                                    nameError(name, draftId, state.customChainProxies, context) != null ||
                                    hops.drop(1).any { it.kind != SubscriptionChainProxyHopKind.Proxy }
                                ) return@IconButton
                                viewModel.putCustom(
                                    SubscriptionCustomChainProxy(
                                        id = draftId,
                                        displayName = name.trim(),
                                        proxyGroupName = group,
                                        hops = hops,
                                    ),
                                )
                                onBack()
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
            item(key = "name") {
                SmallTitle(text = stringResource(R.string.chain_proxy_name))
                TextField(
                    value = name,
                    onValueChange = { name = it },
                    label = stringResource(R.string.chain_proxy_name_placeholder),
                    useLabelAsPlaceholder = true,
                    modifier = Modifier
                        .testTag(TestTags.ChainProxy.NAME)
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .padding(bottom = 6.dp),
                )
                nameError?.let { ErrorText(stringResource(it)) }
            }

            item(key = "group_title") { SmallTitle(text = stringResource(R.string.chain_proxy_group)) }
            if (groupNames.isNotEmpty()) {
                groupedCardItems(
                    keyPrefix = "chain_group",
                    items = listOf(
                        CardItem("chainProxyGroup") {
                            OverlayDropdownPreference(
                                title = stringResource(R.string.chain_proxy_group),
                                items = groupNames,
                                selectedIndex = groupNames.indexOf(groupName).coerceAtLeast(0),
                                onSelectedIndexChange = { index ->
                                    val selected = groupNames[index]
                                    groupName = selected
                                    hopKeys = hops
                                        .filterNot { it.kind == SubscriptionChainProxyHopKind.ProxyGroup && it.name == selected }
                                        .map { it.key }
                                },
                            )
                        },
                    ),
                    outerBottomPadding = 6.dp,
                )
            }
            groupError?.let { item(key = "group_error") { ErrorText(stringResource(it)) } }

            item(key = "path_title") { SmallTitle(text = stringResource(R.string.chain_proxy_path)) }
            when (hops.size) {
                0 -> item(key = "path_empty") { HintText(stringResource(R.string.chain_proxy_path_empty)) }
                1 -> item(key = "path_min") { HintText(stringResource(R.string.chain_proxy_min_hops)) }
            }
            val pinnedGroup = hops.firstOrNull()?.kind == SubscriptionChainProxyHopKind.ProxyGroup
            itemsIndexed(hops, key = { _, hop -> "hop:${hop.key}" }) { index, hop ->
                val movable = hop.kind == SubscriptionChainProxyHopKind.Proxy
                val top = if (pinnedGroup) 1 else 0
                HopItem(
                    index = index,
                    hop = hop,
                    summary = hopSummary(hop, typeOf[hop].orEmpty()),
                    canMoveUp = movable && index > top,
                    canMoveDown = movable && index < hops.lastIndex,
                    onMoveUp = { hopKeys = hops.swap(index, index - 1).map { it.key } },
                    onMoveDown = { hopKeys = hops.swap(index, index + 1).map { it.key } },
                    onRemove = { hopKeys = (hops - hop).map { it.key } },
                    modifier = Modifier.animateItem(),
                )
            }
            hopsError?.let { item(key = "hops_error") { ErrorText(stringResource(it)) } }

            item(key = "candidates_title") { SmallTitle(text = stringResource(R.string.chain_proxy_candidates)) }
            if (available.isEmpty()) {
                item(key = "candidates_empty") { HintText(stringResource(R.string.chain_proxy_no_candidates)) }
            }
            groupedCardItems(
                keyPrefix = "chain_candidate",
                items = available.map { option ->
                    CardItem("chainCandidate.${option.hop.key}") {
                        val selected = option.hop in hops
                        BasicComponent(
                            title = option.name,
                            summary = hopSummary(option.hop, option.type),
                            startAction = {
                                Checkbox(
                                    state = if (selected) ToggleableState.On else ToggleableState.Off,
                                    onClick = { hopKeys = toggle(hops, option).map { it.key } },
                                    modifier = Modifier.padding(end = 12.dp),
                                )
                            },
                            onClick = { hopKeys = toggle(hops, option).map { it.key } },
                        )
                    }
                },
            )

            item(key = "bottom_spacer") {
                Spacer(Modifier.height(24.dp).navigationBarsPadding())
            }
        }
    }
}

@Composable
private fun HopItem(
    index: Int,
    hop: SubscriptionChainProxyHop,
    summary: String,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .padding(horizontal = 12.dp)
            .padding(bottom = 12.dp)
            .testTag(TestTags.ChainProxy.hop(index)),
    ) {
        BasicComponent(
            title = "${index + 1}. ${hop.name}",
            summary = summary,
            endActions = {
                HopAction(AppIcons.MoveUp, stringResource(R.string.override_move_up), canMoveUp, onMoveUp, TestTags.ChainProxy.moveUp(index))
                HopAction(AppIcons.MoveDown, stringResource(R.string.override_move_down), canMoveDown, onMoveDown, TestTags.ChainProxy.moveDown(index))
                HopAction(AppIcons.Close, stringResource(R.string.chain_proxy_remove_hop), true, onRemove, TestTags.ChainProxy.remove(index))
            },
        )
    }
}

@Composable
private fun HopAction(icon: ImageVector, contentDescription: String, enabled: Boolean, onClick: () -> Unit, testTag: String) {
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

@Composable
private fun ErrorText(text: String) {
    Text(
        text = text,
        modifier = Modifier.padding(horizontal = 24.dp).padding(bottom = 6.dp),
        fontSize = 13.sp,
        color = StatusColors.danger,
    )
}

@Composable
private fun hopSummary(hop: SubscriptionChainProxyHop, type: String): String =
    if (hop.kind == SubscriptionChainProxyHopKind.ProxyGroup) {
        listOf(stringResource(R.string.chain_proxy_group_type), type).filter { it.isNotBlank() }.joinToString(" · ")
    } else type

// 选代理组替换已选的代理组并放到首位，再点同一个则取消；节点按点选顺序追加，再点移除。
private fun toggle(hops: List<SubscriptionChainProxyHop>, option: ChainProxyOption): List<SubscriptionChainProxyHop> {
    val hop = option.hop
    val next = hops.toMutableList()
    if (hop.kind == SubscriptionChainProxyHopKind.ProxyGroup) {
        val selectedGroup = next.firstOrNull { it.kind == SubscriptionChainProxyHopKind.ProxyGroup }
        if (selectedGroup != null) next.remove(selectedGroup)
        if (selectedGroup?.name != hop.name) next.add(0, hop)
    } else if (!next.remove(hop)) {
        next.add(hop)
    }
    return next
}

// 名称会成为运行配置里的节点名，不能与现有节点、代理组或其他自定义链重名。
private fun nameError(
    name: String,
    draftId: String,
    customs: List<SubscriptionCustomChainProxy>,
    context: ChainProxyContext,
): Int? {
    val trimmed = name.trim()
    return when {
        trimmed.isEmpty() -> R.string.chain_proxy_error_name
        customs.any { it.id != draftId && it.displayName == trimmed } ||
            trimmed in context.builtinNames ||
            context.proxyGroups.any { it.name == trimmed } ||
            context.candidates.any { it.name == trimmed } -> R.string.chain_proxy_error_duplicate
        else -> null
    }
}

private val SubscriptionChainProxyHop.key: String get() = "${kind.ordinal}:$name"

private fun hopOf(key: String): SubscriptionChainProxyHop =
    SubscriptionChainProxyHop(SubscriptionChainProxyHopKind.entries[key.substringBefore(':').toInt()], key.substringAfter(':'))

private fun List<SubscriptionChainProxyHop>.swap(from: Int, to: Int): List<SubscriptionChainProxyHop> =
    toMutableList().also { it[from] = this[to]; it[to] = this[from] }

private const val MIN_HOPS = 2
