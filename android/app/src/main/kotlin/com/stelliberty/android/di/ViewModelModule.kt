package com.stelliberty.android.di

import com.stelliberty.android.domain.repository.SubscriptionRepository
import com.stelliberty.android.data.api.AutoDelayTester
import com.stelliberty.android.viewmodel.AppProxyViewModel
import com.stelliberty.android.viewmodel.AppUpdateViewModel
import com.stelliberty.android.viewmodel.BackupViewModel
import com.stelliberty.android.viewmodel.ChainProxyViewModel
import com.stelliberty.android.viewmodel.ClashFeaturesViewModel
import com.stelliberty.android.viewmodel.RuleOverrideViewModel
import com.stelliberty.android.viewmodel.ConnectionViewModel
import com.stelliberty.android.viewmodel.DnsQueryViewModel
import com.stelliberty.android.viewmodel.HomeViewModel
import com.stelliberty.android.viewmodel.LogViewModel
import com.stelliberty.android.viewmodel.OverrideProfileViewModel
import com.stelliberty.android.viewmodel.ProviderViewModel
import com.stelliberty.android.viewmodel.ProxyViewModel
import com.stelliberty.android.viewmodel.SubscriptionViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val viewModelModule = module {
    single { AppUpdateViewModel(get(), get()) }
    single {
        HomeViewModel(
            serviceController = get(),
            overrideStore = get(),
            connectionManager = get(),
            latencyTester = get(),
            getActiveSubscriptionId = { get<SubscriptionRepository>().getActive()?.id },
            activeSubscription = get<SubscriptionRepository>().activeSubscription,
            onLiveProviderInfo = get<SubscriptionRepository>()::setLiveProviderInfo,
        )
    }
    single {
        SubscriptionViewModel(
            repository = get(),
            fileManager = get(),
            processor = get(),
            serviceController = get(),
            context = androidContext(),
        )
    }
    single {
        ProxyViewModel(
            proxySelections = get(),
            getActiveUuid = { get<SubscriptionRepository>().getActive()?.id },
            storage = get(),
            autoDelayRuns = get<AutoDelayTester>().completed,
        )
    }
    single {
        AppProxyViewModel(
            storage = get(),
            appListProvider = get(),
            serviceController = get(),
        )
    }
    single { ClashFeaturesViewModel(get()) }
    single { LogViewModel() }
    single { ProviderViewModel() }
    single { ConnectionViewModel() }
    single { DnsQueryViewModel() }
    single {
        OverrideProfileViewModel(
            repository = get(),
            subscriptions = get(),
            serviceController = get(),
            context = androidContext(),
        )
    }
    single {
        ChainProxyViewModel(
            repository = get(),
            subscriptions = get(),
            serviceController = get(),
            context = androidContext(),
        )
    }
    single {
        RuleOverrideViewModel(
            repository = get(),
            subscriptions = get(),
            serviceController = get(),
            context = androidContext(),
        )
    }
    single {
        BackupViewModel(
            backupManager = get(),
            storage = get(),
            context = androidContext(),
        )
    }
}
