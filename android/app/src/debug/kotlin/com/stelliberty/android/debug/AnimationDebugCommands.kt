package com.stelliberty.android.debug

import android.os.Bundle
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.platform.WindowRecomposerFactory
import androidx.compose.ui.platform.WindowRecomposerPolicy
import androidx.compose.ui.platform.createLifecycleAwareWindowRecomposer

internal object DebugAnimationSpeed : MotionDurationScale {
    @Volatile
    var speed: Float = 1f

    override val scaleFactor: Float
        get() = 1f / speed

    @OptIn(InternalComposeUiApi::class)
    fun install() {
        // 在创建窗口前注入，页面和弹出菜单共用动画速度，业务协程仍使用真实时间。
        WindowRecomposerPolicy.setFactory(WindowRecomposerFactory { view ->
            view.createLifecycleAwareWindowRecomposer(coroutineContext = this)
        })
    }
}

internal fun runAnimationCommand(action: String, arg: String?, extras: Bundle?): Bundle {
    val command = "animation.$action"
    when (action) {
        "set_speed" -> {
            val speed = extras.target(arg)?.toFloatOrNull()?.takeIf { it in 0.05f..1f }
                ?: return debugResult(false, "Invalid animation speed (want 0.05..1)", command, arg)
            DebugAnimationSpeed.speed = speed
        }
        "reset_speed" -> DebugAnimationSpeed.speed = 1f
        "get_speed" -> Unit
        else -> return debugResult(false, "Unknown animation action: $action", command, arg)
    }
    val speed = DebugAnimationSpeed.speed
    return debugResult(
        true,
        "Animation speed=${speed}x",
        command,
        data = "speed=$speed; durationScale=${1f / speed}",
    )
}
