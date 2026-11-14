package com.stelliberty.android.ui.util

import android.content.Context
import android.widget.Toast
import androidx.compose.ui.platform.UriHandler
import com.stelliberty.android.R

fun UriHandler.openUriOrShowToast(uri: String, context: Context) {
    try {
        openUri(uri)
    } catch (_: IllegalArgumentException) {
        Toast.makeText(context, context.getString(R.string.about_open_link_failed), Toast.LENGTH_SHORT).show()
    }
}
