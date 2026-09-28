package com.stelliberty.android.platform

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.core.os.HandlerCompat

private var appContext: Context? = null
private val mainHandler: Handler by lazy { HandlerCompat.createAsync(Looper.getMainLooper()) }

fun initToastPlatform(context: PlatformContext) {
    appContext = context.applicationContext
}

fun showToast(message: String, long: Boolean = false) {
    val ctx = appContext ?: return
    val duration = if (long) Toast.LENGTH_LONG else Toast.LENGTH_SHORT
    if (Looper.myLooper() == Looper.getMainLooper()) {
        Toast.makeText(ctx, message, duration).show()
    } else {
        mainHandler.post { Toast.makeText(ctx, message, duration).show() }
    }
}
