package com.stelliberty.android.data.store

import com.stelliberty.android.domain.model.RuleOverrideSet
import com.stelliberty.android.domain.model.RuleTemplate
import com.stelliberty.android.platform.ProfileFileManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class RuleOverrideFile(
    @SerialName("Items") val items: List<RuleOverrideSet> = emptyList(),
    @SerialName("Templates") val templates: List<RuleTemplate> = emptyList(),
)

// 规则覆写，路径和格式同 PC：rules/rule_overrides.json，每个订阅一项，模板全局共用。
class RuleOverrideStore(fileManager: ProfileFileManager, scope: CoroutineScope) {

    private val file = JsonFileStore(
        fileManager, PATH, RuleOverrideFile.serializer(), RuleOverrideFile(), scope,
    )

    val templates: Flow<List<RuleTemplate>> = file.state.map { it.templates }.distinctUntilChanged()

    fun observe(subscriptionId: String): Flow<RuleOverrideSet> =
        file.state.map { find(subscriptionId) }.distinctUntilChanged()

    fun find(subscriptionId: String): RuleOverrideSet =
        file.value.items.firstOrNull { it.subscriptionId == subscriptionId } ?: RuleOverrideSet(subscriptionId)

    fun templates(): List<RuleTemplate> = file.value.templates

    // 空集合直接移除，与没有记录等价。
    suspend fun save(set: RuleOverrideSet) {
        file.update { current ->
            val rest = current.items.filterNot { it.subscriptionId == set.subscriptionId }
            current.copy(items = if (set.isEmpty) rest else rest + set)
        }
    }

    suspend fun delete(subscriptionId: String) {
        file.update { current -> current.copy(items = current.items.filterNot { it.subscriptionId == subscriptionId }) }
    }

    suspend fun upsertTemplate(template: RuleTemplate) {
        file.update { current -> current.copy(templates = current.templates.filterNot { it.id == template.id } + template) }
    }

    suspend fun deleteTemplate(id: String) {
        file.update { current -> current.copy(templates = current.templates.filterNot { it.id == id }) }
    }

    private companion object {
        const val PATH = "rules/rule_overrides.json"
    }
}
