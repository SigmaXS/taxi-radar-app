package com.example.taxiradar

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.TimeUnit

object YandexTaxiSurgeChecker {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .build()

    /**
     * Старт тарифа без надбавки. Цена «от …» у Яндекса = старт + надбавка
     * ступенями (+15 / +35 / +55). Значения приходят с сервера
     * (/api/app-config → surge_base), здесь — на случай без связи.
     */
    @Volatile
    private var basePrices = mapOf(
        "econom" to 30,
        "comfort" to 45,
        "business" to 45,
        "comfortplus" to 65
    )

    fun updateBases(bases: Map<String, Int>) {
        if (bases.isEmpty()) return
        val m = basePrices.toMutableMap()
        bases.forEach { (k, v) -> if (v > 0) m[k] = v }
        // Комфорт у Яндекса называется «business».
        bases["comfort"]?.let { if (it > 0) m["business"] = it }
        basePrices = m
    }

    /**
     * Общий POST-запрос к routestats с произвольным маршрутом (1 точка — для
     * проверки спроса в месте водителя, 2 точки А→Б — для реальной цены поездки).
     */
    private fun requestRouteStats(route: JSONArray): JSONObject? {
        val jsonPayload = JSONObject().apply {
            put("route", route)
            put("selected_class", "")
            put("format_currency", true)
            put("summary_version", 2)
            put("is_lightweight", false)
            put("supports_paid_options", true)
            put("use_toll_roads", false)

            val tariffsArray = JSONArray().apply {
                put(JSONObject().apply { put("class", "econom") })
                put(JSONObject().apply { put("class", "business") })
                put(JSONObject().apply { put("class", "comfortplus") })
            }
            put("tariff_requirements", tariffsArray)
        }.toString()

        val request = Request.Builder()
            .url("https://ya-authproxy.taxi.yandex.md/3.0/routestats")
            .post(jsonPayload.toRequestBody("application/json; charset=utf-8".toMediaType()))
            .addHeader("Accept", "*/*")
            .addHeader("Accept-Language", "ro,ru;q=0.9")
            .addHeader("Origin", "https://taxi.yandex.md")
            .addHeader("Referer", "https://taxi.yandex.md/")
            .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            .addHeader("X-Requested-With", "XMLHttpRequest")
            .addHeader("X-Request-Id", UUID.randomUUID().toString())
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val body = response.body?.string() ?: return null
            return JSONObject(body)
        }
    }

    suspend fun getSurgePrice(longitude: Double, latitude: Double, tariff: String = "econom"): Int? = withContext(Dispatchers.IO) {
        try {
            val targetClass = when (tariff.lowercase()) {
                "comfort" -> "business"
                else -> tariff.lowercase()
            }

            val route = JSONArray().apply {
                put(JSONArray().apply { put(longitude); put(latitude) })
            }

            val json = requestRouteStats(route) ?: return@withContext null
            val levels = json.optJSONArray("service_levels") ?: return@withContext 0

            for (i in 0 until levels.length()) {
                val lvl = levels.getJSONObject(i)
                val className = lvl.optString("class", "").lowercase()
                if (className == tariff.lowercase() || className == targetClass) {
                    return@withContext surgeForLevel(lvl, className)
                }
            }

            0
        } catch (e: Exception) {
            Log.e("TAXI_SURGE", "Ошибка: ${e.message}")
            e.printStackTrace()
            null
        }
    }

    /** Надбавка по всем трём тарифам в точке — одним запросом (для карты спроса). */
    suspend fun getSurgeAll(longitude: Double, latitude: Double): Map<String, Int>? = withContext(Dispatchers.IO) {
        try {
            val route = JSONArray().apply { put(JSONArray().apply { put(longitude); put(latitude) }) }
            val levels = requestRouteStats(route)?.optJSONArray("service_levels") ?: return@withContext null
            val result = mutableMapOf<String, Int>()
            for (i in 0 until levels.length()) {
                val lvl = levels.getJSONObject(i)
                val className = lvl.optString("class", "").lowercase()
                if (className in basePrices) result[className] = surgeForLevel(lvl, className)
            }
            result
        } catch (e: Exception) {
            Log.e("TAXI_SURGE", "Ошибка карты спроса: ${e.message}")
            null
        }
    }

    private fun surgeForLevel(lvl: JSONObject, className: String): Int {
        // 1. Явная надбавка из блока surge, если API отдаёт её напрямую
        val directSurge = lvl.optJSONObject("surge")?.optInt("value", 0) ?: 0
        if (directSurge > 0) return directSurge

        // 2. Разница между ценой старта и базовой посадкой
        val priceString = lvl.optString("price", "")
        val startPrice = Regex("^\\d+").find(priceString.trim())?.value?.toIntOrNull()
            ?: Regex("\\d+").find(priceString)?.value?.toIntOrNull()
            ?: 0
        val base = basePrices[className] ?: 30
        Log.d("TAXI_SURGE", "[$className] Старт: $startPrice L | База: $base L")
        return maxOf(0, startPrice - base)
    }
}