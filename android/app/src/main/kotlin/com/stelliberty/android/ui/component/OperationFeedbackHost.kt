package com.stelliberty.android.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.stelliberty.android.platform.showToast
import com.stelliberty.android.util.OperationFeedback

@Composable
fun OperationFeedbackHost() {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle, context) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            OperationFeedback.messages.collect { message ->
                showToast(context.getString(message.resource, message.succeeded, message.failed))
            }
        }
    }
}
