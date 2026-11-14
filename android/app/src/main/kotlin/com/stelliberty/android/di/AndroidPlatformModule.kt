package com.stelliberty.android.di

import com.stelliberty.android.data.backup.BackupManager
import com.stelliberty.android.platform.AppListProvider
import com.stelliberty.android.platform.AutoDelayAlarm
import com.stelliberty.android.platform.BootStartManager
import com.stelliberty.android.platform.PlatformStorage
import com.stelliberty.android.platform.ProxyServiceController
import com.stelliberty.android.platform.WifiPolicyController
import com.stelliberty.android.service.ProfileUpdateScheduler
import com.stelliberty.android.service.ProfileUpdateTask
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

val androidPlatformModule: Module = module {
    single { AutoDelayAlarm(androidContext()) }
    single { PlatformStorage(androidContext()) }
    single { ProxyServiceController(androidContext(), get()) }
    single { AppListProvider(androidContext()) }
    single { WifiPolicyController(androidContext()) }
    single { BootStartManager(androidContext()) }
    factory { ProfileUpdateTask(get(), get(), get(), get()) }
    single {
        BackupManager(
            context = androidContext(),
            storage = get(),
            subscriptionStore = get(),
            proxySelectionStore = get(),
            overrideStore = get(),
            overrideProfileStore = get(),
            repository = get(),
            bootStartManager = get(),
        )
    }
    single {
        ProfileUpdateScheduler(
            context = androidContext(),
            store = get(),
            scope = get(),
        )
    }
}
