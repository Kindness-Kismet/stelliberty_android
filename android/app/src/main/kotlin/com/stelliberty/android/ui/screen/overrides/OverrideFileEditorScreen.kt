package com.stelliberty.android.ui.screen.overrides

import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stelliberty.android.R
import com.stelliberty.android.domain.model.OverrideFormat
import com.stelliberty.android.platform.showToast
import com.stelliberty.android.ui.component.blur.BlurredBar
import com.stelliberty.android.ui.component.blur.rememberBlurBackdrop
import com.stelliberty.android.ui.icon.AppIcons
import com.stelliberty.android.ui.theme.LocalAppDarkMode
import com.stelliberty.android.ui.theme.StatusColors
import com.stelliberty.android.ui.util.TestTags
import com.stelliberty.android.ui.util.horizontalCutoutPadding
import com.stelliberty.android.viewmodel.OverrideProfileViewModel
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
fun OverrideFileEditorScreen(
    overrideId: String,
    viewModel: OverrideProfileViewModel,
    onBack: () -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val profile = uiState.profiles.find { it.id == overrideId }
    if (profile == null) {
        LaunchedEffect(Unit) { onBack() }
        return
    }

    val controller = rememberCodeEditorController()
    val context = LocalContext.current
    ClearErrorOnExit(viewModel)

    LaunchedEffect(overrideId) {
        viewModel.clearError()
        controller.setDocument(viewModel.readContent(overrideId))
    }

    val backdrop = rememberBlurBackdrop()
    val blurActive = backdrop != null
    val barColor = if (blurActive) Color.Transparent else MiuixTheme.colorScheme.surface

    Scaffold(
        topBar = {
            BlurredBar(backdrop = backdrop, blurActive = blurActive) {
                SmallTopAppBar(
                    title = profile.name,
                    color = barColor,
                    navigationIcon = { BackButton(onBack) },
                    actions = {
                        val canSave = controller.isModified && !uiState.isLoading
                        IconButton(
                            enabled = canSave,
                            modifier = Modifier.testTag(TestTags.Overrides.SAVE),
                            onClick = {
                                val version = controller.documentVersion
                                viewModel.saveContent(overrideId, controller.getText(controller.lineEnding)) {
                                    controller.markSaved(version)
                                    showToast(context.getString(R.string.file_manager_saved))
                                }
                            },
                        ) {
                            if (uiState.isLoading) {
                                CircularProgressIndicator(size = 20.dp, strokeWidth = 2.dp)
                            } else {
                                Icon(
                                    imageVector = AppIcons.Check,
                                    contentDescription = stringResource(R.string.common_save),
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
            if (uiState.error.isNotEmpty()) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .padding(bottom = 12.dp),
                ) {
                    Text(
                        text = uiState.error,
                        color = StatusColors.danger,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    )
                }
            }

            // scripta 没有 JavaScript 语法，按纯文本编辑。
            CompositionLocalProvider(LocalOverscrollFactory provides null) {
                CodeEditor(
                    controller = controller,
                    language = if (profile.format == OverrideFormat.Yaml) EditorLanguage.Yaml else EditorLanguage.PlainText,
                    colors = if (LocalAppDarkMode.current) EditorColors.Default else EditorColors.Light,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                )
            }
        }
    }
}
