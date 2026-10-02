package com.example.taxiradar

import android.content.Context

/** Личный ключ «API Геокодера» пользователя — у каждого водителя свой. */
object YandexApiKey {
    private const val PREFS = "taxi_radar_prefs"
    private const val KEY = "yandex_geocoder_key"

    fun get(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, null)
            ?.takeIf { it.isNotBlank() }

    fun save(context: Context, key: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY, key)
            .remove(PENDING)
            .apply()
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY).apply()
    }

    // Ключ забрали из кабинета, но Яндекс его ещё не включил (первые 15–30 минут
    // новый ключ отвечает 403). Главный экран сам перепроверяет его при каждом открытии.
    private const val PENDING = "yandex_geocoder_key_pending"

    fun getPending(context: Context): List<String> =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(PENDING, null)
            ?.split(',')
            ?.filter { it.isNotBlank() }
            .orEmpty()

    /** Самый вероятный ключ (только что созданный) — первым; не больше пяти. */
    fun savePending(context: Context, keys: List<String>) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(PENDING, keys.take(5).joinToString(","))
            .apply()
    }

    /** Можно считать цены: есть свой ключ или адреса ищет сервер. */
    fun ready(context: Context): Boolean =
        get(context) != null || AppConfig.load(context).sharedGeocoder

    fun masked(key: String): String =
        if (key.length <= 6) "••••" else "••••••${key.takeLast(6)}"
}
