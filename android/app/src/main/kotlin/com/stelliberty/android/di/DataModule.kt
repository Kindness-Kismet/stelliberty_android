package com.stelliberty.android.di

import com.stelliberty.android.R
import com.stelliberty.android.data.api.AutoDelayTester
import com.stelliberty.android.data.api.MihomoConnectionManager
import com.stelliberty.android.data.api.RuleLatencyTester
import com.stelliberty.android.data.repository.ChainProxyRepositoryImpl
import com.stelliberty.android.data.repository.OverrideJsonStore
import com.stelliberty.android.data.repository.OverrideProfileRepositoryImpl
import com.stelliberty.android.data.repository.ProfileProcessor
import com.stelliberty.android.data.repository.RuleOverrideRepositoryImpl
import com.stelliberty.android.data.repository.SubscriptionProxyResolver
import com.stelliberty.android.data.repository.SubscriptionRepositoryImpl
import com.stelliberty.android.data.store.OverrideProfileStore
import com.stelliberty.android.data.store.ProfileTransformWriter
import com.stelliberty.android.data.store.ProxySelectionStore
import com.stelliberty.android.data.store.RuleOverrideStore
import com.stelliberty.android.data.store.SubscriptionStore
import com.stelliberty.android.domain.repository.ChainProxyRepository
import com.stelliberty.android.domain.repository.OverrideProfileRepository
import com.stelliberty.android.domain.repository.RuleOverrideRepository
import com.stelliberty.android.domain.repository.SubscriptionRepository
import com.stelliberty.android.util.AppLogger
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val dataModule = module {
    single<CoroutineScope> {
        val handler = CoroutineExceptionHandler { _, e ->
            AppLogger.error("AppScope", "uncaught in shared scope", e)
        }
        CoroutineScope(SupervisorJob() + Dispatchers.Default + handler)
    }

    single { SubscriptionStore(get(), get()) }
    single { ProxySelectionStore(get(), get()) }
    single { OverrideProfileStore(get(), get()) }
    single { RuleOverrideStore(get(), get()) }
    single { ProfileTransformWriter(get(), get(), get()) }

    single { OverrideJsonStore(get(), get()) }
    single { SubscriptionProxyResolver(get()) }
    single { RuleLatencyTester(get()) }
    single { MihomoConnectionManager(get()) }
    single { AutoDelayTester(get(), get(), get()) }

    single {
        SubscriptionRepositoryImpl(
            store = get(),
            proxySelections = get(),
            ruleOverrides = get(),
            storage = get(),
            scope = get(),
        )
    }
    single<SubscriptionRepository> { get<SubscriptionRepositoryImpl>() }

    single<OverrideProfileRepository> {
        OverrideProfileRepositoryImpl(
            store = get(),
            transformWriter = get(),
            subscriptionStore = get(),
            subscriptions = get(),
            fileManager = get(),
            proxyResolver = get(),
            scope = get(),
        )
    }

    single<ChainProxyRepository> {
        ChainProxyRepositoryImpl(
            subscriptionStore = get(),
            subscriptions = get(),
            transformWriter = get(),
            fileManager = get(),
        )
    }

    single<RuleOverrideRepository> {
        RuleOverrideRepositoryImpl(
            store = get(),
            subscriptionStore = get(),
            transformWriter = get(),
            fileManager = get(),
        )
    }

    factory {
        ProfileProcessor(
            repo = get(),
            fileManager = get(),
            defaultProfileName = androidContext().getString(R.string.subscription_default_name),
            proxyResolver = get(),
        )
    }
}
