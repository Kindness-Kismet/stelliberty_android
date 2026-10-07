package com.stelliberty.android.ui.screen.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.stelliberty.android.BuildConfig
import com.stelliberty.android.R
import com.stelliberty.android.ui.component.AdaptiveTopAppBar
import com.stelliberty.android.ui.component.CardItem
import com.stelliberty.android.ui.component.blur.BlurredBar
import com.stelliberty.android.ui.component.blur.rememberBlurBackdrop
import com.stelliberty.android.ui.component.groupedCardItems
import com.stelliberty.android.ui.util.WideContentBox
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

private fun LazyListScope.settingsGroup(
    keyPrefix: String,
    @StringRes titleRes: Int,
    outerBottomPadding: Dp = 6.dp,
    build: MutableList<CardItem>.() -> Unit,
) {
    item(key = "$keyPrefix:title") {
        SmallTitle(text = stringResource(titleRes))
    }
    groupedCardItems(keyPrefix = keyPrefix, items = buildList(build), outerBottomPadding = outerBottomPadding)
}

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    bottomPadding: Dp = 0.dp,
    onNavigateThemeSettings: () -> Unit = {},
    onNavigateClashFeatures: () -> Unit = {},
    onNavigateOverrides: () -> Unit = {},
    onNavigateFileManager: () -> Unit = {},
    onNavigateAppBehavior: () -> Unit = {},
    onNavigateAbout: () -> Unit = {},
    onNavigateDataManagement: () -> Unit = {},
    onNavigateAgeKey: () -> Unit = {},
) {
    val scrollBehavior = MiuixScrollBehavior()
    val backdrop = rememberBlurBackdrop()
    val blurActive = backdrop != null
    val barColor = if (blurActive) Color.Transparent else MiuixTheme.colorScheme.surface

    Scaffold(
        modifier = modifier,
        topBar = {
            BlurredBar(backdrop = backdrop, blurActive = blurActive) {
                AdaptiveTopAppBar(
                    title = stringResource(R.string.settings_title),
                    color = barColor,
                    scrollBehavior = scrollBehavior,
                )
            }
        },
    ) { innerPadding ->
        WideContentBox { sidePadding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier)
                    .scrollEndHaptic()
                    .overScrollVertical()
                    .nestedScroll(scrollBehavior.nestedScrollConnection),
                contentPadding = PaddingValues(
                    top = innerPadding.calculateTopPadding(),
                    start = sidePadding,
                    end = sidePadding,
                    bottom = bottomPadding,
                ),
            ) {
                settingsGroup("settings_personalization", R.string.settings_group_personalization) {
                    add(CardItem("theme") {
                        ArrowPreference(
                            title = stringResource(R.string.settings_theme_title),
                            summary = stringResource(R.string.settings_theme_summary),
                            onClick = onNavigateThemeSettings,
                        )
                    })
                }

                settingsGroup("settings_clash", R.string.settings_group_clash) {
                    add(CardItem("clashFeatures") {
                        ArrowPreference(
                            title = stringResource(R.string.clash_features_title),
                            summary = stringResource(R.string.clash_features_summary),
                            onClick = onNavigateClashFeatures,
                        )
                    })
                    add(CardItem("overrides") {
                        ArrowPreference(
                            title = stringResource(R.string.override_title),
                            summary = stringResource(R.string.settings_overrides_summary),
                            onClick = onNavigateOverrides,
                        )
                    })
                    add(CardItem("fileManager") {
                        ArrowPreference(
                            title = stringResource(R.string.settings_file_manager),
                            summary = stringResource(R.string.settings_file_manager_summary),
                            onClick = onNavigateFileManager,
                        )
                    })
                }

                settingsGroup("settings_application", R.string.settings_group_application) {
                    add(CardItem("appBehavior") {
                        ArrowPreference(
                            title = stringResource(R.string.app_behavior_title),
                            summary = stringResource(R.string.app_behavior_summary),
                            onClick = onNavigateAppBehavior,
                        )
                    })
                    add(CardItem("about") {
                        ArrowPreference(
                            title = stringResource(R.string.settings_about),
                            summary = "Stelliberty v${BuildConfig.VERSION_NAME}",
                            onClick = onNavigateAbout,
                        )
                    })
                }

                settingsGroup("settings_maintenance", R.string.settings_group_maintenance, outerBottomPadding = 12.dp) {
                    add(CardItem("dataManagement") {
                        ArrowPreference(
                            title = stringResource(R.string.data_management_title),
                            summary = stringResource(R.string.data_management_summary),
                            onClick = onNavigateDataManagement,
                        )
                    })
                    add(CardItem("ageKey") {
                        ArrowPreference(
                            title = stringResource(R.string.age_key_title),
                            summary = stringResource(R.string.age_key_summary),
                            onClick = onNavigateAgeKey,
                        )
                    })
                }
            }
        }
    }
}
