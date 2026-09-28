package com.stelliberty.android.ui.screen.subscription

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.stelliberty.android.R
import com.stelliberty.android.data.repository.ImportProgress
import com.stelliberty.android.data.repository.ImportStep

@Composable
fun importStepLabel(p: ImportProgress): String = when (p.step) {
    ImportStep.Downloading -> stringResource(R.string.subscription_downloading)
    ImportStep.Prefetching ->
        if (p.providerName.isNotEmpty() && p.total > 0) {
            stringResource(R.string.subscription_updating_progress, p.providerName, p.current + 1, p.total)
        } else {
            stringResource(R.string.subscription_prefetching)
        }

    ImportStep.Validating -> stringResource(R.string.subscription_validating)
    ImportStep.Other -> p.rawLabel
}
