package com.stelliberty.android.debug

import android.os.Bundle
import com.stelliberty.android.platform.PlatformStorage
import com.stelliberty.android.platform.ProxyServiceBridge
import com.stelliberty.android.platform.StorageKeys

internal fun runStateCommand(): Bundle {
    val status = ProxyServiceBridge.state.value
    val storage = koin<PlatformStorage>()
    val activeName = storage.getString(StorageKeys.ACTIVE_PROFILE_NAME, "")
    val data = buildString {
        append("state=").append(status.state.name)
        append(" tunMode=").append(status.tunMode.name)
        append(" pid=").append(status.mihomoPid)
        append(" externalController=").append(status.externalController)
        if (status.errorMessage.isNotEmpty()) append(" error=").append(status.errorMessage)
        if (activeName.isNotEmpty()) append(" activeProfile=").append(activeName)
    }
    return debugResult(true, "state read", "state.get", data = data)
}
