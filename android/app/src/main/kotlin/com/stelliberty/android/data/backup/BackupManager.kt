package com.stelliberty.android.data.backup

import android.content.Context
import android.net.Uri
import com.stelliberty.android.data.bridge.StellibertyCoreBridge
import com.stelliberty.android.data.repository.OverrideJsonStore
import com.stelliberty.android.data.repository.ProfileProcessor
import com.stelliberty.android.data.repository.SubscriptionRepositoryImpl
import com.stelliberty.android.data.store.OverrideListFile
import com.stelliberty.android.data.store.OverrideProfileStore
import com.stelliberty.android.data.store.ProxySelectionFile
import com.stelliberty.android.data.store.ProxySelectionStore
import com.stelliberty.android.data.store.StoreJson
import com.stelliberty.android.data.store.SubscriptionListFile
import com.stelliberty.android.data.store.SubscriptionStore
import com.stelliberty.android.domain.model.OverrideFormat
import com.stelliberty.android.domain.model.Subscription
import com.stelliberty.android.platform.BootStartManager
import com.stelliberty.android.platform.PlatformStorage
import com.stelliberty.android.platform.StorageKeys
import com.stelliberty.android.service.ProfileFileOps
import com.stelliberty.android.util.AppLogger
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

class BackupException(message: String) : Exception(message)

enum class RestoreMode { Merge, Overwrite }

@Serializable
data class BackupSnapshot(
    val version: Int,
    val createdAt: Long,
    val stringPrefs: Map<String, String> = emptyMap(),
    val stringSetPrefs: Map<String, List<String>> = emptyMap(),
    val bootStartEnabled: Boolean = false,
)

// snapshot 为 null 表示 PC 的备份包，只带可迁移设置与订阅。
private class StagedArchive(val snapshot: BackupSnapshot?, val settings: JsonObject?)

