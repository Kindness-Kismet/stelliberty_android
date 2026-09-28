package com.stelliberty.android.viewmodel

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stelliberty.android.R
import com.stelliberty.android.data.repository.ConfigValidationException
import com.stelliberty.android.domain.model.EditableRule
import com.stelliberty.android.domain.model.RuleKeys
import com.stelliberty.android.domain.model.RuleOverrideContext
import com.stelliberty.android.domain.model.RuleOverrideSet
import com.stelliberty.android.domain.model.RuleTemplate
import com.stelliberty.android.domain.repository.RuleOverrideRepository
import com.stelliberty.android.domain.repository.SubscriptionRepository
import com.stelliberty.android.platform.ProxyServiceController
import com.stelliberty.android.platform.showToast
import com.stelliberty.android.util.AppLogger
import com.stelliberty.android.util.describe
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// key 对订阅规则是运行时的禁用键，对自定义规则是其自身的 key；missingTarget 只对自定义规则有意义。
@Immutable
data class RuleOverrideRow(
    val orderId: String,
    val key: String,
    val matchKey: String,
    val type: String,
    val payload: String,
    val proxy: String,
    val options: String,
    val customId: String?,
    val isEnabled: Boolean,
    val missingTarget: Boolean = false,
) {
    val isBuiltin: Boolean get() = customId == null
    val isMatch: Boolean get() = type.equals(RuleKeys.MATCH, ignoreCase = true)
}

@Immutable
data class RuleOverrideUiState(
    val subscriptionId: String? = null,
    val isLoading: Boolean = false,
    val isReady: Boolean = false,
    val isSaving: Boolean = false,
    val error: String = "",
    val rows: ImmutableList<RuleOverrideRow> = persistentListOf(),
    val customRules: ImmutableList<EditableRule> = persistentListOf(),
    val proxyOptions: ImmutableList<String> = persistentListOf(),
    val templates: ImmutableList<RuleTemplate> = persistentListOf(),
    val hasCustomOrder: Boolean = false,
    val hasChanges: Boolean = false,
)

