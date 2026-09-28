package com.stelliberty.android.platform

interface ProfileFileManager {
    fun savePendingConfig(uuid: String, content: String)
    fun releasePending(uuid: String)

    fun prepareProcessing(uuid: String): String

    fun writeProcessingConfig(workDir: String, content: String)

    fun cleanupProcessing()

    fun commitProcessingToImported(uuid: String)

    fun getMihomoWorkDir(): String

    fun readMihomoFile(relativePath: String): String?

    fun writeMihomoFile(relativePath: String, content: String)

    fun backupMihomoFile(relativePath: String)

    fun getImportedDir(uuid: String): String
    fun listImportedFiles(uuid: String): List<String>
    fun readImportedFile(uuid: String, relativePath: String): String?
    fun writeImportedFile(uuid: String, relativePath: String, content: String)
    fun deleteDirs(uuid: String)

    fun deleteOrphanDirs(knownUuids: Set<String>): List<String>
}
