package com.stelliberty.android.debug

import android.os.Bundle
import com.stelliberty.android.domain.model.BuiltinRule
import com.stelliberty.android.domain.model.EditableRule
import com.stelliberty.android.domain.model.RuleKeys
import com.stelliberty.android.domain.model.RuleOverrideSet
import com.stelliberty.android.domain.repository.RuleOverrideRepository
import com.stelliberty.android.domain.repository.SubscriptionRepository
import com.stelliberty.android.platform.ProxyServiceController
import com.stelliberty.android.viewmodel.RuleOverrideViewModel
import kotlinx.coroutines.runBlocking

// --arg 是订阅；保存后按页面行为在当前订阅上重启。name 先按自定义规则 Id 前缀匹配，
// 匹配不到按「类型,内容,目标[,选项]」原文找订阅规则。
internal fun runRuleCommand(action: String, arg: String?, extras: Bundle?): Bundle {
    val command = "rule.$action"
    val repository = koin<RuleOverrideRepository>()
    val subscriptions = koin<SubscriptionRepository>()
    val controller = koin<ProxyServiceController>()
    val token = extras.target(arg) ?: return debugResult(false, "Missing subscription", command)
    val subscription = subscriptions.subscriptions.value.let { items ->
        items.firstOrNull { it.id == token } ?: items.firstOrNull { it.id.startsWith(token) }
            ?: items.firstOrNull { it.name == token }
    } ?: return debugResult(false, "Subscription not found", command, token)
    val set = repository.find(subscription.id)

    suspend fun save(next: RuleOverrideSet, message: String): Bundle =
        runCatching { repository.save(next) }.fold(
            onSuccess = {
                if (subscriptions.currentSubscriptionId.value == subscription.id) {
                    controller.restartWhenReady(subscription.id)
                }
                debugResult(true, message, command, subscription.id)
            },
            onFailure = { debugResult(false, it.describeForDebug(), command, subscription.id) },
        )

    return runBlocking {
        when (action) {
            "list" -> runCatching { repository.loadContext(subscription.id) }.fold(
                onSuccess = { context ->
                    val custom = set.customRules.joinToString(";") { rule ->
                        (if (rule.isEnabled) "" else "-") + "${rule.render()}[${rule.id.removePrefix("custom-").take(8)}]"
                    }
                    val disabled = context.rules.filter { it.key in set.disabledBuiltinRuleKeys }
                        .joinToString(";") { it.render() }
                    debugResult(
                        true,
                        "${context.rules.size} builtin, ${set.customRules.size} custom",
                        command,
                        subscription.id,
                        "custom=$custom; disabled=$disabled; order=${set.ruleOrder.size}; " +
                            "groups=${context.proxyGroups.size}; targets=${context.targets.size}",
                    )
                },
                onFailure = { debugResult(false, it.describeForDebug(), command, subscription.id) },
            )

            "add" -> {
                val type = extras.string(EXTRA_TYPE) ?: return@runBlocking debugResult(false, "Missing type", command)
                val proxy = extras.string(EXTRA_PROXY) ?: return@runBlocking debugResult(false, "Missing proxy", command)
                val rule = EditableRule(
                    id = RuleOverrideViewModel.newRuleId(),
                    type = type,
                    payload = extras.string(EXTRA_PAYLOAD).orEmpty(),
                    proxy = proxy,
                    options = extras.string(EXTRA_OPTIONS).orEmpty(),
                )
                save(set.copy(customRules = set.customRules + rule), "added ${rule.id}")
            }

            "toggle" -> {
                val name = extras.string(EXTRA_NAME) ?: return@runBlocking debugResult(false, "Missing name", command)
                val custom = set.findCustom(name)
                if (custom != null) {
                    val rules = set.customRules.map { if (it.id == custom.id) it.copy(isEnabled = !it.isEnabled) else it }
                    return@runBlocking save(set.copy(customRules = rules), "toggled ${custom.id}")
                }
                val key = ruleKeyOf(name)
                val context = runCatching { repository.loadContext(subscription.id) }
                    .getOrElse { return@runBlocking debugResult(false, it.describeForDebug(), command, subscription.id) }
                if (context.rules.none { it.key == key }) {
                    return@runBlocking debugResult(false, "Rule not found: $name", command, subscription.id)
                }
                val disabled = set.disabledBuiltinRuleKeys
                save(set.copy(disabledBuiltinRuleKeys = if (key in disabled) disabled - key else disabled + key), "toggled $name")
            }

            "delete" -> {
                val name = extras.string(EXTRA_NAME) ?: return@runBlocking debugResult(false, "Missing name", command)
                val custom = set.findCustom(name)
                    ?: return@runBlocking debugResult(false, "Rule not found: $name", command, subscription.id)
                save(
                    set.copy(customRules = set.customRules - custom, ruleOrder = set.ruleOrder - custom.orderId),
                    "deleted ${custom.id}",
                )
            }

            else -> debugResult(false, "Unknown rule action: $action", command, arg)
        }
    }
}

private fun RuleOverrideSet.findCustom(token: String): EditableRule? =
    customRules.firstOrNull { it.id == token || it.id.removePrefix("custom-").startsWith(token) }

// 与 Go 侧 parseRuleKey 同一拆法：MATCH 没有匹配内容，其余段落依次为内容、目标、选项。
private fun ruleKeyOf(text: String): String {
    val parts = text.split(',')
    return if (parts.first().trim().equals(RuleKeys.MATCH, ignoreCase = true)) {
        RuleKeys.create(parts[0], "", parts.getOrElse(1) { "" }, parts.drop(2).joinToString(","))
    } else {
        RuleKeys.create(parts[0], parts.getOrElse(1) { "" }, parts.getOrElse(2) { "" }, parts.drop(3).joinToString(","))
    }
}

private fun BuiltinRule.render(): String =
    EditableRule(id = "", type = type, payload = payload, proxy = proxy, options = options).render()
