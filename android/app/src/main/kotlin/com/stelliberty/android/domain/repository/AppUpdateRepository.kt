package com.stelliberty.android.domain.repository

import com.stelliberty.android.domain.model.AppUpdateResult
import com.stelliberty.android.domain.model.UpdateChannel

interface AppUpdateRepository {
    suspend fun checkForUpdate(currentVersion: String, channel: UpdateChannel): AppUpdateResult
}
