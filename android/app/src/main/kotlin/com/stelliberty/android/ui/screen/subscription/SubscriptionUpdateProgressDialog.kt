package com.stelliberty.android.ui.screen.subscription

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stelliberty.android.R
import com.stelliberty.android.viewmodel.ProfileOperation
import com.stelliberty.android.viewmodel.SubscriptionViewModel

// 导航栈中的页面会同时保留组合，所有订阅操作共用导航层的唯一进度窗口。
@Composable
fun SubscriptionUpdateProgressDialog(viewModel: SubscriptionViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val progress = uiState.updateAll
    val step = if (progress != null) {
        val label = stringResource(
            R.string.subscription_updating_progress,
            progress.currentName,
            progress.completed + 1,
            progress.total,
        )
        progress.currentStep?.let { "$label\n${importStepLabel(it)}" } ?: label
    } else {
        uiState.importProgress?.let { importStepLabel(it) } ?: stringResource(R.string.common_processing)
    }

    ImportProgressDialog(
        show = uiState.isLoading || progress != null,
        step = step,
        title = stringResource(
            when {
                progress != null -> R.string.subscription_updating_title
                uiState.operation == ProfileOperation.Update -> R.string.subscription_update_config
                uiState.operation == ProfileOperation.Edit -> R.string.subscription_config
                else -> R.string.subscription_import_config
            },
        ),
        onCancel = viewModel::cancelCurrentUpdate,
    )
}
