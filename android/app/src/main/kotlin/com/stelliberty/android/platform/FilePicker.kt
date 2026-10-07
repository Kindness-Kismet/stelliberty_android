package com.stelliberty.android.platform

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import java.io.Writer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.stelliberty.android.util.AppLogger

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
    private val saveLauncher = activity.registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val cb = saveCallback
        saveCallback = null
        AppLogger.info("FilePicker", "Create document result: code=${result.resultCode}, hasUri=${result.data?.data != null}")
        cb?.invoke(if (result.resultCode == Activity.RESULT_OK) result.data?.data else null)
    }

    private var pickCallback: ((Uri?) -> Unit)? = null
    private val pickLauncher = activity.registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        val cb = pickCallback
        pickCallback = null
        cb?.invoke(uri)
    }

    fun createDocument(suggestedName: String, mimeType: String, onResult: (Uri?) -> Unit) {
        saveCallback = onResult
        try {
            saveLauncher.launch(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = mimeType
                putExtra(Intent.EXTRA_TITLE, suggestedName)
            })
        } catch (error: Exception) {
            saveCallback = null
            throw error
        }
    }

    suspend fun writeTextDocument(uri: Uri, write: (Writer) -> Unit): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val output = checkNotNull(activity.contentResolver.openOutputStream(uri, "wt")) {
                "Unable to open document for writing"
            }
            output.bufferedWriter(Charsets.UTF_8).use(write)
            AppLogger.info("FilePicker", "Document saved")
        }
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
