package com.stelliberty.android.data.store

import com.stelliberty.android.domain.model.OverrideFormat
import com.stelliberty.android.domain.model.OverrideProfile
import com.stelliberty.android.platform.ProfileFileManager
import com.stelliberty.android.util.AppLogger
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class OverrideListFile(
    @SerialName("Overrides") val overrides: List<OverrideProfile> = emptyList(),
)

// Go 侧 overrides.Spec。
@Serializable
internal data class OverrideSpec(
    @SerialName("name") val name: String,
    @SerialName("format") val format: String,
    @SerialName("path") val path: String,
)

// 校验时用尚未保存的内容或格式代替某个覆写，relativePath 相对 files/mihomo/。
data class OverrideReplacement(val id: String, val format: OverrideFormat, val relativePath: String)

// 覆写列表与内容文件，路径和格式同 PC：overrides/overrides_list.json + overrides/{id}.yaml|js。
class OverrideProfileStore(private val fileManager: ProfileFileManager, scope: CoroutineScope) {

    private val listFile = JsonFileStore(
        fileManager, LIST_PATH, OverrideListFile.serializer(), OverrideListFile(), scope,
    )

    val profiles: Flow<List<OverrideProfile>> = listFile.state.map { it.overrides }

    fun all(): List<OverrideProfile> = listFile.value.overrides

    fun find(id: String): OverrideProfile? = all().firstOrNull { it.id == id }

    fun contentPath(profile: OverrideProfile): String = "$DIRECTORY/${profile.fileName}"

    fun readContent(profile: OverrideProfile): String = fileManager.readMihomoFile(contentPath(profile)).orEmpty()

    // 先写内容再写列表，列表里的条目总有对应文件；content 为 null 时沿用现有内容，换格式时改名。
    suspend fun save(profile: OverrideProfile, content: String?): Unit = withContext(Dispatchers.IO + NonCancellable) {
        val previousPath = find(profile.id)?.let(::contentPath)
        val target = contentPath(profile)
        val moved = previousPath != null && previousPath != target
        when {
            content != null -> fileManager.writeMihomoFile(target, content)
            moved -> fileManager.writeMihomoFile(target, fileManager.readMihomoFile(previousPath).orEmpty())
        }
        listFile.update { it.copy(overrides = it.overrides.upsert(profile)) }
        if (moved) mihomoFile(previousPath).delete()
    }

    suspend fun delete(id: String): Unit = withContext(Dispatchers.IO) {
        val profile = find(id) ?: return@withContext
        listFile.update { file -> file.copy(overrides = file.overrides.filterNot { it.id == id }) }
        mihomoFile(contentPath(profile)).delete()
    }

    // 按选择顺序给出变换清单里的覆写条目，已删除的覆写跳过。
    internal fun specs(overrideIds: List<String>, replacement: OverrideReplacement? = null): List<OverrideSpec> =
        overrideIds.mapNotNull { id ->
            val profile = find(id) ?: run {
                AppLogger.warn(TAG, "selected override missing, skipped: $id")
                return@mapNotNull null
            }
            if (replacement?.id == id) {
                OverrideSpec(profile.name, replacement.format.extension, mihomoFile(replacement.relativePath).path)
            } else {
                OverrideSpec(profile.name, profile.format.extension, mihomoFile(contentPath(profile)).path)
            }
        }

    private fun mihomoFile(relativePath: String) = File(fileManager.getMihomoWorkDir(), relativePath)

    fun encodeList(): String = listFile.encode()

    // 备份合并用：内容文件已由调用方放好，这里只追加列表条目。
    suspend fun addAll(profiles: List<OverrideProfile>) {
        if (profiles.isEmpty()) return
        listFile.update { it.copy(overrides = it.overrides + profiles) }
    }

    suspend fun reload() = listFile.reload()

    companion object {
        const val DIRECTORY = "overrides"
        const val LIST_PATH = "$DIRECTORY/overrides_list.json"
        private const val TAG = "OverrideProfileStore"
    }
}

private fun List<OverrideProfile>.upsert(item: OverrideProfile): List<OverrideProfile> {
    val index = indexOfFirst { it.id == item.id }
    if (index < 0) return this + item
    return toMutableList().also { it[index] = item }
}
