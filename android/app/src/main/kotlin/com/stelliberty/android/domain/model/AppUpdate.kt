package com.stelliberty.android.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class UpdateChannel { STABLE, TEST }

@Serializable
data class AppUpdateInfo(
    val latestVersion: String,
    val releaseUrl: String,
    val releaseNotes: String,
)

@Serializable
sealed interface AppUpdateResult {
    @Serializable
    data class Available(val info: AppUpdateInfo) : AppUpdateResult

    @Serializable
    data object UpToDate : AppUpdateResult

    @Serializable
    data object Failed : AppUpdateResult
}
