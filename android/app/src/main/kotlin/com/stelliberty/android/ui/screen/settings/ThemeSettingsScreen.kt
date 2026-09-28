package com.stelliberty.android.ui.screen.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.maxLength
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.stelliberty.android.R
import com.stelliberty.android.platform.PlatformStorage
import com.stelliberty.android.platform.StorageKeys
import com.stelliberty.android.ui.component.AdaptiveTopAppBar
import com.stelliberty.android.ui.component.CardItem
import com.stelliberty.android.ui.component.blur.BlurredBar
import com.stelliberty.android.ui.component.blur.rememberBlurBackdrop
import com.stelliberty.android.ui.component.groupedCardItems
import com.stelliberty.android.ui.icon.AppIcons
import com.stelliberty.android.ui.theme.BottomBarMode
import com.stelliberty.android.ui.theme.FloatingBottomBarStyle
import com.stelliberty.android.ui.theme.MaxDensityScale
import com.stelliberty.android.ui.theme.MinDensityScale
import com.stelliberty.android.ui.theme.ThemeAccentColor
import com.stelliberty.android.ui.theme.ThemeColorMode
import com.stelliberty.android.ui.theme.ThemeConfig
import com.stelliberty.android.ui.theme.ThemePaletteStyles
import com.stelliberty.android.ui.theme.TopBarBlurStyle
import com.stelliberty.android.ui.theme.label
import com.stelliberty.android.ui.theme.normalizeDensityScale
import com.stelliberty.android.ui.theme.summary
import com.stelliberty.android.ui.theme.writeThemeConfig
import com.stelliberty.android.ui.util.horizontalCutoutPadding
import kotlin.math.roundToInt
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Slider
import top.yukonga.miuix.kmp.basic.SliderDefaults
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.blur.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import top.yukonga.miuix.kmp.window.WindowDialog

