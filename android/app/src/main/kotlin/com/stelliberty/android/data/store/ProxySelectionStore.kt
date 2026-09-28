package com.stelliberty.android.data.store

import com.stelliberty.android.platform.ProfileFileManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class ProxySelectionFile(
    // 订阅 id → 代理组名 → 选中的节点名
    @SerialName("Subscriptions") val subscriptions: Map<String, Map<String, String>> = emptyMap(),
)

// 各订阅的节点选择，文件格式与 PC 的 proxies/selection_state.json 相同。
class ProxySelectionStore(fileManager: ProfileFileManager, scope: CoroutineScope) {

    private val file = JsonFileStore(
        fileManager, SELECTION_PATH, ProxySelectionFile.serializer(), ProxySelectionFile(), scope,
    )

    fun selections(subscriptionId: String): Map<String, String> =
        file.value.subscriptions[subscriptionId].orEmpty()

    fun select(subscriptionId: String, group: String, proxy: String) {
        file.updateLater { state ->
            val groups = state.subscriptions[subscriptionId].orEmpty() + (group to proxy)
            state.copy(subscriptions = state.subscriptions + (subscriptionId to groups))
        }
    }

    fun clear(subscriptionId: String, group: String) {
        file.updateLater { state ->
            val groups = state.subscriptions[subscriptionId] ?: return@updateLater state
            val remaining = groups - group
            state.copy(
                subscriptions = if (remaining.isEmpty()) state.subscriptions - subscriptionId
                else state.subscriptions + (subscriptionId to remaining)
            )
        }
    }

    fun removeSubscription(subscriptionId: String) {
        file.updateLater { it.copy(subscriptions = it.subscriptions - subscriptionId) }
    }

    fun snapshotFiles(): Map<String, String> = mapOf(SELECTION_PATH to file.encode())

    suspend fun reload() = file.reload()

    companion object {
        const val DIRECTORY = "proxies"
        const val SELECTION_PATH = "$DIRECTORY/selection_state.json"
    }
}
