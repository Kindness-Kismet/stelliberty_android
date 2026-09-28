package com.stelliberty.android.domain.repository

import com.stelliberty.android.domain.model.EditableRule
import com.stelliberty.android.domain.model.RuleOverrideContext
import com.stelliberty.android.domain.model.RuleOverrideSet
import com.stelliberty.android.domain.model.RuleTemplate
import kotlinx.coroutines.flow.Flow

// 规则覆写按订阅保存，模板全局共用；页面基线取自套完覆写与链式代理的配置，与运行时看到的一致。
interface RuleOverrideRepository {

    val templates: Flow<List<RuleTemplate>>

    fun observe(subscriptionId: String): Flow<RuleOverrideSet>

    fun find(subscriptionId: String): RuleOverrideSet

    suspend fun loadContext(subscriptionId: String): RuleOverrideContext

    // 保存前按运行时流程校验，失败抛 ConfigValidationException、不保存。
    suspend fun save(set: RuleOverrideSet)

    // 同名模板（不区分大小写）覆盖原有内容。
    suspend fun saveTemplate(name: String, rules: List<EditableRule>)

    suspend fun deleteTemplate(id: String)
}
