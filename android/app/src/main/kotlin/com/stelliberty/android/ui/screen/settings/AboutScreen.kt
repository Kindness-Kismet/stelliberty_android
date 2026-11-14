package com.stelliberty.android.ui.screen.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import com.stelliberty.android.BuildConfig
import com.stelliberty.android.R
import com.stelliberty.android.ui.component.BackButton
import com.stelliberty.android.ui.component.PreferenceLayout
import com.stelliberty.android.ui.component.blur.BlurredBar
import com.stelliberty.android.ui.component.blur.ColorBlendToken
import com.stelliberty.android.ui.component.blur.rememberBlurBackdrop
import com.stelliberty.android.ui.component.blur.rememberBlurEnabled
import com.stelliberty.android.ui.component.effect.BgEffectBackground
import com.stelliberty.android.ui.icon.AppIcons
import com.stelliberty.android.ui.theme.LocalAppDarkMode
import com.stelliberty.android.ui.util.TestTags
import com.stelliberty.android.ui.util.horizontalCutoutPadding
import com.stelliberty.android.viewmodel.AppUpdateViewModel
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.blur.BlurColors
import top.yukonga.miuix.kmp.blur.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

internal const val PROJECT_REPOSITORY_URL = "https://github.com/Kindness-Kismet/stelliberty_android"

@Composable
fun AboutScreen(
    appUpdateViewModel: AppUpdateViewModel? = null,
    onBack: () -> Unit = {},
    onOpenUrl: (String) -> Unit = {},
    onOpenSource: () -> Unit = {},
) {
    val scrollBehavior = MiuixScrollBehavior()
    val lazyListState = rememberLazyListState()
    val scrollProgressState = remember {
        derivedStateOf {
            if (lazyListState.firstVisibleItemIndex > 0) 1f
            else {
                val spacer = lazyListState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == "logoSpacer" }
                if (spacer != null && spacer.size > 0) {
                    (lazyListState.firstVisibleItemScrollOffset.toFloat() / spacer.size).coerceIn(0f, 1f)
                } else 0f
            }
        }
    }
    val heroCollapsed by remember { derivedStateOf { scrollProgressState.value == 1f } }
    val backdrop = rememberBlurBackdrop()
    val blurActive = backdrop != null && heroCollapsed
    val barColor = if (heroCollapsed && !blurActive) colorScheme.surface else Color.Transparent

    Scaffold(
        topBar = {
            BlurredBar(backdrop = backdrop, blurActive = blurActive) {
                SmallTopAppBar(
                    modifier = Modifier.horizontalCutoutPadding().testTag(TestTags.About.TOP_BAR),
                    title = stringResource(R.string.about_title),
                    scrollBehavior = scrollBehavior,
                    color = barColor,
                    titleColor = if (heroCollapsed) colorScheme.onSurface else Color.Transparent,
                    defaultWindowInsetsPadding = false,
                    navigationIcon = { BackButton(onBack) },
                )
            }
        },
        contentWindowInsets = WindowInsets.systemBars.add(WindowInsets.displayCutout).only(WindowInsetsSides.Horizontal),
    ) { innerPadding ->
        Box(modifier = if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier) {
            AboutContent(
                appUpdateViewModel = appUpdateViewModel,
                innerPadding = innerPadding,
                scrollBehavior = scrollBehavior,
                lazyListState = lazyListState,
                scrollProgress = { scrollProgressState.value },
                onOpenSource = onOpenSource,
                onOpenRepository = { onOpenUrl(PROJECT_REPOSITORY_URL) },
            )
        }
    }
}

