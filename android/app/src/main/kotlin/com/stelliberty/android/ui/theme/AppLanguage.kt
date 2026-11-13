package com.stelliberty.android.ui.theme

import android.app.LocaleManager
import android.content.Context
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.stelliberty.android.R
import com.stelliberty.android.platform.PlatformStorage
import com.stelliberty.android.platform.StorageKeys

enum class AppLanguage(val languageTag: String) {
    System(""),
    English("en"),
    SimplifiedChinese("zh-CN"),
    TraditionalChinese("zh-TW");

    companion object {
        fun fromStorage(value: String): AppLanguage = entries.firstOrNull { it.languageTag == value } ?: System
    }
}

fun readAppLanguage(context: Context, storage: PlatformStorage): AppLanguage {
    val tag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        context.getSystemService(LocaleManager::class.java).applicationLocales.toLanguageTags()
    } else {
        storage.getString(StorageKeys.APP_LANGUAGE, "")
    }
    return AppLanguage.fromStorage(tag)
}

@Composable
fun AppLanguage.label(): String = stringResource(
    when (this) {
        AppLanguage.System -> R.string.settings_theme_system
        AppLanguage.English -> R.string.settings_language_english
        AppLanguage.SimplifiedChinese -> R.string.settings_language_simplified
        AppLanguage.TraditionalChinese -> R.string.settings_language_traditional
    },
)
