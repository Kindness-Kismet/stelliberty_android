package com.stelliberty.android.platform

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts

data class FilePickResult(
    val fileName: String,
    val content: String,
)

class FilePicker(private val activity: ComponentActivity) {

    private var callback: ((FilePickResult?) -> Unit)? = null

    private val launcher = activity.registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result -> handleResult(result) }

    fun pickYamlFile(onResult: (FilePickResult?) -> Unit) {
        callback = onResult
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
        }
        launcher.launch(intent)
    }

    private var saveCallback: ((Uri?) -> Unit)? = null
    // 用 octet-stream：按 zip 类型保存时文档提供方会给 .stelliberty 文件名再补一个 .zip 后缀。
    private val saveLauncher = activity.registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        val cb = saveCallback
        saveCallback = null
        cb?.invoke(uri)
    }

    private var pickCallback: ((Uri?) -> Unit)? = null
    private val pickLauncher = activity.registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        val cb = pickCallback
        pickCallback = null
        cb?.invoke(uri)
    }

    fun createZipDocument(suggestedName: String, onResult: (Uri?) -> Unit) {
        saveCallback = onResult
        saveLauncher.launch(suggestedName)
    }

    fun pickZipDocument(onResult: (Uri?) -> Unit) {
        pickCallback = onResult
        pickLauncher.launch(arrayOf("*/*"))
    }

    private fun handleResult(result: ActivityResult) {
        val data = result.data
        if (result.resultCode != Activity.RESULT_OK || data?.data == null) {
            callback?.invoke(null)
            callback = null
            return
        }
        val uri: Uri = data.data!!
        try {
            val fileName = getFileName(uri)
            val content = activity.contentResolver.openInputStream(uri)
                ?.bufferedReader()
                ?.use { it.readText() }
                ?: ""
            callback?.invoke(FilePickResult(fileName, content))
        } catch (_: Exception) {
            callback?.invoke(null)
        }
        callback = null
    }

    private fun getFileName(uri: Uri): String {
        var name = "imported.yaml"
        activity.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIndex >= 0 && cursor.moveToFirst()) {
                name = cursor.getString(nameIndex)
            }
        }
        return name
    }
}