@Composable
fun ThemeSettingsScreen(
    storage: PlatformStorage,
    themeConfig: ThemeConfig,
    onThemeConfigChange: (ThemeConfig) -> Unit,
    onPredictiveBackChange: ((Boolean) -> Unit)? = null,
    swipeDismissEnabled: Boolean = true,
    onSwipeDismissChange: ((Boolean) -> Unit)? = null,
    onBack: () -> Unit = {},
) {
    val scrollBehavior = MiuixScrollBehavior()
    var isPredictiveBackEnabled by remember {
        mutableStateOf(storage.getString(StorageKeys.PREDICTIVE_BACK, "false") == "true")
    }
    var densityScaleDraft by remember { mutableFloatStateOf((themeConfig.densityScale * 100f).roundToInt().toFloat()) }
    var showDensityScaleDialog by remember { mutableStateOf(false) }
    val densityScaleTextState = rememberTextFieldState()

    LaunchedEffect(themeConfig.densityScale) {
        densityScaleDraft = (themeConfig.densityScale * 100f).roundToInt().toFloat()
    }

    fun updateTheme(next: ThemeConfig) {
        writeThemeConfig(storage, next)
        onThemeConfigChange(next)
    }

    fun updateDensityScale(percent: Float) {
        val nextPercent = percent.roundToInt().coerceIn(
            (MinDensityScale * 100f).roundToInt(),
            (MaxDensityScale * 100f).roundToInt(),
        )
        val nextScale = normalizeDensityScale(nextPercent / 100f)
        densityScaleDraft = nextScale * 100f
        updateTheme(themeConfig.copy(densityScale = nextScale))
    }

    fun openDensityScaleDialog() {
        densityScaleTextState.setTextAndPlaceCursorAtEnd(densityScaleDraft.roundToInt().toString())
        showDensityScaleDialog = true
    }

    val colorModes = ThemeColorMode.entries
    val themeItems = colorModes.map { mode -> mode.label() }
    val paletteStyles = ThemePaletteStyles
    val paletteItems = paletteStyles.map { style -> style.label() }
    val selectedPaletteIndex = paletteStyles.indexOf(themeConfig.paletteStyle).coerceAtLeast(0)
    val accentOptions = ThemeAccentColor.entries.toList()
    val accentItems = accentOptions.map { accent -> accent.label() }
    val selectedAccentIndex = accentOptions.indexOf(themeConfig.accentColor).coerceAtLeast(0)
    val floatingBottomBarStyles = FloatingBottomBarStyle.entries.toList()
    val floatingBottomBarStyleItems = floatingBottomBarStyles.map { style -> style.label() }
    val selectedFloatingBottomBarStyleIndex =
        floatingBottomBarStyles.indexOf(themeConfig.floatingBottomBarStyle).coerceAtLeast(0)
    val bottomBarModes = BottomBarMode.entries.toList()
    val bottomBarModeItems = bottomBarModes.map { mode -> mode.label() }
    val selectedBottomBarModeIndex = bottomBarModes.indexOf(themeConfig.bottomBarMode).coerceAtLeast(0)
    val topBarBlurStyles = TopBarBlurStyle.entries.toList()
    val topBarBlurStyleItems = topBarBlurStyles.map { style -> style.label() }
    val topBarBlurStyleSummaries = topBarBlurStyles.map { style -> style.summary() }
    val selectedTopBarBlurStyleIndex = topBarBlurStyles.indexOf(themeConfig.topBarBlurStyle).coerceAtLeast(0)
    val isBlurSupported = isRuntimeShaderSupported()

    val backdrop = rememberBlurBackdrop()
    val blurActive = backdrop != null
    val barColor = if (blurActive) Color.Transparent else MiuixTheme.colorScheme.surface

    Scaffold(
        topBar = {
            BlurredBar(backdrop = backdrop, blurActive = blurActive) {
                AdaptiveTopAppBar(
                    title = stringResource(R.string.settings_theme_title),
                    color = barColor,
                    scrollBehavior = scrollBehavior,
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = AppIcons.Back,
                                contentDescription = stringResource(R.string.common_back),
                                tint = MiuixTheme.colorScheme.onSurface,
                            )
                        }
                    },
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
            item { SmallTitle(text = stringResource(R.string.settings_theme_group_color)) }
            groupedCardItems(
                keyPrefix = "theme_color",
                items = listOf(
                    CardItem("mode") {
                        OverlayDropdownPreference(
                            title = stringResource(R.string.settings_theme_mode),
                            items = themeItems,
                            selectedIndex = themeConfig.colorMode.ordinal,
                            onSelectedIndexChange = { index ->
                                updateTheme(themeConfig.copy(colorMode = colorModes[index]))
                            },
                        )
                    },
                    CardItem("monet") {
                        SwitchPreference(
                            title = stringResource(R.string.settings_theme_monet),
                            summary = stringResource(R.string.settings_theme_monet_summary),
                            checked = themeConfig.useMonet,
                            onCheckedChange = { checked ->
                                updateTheme(themeConfig.copy(useMonet = checked))
                            },
                        )
                        AnimatedVisibility(
                            visible = themeConfig.useMonet,
                            enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
                            exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut(),
                        ) {
                            Column {
                                OverlayDropdownPreference(
                                    title = stringResource(R.string.settings_theme_palette_style),
                                    items = paletteItems,
                                    selectedIndex = selectedPaletteIndex,
                                    onSelectedIndexChange = { index ->
                                        updateTheme(themeConfig.copy(paletteStyle = paletteStyles[index]))
                                    },
                                )
                                OverlayDropdownPreference(
                                    title = stringResource(R.string.settings_theme_accent_title),
                                    items = accentItems,
                                    selectedIndex = selectedAccentIndex,
                                    onSelectedIndexChange = { index ->
                                        updateTheme(themeConfig.copy(accentColor = accentOptions[index]))
                                    },
                                )
                                SwitchPreference(
                                    title = stringResource(R.string.settings_theme_pure_black),
                                    summary = stringResource(R.string.settings_theme_pure_black_summary),
                                    checked = themeConfig.pureBlack,
                                    onCheckedChange = { checked ->
                                        updateTheme(themeConfig.copy(pureBlack = checked))
                                    },
                                )
                            }
                        }
                    },
                ),
            )

            item { SmallTitle(text = stringResource(R.string.settings_theme_group_interface)) }
            groupedCardItems(
                keyPrefix = "theme_interface",
                items = buildList {
                    add(CardItem("blur") {
                        SwitchPreference(
                            title = stringResource(R.string.settings_theme_blur),
                            summary = stringResource(R.string.settings_theme_blur_summary),
                            checked = themeConfig.blurEnabled && isBlurSupported,
                            onCheckedChange = { checked ->
                                updateTheme(themeConfig.copy(blurEnabled = checked))
                            },
                            enabled = isBlurSupported,
                        )
                        AnimatedVisibility(
                            visible = themeConfig.blurEnabled && isBlurSupported,
                            enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
                            exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut(),
                        ) {
                            OverlayDropdownPreference(
                                title = stringResource(R.string.settings_theme_blur_style),
                                summary = topBarBlurStyleSummaries.getOrElse(selectedTopBarBlurStyleIndex) {
                                    topBarBlurStyleSummaries.first()
                                },
                                items = topBarBlurStyleItems,
                                selectedIndex = selectedTopBarBlurStyleIndex,
                                onSelectedIndexChange = { index ->
                                    updateTheme(themeConfig.copy(topBarBlurStyle = topBarBlurStyles[index]))
                                },
                            )
                        }
                    })
                    if (onSwipeDismissChange != null) {
                        add(CardItem("swipeDismiss") {
                            SwitchPreference(
                                title = stringResource(R.string.settings_swipe_dismiss),
                                summary = stringResource(R.string.settings_swipe_dismiss_summary),
                                checked = swipeDismissEnabled,
                                onCheckedChange = { checked -> onSwipeDismissChange(checked) },
                            )
                        })
                    }
                    if (onPredictiveBackChange != null) {
                        add(CardItem("predictiveBack") {
                            SwitchPreference(
                                title = stringResource(R.string.settings_predictive_back),
                                summary = stringResource(R.string.settings_predictive_back_summary),
                                checked = isPredictiveBackEnabled,
                                onCheckedChange = { checked ->
                                    storage.putString(StorageKeys.PREDICTIVE_BACK, checked.toString())
                                    isPredictiveBackEnabled = checked
                                    onPredictiveBackChange(checked)
                                },
                            )
                        })
                    }
                    add(CardItem("densityScale") {
                        ArrowPreference(
                            title = stringResource(R.string.settings_theme_density_scale),
                            summary = stringResource(R.string.settings_theme_density_scale_summary),
                            endActions = {
                                Text(
                                    text = formatDensityScalePercent(densityScaleDraft),
                                    fontSize = MiuixTheme.textStyles.body2.fontSize,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                                )
                            },
                            bottomAction = {
                                Slider(
                                    value = densityScaleDraft.coerceIn(
                                        MinDensityScale * 100f,
                                        MaxDensityScale * 100f,
                                    ),
                                    onValueChange = { value ->
                                        densityScaleDraft = value
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    valueRange = (MinDensityScale * 100f)..(MaxDensityScale * 100f),
                                    onValueChangeFinished = {
                                        updateDensityScale(densityScaleDraft)
                                    },
                                    showKeyPoints = true,
                                    keyPoints = listOf(80f, 90f, 100f, 110f),
                                    magnetThreshold = 0.01f,
                                    hapticEffect = SliderDefaults.SliderHapticEffect.Step,
                                )
                            },
                            onClick = ::openDensityScaleDialog,
                            holdDownState = showDensityScaleDialog,
                        )
                    })
                },
            )

            item { SmallTitle(text = stringResource(R.string.settings_theme_group_navigation)) }
            groupedCardItems(
                keyPrefix = "theme_navigation",
                items = listOf(
                    CardItem("floating") {
                        SwitchPreference(
                            title = stringResource(R.string.settings_theme_floating_bottom_bar),
                            summary = stringResource(R.string.settings_theme_floating_bottom_bar_summary),
                            checked = themeConfig.floatingBottomBar,
                            onCheckedChange = { checked ->
                                updateTheme(themeConfig.copy(floatingBottomBar = checked))
                            },
                        )
                        AnimatedVisibility(
                            visible = themeConfig.floatingBottomBar,
                            enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
                            exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut(),
                        ) {
                            OverlayDropdownPreference(
                                title = stringResource(R.string.settings_theme_floating_bottom_bar_style),
                                items = floatingBottomBarStyleItems,
                                selectedIndex = selectedFloatingBottomBarStyleIndex,
                                onSelectedIndexChange = { index ->
                                    updateTheme(
                                        themeConfig.copy(
                                            floatingBottomBarStyle = floatingBottomBarStyles[index],
                                        ),
                                    )
                                },
                            )
                        }
                    },
                    CardItem("mode") {
                        OverlayDropdownPreference(
                            title = stringResource(R.string.settings_theme_bottom_bar_mode),
                            items = bottomBarModeItems,
                            selectedIndex = selectedBottomBarModeIndex,
                            onSelectedIndexChange = { index ->
                                updateTheme(
                                    themeConfig.copy(
                                        bottomBarMode = bottomBarModes[index],
                                    ),
                                )
                            },
                        )
                    },
                ),
            )
            item {
                Spacer(
                    Modifier
                        .height(24.dp)
                        .navigationBarsPadding()
                )
            }

        }
    }

    DensityScaleDialog(
        show = showDensityScaleDialog,
        textState = densityScaleTextState,
        currentPercent = { densityScaleDraft },
        onDismiss = { showDensityScaleDialog = false },
        onConfirm = { percent ->
            updateDensityScale(percent)
            showDensityScaleDialog = false
        },
    )
}

