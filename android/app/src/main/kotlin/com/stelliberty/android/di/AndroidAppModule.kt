package com.stelliberty.android.di

import com.stelliberty.android.platform.ProfileFileManager
import com.stelliberty.android.service.AndroidProfileFileManager
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val androidAppModule = module {
    single<ProfileFileManager> { AndroidProfileFileManager(androidContext()) }
}
