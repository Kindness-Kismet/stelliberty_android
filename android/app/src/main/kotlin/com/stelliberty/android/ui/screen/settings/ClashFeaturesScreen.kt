package com.stelliberty.android.ui.screen.settings

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stelliberty.android.R
import com.stelliberty.android.ui.component.CardItem
import com.stelliberty.android.ui.component.groupedCardItems
import com.stelliberty.android.viewmodel.ClashFeaturesViewModel
import top.yukonga.miuix.kmp.preference.ArrowPreference

@Composable
fun ClashFeaturesScreen(
    viewModel: ClashFeaturesViewModel,
    onBack: () -> Unit = {},
    onNavigateNetwork: () -> Unit = {},
    onNavigatePortControl: () -> Unit = {},
    onNavigateSystemIntegration: () -> Unit = {},
    onNavigateDns: () -> Unit = {},
    onNavigatePerformance: () -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    SettingsSubPage(title = stringResource(R.string.clash_features_title), onBack = onBack) {
        item { Spacer(Modifier.height(12.dp)) }
        groupedCardItems(
            keyPrefix = "clash_features",
            items = listOf(
                CardItem("network") {
                    ArrowPreference(
                        title = stringResource(R.string.network_settings_title),
                        summary = stringResource(R.string.network_settings_summary),
                        onClick = onNavigateNetwork,
                    )
                },
                CardItem("portControl") {
                    ArrowPreference(
                        title = stringResource(R.string.port_control_title),
                        summary = stringResource(R.string.port_control_summary),
                        onClick = onNavigatePortControl,
                    )
                },
                CardItem("systemIntegration") {
                    ArrowPreference(
                        title = stringResource(R.string.system_integration_title),
                        summary = stringResource(R.string.system_integration_summary),
                        onClick = onNavigateSystemIntegration,
                    )
                },
                CardItem("dns") {
                    ArrowPreference(
                        title = stringResource(R.string.dns_settings_title),
                        summary = stringResource(R.string.dns_settings_summary),
                        onClick = onNavigateDns,
                    )
                },
                CardItem("performance") {
                    ArrowPreference(
                        title = stringResource(R.string.performance_settings_title),
                        summary = stringResource(R.string.performance_settings_summary),
                        onClick = onNavigatePerformance,
                    )
                },
                CardItem("logLevel") {
                    OverrideChoicePreference(
                        title = stringResource(R.string.log_source_core),
                        summary = stringResource(R.string.settings_core_log_summary),
                        choices = LOG_LEVEL_CHOICES,
                        value = state.logLevel,
                        onValueChange = { v -> viewModel.update { it.copy(logLevel = v) } },
                    )
                },
            ),
        )
    }
}
