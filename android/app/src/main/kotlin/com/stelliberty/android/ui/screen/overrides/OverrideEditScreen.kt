package com.stelliberty.android.ui.screen.overrides

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stelliberty.android.R
import com.stelliberty.android.domain.model.OverrideFormat
import com.stelliberty.android.domain.model.SubscriptionUpdateProxyMode
import com.stelliberty.android.platform.FilePickResult
import com.stelliberty.android.ui.component.AdaptiveTopAppBar
import com.stelliberty.android.ui.component.CardItem
import com.stelliberty.android.ui.component.blur.BlurredBar
import com.stelliberty.android.ui.component.blur.rememberBlurBackdrop
import com.stelliberty.android.ui.component.groupedCardItems
import com.stelliberty.android.ui.theme.StatusColors
import com.stelliberty.android.ui.util.TestTags
import com.stelliberty.android.ui.util.horizontalCutoutPadding
import com.stelliberty.android.viewmodel.OverrideProfileViewModel
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

private enum class AddMethod { Remote, Blank, Local }

// overrideId 为 null 时是添加页，否则编辑已有覆写的名称、地址、格式与更新方式。
@Composable
fun OverrideEditScreen(
    overrideId: String?,
    viewModel: OverrideProfileViewModel,
    onBack: () -> Unit = {},
    onSaved: () -> Unit = {},
    onPickFile: ((FilePickResult?) -> Unit) -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val profile = overrideId?.let { id -> uiState.profiles.find { it.id == id } }
    if (overrideId != null && profile == null) {
        LaunchedEffect(Unit) { onBack() }
        return
    }

    ClearErrorOnExit(viewModel)

    var method by rememberSaveable { mutableStateOf(AddMethod.Remote) }
    var name by rememberSaveable(overrideId) { mutableStateOf(profile?.name.orEmpty()) }
    var url by rememberSaveable(overrideId) {
        mutableStateOf(profile?.takeIf { it.isRemote }?.sourceLocation.orEmpty())
    }
    var format by rememberSaveable(overrideId) { mutableStateOf(profile?.format ?: OverrideFormat.Yaml) }
    val savedViaProxy = profile?.let { it.updateProxyMode != SubscriptionUpdateProxyMode.Direct } ?: true
    var updateViaProxy by rememberSaveable(overrideId) { mutableStateOf(savedViaProxy) }
    var picked by remember { mutableStateOf<FilePickResult?>(null) }
    var localError by remember { mutableStateOf("") }

    val isRemote = profile?.isRemote ?: (method == AddMethod.Remote)
    val hasChanges = profile == null ||
            name != profile.name ||
            format != profile.format ||
            (profile.isRemote && (url != profile.sourceLocation || updateViaProxy != savedViaProxy))
    val fileRequiredText = stringResource(R.string.override_error_file_required)

    val scrollBehavior = MiuixScrollBehavior()
    val backdrop = rememberBlurBackdrop()
    val blurActive = backdrop != null
    val barColor = if (blurActive) Color.Transparent else MiuixTheme.colorScheme.surface

    Scaffold(
        topBar = {
            BlurredBar(backdrop = backdrop, blurActive = blurActive) {
                AdaptiveTopAppBar(
                    title = stringResource(if (profile == null) R.string.override_add else R.string.override_edit),
                    color = barColor,
                    scrollBehavior = scrollBehavior,
                    navigationIcon = { BackButton(onBack) },
                )
            }
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .horizontalCutoutPadding()
                .then(if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier)
                .scrollEndHaptic()
                .overScrollVertical()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = PaddingValues(top = innerPadding.calculateTopPadding()),
        ) {
            if (profile == null) {
                item(key = "top_padding") { Spacer(Modifier.height(12.dp)) }
                groupedCardItems(
                    keyPrefix = "override_method",
                    items = listOf(
                        CardItem("overrideAddMethod") {
                            val methods = AddMethod.entries
                            OverlayDropdownPreference(
                                title = stringResource(R.string.override_add_method),
                                summary = stringResource(method.summaryRes),
                                items = methods.map { stringResource(it.labelRes) },
                                selectedIndex = methods.indexOf(method),
                                onSelectedIndexChange = {
                                    method = methods[it]
                                    localError = ""
                                },
                            )
                        },
                    ),
                )
            }

            item(key = "fields") {
                SmallTitle(text = stringResource(R.string.override_name))
                TextField(
                    value = name,
                    onValueChange = { name = it },
                    label = stringResource(R.string.override_name_placeholder),
                    useLabelAsPlaceholder = true,
                    modifier = Modifier
                        .testTag(TestTags.Overrides.NAME)
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .padding(bottom = 6.dp),
                )
                if (isRemote) {
                    SmallTitle(text = stringResource(R.string.override_url))
                    TextField(
                        value = url,
                        onValueChange = { url = it },
                        label = stringResource(R.string.override_url_placeholder),
                        useLabelAsPlaceholder = true,
                        modifier = Modifier
                            .testTag(TestTags.Overrides.URL)
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp)
                            .padding(bottom = 6.dp),
                    )
                }
            }

            if (profile == null && method == AddMethod.Local) {
                groupedCardItems(
                    keyPrefix = "override_file",
                    items = listOf(
                        CardItem("overrideLocalFile") {
                            ArrowPreference(
                                title = stringResource(R.string.override_local_file),
                                summary = picked?.fileName ?: stringResource(R.string.override_method_local_summary),
                                onClick = {
                                    onPickFile { result ->
                                        if (result == null) return@onPickFile
                                        picked = result
                                        localError = ""
                                        if (name.isBlank()) name = result.fileName.substringBeforeLast('.')
                                        format = if (result.fileName.endsWith(".js", ignoreCase = true)) {
                                            OverrideFormat.JavaScript
                                        } else OverrideFormat.Yaml
                                    }
                                },
                            )
                        },
                    ),
                )
            }

            groupedCardItems(
                keyPrefix = "override_options",
                items = buildList {
                    add(CardItem("overrideFormat") {
                        val formats = OverrideFormat.entries
                        OverlayDropdownPreference(
                            title = stringResource(R.string.override_format),
                            items = formats.map { it.label },
                            selectedIndex = formats.indexOf(format),
                            onSelectedIndexChange = { format = formats[it] },
                        )
                    })
                    if (isRemote) {
                        add(CardItem("overrideUpdateViaProxy") {
                            SwitchPreference(
                                title = stringResource(R.string.subscription_update_via_proxy),
                                summary = stringResource(R.string.override_update_via_proxy_summary),
                                checked = updateViaProxy,
                                onCheckedChange = { updateViaProxy = it },
                            )
                        })
                    }
                },
            )

            val error = localError.ifEmpty { uiState.error }
            if (error.isNotEmpty()) {
                item(key = "error") {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp)
                            .padding(bottom = 6.dp),
                        insideMargin = PaddingValues(16.dp),
                    ) {
                        Text(text = error, color = StatusColors.danger)
                    }
                }
            }

            item(key = "save") {
                TextButton(
                    text = stringResource(if (uiState.isLoading) R.string.common_processing else R.string.common_save),
                    onClick = {
                        // 开关没动就原样写回，保留 PC 上设的 SystemProxy。
                        val proxyMode = when {
                            profile != null && updateViaProxy == savedViaProxy -> profile.updateProxyMode
                            updateViaProxy -> SubscriptionUpdateProxyMode.Core
                            else -> SubscriptionUpdateProxyMode.Direct
                        }
                        val file = picked
                        when {
                            profile != null -> viewModel.edit(profile, name, url, format, proxyMode, onSaved)
                            method == AddMethod.Remote -> viewModel.addRemote(name, url, format, proxyMode, onSaved)
                            method == AddMethod.Blank -> viewModel.addBlank(name, format, onSaved)
                            file == null -> localError = fileRequiredText
                            else -> viewModel.addLocal(name, file.fileName, format, file.content, onSaved)
                        }
                    },
                    enabled = hasChanges && !uiState.isLoading && name.isNotBlank(),
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .padding(top = 6.dp, bottom = 12.dp)
                        .testTag(TestTags.Overrides.SAVE),
                )
            }

            item(key = "bottom_spacer") {
                Spacer(Modifier.height(24.dp).navigationBarsPadding())
            }
        }
    }
}

private val AddMethod.labelRes: Int
    get() = when (this) {
        AddMethod.Remote -> R.string.override_source_remote
        AddMethod.Blank -> R.string.override_method_blank
        AddMethod.Local -> R.string.override_method_local
    }

private val AddMethod.summaryRes: Int
    get() = when (this) {
        AddMethod.Remote -> R.string.override_method_remote_summary
        AddMethod.Blank -> R.string.override_method_blank_summary
        AddMethod.Local -> R.string.override_method_local_summary
    }

internal val OverrideFormat.label: String
    get() = when (this) {
        OverrideFormat.Yaml -> "YAML"
        OverrideFormat.JavaScript -> "JavaScript"
    }
