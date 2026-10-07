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
import com.stelliberty.android.ui.component.RestartRequiredHint
import com.stelliberty.android.ui.component.TriStatePreference
import com.stelliberty.android.ui.component.groupedCardItems
import com.stelliberty.android.viewmodel.ClashFeaturesViewModel

@Composable
fun PerformanceSettingsScreen(
    viewModel: ClashFeaturesViewModel,
    onBack: () -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    SettingsSubPage(title = stringResource(R.string.performance_settings_title), onBack = onBack) {
        item { RestartRequiredHint() }
        item { Spacer(Modifier.height(6.dp)) }

        groupedCardItems(
            keyPrefix = "performance",
            items = listOf(
                CardItem("geodataMode") {
                    TriStatePreference(
                        title = stringResource(R.string.performance_geodata_mode),
                        value = state.geodataMode,
                        onValueChange = { v -> viewModel.update { it.copy(geodataMode = v) } },
                    )
                },
                CardItem("findProcessMode") {
                    OverrideChoicePreference(
                        title = stringResource(R.string.performance_find_process_mode),
                        choices = FIND_PROCESS_MODE_CHOICES,
                        value = state.findProcessMode,
                        onValueChange = { v -> viewModel.update { it.copy(findProcessMode = v) } },
                    )
                },
            ),
        )
    }
}
