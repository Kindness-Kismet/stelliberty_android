package com.stelliberty.android.ui.screen.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.stelliberty.android.R
import com.stelliberty.android.ui.component.CardItem
import com.stelliberty.android.ui.component.PreferenceLayout
import com.stelliberty.android.ui.component.groupedCardItems
import com.stelliberty.android.ui.util.TestTags
import kotlinx.collections.immutable.persistentListOf
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.squircle.squircleClip

@Composable
fun OpenSourceLibrariesScreen(
    onBack: () -> Unit,
    onOpenUrl: (String) -> Unit,
    mihomoVersion: String = "",
) {
    val projects = remember(mihomoVersion) {
        persistentListOf(
            OpenSourceProject("Stelliberty", "Stelliberty", PROJECT_REPOSITORY_URL),
            OpenSourceProject("mihomo", if (mihomoVersion.isEmpty()) "mihomo" else "mihomo ($mihomoVersion)", "https://github.com/MetaCubeX/mihomo"),
            OpenSourceProject("miuix", "miuix", "https://github.com/compose-miuix-ui/miuix"),
            OpenSourceProject("MingCute", "MingCute", "https://github.com/mingcute-design/mingcute-icons"),
            OpenSourceProject("scripta", "scripta", "https://github.com/YuKongA/scripta"),
            OpenSourceProject("AndroidHiddenApiBypass", "AndroidHiddenApiBypass", "https://github.com/LSPosed/AndroidHiddenApiBypass"),
            OpenSourceProject("AndroidX", "AndroidX", "https://github.com/androidx/androidx"),
            OpenSourceProject("Koin", "Koin", "https://github.com/InsertKoinIO/koin"),
            OpenSourceProject("Kotlin", "Kotlin", "https://github.com/JetBrains/kotlin"),
            OpenSourceProject("kotlinx.collections.immutable", "kotlinx.collections.immutable", "https://github.com/Kotlin/kotlinx.collections.immutable"),
            OpenSourceProject("kotlinx.coroutines", "kotlinx.coroutines", "https://github.com/Kotlin/kotlinx.coroutines"),
            OpenSourceProject("kotlinx-datetime", "kotlinx-datetime", "https://github.com/Kotlin/kotlinx-datetime"),
            OpenSourceProject("kotlinx.serialization", "kotlinx.serialization", "https://github.com/Kotlin/kotlinx.serialization"),
            OpenSourceProject("Ktor", "Ktor", "https://github.com/ktorio/ktor"),
            OpenSourceProject("quickie", "quickie", "https://github.com/G00fY2/quickie"),
        )
    }
    Box(Modifier.fillMaxSize().testTag(TestTags.Libraries.SCREEN)) {
        SettingsSubPage(
            title = stringResource(R.string.about_open_source),
            onBack = onBack,
            topSpacing = PreferenceLayout.TopSpacing,
        ) {
            groupedCardItems(
                keyPrefix = "open_source_libraries",
                items = projects.map { project ->
                    CardItem(project.key) {
                        ArrowPreference(
                            title = project.label,
                            modifier = Modifier.squircleClip(16.dp).testTag(TestTags.Libraries.project(project.key)),
                            onClick = { onOpenUrl(project.url) },
                        )
                    }
                },
                outerBottomPadding = 0.dp,
            )
        }
    }
}

@Immutable
private data class OpenSourceProject(val key: String, val label: String, val url: String)
