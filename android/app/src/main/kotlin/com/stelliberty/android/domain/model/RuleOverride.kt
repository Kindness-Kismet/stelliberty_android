package com.stelliberty.android.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// 与 PC 的 Stelliberty.Domain.Rules 同一契约，文件为 rules/rule_overrides.json；Go 侧 overrides.EditableRule 也按这些键名读取。
@Serializable
data class EditableRule(
    @SerialName("Id") val id: String,
    @SerialName("Type") val type: String,
    @SerialName("Payload") val payload: String = "",
    @SerialName("Proxy") val proxy: String,
    @SerialName("Options") val options: String = "",
    @SerialName("IsEnabled") val isEnabled: Boolean = true,
) {
    val key: String get() = RuleKeys.create(type, payload, proxy, options)
    val matchKey: String get() = RuleKeys.match(type, payload)
    val orderId: String get() = RuleKeys.customOrderId(id)
    val isMatch: Boolean get() = type.trim().equals(RuleKeys.MATCH, ignoreCase = true)

    fun render(): String = buildList {
        add(type.trim())
        if (!isMatch) add(payload.trim())
        add(proxy.trim())
        if (options.isNotBlank()) add(options.trim())
    }.joinToString(",")
}

@Serializable
data class RuleOverrideSet(
    @SerialName("SubscriptionId") val subscriptionId: String,
    @SerialName("CustomRules") val customRules: List<EditableRule> = emptyList(),
    @SerialName("DisabledBuiltinRuleKeys") val disabledBuiltinRuleKeys: List<String> = emptyList(),
    @SerialName("RuleOrder") val ruleOrder: List<String> = emptyList(),
) {
    val isEmpty: Boolean
        get() = customRules.isEmpty() && disabledBuiltinRuleKeys.isEmpty() && ruleOrder.isEmpty()
}

@Serializable
data class RuleTemplate(
    @SerialName("Id") val id: String,
    @SerialName("Name") val name: String,
    @SerialName("Rules") val rules: List<EditableRule> = emptyList(),
)

// 规则覆写页面的基线，由 Go 侧从套完覆写与链式代理的配置中解析；订阅规则的 key 与运行时同一算法。
@Serializable
data class RuleOverrideContext(
    @SerialName("rules") val rules: List<BuiltinRule> = emptyList(),
    @SerialName("proxyGroups") val proxyGroups: List<String> = emptyList(),
    @SerialName("targets") val targets: List<String> = emptyList(),
)

@Serializable
data class BuiltinRule(
    @SerialName("type") val type: String,
    @SerialName("payload") val payload: String = "",
    @SerialName("proxy") val proxy: String = "",
    @SerialName("options") val options: String = "",
    @SerialName("key") val key: String,
    @SerialName("matchKey") val matchKey: String,
) {
    val orderId: String get() = RuleKeys.builtinOrderId(key)
}

// 同 PC 的 RuleKey / RuleOrderKey：各段折叠空白并转大写后以 \u001f 相连，查重只看类型与匹配内容。
object RuleKeys {
    const val MATCH = "MATCH"
    private const val SEPARATOR = "\u001f"
    private const val BUILTIN_PREFIX = "builtin:"
    private const val CUSTOM_PREFIX = "custom:"

    fun create(type: String, payload: String, proxy: String, options: String): String =
        listOf(type, payload, proxy, options).joinToString(SEPARATOR, transform = ::normalize)

    fun match(type: String, payload: String): String = normalize(type) + SEPARATOR + normalize(payload)

    fun builtinOrderId(key: String): String = BUILTIN_PREFIX + key

    fun customOrderId(id: String): String = CUSTOM_PREFIX + id

    // isWhitespace 覆盖 Unicode 空白，与 Go strings.Fields、C# Char.IsWhiteSpace 一致。
    private fun normalize(value: String): String = buildString {
        var gap = false
        for (c in value) {
            if (c.isWhitespace()) {
                gap = isNotEmpty()
                continue
            }
            if (gap) append(' ')
            gap = false
            append(c)
        }
    }.uppercase()
}
