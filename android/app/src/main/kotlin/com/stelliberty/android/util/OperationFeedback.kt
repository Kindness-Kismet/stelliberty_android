package com.stelliberty.android.util

import androidx.annotation.StringRes
import com.stelliberty.android.R
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

data class OperationMessage(
    @StringRes val resource: Int,
    val succeeded: Int = 0,
    val failed: Int = 0,
)

object OperationFeedback {
    private val events = MutableSharedFlow<OperationMessage>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val messages = events.asSharedFlow()

    fun show(@StringRes resource: Int) {
        events.tryEmit(OperationMessage(resource))
    }

    fun updateResult(succeeded: Int, failed: Int) {
        val resource = when {
            succeeded + failed == 0 -> R.string.operation_nothing_to_update
            failed == 0 -> R.string.operation_update_success
            succeeded == 0 -> R.string.operation_update_failed
            else -> R.string.operation_update_partial
        }
        events.tryEmit(OperationMessage(resource, succeeded, failed))
    }
}
