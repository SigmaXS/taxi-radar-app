package com.example.taxiradar

import android.content.Context
import android.content.res.Configuration
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import java.util.Locale

/**
 * Язык интерфейса: русский, румынский или как в телефоне. Выбор хранит
 * AppCompat (на Android 13+ — сама система, в «Язык приложения»).
 */
object AppLanguage {

    /** "ru", "ro" или "" — как в телефоне. */
    fun current(): String =
        AppCompatDelegate.getApplicationLocales().get(0)?.language.orEmpty()

    fun set(tag: String) {
        AppCompatDelegate.setApplicationLocales(
            if (tag.isEmpty()) LocaleListCompat.getEmptyLocaleList() else LocaleListCompat.forLanguageTags(tag)
        )
    }

    /**
     * До Android 13 AppCompat меняет язык только у экранов приложения, а виджет —
     * это служба. Ей язык подставляем сами.
     */
    fun wrap(base: Context): Context {
        val locale = AppCompatDelegate.getApplicationLocales().get(0) ?: return base
        val config = Configuration(base.resources.configuration)
        config.setLocale(Locale(locale.language))
        return base.createConfigurationContext(config)
    }
}