@Composable
private fun AboutContent(
    appUpdateViewModel: AppUpdateViewModel?,
    innerPadding: PaddingValues,
    scrollBehavior: ScrollBehavior,
    lazyListState: LazyListState,
    scrollProgress: () -> Float,
    onOpenSource: () -> Unit,
    onOpenRepository: () -> Unit,
) {
    val layoutDirection = LocalLayoutDirection.current
    val density = LocalDensity.current
    val isDark = LocalAppDarkMode.current
    val blurEnabled by rememberBlurEnabled()
    val effectBackground = blurEnabled && isRuntimeShaderSupported()
    val backdrop = if (effectBackground) rememberLayerBackdrop() else null
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val cardBlendColors = remember(isDark) {
        if (isDark) ColorBlendToken.Overlay_Thin_Light else ColorBlendToken.Pured_Regular_Light
    }
    val logoBlend = remember(isDark) {
        if (isDark) ColorBlendToken.LogoDark else ColorBlendToken.LogoLight
    }
    var logoHeightDp by remember { mutableStateOf(300.dp) }
    val quickActionsVisible by remember { derivedStateOf { scrollProgress() < 0.2f } }
    val versionProgress = { ((scrollProgress() - 0.05f) / 0.15f).coerceIn(0f, 1f) }
    val projectNameProgress = { ((scrollProgress() - 0.20f) / 0.15f).coerceIn(0f, 1f) }
    val iconProgress = { ((scrollProgress() - 0.35f) / 0.15f).coerceIn(0f, 1f) }
    val scrollPadding = PaddingValues(
        top = innerPadding.calculateTopPadding(),
        start = innerPadding.calculateStartPadding(layoutDirection),
        end = innerPadding.calculateEndPadding(layoutDirection),
    )
    val logoPadding = PaddingValues(
        top = innerPadding.calculateTopPadding() + 40.dp,
        start = innerPadding.calculateStartPadding(layoutDirection),
        end = innerPadding.calculateEndPadding(layoutDirection),
    )
    val actionsTopPadding = logoHeightDp + 52.dp + logoPadding.calculateTopPadding() -
        scrollPadding.calculateTopPadding() + 16.dp
    BgEffectBackground(
        dynamicBackground = effectBackground && lifecycleState.isAtLeast(Lifecycle.State.RESUMED),
        modifier = Modifier.fillMaxSize(),
        bgModifier = if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier,
        isFullSize = true,
        effectBackground = effectBackground,
        alpha = { 1f - scrollProgress() },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    top = logoPadding.calculateTopPadding() + 52.dp,
                    start = logoPadding.calculateStartPadding(layoutDirection),
                    end = logoPadding.calculateEndPadding(layoutDirection),
                )
                .onSizeChanged { size -> with(density) { logoHeightDp = size.height.toDp() } },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(100.dp).clipToBounds().testTag(TestTags.About.ICON).graphicsLayer {
                    val progress = iconProgress()
                    alpha = 1 - progress
                    scaleX = 1 - progress * 0.05f
                    scaleY = 1 - progress * 0.05f
                },
            ) {
                Image(
                    modifier = Modifier.requiredSize(200.dp).then(
                        if (backdrop != null) Modifier.textureBlur(
                            backdrop = backdrop,
                            shape = RoundedCornerShape(0.dp),
                            blurRadius = 150f,
                            colors = BlurColors(blendColors = logoBlend),
                            contentBlendMode = BlendMode.DstIn,
                        ) else Modifier,
                    ),
                    painter = painterResource(R.drawable.ic_launcher_foreground),
                    colorFilter = ColorFilter.tint(colorScheme.onBackground),
                    contentDescription = null,
                )
            }
            Text(
                modifier = Modifier.padding(top = 12.dp, bottom = 5.dp).testTag(TestTags.About.NAME).graphicsLayer {
                    val progress = projectNameProgress()
                    alpha = 1 - progress
                    scaleX = 1 - progress * 0.05f
                    scaleY = 1 - progress * 0.05f
                }.then(
                    if (backdrop != null) Modifier.textureBlur(
                        backdrop = backdrop,
                        shape = RoundedCornerShape(16.dp),
                        blurRadius = 150f,
                        colors = BlurColors(blendColors = logoBlend),
                        contentBlendMode = BlendMode.DstIn,
                    ) else Modifier,
                ),
                text = stringResource(R.string.app_name),
                color = colorScheme.onBackground,
                fontWeight = FontWeight.Bold,
                fontSize = 35.sp,
            )
            Text(
                modifier = Modifier.fillMaxWidth().testTag(TestTags.About.VERSION).graphicsLayer {
                    val progress = versionProgress()
                    alpha = 1 - progress
                    scaleX = 1 - progress * 0.05f
                    scaleY = 1 - progress * 0.05f
                },
                color = colorScheme.onSurfaceVariantSummary,
                text = "v${BuildConfig.VERSION_NAME}",
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
            )
        }
        LazyColumn(
            state = lazyListState,
            modifier = Modifier.fillMaxSize().scrollEndHaptic().overScrollVertical().nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = scrollPadding,
        ) {
            item(key = "logoSpacer") {
                Box(
                    Modifier.fillMaxWidth().height(actionsTopPadding + PreferenceLayout.ActionButtonSize + 70.dp),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    // 快捷入口位于滚动层，避免静态主体下方的列表截获点击。
                    Row(
                        modifier = Modifier.padding(top = actionsTopPadding).graphicsLayer { alpha = 1f - versionProgress() },
                        horizontalArrangement = Arrangement.spacedBy(PreferenceLayout.ItemSpacing),
                    ) {
                        IconButton(
                            onClick = onOpenSource,
                            enabled = quickActionsVisible,
                            modifier = Modifier.size(PreferenceLayout.ActionButtonSize).testTag(TestTags.About.OPEN_SOURCE),
                        ) {
                            Icon(
                                imageVector = AppIcons.LibraryBooks,
                                contentDescription = stringResource(R.string.about_open_source),
                                modifier = Modifier.size(PreferenceLayout.IconSize),
                                tint = colorScheme.onBackground,
                            )
                        }
                        IconButton(
                            onClick = onOpenRepository,
                            enabled = quickActionsVisible,
                            modifier = Modifier.size(PreferenceLayout.ActionButtonSize).testTag(TestTags.About.REPOSITORY),
                        ) {
                            Icon(
                                imageVector = AppIcons.GitHub,
                                contentDescription = stringResource(R.string.about_repository),
                                modifier = Modifier.size(PreferenceLayout.IconSize),
                                tint = colorScheme.onBackground,
                            )
                        }
                    }
                }
            }
            item(key = "about") {
                Box {
                    Spacer(Modifier.fillParentMaxHeight())
                    Column(Modifier.padding(bottom = 12.dp)) {
                        appUpdateViewModel?.let { viewModel ->
                            Card(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(bottom = 12.dp).then(
                                    if (backdrop != null) Modifier.textureBlur(
                                        backdrop = backdrop,
                                        shape = RoundedCornerShape(16.dp),
                                        blurRadius = 60f,
                                        colors = BlurColors(blendColors = cardBlendColors),
                                    ) else Modifier,
                                ).testTag(TestTags.About.UPDATE_CARD),
                                colors = CardDefaults.defaultColors(
                                    if (backdrop != null) Color.Transparent else colorScheme.surfaceContainer,
                                    Color.Transparent,
                                ),
                            ) {
                                AboutUpdatePreferences(viewModel)
                            }
                        }
                        Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.systemBars))
                    }
                }
            }
        }
    }
}