@Composable
private fun DensityScaleDialog(
    show: Boolean,
    textState: TextFieldState,
    currentPercent: () -> Float,
    onDismiss: () -> Unit,
    onConfirm: (Float) -> Unit,
) {
    WindowDialog(
        show = show,
        title = stringResource(R.string.settings_theme_density_scale),
        summary = stringResource(R.string.settings_theme_density_scale_summary),
        onDismissRequest = onDismiss,
    ) {
        TextField(
            state = textState,
            modifier = Modifier.fillMaxWidth(),
            inputTransformation = DigitsOnlyTransformation.maxLength(3),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            trailingIcon = {
                Text(
                    text = "%",
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                )
            },
        )
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TextButton(
                text = stringResource(R.string.common_cancel),
                modifier = Modifier.weight(1f),
                onClick = onDismiss,
            )
            TextButton(
                text = stringResource(R.string.common_confirm),
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.textButtonColorsPrimary(),
                onClick = {
                    val percent = textState.text.toString().toIntOrNull()?.toFloat() ?: currentPercent()
                    onConfirm(percent)
                },
            )
        }
    }
}

private val DigitsOnlyTransformation = InputTransformation {
    if (!asCharSequence().all { it.isDigit() }) revertAllChanges()
}

private fun formatDensityScalePercent(value: Float): String = "${value.roundToInt()}%"
