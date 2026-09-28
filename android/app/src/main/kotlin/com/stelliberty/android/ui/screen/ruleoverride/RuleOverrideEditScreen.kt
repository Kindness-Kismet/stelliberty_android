package com.stelliberty.android.ui.screen.ruleoverride

import android.net.InetAddresses
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stelliberty.android.R
import com.stelliberty.android.domain.model.EditableRule
import com.stelliberty.android.domain.model.RuleKeys
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
import com.stelliberty.android.viewmodel.RuleOverrideRow
import com.stelliberty.android.viewmodel.RuleOverrideViewModel
import java.net.Inet6Address
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

// 校验同 PC：匹配内容不能含逗号，IP 段按类型检查地址族，与其他自定义规则或启用的订阅规则不能重复。
@Composable
fun RuleOverrideEditScreen(
    subscriptionId: String,
    ruleId: String?,
    viewModel: RuleOverrideViewModel,
    onBack: () -> Unit = {},
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val existing = ruleId?.let { id -> state.customRules.find { it.id == id } }
    if (state.subscriptionId != subscriptionId || !state.isReady || (ruleId != null && existing == null)) {
        LaunchedEffect(Unit) { onBack() }
        return
    }
    val rows = state.rows
    val targets = state.proxyOptions

    val draftId = rememberSaveable { ruleId ?: RuleOverrideViewModel.newRuleId() }
    var typeIndex by rememberSaveable { mutableIntStateOf(existing?.let(::typeIndexOf) ?: DEFAULT_TYPE) }
    var payload by rememberSaveable { mutableStateOf(existing?.payload.orEmpty()) }
    var target by rememberSaveable { mutableStateOf(existing?.proxy?.trim()?.takeIf { it in targets } ?: targets.first()) }
    val maxPosition = if (existing == null) rows.size + 1 else rows.size
    var position by rememberSaveable {
        val current = existing?.let { rule -> rows.indexOfFirst { it.customId == rule.id } + 1 }
        mutableStateOf((current ?: defaultPosition(rows)).toString())
    }
    var attempted by rememberSaveable { mutableStateOf(false) }

    val option = RULE_TYPES[typeIndex]
    val payloadEnabled = !option.type.equals(RuleKeys.MATCH, ignoreCase = true)
    val payloadError = if (attempted) payloadError(option, payload, draftId, rows) else null
    val positionValue = position.trim().toIntOrNull()?.takeIf { it in 1..maxPosition }
    val positionError = attempted && positionValue == null

    val scrollBehavior = MiuixScrollBehavior()
    val backdrop = rememberBlurBackdrop()
    val blurActive = backdrop != null
    val barColor = if (blurActive) Color.Transparent else MiuixTheme.colorScheme.surface

    Scaffold(
        topBar = {
            BlurredBar(backdrop = backdrop, blurActive = blurActive) {
                AdaptiveTopAppBar(
                    title = stringResource(if (existing == null) R.string.rule_override_add else R.string.rule_override_edit),
                    color = barColor,
                    scrollBehavior = scrollBehavior,
                    navigationIcon = { BackButton(onBack) },
                    actions = {
                        IconButton(
                            modifier = Modifier.testTag(TestTags.RuleOverride.SAVE),
                            onClick = {
                                attempted = true
                                val at = positionValue
                                if (at == null || payloadError(option, payload, draftId, rows) != null) return@IconButton
                                viewModel.putCustom(
                                    EditableRule(
                                        id = draftId,
                                        type = option.type,
                                        payload = if (payloadEnabled) payload.trim() else "",
                                        proxy = target,
                                        options = option.options,
                                    ),
                                    at,
                                )
                                onBack()
                            },
                        ) {
                            Icon(
                                imageVector = AppIcons.Check,
                                contentDescription = stringResource(R.string.common_save),
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
            groupedCardItems(
                keyPrefix = "rule_fields",
                items = listOf(
                    CardItem("ruleType") {
                        OverlayDropdownPreference(
                            title = stringResource(R.string.rule_override_type),
                            items = RULE_TYPES.map { it.label },
                            selectedIndex = typeIndex,
                            onSelectedIndexChange = { typeIndex = it },
                        )
                    },
                    CardItem("ruleTarget") {
                        OverlayDropdownPreference(
                            title = stringResource(R.string.rule_override_target),
                            items = targets,
                            selectedIndex = targets.indexOf(target).coerceAtLeast(0),
                            onSelectedIndexChange = { target = targets[it] },
                        )
                    },
                ),
            )

            if (payloadEnabled) {
                item(key = "payload") {
                    SmallTitle(text = stringResource(R.string.rule_override_payload))
                    TextField(
                        value = payload,
                        onValueChange = { payload = it },
                        label = stringResource(R.string.rule_override_payload),
                        useLabelAsPlaceholder = true,
                        singleLine = true,
                        modifier = Modifier
                            .testTag(TestTags.RuleOverride.PAYLOAD)
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp)
                            .padding(bottom = 6.dp),
                    )
                    payloadError?.let { ErrorText(stringResource(it)) }
                }
            }

            item(key = "position") {
                SmallTitle(text = stringResource(R.string.rule_override_position))
                TextField(
                    value = position,
                    onValueChange = { position = it },
                    label = stringResource(R.string.rule_override_position_range, maxPosition),
                    useLabelAsPlaceholder = true,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier
                        .testTag(TestTags.RuleOverride.POSITION)
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .padding(bottom = 6.dp),
                )
                if (positionError) ErrorText(stringResource(R.string.rule_override_error_position, maxPosition))
            }

            if (existing != null) {
                item(key = "delete") {
                    TextButton(
                        text = stringResource(R.string.rule_override_delete),
                        onClick = {
                            viewModel.removeCustom(existing.id)
                            onBack()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp)
                            .padding(top = 12.dp)
                            .testTag(TestTags.RuleOverride.DELETE),
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
private fun ErrorText(text: String) {
    Text(
        text = text,
        modifier = Modifier.padding(horizontal = 24.dp).padding(bottom = 6.dp),
        fontSize = 13.sp,
        color = StatusColors.danger,
    )
}

private data class RuleTypeOption(val label: String, val type: String, val options: String = "")

// 与 PC 规则编辑器的类型列表一致，no-resolve 作为独立选项写进 Options。
private val RULE_TYPES = listOf(
    RuleTypeOption("DOMAIN", "DOMAIN"),
    RuleTypeOption("DOMAIN-SUFFIX", "DOMAIN-SUFFIX"),
    RuleTypeOption("DOMAIN-KEYWORD", "DOMAIN-KEYWORD"),
    RuleTypeOption("IP-CIDR", "IP-CIDR"),
    RuleTypeOption("IP-CIDR (no-resolve)", "IP-CIDR", NO_RESOLVE),
    RuleTypeOption("IP-CIDR6", "IP-CIDR6"),
    RuleTypeOption("IP-CIDR6 (no-resolve)", "IP-CIDR6", NO_RESOLVE),
    RuleTypeOption("GEOIP", "GEOIP"),
    RuleTypeOption("GEOIP (no-resolve)", "GEOIP", NO_RESOLVE),
    RuleTypeOption("GEOSITE", "GEOSITE"),
    RuleTypeOption("RULE-SET", "RULE-SET"),
    RuleTypeOption("PROCESS-NAME", "PROCESS-NAME"),
    RuleTypeOption("PROCESS-PATH", "PROCESS-PATH"),
    RuleTypeOption("DST-PORT", "DST-PORT"),
    RuleTypeOption("SRC-IP-CIDR", "SRC-IP-CIDR"),
    RuleTypeOption("SRC-IP-CIDR (no-resolve)", "SRC-IP-CIDR", NO_RESOLVE),
    RuleTypeOption("MATCH", "MATCH"),
)

private const val NO_RESOLVE = "no-resolve"
private const val DEFAULT_TYPE = 1
private val CIDR_TYPES = setOf("IP-CIDR", "IP-CIDR6", "SRC-IP-CIDR")

// 找不到对应选项时同 PC 退回 DOMAIN-SUFFIX。
private fun typeIndexOf(rule: EditableRule): Int =
    RULE_TYPES.indexOfFirst {
        it.type.equals(rule.type.trim(), ignoreCase = true) && it.options.equals(rule.options.trim(), ignoreCase = true)
    }.takeIf { it >= 0 } ?: DEFAULT_TYPE

// 新规则默认插在首个 MATCH 前，与不指定位置时的运行时顺序一致。
private fun defaultPosition(rows: List<RuleOverrideRow>): Int =
    rows.indexOfFirst { it.isMatch }.let { if (it < 0) rows.size + 1 else it + 1 }

private fun payloadError(option: RuleTypeOption, payload: String, draftId: String, rows: List<RuleOverrideRow>): Int? {
    if (option.type == RuleKeys.MATCH) return null
    val matchKey = RuleKeys.match(option.type, payload)
    return when {
        payload.isBlank() -> R.string.rule_override_error_payload_required
        ',' in payload -> R.string.rule_override_error_payload_delimiter
        option.type in CIDR_TYPES && !isValidCidr(payload, option.type) -> R.string.rule_override_error_cidr
        rows.any { it.customId != null && it.customId != draftId && it.matchKey == matchKey } ->
            R.string.rule_override_error_duplicate_custom
        rows.any { it.isBuiltin && it.isEnabled && it.matchKey == matchKey } -> R.string.rule_override_error_duplicate_builtin
        else -> null
    }
}

// 只接受数字地址，不做域名解析；IP-CIDR6 要求 IPv6，其余要求 IPv4。
private fun isValidCidr(value: String, type: String): Boolean {
    val parts = value.trim().split('/').map { it.trim() }
    if (parts.size != 2 || !InetAddresses.isNumericAddress(parts[0])) return false
    val prefix = parts[1].toIntOrNull() ?: return false
    val ipv6 = type == "IP-CIDR6"
    return (InetAddresses.parseNumericAddress(parts[0]) is Inet6Address) == ipv6 && prefix in 0..(if (ipv6) 128 else 32)
}
