package com.stelliberty.android.debug

import android.os.Bundle
import com.stelliberty.android.domain.model.AppUpdateInfo
import com.stelliberty.android.domain.model.AppUpdateResult
import com.stelliberty.android.viewmodel.AppUpdateUiState
import com.stelliberty.android.viewmodel.AppUpdateViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.runBlocking

internal fun runDialogFixtureCommand(arg: String?, extras: Bundle?): Bundle {
    val target = extras.target(arg)
    if (target != "app_update") return debugResult(false, "Unknown dialog fixture", "dialog.show_fixture", target)
    runBlocking(Dispatchers.Main) {
        val viewModel = koin<AppUpdateViewModel>()
        val job = AppUpdateViewModel::class.java.getDeclaredField("checkJob").apply { isAccessible = true }
        (job.get(viewModel) as Job?)?.cancel()
        // 调试夹具只替换内存状态，真实发布信息与偏好保持在业务入口管理。
        val field = AppUpdateViewModel::class.java.getDeclaredField("_uiState").apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        val state = field.get(viewModel) as MutableStateFlow<AppUpdateUiState>
        state.update {
            it.copy(
                checking = false,
                showDialog = true,
                result = AppUpdateResult.Available(
                    AppUpdateInfo(
                        latestVersion = "999.0.0",
                        releaseUrl = "https://example.com/update",
                        releaseNotes = "- Example update\n\n---\n\n- 示例更新",
                    ),
                ),
            )
        }
    }
    return debugResult(true, "Opened dialog fixture", "dialog.show_fixture", target)
}