// 备份包与 PC 同结构：根目录放 PC 认的可迁移设置、订阅列表、明文订阅与覆写，Android 专有内容放 android/ 下，
// PC 恢复时按白名单跳过它。两端的包都能在这里恢复。
class BackupManager(
    private val context: Context,
    private val storage: PlatformStorage,
    private val subscriptionStore: SubscriptionStore,
    private val proxySelectionStore: ProxySelectionStore,
    private val overrideStore: OverrideJsonStore,
    private val overrideProfileStore: OverrideProfileStore,
    private val repository: SubscriptionRepositoryImpl,
    private val bootStartManager: BootStartManager,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val mihomoDir: File
        get() = File(context.filesDir, "mihomo")

    suspend fun exportTo(uri: Uri) = withContext(Dispatchers.IO) {
        val out = context.contentResolver.openOutputStream(uri, "wt")
            ?: throw BackupException("Cannot open $uri for writing")
        out.use { writeBackup(it) }
    }

    suspend fun writeBackupTo(file: File) = withContext(Dispatchers.IO) {
        file.outputStream().use { writeBackup(it) }
    }

    // 边读边写，整个压缩包不进内存。备份内容能有几十兆，先在内存里攒完整个压缩包、
    // 恢复时再全量解压一份，这两头叠起来正是最容易内存溢出的组合。
    private suspend fun writeBackup(out: OutputStream) = ProfileProcessor.withProcessLock {
        withContext(Dispatchers.IO) {
            val prefs = storage.dumpAll().filterKeys { it !in EXCLUDED_PREF_KEYS }
            val stringPrefs = prefs.mapNotNull { (k, v) -> (v as? String)?.let { k to it } }.toMap()
            val snapshot = BackupSnapshot(
                version = BACKUP_VERSION,
                createdAt = System.currentTimeMillis(),
                stringPrefs = stringPrefs,
                stringSetPrefs = prefs
                    .mapNotNull { (k, v) ->
                        @Suppress("UNCHECKED_CAST")
                        (v as? Set<String>)?.let { k to it.toList() }
                    }
                    .toMap(),
                bootStartEnabled = bootStartManager.isEnabled(),
            )
            val override = overrideStore.state.value
            // 列表取存储的内存值：当前订阅与节点选择是异步写盘的，文件可能落后一步。
            val (storeFiles, imported) = repository.withProfileLock {
                subscriptionStore.snapshotFiles() + proxySelectionStore.snapshotFiles() to subscriptionStore.imported()
            }

            ZipOutputStream(out.buffered()).use { zip ->
                zip.putEntry(ENTRY_SETTINGS, PortableSettings.export(override, stringPrefs).toString().toByteArray())
                storeFiles.forEach { (path, text) ->
                    val name = if (path == SubscriptionStore.IMPORTED_PATH) path else "$ANDROID_DIR/$path"
                    zip.putEntry(name, text.toByteArray())
                }
                imported.forEach { zip.putSubscriptionConfig(it) }
                zip.putOverrides()
                zip.putEntry("$ANDROID_DIR/$ENTRY_SNAPSHOT", json.encodeToString(snapshot).toByteArray())
                zip.putEntry("$ANDROID_DIR/$OVERRIDE_FILE", overrideStore.encode(override).toByteArray())
                zipDirIfExists(zip, File(mihomoDir, IMPORTED_DIR), "$ANDROID_DIR/$IMPORTED_DIR", skipConfig = true)
                zipDirIfExists(zip, File(mihomoDir, PENDING_DIR), "$ANDROID_DIR/$PENDING_DIR", skipConfig = false)
            }
        }
    }

    // PC 按 subscriptions/{id}.yaml 读明文配置；带 age 密钥的订阅先解密，密钥在列表里随包导出。
    private fun ZipOutputStream.putSubscriptionConfig(subscription: Subscription) {
        val config = File(mihomoDir, "$IMPORTED_DIR/${subscription.id}/$CONFIG_FILE")
        if (!config.isFile) return
        val name = "${SubscriptionStore.DIRECTORY}/${subscription.id}$CONFIG_SUFFIX"
        if (subscription.ageSecretKey.isEmpty()) return putFile(name, config)
        val plain = File(context.cacheDir, DECRYPT_FILE)
        try {
            runCatching { StellibertyCoreBridge.decryptFile(config, plain, subscription.ageSecretKey) }
                .getOrElse { throw BackupException("Cannot decrypt subscription ${subscription.id}: ${it.message}") }
            putFile(name, plain)
        } finally {
            plain.delete()
        }
    }

    // 列表取内存值，只带列表里有的内容文件；内容文件是原子写入的，读到的总是某个完整版本。
    private fun ZipOutputStream.putOverrides() {
        val profiles = overrideProfileStore.all()
        if (profiles.isEmpty()) return
        putEntry(OverrideProfileStore.LIST_PATH, overrideProfileStore.encodeList().toByteArray())
        profiles.forEach { profile ->
            val file = File(mihomoDir, overrideProfileStore.contentPath(profile))
            putFile(overrideProfileStore.contentPath(profile), file)
        }
    }

    suspend fun <T> withTransferFile(block: suspend (File) -> T): T {
        val file = File(context.cacheDir, TRANSFER_FILE)
        return try {
            block(file)
        } finally {
            withContext(Dispatchers.IO) { file.delete() }
        }
    }

    suspend fun importFrom(uri: Uri, mode: RestoreMode) = withContext(Dispatchers.IO) {
        val input = context.contentResolver.openInputStream(uri)
            ?: throw BackupException("Cannot open $uri for reading")
        input.use { restoreBackup(it, mode) }
    }

    suspend fun restoreBackupFrom(file: File, mode: RestoreMode) = withContext(Dispatchers.IO) {
        file.inputStream().use { restoreBackup(it, mode) }
    }

    private suspend fun restoreBackup(input: InputStream, mode: RestoreMode) = ProfileProcessor.withProcessLock {
        withContext(Dispatchers.IO) {
            val archive = extractToStaging(input)
            when (mode) {
                RestoreMode.Overwrite -> overwrite(archive)
                RestoreMode.Merge -> merge()
            }
            if (subscriptionStore.current() == null) {
                subscriptionStore.setCurrentId(subscriptionStore.imported().firstOrNull()?.id)
            }
            storage.putString(StorageKeys.ACTIVE_PROFILE_NAME, subscriptionStore.current()?.name.orEmpty())
        }
    }

    // 分两步落地：先全部写进暂存目录，正式目录直到改名换上去那一刻才被碰，失败就从备份目录退回来。
    // 绝不能先删再逐条校验——压缩包有问题或磁盘写满时，用户的订阅目录就永久消失了。
    private suspend fun overwrite(archive: StagedArchive) {
        val snapshot = archive.snapshot
        // PC 的包没有 Android 专有内容：设置按可迁移键合进本机覆写，草稿与节点选择随覆盖清空。
        val portable = if (snapshot == null) {
            PortableSettings.import(archive.settings ?: JsonObject(emptyMap()), overrideStore.state.value, stringPrefs())
                .also { File(mihomoDir, "$RESTORE_STAGING/$OVERRIDE_FILE").writeText(overrideStore.encode(it.override)) }
        } else {
            null
        }

        // 换入后立即重新载入，内存里的旧值不能再写回磁盘。
        repository.withProfileLock {
            swapStagedFiles()
            subscriptionStore.reload()
            proxySelectionStore.reload()
            overrideStore.reload()
            overrideProfileStore.reload()
        }

        if (snapshot != null) {
            // 快照里没有的偏好在源设备上是默认值，覆盖后同样回到默认。
            val restored = snapshot.stringPrefs.keys + snapshot.stringSetPrefs.keys
            storage.dumpAll().keys
                .filter { it !in EXCLUDED_PREF_KEYS && it !in restored }
                .forEach(storage::remove)
            snapshot.stringPrefs.forEach { (k, v) ->
                if (k !in EXCLUDED_PREF_KEYS) storage.putString(k, v)
            }
            snapshot.stringSetPrefs.forEach { (k, v) ->
                if (k !in EXCLUDED_PREF_KEYS) storage.putStringSet(k, v.toSet())
            }
            bootStartManager.setEnabled(snapshot.bootStartEnabled)
        }
        portable?.prefs?.forEach { (k, v) -> storage.putString(k, v) }
    }

    // 只加入本机没有的订阅与覆写，连同配置、provider 缓存与节点选择；本机设置、草稿与当前订阅不动。
    // 先移入目录再写列表：中途失败时多出的目录不在列表里，下次启动按孤儿清理。
    private suspend fun merge() {
        val staging = File(mihomoDir, RESTORE_STAGING)
        try {
            val incoming = readStaged(staging, SubscriptionStore.IMPORTED_PATH, SubscriptionListFile.serializer())
                ?.subscriptions.orEmpty()
            val selections = readStaged(staging, ProxySelectionStore.SELECTION_PATH, ProxySelectionFile.serializer())
                ?.subscriptions.orEmpty()
            mergeOverrides(staging)
            repository.withProfileLock {
                val known = subscriptionStore.imported().mapTo(HashSet()) { it.id }
                val added = incoming.filter { it.id !in known && ID_PATTERN.matches(it.id) }.distinctBy { it.id }
                added.forEach { moveStagedProfile(staging, it.id) }
                subscriptionStore.addImported(added)
                added.forEach { subscription ->
                    selections[subscription.id]?.forEach { (group, node) ->
                        proxySelectionStore.select(subscription.id, group, node)
                    }
                }
            }
        } finally {
            staging.deleteRecursively()
        }
    }

    private suspend fun mergeOverrides(staging: File) {
        val incoming = readStaged(staging, OverrideProfileStore.LIST_PATH, OverrideListFile.serializer())
            ?.overrides.orEmpty()
        val known = overrideProfileStore.all().mapTo(HashSet()) { it.id }
        val added = incoming.filter { it.id !in known && ID_PATTERN.matches(it.id) }.distinctBy { it.id }
        added.forEach { profile ->
            val path = overrideProfileStore.contentPath(profile)
            val source = File(staging, path)
            val target = File(mihomoDir, path)
            target.parentFile?.mkdirs()
            if (source.isFile && !source.renameTo(target)) throw BackupException("Cannot move in override ${profile.id}")
        }
        overrideProfileStore.addAll(added)
    }

    private fun <T> readStaged(staging: File, path: String, deserializer: DeserializationStrategy<T>): T? {
        val file = File(staging, path)
        if (!file.isFile) return null
        return runCatching { StoreJson.decodeFromString(deserializer, file.readText()) }
            .getOrElse { throw BackupException("Invalid $path: ${it.message}") }
    }

    // 该 id 不在本机列表里，同名目录只可能是孤儿。
    private fun moveStagedProfile(staging: File, id: String) {
        val source = File(staging, "$IMPORTED_DIR/$id")
        if (!source.isDirectory) return
        val target = File(mihomoDir, "$IMPORTED_DIR/$id")
        target.deleteRecursively()
        target.parentFile?.mkdirs()
        if (!source.renameTo(target)) throw BackupException("Cannot move in subscription $id")
    }

    private fun stringPrefs(): Map<String, String> =
        storage.dumpAll().mapNotNull { (k, v) -> (v as? String)?.let { k to it } }.toMap()

    private fun ZipOutputStream.putEntry(name: String, data: ByteArray) {
        putNextEntry(ZipEntry(name))
        write(data)
        closeEntry()
    }

    private fun ZipOutputStream.putFile(name: String, file: File) {
        putNextEntry(ZipEntry(name))
        file.inputStream().use { it.copyTo(this) }
        closeEntry()
    }

    private fun extractToStaging(input: InputStream): StagedArchive {
        val staging = File(mihomoDir, RESTORE_STAGING)
        staging.deleteRecursively()
        val stagingRoot = staging.also { it.mkdirs() }.canonicalPath + File.separator
        try {
            var snapshotBytes: ByteArray? = null
            var settingsBytes: ByteArray? = null
            var hasProfiles = false
            ZipInputStream(input.buffered()).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    val name = entry.name
                    if (!entry.isDirectory) {
                        when (name) {
                            ENTRY_SETTINGS -> settingsBytes = zis.readBytes()
                            "$ANDROID_DIR/$ENTRY_SNAPSHOT" -> snapshotBytes = zis.readBytes()
                            else -> stagingPathOf(name)?.let { path ->
                                val target = File(staging, path)
                                if (!target.canonicalPath.startsWith(stagingRoot)) {
                                    throw BackupException("Illegal entry path: $name")
                                }
                                target.parentFile?.mkdirs()
                                target.outputStream().use { zis.copyTo(it) }
                                if (path == SubscriptionStore.IMPORTED_PATH || path == OverrideProfileStore.LIST_PATH) {
                                    hasProfiles = true
                                }
                            }
                        }
                    }
                    entry = zis.nextEntry
                }
            }

            val snapshot = snapshotBytes?.let { bytes ->
                runCatching { json.decodeFromString<BackupSnapshot>(bytes.decodeToString()) }
                    .getOrElse { throw BackupException("Invalid $ANDROID_DIR/$ENTRY_SNAPSHOT: ${it.message}") }
            }
            if (snapshot != null && snapshot.version != BACKUP_VERSION) {
                throw BackupException("Unsupported backup version: ${snapshot.version}")
            }
            if (snapshot == null && settingsBytes == null && !hasProfiles) {
                throw BackupException("Archive contains no restorable data")
            }
            val settings = settingsBytes?.let { bytes ->
                runCatching { json.parseToJsonElement(bytes.decodeToString()).jsonObject }
                    .getOrElse { throw BackupException("Invalid $ENTRY_SETTINGS: ${it.message}") }
            }
            validateStagedOverrides(staging)
            return StagedArchive(snapshot, settings)
        } catch (e: Throwable) {
            staging.deleteRecursively()
            throw e
        }
    }

    // 列表和内容在换入前一起校验，损坏的包不能覆盖本机已有覆写。
    private fun validateStagedOverrides(staging: File) {
        val profiles = readStaged(staging, OverrideProfileStore.LIST_PATH, OverrideListFile.serializer())
            ?.overrides.orEmpty()
        val ids = HashSet<String>()
        profiles.forEach { profile ->
            if (!ID_PATTERN.matches(profile.id) || !ids.add(profile.id)) {
                throw BackupException("Invalid or duplicate override id")
            }
            if (!File(staging, overrideProfileStore.contentPath(profile)).isFile) {
                throw BackupException("Missing content for override ${profile.id}")
            }
        }
    }

    // 包内路径 → 暂存区路径；不认识的条目（PC 的 dns.json 等）返回 null 跳过。
    private fun stagingPathOf(name: String): String? {
        if (name == SubscriptionStore.IMPORTED_PATH || name == OverrideProfileStore.LIST_PATH) return name
        val overrideFile = name.removePrefix("${OverrideProfileStore.DIRECTORY}/")
        if (overrideFile != name) {
            val id = overrideFile.substringBeforeLast('.')
            val extension = overrideFile.substringAfterLast('.', "")
            return name.takeIf { ID_PATTERN.matches(id) && extension in OVERRIDE_EXTENSIONS }
        }
        val subscriptionConfig = name.removePrefix("${SubscriptionStore.DIRECTORY}/")
        if (subscriptionConfig != name && subscriptionConfig.endsWith(CONFIG_SUFFIX)) {
            val id = subscriptionConfig.removeSuffix(CONFIG_SUFFIX)
            return if (ID_PATTERN.matches(id)) "$IMPORTED_DIR/$id/$CONFIG_FILE" else null
        }
        val path = name.removePrefix("$ANDROID_DIR/")
        if (path == name) return null
        return when {
            path in ANDROID_FILES -> path
            path.startsWith("$PENDING_DIR/") -> path
            // 订阅配置以根目录的明文为准。
            path.startsWith("$IMPORTED_DIR/") && path.substringAfter('/').substringAfter('/') != CONFIG_FILE -> path
            else -> null
        }
    }

    private fun swapStagedFiles() {
        val staging = File(mihomoDir, RESTORE_STAGING)
        val old = File(mihomoDir, RESTORE_OLD)
        old.deleteRecursively()
        try {
            old.mkdirs()
            RESTORE_TARGETS.forEach { swapIn(it, staging, old) }
        } catch (e: Throwable) {
            AppLogger.error(TAG, "Restore swap failed, rolling back", e)
            RESTORE_TARGETS.forEach { rollbackFrom(it, old) }
            throw e
        } finally {
            staging.deleteRecursively()
            old.deleteRecursively()
        }
    }

    private fun swapIn(name: String, staging: File, old: File) {
        val current = File(mihomoDir, name)
        if (current.exists() && !current.renameTo(File(old, name))) {
            throw BackupException("Cannot move aside $name")
        }
        val incoming = File(staging, name)
        if (incoming.exists() && !incoming.renameTo(current)) {
            throw BackupException("Cannot swap in $name")
        }
    }

    private fun rollbackFrom(name: String, old: File) {
        val saved = File(old, name)
        if (!saved.exists()) return
        val current = File(mihomoDir, name)
        current.deleteRecursively()
        saved.renameTo(current)
    }

    private fun zipDirIfExists(zip: ZipOutputStream, dir: File, entryPrefix: String, skipConfig: Boolean) {
        if (!dir.isDirectory) return
        dir.walkTopDown()
            .filter { it.isFile }
            .forEach { file ->
                if (java.nio.file.Files.isSymbolicLink(file.toPath())) return@forEach
                if (file.name in ProfileFileOps.GEODATA_FILES) return@forEach
                val relative = file.relativeTo(dir).invariantSeparatorsPath
                if (skipConfig && relative.substringAfter('/') == CONFIG_FILE) return@forEach
                zip.putFile("$entryPrefix/$relative", file)
            }
    }

    companion object {
        private const val TAG = "BackupManager"

        const val BACKUP_VERSION = 3

        // 与 PC 相同的文件名：stelliberty-yyyyMMdd-HHmmss.stelliberty（本机时间）。
        fun newBackupFileName(now: Date = Date()): String =
            "stelliberty-${SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(now)}$BACKUP_EXTENSION"

        const val BACKUP_EXTENSION = ".stelliberty"

        private const val ENTRY_SETTINGS = "settings.json"
        private const val ENTRY_SNAPSHOT = "backup.json"
        private const val ANDROID_DIR = "android"
        private const val IMPORTED_DIR = "imported"
        private const val PENDING_DIR = "pending"
        private const val CONFIG_FILE = "config.yaml"
        private const val CONFIG_SUFFIX = ".yaml"
        private const val OVERRIDE_FILE = OverrideJsonStore.FILE_NAME
        private val ID_PATTERN = Regex("[A-Za-z0-9-]+")
        private val OVERRIDE_EXTENSIONS = OverrideFormat.entries.map { it.extension }.toSet()

        private val ANDROID_FILES = setOf(
            SubscriptionStore.PENDING_PATH,
            SubscriptionStore.SELECTION_PATH,
            ProxySelectionStore.SELECTION_PATH,
            OVERRIDE_FILE,
        )

        private const val RESTORE_STAGING = ".restore"
        private const val RESTORE_OLD = ".restore-old"

        private const val TRANSFER_FILE = "stelliberty-backup-transfer$BACKUP_EXTENSION"
        private const val DECRYPT_FILE = "stelliberty-backup-decrypt.yaml"
        private val RESTORE_TARGETS = listOf(
            IMPORTED_DIR,
            PENDING_DIR,
            SubscriptionStore.DIRECTORY,
            ProxySelectionStore.DIRECTORY,
            OverrideProfileStore.DIRECTORY,
            OVERRIDE_FILE,
        )

        private val EXCLUDED_PREF_KEYS = setOf(
            StorageKeys.SERVICE_WAS_RUNNING,
            StorageKeys.HAS_ROOT,
            StorageKeys.ROOT_MIHOMO_PID,
            StorageKeys.ROOT_MIHOMO_SECRET,
            StorageKeys.ROOT_START_TIME,
            StorageKeys.ROOT_ACTIVE_SUBSCRIPTION_ID,
            StorageKeys.ROOT_BOOT_COUNT,
            StorageKeys.ROOT_SUBMODE_ACTIVE,
            StorageKeys.ROOT_TETHER_MODE_ACTIVE,
            StorageKeys.ROOT_TPROXY_KERNEL_CAPABLE,
            StorageKeys.WIFI_POLICY_MATCHED,
            StorageKeys.WIFI_POLICY_MATCHED_ACTION,
            StorageKeys.WIFI_POLICY_PENDING_RESTART,
            StorageKeys.WIFI_POLICY_RUNTIME_MODE,
            StorageKeys.WEBDAV_URL,
            StorageKeys.WEBDAV_USERNAME,
            StorageKeys.WEBDAV_PASSWORD,
        )
    }
}
