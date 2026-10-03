package com.stelliberty.android.ui.screen.subscription

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stelliberty.android.R
import com.stelliberty.android.viewmodel.SubscriptionViewModel

// 批量更新可在任意页面启动，状态收集必须跟随应用导航层，不能受离屏订阅页的生命周期限制。
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
        stringResource(R.string.common_processing)
    }

    ImportProgressDialog(
        show = progress != null,
        step = step,
        title = stringResource(R.string.subscription_updating_title),
        onCancel = viewModel::cancelCurrentUpdate,
    )
}
