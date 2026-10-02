package com.example.taxiradar

import android.content.Context
import org.json.JSONObject

/**
 * Контакты поддержки и ссылка на группу. Приходят с сервера (/api/app-config,
 * переменные в Railway) и кешируются; без связи — последние сохранённые
 * или встроенные значения.
 */
data class AppConfig(
    val telegram: String,
    val whatsapp: String,
    val viber: String,
    val phone: String,
    val groupUrl: String,
    val tilesApiKey: String,
    val referralBonusDays: Int,
    /** Адреса ищет сервер Taxi Radar — свой ключ Яндекса водителю не нужен. */
    val sharedGeocoder: Boolean,
    /** Колокольчик: последняя версия приложения и где её скачать (пост в Telegram). */
    val latestVersionCode: Int = 0,
    val latestVersionName: String = "",
    val updateUrl: String = "",
    val updateNotes: String = "",
    /** Тарифы экрана «Подписка»: дни → цена. */
    val tariffs: List<Tariff> = listOf(Tariff(30, 99)),
    val currency: String = "лей",
    /** Цена «от …» без спроса по тарифам — от неё считается надбавка. */
    val surgeBase: Map<String, Int> = emptyMap()
) {
    data class Tariff(val days: Int, val price: Int)

    /** «лей» с сервера → «lei» в румынском интерфейсе. */
    fun currencyLabel(context: Context): String =
        if (currency == "лей" && context.getString(R.string.lang_button) != "RU") "lei" else currency

    companion object {
        private const val PREFS = "taxi_radar_prefs"
        private const val KEY = "app_config_json"

        private val DEFAULT = AppConfig(
            telegram = "sigmalxl",
            whatsapp = "+37378293919",
            viber = "+37378293919",
            phone = "+37378293919",
            groupUrl = "https://t.me/taxi_radar_chisinau",
            tilesApiKey = "",
            referralBonusDays = 3,
            sharedGeocoder = false
        )

        fun load(context: Context): AppConfig {
            val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
                ?: return DEFAULT
            return try {
                fromJson(JSONObject(raw))
            } catch (e: Exception) {
                DEFAULT
            }
        }

        fun save(context: Context, json: JSONObject) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, json.toString()).apply()
        }

        private fun fromJson(j: JSONObject) = AppConfig(
            telegram = j.optString("telegram", DEFAULT.telegram).removePrefix("@"),
            whatsapp = j.optString("whatsapp", DEFAULT.whatsapp),
            viber = j.optString("viber", DEFAULT.viber),
            phone = j.optString("phone", DEFAULT.phone),
            groupUrl = j.optString("group_url", DEFAULT.groupUrl),
            tilesApiKey = j.optString("tiles_api_key", ""),
            referralBonusDays = j.optInt("referral_bonus_days", DEFAULT.referralBonusDays),
            sharedGeocoder = j.optBoolean("shared_geocoder", false),
            latestVersionCode = j.optInt("latest_version_code", 0),
            latestVersionName = j.optString("latest_version_name", ""),
            updateUrl = j.optString("update_url", ""),
            updateNotes = j.optString("update_notes", ""),
            tariffs = parseTariffs(j),
            currency = j.optString("currency", "лей").ifBlank { "лей" },
            surgeBase = j.optJSONObject("surge_base")?.let { o ->
                o.keys().asSequence().associateWith { o.optInt(it) }.filterValues { it > 0 }
            } ?: emptyMap()
        )

        private fun parseTariffs(j: JSONObject): List<Tariff> {
            val arr = j.optJSONArray("tariffs") ?: return listOf(Tariff(30, 99))
            val list = (0 until arr.length()).mapNotNull { i ->
                arr.optJSONObject(i)?.let { Tariff(it.optInt("days"), it.optInt("price")) }
            }.filter { it.days > 0 }
            return list.ifEmpty { listOf(Tariff(30, 99)) }
        }
    }
}
