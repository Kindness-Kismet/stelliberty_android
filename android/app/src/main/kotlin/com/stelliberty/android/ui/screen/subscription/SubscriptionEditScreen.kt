package com.stelliberty.android.ui.screen.subscription

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stelliberty.android.R
import com.stelliberty.android.domain.model.Subscription
import com.stelliberty.android.domain.model.SubscriptionAutoUpdateMode
import com.stelliberty.android.domain.model.SubscriptionUpdateProxyMode
import com.stelliberty.android.ui.component.AdaptiveTopAppBar
import com.stelliberty.android.ui.component.CardItem
import com.stelliberty.android.ui.component.blur.BlurredBar
import com.stelliberty.android.ui.component.blur.rememberBlurBackdrop
import com.stelliberty.android.ui.component.groupedCardItems
import com.stelliberty.android.ui.icon.AppIcons
import com.stelliberty.android.ui.util.TestTags
import com.stelliberty.android.ui.util.horizontalCutoutPadding
import com.stelliberty.android.viewmodel.SubscriptionViewModel
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

// 规则覆写入口的摘要：自定义规则数与被禁用的订阅规则数。
@Immutable
data class RuleOverrideSummary(val customCount: Int, val disabledCount: Int)

@Composable
fun SubscriptionEditScreen(
    uuid: String,
    viewModel: SubscriptionViewModel,
    onBack: () -> Unit = {},
    onSaved: () -> Unit = {},
    onNavigateOverrides: () -> Unit = {},
    onNavigateChainProxies: () -> Unit = {},
    ruleOverrideSummary: RuleOverrideSummary? = null,
    onNavigateRuleOverrides: () -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val subscription = uiState.subscriptions.find { it.id == uuid }
    val scrollBehavior = MiuixScrollBehavior()

    var name by rememberSaveable(uuid) { mutableStateOf(subscription?.name ?: "") }
    var url by rememberSaveable(uuid) { mutableStateOf(subscription?.sourceLocation ?: "") }
    var userAgent by rememberSaveable(uuid) { mutableStateOf(subscription?.userAgent ?: "") }
    var ageSecretKey by rememberSaveable(uuid) { mutableStateOf(subscription?.ageSecretKey ?: "") }
    var autoUpdateMode by rememberSaveable(uuid) {
        mutableStateOf(subscription?.autoUpdateMode ?: SubscriptionAutoUpdateMode.Disabled)
    }
    var intervalMinutes by rememberSaveable(uuid) { mutableStateOf(subscription?.intervalText().orEmpty()) }
    var updateViaProxy by rememberSaveable(uuid) { mutableStateOf(subscription?.usesProxyForUpdate ?: true) }
    var autoDelayMinutes by rememberSaveable(uuid) { mutableStateOf(subscription?.autoDelayText().orEmpty()) }

    if (subscription == null) {
        onBack()
        return
    }

    val isFile = subscription.isLocalFile
    val hasChanges = name != subscription.name ||
            url != subscription.sourceLocation ||
            userAgent.trim() != subscription.userAgent ||
            ageSecretKey.trim() != subscription.ageSecretKey ||
            autoUpdateMode != subscription.autoUpdateMode ||
            (autoUpdateMode == SubscriptionAutoUpdateMode.Interval && intervalMinutes != subscription.intervalText()) ||
            (!isFile && updateViaProxy != subscription.usesProxyForUpdate) ||
            autoDelayMinutes != subscription.autoDelayText()

    val backdrop = rememberBlurBackdrop()
    val blurActive = backdrop != null
    val barColor = if (blurActive) Color.Transparent else MiuixTheme.colorScheme.surface

    Scaffold(
        topBar = {
            BlurredBar(backdrop = backdrop, blurActive = blurActive) {
                AdaptiveTopAppBar(
                    title = stringResource(R.string.subscription_edit),
                    color = barColor,
                    scrollBehavior = scrollBehavior,
                    navigationIcon = {
                        IconButton(onClick = onBack) {
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
                    },
                )
            }
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .horizontalCutoutPadding()
                .then(if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier)
                .scrollEndHaptic()
                .overScrollVertical()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = PaddingValues(
                top = innerPadding.calculateTopPadding(),
                bottom = 24.dp,
            ),
        ) {
            item {
                SmallTitle(text = stringResource(R.string.subscription_config_name))
                TextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier
                        .testTag(TestTags.Subscription.NAME)
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .padding(bottom = 6.dp),
                )
                if (!isFile) {
                    SmallTitle(text = stringResource(R.string.subscription_sub_url))
                    TextField(
                        value = url,
                        onValueChange = { url = it },
                        modifier = Modifier
                            .testTag(TestTags.Subscription.URL)
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp)
                            .padding(bottom = 6.dp),
                    )
                    SmallTitle(text = stringResource(R.string.subscription_user_agent))
                    TextField(
                        value = userAgent,
                        onValueChange = { userAgent = it },
                        label = stringResource(R.string.subscription_user_agent_placeholder),
                        useLabelAsPlaceholder = true,
                        modifier = Modifier
                            .testTag(TestTags.Subscription.USER_AGENT)
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp)
                            .padding(bottom = 6.dp),
                    )
                    SmallTitle(text = stringResource(R.string.subscription_age_secret_key))
                    TextField(
                        value = ageSecretKey,
                        onValueChange = { ageSecretKey = it },
                        label = stringResource(R.string.subscription_age_secret_key_placeholder),
                        useLabelAsPlaceholder = true,
                        modifier = Modifier
                            .testTag(TestTags.Subscription.AGE_KEY)
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp)
                            .padding(bottom = 6.dp),
                    )
                }
            }
            if (!isFile) {
                groupedCardItems(
                    keyPrefix = "auto_update",
                    items = listOf(
                        CardItem("subscriptionAutoUpdate") {
                            AutoUpdateModePreference(mode = autoUpdateMode, onModeChange = { autoUpdateMode = it })
                        },
                    ),
                    outerBottomPadding = 6.dp,
                )
                if (autoUpdateMode == SubscriptionAutoUpdateMode.Interval) {
                    item(key = "interval_field") {
                        SmallTitle(text = stringResource(R.string.subscription_auto_update_interval_minutes))
                        TextField(
                            value = intervalMinutes,
                            onValueChange = { intervalMinutes = it.filter { c -> c.isDigit() } },
                            label = stringResource(R.string.subscription_auto_update_placeholder),
                            useLabelAsPlaceholder = true,
                            modifier = Modifier
                                .testTag(TestTags.Subscription.INTERVAL)
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp)
                                .padding(bottom = 6.dp),
                        )
                    }
                }
                groupedCardItems(
                    keyPrefix = "update_proxy",
                    items = listOf(
                        CardItem("subscriptionUpdateViaProxy") {
                            SwitchPreference(
                                title = stringResource(R.string.subscription_update_via_proxy),
                                summary = stringResource(R.string.subscription_update_via_proxy_summary),
                                checked = updateViaProxy,
                                onCheckedChange = { updateViaProxy = it },
                            )
                        },
                    ),
                    outerBottomPadding = 6.dp,
                )
            }
            item(key = "auto_delay_field") {
                AutoDelayField(
                    value = autoDelayMinutes,
                    onValueChange = { autoDelayMinutes = it },
                    modifier = Modifier.padding(bottom = 6.dp),
                )
            }

            groupedCardItems(
                keyPrefix = "overrides",
                items = listOf(
                    CardItem("subscriptionOverrides") {
                        val count = subscription.overrideIds.size
                        ArrowPreference(
                            title = stringResource(R.string.subscription_overrides),
                            summary = if (count == 0) {
                                stringResource(R.string.subscription_override_none)
                            } else pluralStringResource(R.plurals.subscription_override_count, count, count),
                            onClick = onNavigateOverrides,
                        )
                    },
                    CardItem("subscriptionChainProxies") {
                        // 与 PC 相同，内置链计数取自订阅原文。
                        val count = subscription.builtinChainProxyNames.size + subscription.customChainProxies.size
                        ArrowPreference(
                            title = stringResource(R.string.chain_proxy_title),
                            summary = if (count == 0) {
                                stringResource(R.string.subscription_chain_proxy_none)
                            } else pluralStringResource(R.plurals.subscription_chain_proxy_count, count, count),
                            onClick = onNavigateChainProxies,
                        )
                    },
                    CardItem("subscriptionRuleOverrides") {
                        val summary = ruleOverrideSummary
                        ArrowPreference(
                            title = stringResource(R.string.rule_override_title),
                            summary = if (summary == null || summary.customCount + summary.disabledCount == 0) {
                                stringResource(R.string.rule_override_none)
                            } else {
                                stringResource(R.string.rule_override_summary, summary.customCount, summary.disabledCount)
                            },
                            onClick = onNavigateRuleOverrides,
                        )
                    },
                ),
                outerBottomPadding = 6.dp,
            )

            if (uiState.error.isNotEmpty()) {
                item(key = "error") {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp)
                            .padding(bottom = 6.dp),
                        insideMargin = PaddingValues(16.dp),
                    ) {
                        Text(
                            text = uiState.error,
                            fontSize = 14.sp,
                            color = MiuixTheme.colorScheme.primary,
                        )
                    }
                }
            }

            item {
                TextButton(
                    text = stringResource(R.string.common_save),
                    onClick = {
                        // 开关没动就原样写回，保留 PC 上设的 SystemProxy。
                        val proxyMode = when {
                            isFile || updateViaProxy == subscription.usesProxyForUpdate -> subscription.updateProxyMode
                            updateViaProxy -> SubscriptionUpdateProxyMode.Core
                            else -> SubscriptionUpdateProxyMode.Direct
                        }
                        viewModel.editSubscription(
                            uuid = uuid,
                            name = name,
                            source = url,
                            autoUpdateMode = if (isFile) subscription.autoUpdateMode else autoUpdateMode,
                            intervalMinutes = if (autoUpdateMode == SubscriptionAutoUpdateMode.Interval) {
                                intervalMinutes.toIntOrNull() ?: 0
                            } else 0,
                            userAgent = userAgent.trim(),
                            ageSecretKey = ageSecretKey.trim(),
                            updateProxyMode = proxyMode,
                            autoTestDelayMinutes = autoDelayMinutes.toIntOrNull() ?: 0,
                            onComplete = onSaved,
                        )
                    },
                    enabled = hasChanges && !uiState.isLoading && name.isNotBlank(),
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .padding(top = 6.dp, bottom = 16.dp + innerPadding.calculateBottomPadding()),
                )
            }
        }
    }
}

private fun Subscription.intervalText(): String =
    autoUpdateIntervalMinutes.takeIf { it > 0 }?.toString().orEmpty()

private fun Subscription.autoDelayText(): String =
    autoTestDelayIntervalMinutes.takeIf { it > 0 }?.toString().orEmpty()
