package com.stelliberty.android.debug

import android.os.Bundle
import com.stelliberty.android.ui.navigation.DebugNavBridge

internal fun runPageCommand(arg: String?, extras: Bundle?): Bundle {
    val pageId = extras.target(arg)
        ?: return debugResult(false, "Missing page id", "page.open", arg)
    val known = DebugNavBridge.mainTabIndexOf(pageId) != null || DebugNavBridge.routeOf(pageId) != null
    if (!known) {
        return debugResult(false, "Unknown page: $pageId", "page.open", pageId)
    }
    if (!DebugNavBridge.request(pageId)) {
        return debugResult(false, "No navigation collector; run `am start` first", "page.open", pageId)
    }
    return debugResult(true, "Opened $pageId", "page.open", pageId)
}
