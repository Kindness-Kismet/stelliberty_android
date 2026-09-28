package com.stelliberty.android.ui.screen.settings

import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stelliberty.android.R
import com.stelliberty.android.data.bridge.StellibertyCoreBridge
import com.stelliberty.android.platform.ProfileFileManager
import com.stelliberty.android.platform.showToast
import com.stelliberty.android.ui.component.blur.BlurredBar
import com.stelliberty.android.ui.component.blur.rememberBlurBackdrop
import com.stelliberty.android.ui.icon.AppIcons
import com.stelliberty.android.ui.theme.LocalAppDarkMode
import com.stelliberty.android.ui.util.horizontalCutoutPadding
import com.stelliberty.android.viewmodel.SubscriptionViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.scripta.editor.CodeEditor
import top.yukonga.scripta.editor.EditorColors
import top.yukonga.scripta.editor.EditorLanguage
import top.yukonga.scripta.editor.rememberCodeEditorController

@Composable
fun FileManagerEditorScreen(
    uuid: String,
    relativePath: String,
    subscriptionViewModel: SubscriptionViewModel? = null,
    onBack: () -> Unit = {},
) {
    val fileManager = subscriptionViewModel?.fileManager
    val controller = rememberCodeEditorController()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var isSaving by remember { mutableStateOf(false) }

    LaunchedEffect(uuid, relativePath, fileManager) {
        val content = withContext(Dispatchers.IO) {
            fileManager?.readImportedFile(uuid, relativePath)
        } ?: ""
        controller.setDocument(content)
    }

    val backdrop = rememberBlurBackdrop()
    val blurActive = backdrop != null
    val barColor = if (blurActive) Color.Transparent else MiuixTheme.colorScheme.surface

    Scaffold(
        topBar = {
            BlurredBar(backdrop = backdrop, blurActive = blurActive) {
                SmallTopAppBar(
                    title = relativePath,
                    color = barColor,
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            val ld = LocalLayoutDirection.current
                            Icon(
                                imageVector = AppIcons.Back,
                                contentDescription = stringResource(R.string.common_back),
                                tint = MiuixTheme.colorScheme.onSurface,
                                modifier = Modifier.graphicsLayer {
                                    scaleX = if (ld == LayoutDirection.Rtl) -1f else 1f
                                },
                            )
                        }
                    },
                    actions = {
                        val canSave = controller.isModified && !isSaving && fileManager != null
                        IconButton(
                            enabled = canSave,
                            onClick = onSave@{
                                if (fileManager == null) return@onSave
                                val version = controller.documentVersion
                                val newContent = controller.getText(controller.lineEnding)
                                val userAgent = subscriptionViewModel.uiState.value
                                    .subscriptions
                                    .find { it.id == uuid }
                                    ?.userAgent
                                    .orEmpty()
                                isSaving = true
                                scope.launch {
                                    val err = runCatching {
                                        saveWithValidation(
                                            fileManager = fileManager,
                                            uuid = uuid,
                                            relativePath = relativePath,
                                            newContent = newContent,
                                            userAgent = userAgent,
                                        )
                                    }
                                    isSaving = false
                                    err.onSuccess { errMsg ->
                                        if (errMsg == null) {
                                            controller.markSaved(version)
                                            showToast(context.getString(R.string.file_manager_saved))
                                        } else {
                                            showToast(context.getString(R.string.file_manager_save_failed, errMsg), long = true)
                                        }
                                    }.onFailure { t ->
                                        showToast(
                                            context.getString(R.string.file_manager_save_failed, t.message ?: "unknown"),
                                            long = true,
                                        )
                                    }
                                }
                            },
                        ) {
                            if (isSaving) {
                                CircularProgressIndicator(size = 20.dp, strokeWidth = 2.dp)
                            } else {
                                Icon(
                                    imageVector = AppIcons.Check,
                                    contentDescription = stringResource(R.string.file_manager_save),
                                    tint = if (canSave) MiuixTheme.colorScheme.onSurface
                                    else MiuixTheme.colorScheme.disabledOnSecondaryVariant,
                                )
                            }
                        }
                    },
                )
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .horizontalCutoutPadding()
                .then(if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier)
                .padding(top = innerPadding.calculateTopPadding()),
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .padding(bottom = 12.dp),
            ) {
                Text(
                    text = stringResource(R.string.file_manager_edit_warning),
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }

            CompositionLocalProvider(LocalOverscrollFactory provides null) {
                CodeEditor(
                    controller = controller,
                    language = if (isYamlPath(relativePath)) EditorLanguage.Yaml else EditorLanguage.PlainText,
                    colors = if (LocalAppDarkMode.current) EditorColors.Default else EditorColors.Light,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                )
            }
        }
    }
}

private fun isYamlPath(path: String): Boolean = path.endsWith(".yaml") || path.endsWith(".yml")

private suspend fun saveWithValidation(
    fileManager: ProfileFileManager,
    uuid: String,
    relativePath: String,
    newContent: String,
    userAgent: String,
): String? = withContext(Dispatchers.IO) {
    if (!isYamlPath(relativePath)) {
        fileManager.writeImportedFile(uuid, relativePath, newContent)
        return@withContext null
    }
    val original = fileManager.readImportedFile(uuid, relativePath)
    fileManager.writeImportedFile(uuid, relativePath, newContent)
    val workDir = fileManager.getImportedDir(uuid)
    val err = runCatching {
        StellibertyCoreBridge.fetchAndValid(
            workDir = workDir,
            url = "",
            force = false,
            httpProxy = null,
            userAgent = userAgent,
            ageSecretKey = "",
            onProgress = {},
        )
    }.exceptionOrNull()?.message
    if (err != null && original != null) {
        fileManager.writeImportedFile(uuid, relativePath, original)
    }
    err
}
