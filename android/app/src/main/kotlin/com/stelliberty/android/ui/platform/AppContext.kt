package com.stelliberty.android.ui.platform

import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.os.LocaleList
import com.stelliberty.android.ui.theme.AppLanguage
import com.stelliberty.android.ui.theme.DefaultDensityScale
import com.stelliberty.android.ui.theme.normalizeDensityScale
import java.util.Locale
import kotlin.math.roundToInt

fun localizedAppContext(base: Context, language: AppLanguage): Context {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU || language == AppLanguage.System) return base
    val configuration = Configuration(base.resources.configuration).apply {
        setLocales(LocaleList.forLanguageTags(language.languageTag))
    }
    return base.createConfigurationContext(configuration)
}

// 密度由 Activity 的系统基准统一缩放，独立 Dialog 与 Popup 继承同一配置。
fun scaledAppContext(base: Context, scale: Float): Context {
    val value = normalizeDensityScale(scale)
    if (value == DefaultDensityScale) return base
    val configuration = Configuration(base.resources.configuration).apply {
        densityDpi = (densityDpi * value).roundToInt()
        screenWidthDp = (screenWidthDp / value).roundToInt()
        screenHeightDp = (screenHeightDp / value).roundToInt()
        smallestScreenWidthDp = (smallestScreenWidthDp / value).roundToInt()
    }
    return base.createConfigurationContext(configuration)
}

fun updateAppLanguage(context: Context, language: AppLanguage) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        context.getSystemService(LocaleManager::class.java).applicationLocales =
            LocaleList.forLanguageTags(language.languageTag)
    } else {
        updateLegacyAppLanguage(context, language)
    }
}

@Suppress("DEPRECATION")
internal fun updateLegacyAppLanguage(context: Context, language: AppLanguage) {
    val locales = if (language == AppLanguage.System) Resources.getSystem().configuration.locales
    else LocaleList.forLanguageTags(language.languageTag)
    Locale.setDefault(locales[0])
    val resources = context.applicationContext.resources
    val configuration = Configuration(resources.configuration).apply { setLocales(locales) }
    resources.updateConfiguration(configuration, resources.displayMetrics)
}
