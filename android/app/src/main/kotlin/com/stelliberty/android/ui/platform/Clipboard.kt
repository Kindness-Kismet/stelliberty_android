package com.stelliberty.android.ui.platform

import android.content.ClipData
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard

internal suspend fun Clipboard.setPlainText(text: String) {
    setClipEntry(ClipEntry(ClipData.newPlainText("Clipboard", text)))
}

internal suspend fun Clipboard.getPlainText(): String? {
    val clipData = getClipEntry()?.clipData ?: return null
    if (clipData.itemCount == 0) return null
    return clipData.getItemAt(0).text?.toString()
}
