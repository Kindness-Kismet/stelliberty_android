package com.stelliberty.android.data.store

import com.stelliberty.android.domain.model.RuleOverrideSet
import com.stelliberty.android.domain.model.Subscription
import com.stelliberty.android.domain.model.SubscriptionCustomChainProxy
import com.stelliberty.android.platform.ProfileFileManager
import java.io.File
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Go 侧 overrides.Transform：先按顺序套覆写，再处理链式代理，最后套规则覆写。
@Serializable
internal data class TransformSpec(
    @SerialName("overrides") val overrides: List<OverrideSpec>,
    @SerialName("disabledBuiltinChainProxyNames") val disabledBuiltinChainProxyNames: List<String>,
    @SerialName("customChainProxies") val customChainProxies: List<SubscriptionCustomChainProxy>,
    @SerialName("ruleOverride") val ruleOverride: RuleOverrideSet?,
)

// 订阅的覆写、链式代理与规则覆写合成一份变换文件：运行时经 --transform 交给内核，应用内校验与各页面的基线也读它。
class ProfileTransformWriter(
    private val fileManager: ProfileFileManager,
    private val overrides: OverrideProfileStore,
    private val ruleOverrides: RuleOverrideStore,
) {

    // 返回文件的绝对路径；无需变换时删掉旧文件、返回 null。withChains = false 只带覆写，rules 为 null 时不套规则覆写。
    fun write(
        subscription: Subscription,
        relativePath: String,
        replacement: OverrideReplacement? = null,
        withChains: Boolean = true,
        rules: RuleOverrideSet? = ruleOverrides.find(subscription.id),
    ): String? {
        val spec = TransformSpec(
            overrides = overrides.specs(subscription.orderedOverrideIds, replacement),
            disabledBuiltinChainProxyNames = if (withChains) subscription.disabledBuiltinChainProxyNames else emptyList(),
            customChainProxies = if (withChains) subscription.customChainProxies else emptyList(),
            ruleOverride = rules?.takeUnless { it.isEmpty },
        )
        val target = File(fileManager.getMihomoWorkDir(), relativePath)
        val hasChains = spec.disabledBuiltinChainProxyNames.isNotEmpty() || spec.customChainProxies.any { it.isEnabled }
        if (spec.overrides.isEmpty() && !hasChains && spec.ruleOverride == null) {
            target.delete()
            return null
        }
        fileManager.writeMihomoFile(relativePath, StoreJson.encodeToString(TransformSpec.serializer(), spec))
        return target.path
    }

    // 代理启动前调用：总按当前订阅重写，没有订阅时同样清掉旧文件。
    fun writeRuntime(subscription: Subscription?): String? {
        if (subscription == null) {
            File(fileManager.getMihomoWorkDir(), RUNTIME_PATH).delete()
            return null
        }
        return write(subscription, RUNTIME_PATH)
    }

    companion object {
        const val RUNTIME_PATH = "profile.transform.json"
    }
}
