package com.stelliberty.android.data.store

import com.stelliberty.android.domain.model.Subscription
import com.stelliberty.android.platform.ProfileFileManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class SubscriptionListFile(
    @SerialName("Subscriptions") val subscriptions: List<Subscription> = emptyList(),
)

@Serializable
internal data class SubscriptionSelectionFile(
    @SerialName("CurrentSubscriptionId") val currentSubscriptionId: String? = null,
)

// 订阅列表与当前订阅，文件格式与 PC 相同；草稿列表是 Android 专有的。
// 列表与草稿先写盘再生效：提交时 imported/{id}/ 已换入，列表未写盘就被杀会让启动清理把它当孤儿删除。
class SubscriptionStore(fileManager: ProfileFileManager, scope: CoroutineScope) {

    private val importedFile = JsonFileStore(
        fileManager, IMPORTED_PATH, SubscriptionListFile.serializer(), SubscriptionListFile(), scope,
    )
    private val pendingFile = JsonFileStore(
        fileManager, PENDING_PATH, SubscriptionListFile.serializer(), SubscriptionListFile(), scope,
    )
    private val selectionFile = JsonFileStore(
        fileManager, SELECTION_PATH, SubscriptionSelectionFile.serializer(), SubscriptionSelectionFile(), scope,
    )

    val importedFlow: Flow<List<Subscription>> = importedFile.state.map { it.subscriptions }
    val pendingFlow: Flow<List<Subscription>> = pendingFile.state.map { it.subscriptions }
    val currentIdState: StateFlow<String?> = selectionFile.state
        .map { it.currentSubscriptionId }
        .stateIn(scope, SharingStarted.Eagerly, selectionFile.value.currentSubscriptionId)

    // 列表可信时才允许按列表删除孤儿目录，否则会删掉全部订阅。
    val isComplete: Boolean
        get() = !importedFile.loadFailed && !pendingFile.loadFailed

    fun imported(): List<Subscription> = importedFile.value.subscriptions

    fun pending(): List<Subscription> = pendingFile.value.subscriptions

    fun findImported(id: String): Subscription? = imported().firstOrNull { it.id == id }

    fun findPending(id: String): Subscription? = pending().firstOrNull { it.id == id }

    fun currentId(): String? = selectionFile.value.currentSubscriptionId

    fun current(): Subscription? = currentId()?.let(::findImported)

    // 运行期是否要补 mixed-port：有订阅经内核更新时才需要；还没有订阅时按新订阅的默认值（经内核）算。
    fun anyUpdatesViaCore(): Boolean {
        val all = imported() + pending()
        return all.isEmpty() || all.any { it.updatesViaCore }
    }

    suspend fun putImported(subscription: Subscription) {
        importedFile.update { it.copy(subscriptions = it.subscriptions.upsert(subscription)) }
    }

    suspend fun addImported(subscriptions: List<Subscription>) {
        importedFile.update { it.copy(subscriptions = it.subscriptions + subscriptions) }
    }

    suspend fun removeImported(id: String) {
        importedFile.update { file -> file.copy(subscriptions = file.subscriptions.filterNot { it.id == id }) }
    }

    suspend fun putPending(subscription: Subscription) {
        pendingFile.update { it.copy(subscriptions = it.subscriptions.upsert(subscription)) }
    }

    suspend fun removePending(id: String) {
        pendingFile.update { file -> file.copy(subscriptions = file.subscriptions.filterNot { it.id == id }) }
    }

    fun setCurrentId(id: String?) {
        selectionFile.updateLater { it.copy(currentSubscriptionId = id?.takeIf { value -> value.isNotEmpty() }) }
    }

    // 备份导出直接取内存值：当前订阅是异步写盘的，读文件可能拿到上一次的值。
    fun snapshotFiles(): Map<String, String> = mapOf(
        IMPORTED_PATH to importedFile.encode(),
        PENDING_PATH to pendingFile.encode(),
        SELECTION_PATH to selectionFile.encode(),
    )

    suspend fun reload() {
        importedFile.reload()
        pendingFile.reload()
        selectionFile.reload()
    }

    companion object {
        const val DIRECTORY = "subscriptions"
        const val IMPORTED_PATH = "$DIRECTORY/subscriptions_list.json"
        const val PENDING_PATH = "$DIRECTORY/pending_list.json"
        const val SELECTION_PATH = "$DIRECTORY/selection_state.json"
    }
}

// 保持原有顺序：订阅页按列表顺序展示。
private fun List<Subscription>.upsert(item: Subscription): List<Subscription> {
    val index = indexOfFirst { it.id == item.id }
    if (index < 0) return this + item
    return toMutableList().also { it[index] = item }
}
