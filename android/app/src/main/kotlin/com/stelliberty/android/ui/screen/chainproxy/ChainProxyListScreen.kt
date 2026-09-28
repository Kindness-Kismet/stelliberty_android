package com.stelliberty.android.ui.screen.chainproxy

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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stelliberty.android.R
import com.stelliberty.android.domain.model.ChainProxyContext
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
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

// 改动先留在 ViewModel 的草稿里，点保存才写入订阅；不保存直接返回即放弃。
@Composable
fun ChainProxyListScreen(
    subscriptionId: String,
    session: String,
    viewModel: ChainProxyViewModel,
    onBack: () -> Unit = {},
    onAdd: () -> Unit = {},
    onEdit: (String) -> Unit = {},
) {
    LaunchedEffect(subscriptionId, session) { viewModel.open(subscriptionId, session) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val ready = state.subscriptionId == subscriptionId && !state.isLoading

    val scrollBehavior = MiuixScrollBehavior()
    val backdrop = rememberBlurBackdrop()
    val blurActive = backdrop != null
    val barColor = if (blurActive) Color.Transparent else MiuixTheme.colorScheme.surface

    Scaffold(
        topBar = {
            BlurredBar(backdrop = backdrop, blurActive = blurActive) {
                AdaptiveTopAppBar(
                    title = stringResource(R.string.chain_proxy_title),
                    color = barColor,
                    scrollBehavior = scrollBehavior,
                    navigationIcon = { BackButton(onBack) },
                    actions = {
                        val canSave = ready && state.hasChanges && !state.isSaving
                        IconButton(
                            enabled = canSave,
                            modifier = Modifier.testTag(TestTags.ChainProxy.SAVE),
                            onClick = { viewModel.save(onBack) },
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

            if (!ready) {
                item(key = "loading") { HintText(stringResource(R.string.chain_proxy_loading)) }
            } else {
                val builtinNames = state.context.builtinNames
                if (builtinNames.isNotEmpty()) {
                    item(key = "builtin_title") { SmallTitle(text = stringResource(R.string.chain_proxy_builtin)) }
                    groupedCardItems(
                        keyPrefix = "chain_builtin",
                        items = builtinNames.map { name ->
                            CardItem("chainBuiltin.$name") {
                                SwitchPreference(
                                    title = name,
                                    checked = name !in state.disabledBuiltinNames,
                                    onCheckedChange = { viewModel.toggleBuiltin(name) },
                                )
                            }
                        },
                    )
                }

                item(key = "custom_title") { SmallTitle(text = stringResource(R.string.chain_proxy_custom)) }
                if (state.customChainProxies.isEmpty()) {
                    item(key = "custom_empty") { HintText(stringResource(R.string.chain_proxy_custom_empty)) }
                }
                items(state.customChainProxies, key = { "custom:${it.id}" }) { chain ->
                    CustomChainItem(
                        chain = chain,
                        missing = missingNames(chain, state.context),
                        enabled = !state.isSaving,
                        onClick = { onEdit(chain.id) },
                        onToggle = { viewModel.toggleCustom(chain.id) },
                        onDelete = { viewModel.removeCustom(chain.id) },
                        modifier = Modifier.animateItem(),
                    )
                }
                item(key = "add") {
                    TextButton(
                        text = stringResource(R.string.chain_proxy_add),
                        onClick = onAdd,
                        enabled = state.context.proxyGroups.isNotEmpty() && !state.isSaving,
                        colors = ButtonDefaults.textButtonColorsPrimary(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp)
                            .padding(bottom = 12.dp)
                            .testTag(TestTags.ChainProxy.ADD),
                    )
                }
            }

            item(key = "bottom_spacer") {
                Spacer(Modifier.height(24.dp).navigationBarsPadding())
            }
        }
    }
}

@Composable
private fun CustomChainItem(
    chain: SubscriptionCustomChainProxy,
    missing: String,
    enabled: Boolean,
    onClick: () -> Unit,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .padding(bottom = 12.dp)
            .testTag(TestTags.ChainProxy.custom(chain.id)),
        insideMargin = PaddingValues(16.dp),
        onClick = onClick,
        pressFeedbackType = PressFeedbackType.Sink,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = chain.displayName,
                    fontSize = 17.sp,
                    fontWeight = FontWeight(550),
                    color = MiuixTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(R.string.chain_proxy_group_summary, chain.proxyGroupName),
                    modifier = Modifier.padding(top = 2.dp),
                    fontSize = 12.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            Switch(
                checked = chain.isEnabled,
                onCheckedChange = { onToggle() },
                enabled = enabled,
                modifier = Modifier.testTag(TestTags.ChainProxy.toggle(chain.id)),
            )
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = chain.hops.joinToString(" → ") { it.name },
                    fontSize = 12.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (missing.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.chain_proxy_missing, missing),
                        modifier = Modifier.padding(top = 2.dp),
                        fontSize = 12.sp,
                        color = StatusColors.danger,
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            IconButton(
                onClick = onDelete,
                enabled = enabled,
                minHeight = 35.dp,
                minWidth = 35.dp,
                backgroundColor = MiuixTheme.colorScheme.secondaryContainer,
                modifier = Modifier.testTag(TestTags.ChainProxy.delete(chain.id)),
            ) {
                Icon(
                    modifier = Modifier.size(20.dp),
                    imageVector = AppIcons.Delete,
                    contentDescription = stringResource(R.string.common_delete),
                    tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
        }
    }
}

@Composable
internal fun HintText(text: String) {
    Text(
        text = text,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 24.dp),
        fontSize = 14.sp,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        textAlign = TextAlign.Center,
    )
}

// 与 PC 相同：所属代理组或任一跳点不在套完覆写后的候选项里即视为缺失，运行时会跳过这条链。
private fun missingNames(chain: SubscriptionCustomChainProxy, context: ChainProxyContext): String {
    val candidates = context.candidates.mapTo(HashSet()) { it.hop }
    return buildList {
        if (context.proxyGroups.none { it.name == chain.proxyGroupName }) add(chain.proxyGroupName)
        chain.hops.filterNot { it in candidates }.forEach { add(it.name) }
    }.joinToString(", ")
}