// 列表页与单条规则的编辑页共用一份未保存的草稿，点保存才写入；session 区分每次进入列表页。
// 基线读取失败时页面只读：没有订阅规则集合就无法判定查重与顺序。
class RuleOverrideViewModel(
    private val repository: RuleOverrideRepository,
    private val subscriptions: SubscriptionRepository,
    private val serviceController: ProxyServiceController,
    private val context: Context,
) : ViewModel() {

    // order 为空表示默认顺序；非空时是完整的展示顺序，保存时原样写入 RuleOrder。
    private data class Draft(
        val disabledKeys: Set<String>,
        val customRules: List<EditableRule>,
        val order: List<String>,
    )

    private val _uiState = MutableStateFlow(RuleOverrideUiState())
    val uiState: StateFlow<RuleOverrideUiState> = _uiState.asStateFlow()

    private var session: String? = null
    private var loadJob: Job? = null
    private var baseline = RuleOverrideContext()
    private var saved: Draft? = null
    private var draft: Draft? = null

    init {
        viewModelScope.launch {
            repository.templates.collect { templates ->
                _uiState.update { it.copy(templates = templates.toPersistentList()) }
            }
        }
    }

    fun observe(subscriptionId: String): Flow<RuleOverrideSet> = repository.observe(subscriptionId)

    fun open(subscriptionId: String, session: String) {
        if (this.session == session) return
        this.session = session
        loadJob?.cancel()
        saved = null
        draft = null
        _uiState.update {
            RuleOverrideUiState(subscriptionId = subscriptionId, isLoading = true, templates = it.templates)
        }
        loadJob = viewModelScope.launch {
            try {
                baseline = repository.loadContext(subscriptionId)
                val set = repository.find(subscriptionId)
                val initial = Draft(
                    disabledKeys = set.disabledBuiltinRuleKeys.toSet(),
                    customRules = set.customRules.distinctBy { it.key },
                    order = set.ruleOrder,
                )
                // 保存过的顺序展开成完整顺序，结果与运行时合并一致，之后的比较与保存都基于它。
                val expanded = if (initial.order.isEmpty()) initial else initial.copy(order = rows(initial).map { it.orderId })
                saved = expanded
                draft = expanded
                publish { it.copy(isLoading = false, isReady = true) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                AppLogger.warn(TAG, "Rule override context failed for $subscriptionId", e)
                _uiState.update {
                    it.copy(isLoading = false, error = context.getString(R.string.rule_override_load_failed, e.describe()))
                }
            }
        }
    }

    fun toggleBuiltin(key: String) = edit { draft ->
        draft.copy(disabledKeys = if (key in draft.disabledKeys) draft.disabledKeys - key else draft.disabledKeys + key)
    }

    fun toggleCustom(id: String) = edit { draft ->
        draft.copy(customRules = draft.customRules.map { if (it.id == id) it.copy(isEnabled = !it.isEnabled) else it })
    }

    fun removeCustom(id: String) = edit { draft ->
        draft.copy(
            customRules = draft.customRules.filterNot { it.id == id },
            order = draft.order - RuleKeys.customOrderId(id),
        )
    }

    // position 从 1 起算；编辑已有规则时保留启用状态，位置不变时不产生自定义顺序。
    fun putCustom(rule: EditableRule, position: Int) = edit { draft ->
        val index = draft.customRules.indexOfFirst { it.id == rule.id }
        val customs = if (index < 0) {
            draft.customRules + rule
        } else {
            draft.customRules.toMutableList().also { it[index] = rule.copy(isEnabled = it[index].isEnabled) }
        }
        val next = draft.copy(customRules = customs)
        val order = rows(next).map { it.orderId }
        val from = order.indexOf(rule.orderId)
        val to = (position - 1).coerceIn(0, order.lastIndex)
        if (from == to) next else next.copy(order = order.toMutableList().apply { add(to, removeAt(from)) })
    }

    fun resetOrder() = edit { it.copy(order = emptyList()) }

    // 同 PC：按 key 合并，已有的规则不重复添加；新加入的规则换新 Id，避免与已有规则撞 Id。
    fun applyTemplate(template: RuleTemplate) = edit { draft ->
        val keys = draft.customRules.mapTo(HashSet()) { it.key }
        val added = template.rules.filter { keys.add(it.key) }.map { it.copy(id = newRuleId()) }
        draft.copy(customRules = draft.customRules + added)
    }

    fun saveTemplate(name: String) {
        val rules = draft?.customRules ?: return
        viewModelScope.launch {
            runCatching { repository.saveTemplate(name, rules) }
                .onSuccess { showToast(context.getString(R.string.rule_override_template_saved)) }
                .onFailure { e ->
                    AppLogger.error(TAG, "Rule template save failed", e)
                    showToast(context.getString(R.string.error_save_failed, e.describe()))
                }
        }
    }

    fun deleteTemplate(id: String) {
        viewModelScope.launch {
            runCatching { repository.deleteTemplate(id) }
                .onFailure { AppLogger.error(TAG, "Rule template delete failed", it) }
        }
    }

    fun save(onComplete: () -> Unit) {
        val state = _uiState.value
        val subscriptionId = state.subscriptionId ?: return
        val current = draft ?: return
        if (state.isSaving || !state.isReady) return
        val rows = rows(current)
        validationError(current, rows)?.let { message ->
            _uiState.update { it.copy(error = context.getString(message)) }
            return
        }
        val set = RuleOverrideSet(
            subscriptionId = subscriptionId,
            customRules = current.customRules,
            disabledBuiltinRuleKeys = rows.filter { it.isBuiltin && !it.isEnabled }.map { it.key },
            ruleOrder = if (current.order.isEmpty()) emptyList() else rows.map { it.orderId },
        )
        _uiState.update { it.copy(isSaving = true, error = "") }
        viewModelScope.launch {
            val error = try {
                repository.save(set)
                saved = current
                if (subscriptions.currentSubscriptionId.value == subscriptionId) {
                    serviceController.restartWhenReady(subscriptionId)
                }
                onComplete()
                ""
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                AppLogger.error(TAG, "Rule override save failed for $subscriptionId", e)
                if (e is ConfigValidationException) {
                    context.getString(R.string.error_validation_failed, e.describe())
                } else {
                    context.getString(R.string.error_save_failed, e.describe())
                }
            }
            publish { it.copy(isSaving = false, error = error) }
        }
    }

    // 同 PC 的保存校验：自定义规则之间、以及与启用的订阅规则之间，类型与匹配内容都不能重复。
    private fun validationError(draft: Draft, rows: List<RuleOverrideRow>): Int? {
        if (draft.customRules.any { it.type.isBlank() || it.proxy.isBlank() || (!it.isMatch && it.payload.isBlank()) }) {
            return R.string.rule_override_error_invalid
        }
        if (draft.customRules.groupingBy { it.matchKey }.eachCount().any { it.value > 1 }) {
            return R.string.rule_override_error_duplicate_custom
        }
        val builtin = rows.filter { it.isBuiltin && it.isEnabled }.mapTo(HashSet()) { it.matchKey }
        if (draft.customRules.any { it.matchKey in builtin }) return R.string.rule_override_error_duplicate_builtin
        return null
    }

    private fun edit(transform: (Draft) -> Draft) {
        val current = draft ?: return
        if (_uiState.value.isSaving) return
        draft = transform(current)
        publish { it.copy(error = "") }
    }

    private fun publish(transform: (RuleOverrideUiState) -> RuleOverrideUiState) {
        val current = draft
        _uiState.update { state ->
            transform(
                if (current == null) state else state.copy(
                    rows = rows(current).toPersistentList(),
                    customRules = current.customRules.toPersistentList(),
                    proxyOptions = (BUILTIN_ACTIONS + baseline.proxyGroups).distinct().toPersistentList(),
                    hasCustomOrder = current.order.isNotEmpty(),
                    hasChanges = current != saved,
                ),
            )
        }
    }

    private fun rows(draft: Draft): List<RuleOverrideRow> {
        val targets = baseline.targets.toHashSet()
        val builtin = baseline.rules.map { rule ->
            RuleOverrideRow(
                orderId = rule.orderId,
                key = rule.key,
                matchKey = rule.matchKey,
                type = rule.type,
                payload = rule.payload,
                proxy = rule.proxy,
                options = rule.options,
                customId = null,
                isEnabled = rule.key !in draft.disabledKeys,
            )
        }
        val custom = draft.customRules.map { rule ->
            RuleOverrideRow(
                orderId = rule.orderId,
                key = rule.key,
                matchKey = rule.matchKey,
                type = rule.type.trim(),
                payload = rule.payload.trim(),
                proxy = rule.proxy.trim(),
                options = rule.options.trim(),
                customId = rule.id,
                isEnabled = rule.isEnabled,
                missingTarget = rule.proxy.trim() !in targets,
            )
        }
        return mergeRuleOrder(builtin, custom, draft.order)
    }

    companion object {
        private const val TAG = "RuleOverrideViewModel"

        // 同 PC：出站目标只提供内置动作与订阅的代理组。
        private val BUILTIN_ACTIONS = listOf("DIRECT", "REJECT", "REJECT-DROP")

        @OptIn(ExperimentalUuidApi::class)
        fun newRuleId(): String = "custom-${Uuid.random().toHexString()}"
    }
}

// 同 PC：有顺序时同 id 只留第一条，顺序里没有的订阅规则接在末尾，新自定义规则插到首个 MATCH 前。
internal fun mergeRuleOrder(
    builtin: List<RuleOverrideRow>,
    custom: List<RuleOverrideRow>,
    order: List<String>,
): List<RuleOverrideRow> {
    if (order.isEmpty()) return insertBeforeMatch(builtin, custom)
    val pending = LinkedHashMap<String, RuleOverrideRow>()
    (builtin + custom).forEach { pending.putIfAbsent(it.orderId, it) }
    val ordered = order.mapNotNullTo(ArrayList()) { pending.remove(it) }
    val (restBuiltin, restCustom) = pending.values.partition { it.isBuiltin }
    ordered += restBuiltin
    return insertBeforeMatch(ordered, restCustom)
}

private fun insertBeforeMatch(rows: List<RuleOverrideRow>, inserted: List<RuleOverrideRow>): List<RuleOverrideRow> {
    val at = rows.indexOfFirst { it.isMatch }.let { if (it < 0) rows.size else it }
    return rows.subList(0, at) + inserted + rows.subList(at, rows.size)
}
