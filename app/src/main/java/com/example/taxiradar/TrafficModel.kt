package com.example.taxiradar

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar
import kotlin.math.abs

/**
 * Поправка на пробки. OSRM считает время по пустым дорогам, а Яндекс берёт
 * за каждую минуту в пути (1 L/мин) по своему прогнозу с пробками — днём
 * это давало ошибку 5–10 L даже на коротких поездках.
 *
 * Тарифы не трогаем — уточняем только минуты. После принятия заказа Яндекс
 * Про показывает свой прогноз поездки («2,8 km · 8 min»); сохраняем пару
 * «наше время / время Яндекса» и по ним считаем, во сколько раз Яндекс
 * дольше OSRM в этот час. Пока данных мало — без поправки.
 */
object TrafficModel {

    private const val PREFS = "taxi_radar_prefs"
    private const val KEY = "traffic_samples"
    private const val MAX_SAMPLES = 300

    // Короткие поездки слишком грубые: 3 мин против 4 — это уже ×1.33.
    private const val MIN_OSRM_MIN = 3.0

    /**
     * Пока своих поездок в этот час меньше трёх — без поправки (цена как
     * раньше). Придуманные «на глаз» коэффициенты могли бы и ухудшить цену:
     * заранее неизвестно, в какую сторону ошибается OSRM в каждый час.
     */
    private const val DEFAULT_FACTOR = 1.0

    data class Sample(
        val at: Long,
        val hour: Int,
        val weekend: Boolean,
        val osrmMin: Double,
        val yandexMin: Int,
        val osrmKm: Double,
        val yandexKm: Double
    ) {
        val ratio: Double get() = yandexMin / osrmMin
    }

    data class Factor(val value: Double, val fromDrivers: Boolean)

    data class Stats(
        val count: Int,
        val factorNow: Factor,
        /** Средняя ошибка минут (≈ L) по сохранённым поездкам: без поправки и с ней. */
        val errorBefore: Double?,
        val errorAfter: Double?
    )

    fun load(context: Context): List<Sample> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Sample(
                    at = o.getLong("at"),
                    hour = o.getInt("h"),
                    weekend = o.getBoolean("we"),
                    osrmMin = o.getDouble("om"),
                    yandexMin = o.getInt("ym"),
                    osrmKm = o.getDouble("ok"),
                    yandexKm = o.getDouble("yk")
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun add(context: Context, sample: Sample) {
        val all = (load(context) + sample).takeLast(MAX_SAMPLES)
        val arr = JSONArray()
        all.forEach {
            arr.put(
                JSONObject()
                    .put("at", it.at).put("h", it.hour).put("we", it.weekend)
                    .put("om", it.osrmMin).put("ym", it.yandexMin)
                    .put("ok", it.osrmKm).put("yk", it.yandexKm)
            )
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, arr.toString()).apply()
    }

    fun factor(context: Context, at: Calendar = Calendar.getInstance()): Factor =
        factorFor(usable(load(context)), at.get(Calendar.HOUR_OF_DAY), isWeekend(at))

    fun stats(context: Context): Stats {
        val samples = usable(load(context))
        val now = Calendar.getInstance()
        val factorNow = factorFor(samples, now.get(Calendar.HOUR_OF_DAY), isWeekend(now))
        if (samples.isEmpty()) return Stats(0, factorNow, null, null)
        val before = samples.map { abs(it.yandexMin - it.osrmMin) }.average()
        // «С поправкой» — каждую поездку оцениваем по остальным, без неё самой.
        val after = samples.map { s ->
            val f = factorFor(samples - s, s.hour, s.weekend).value
            abs(s.yandexMin - s.osrmMin * f)
        }.average()
        return Stats(samples.size, factorNow, before, after)
    }

    /**
     * Час пик в Кишинёве по будням, пока своих поездок в этот час мало: 7–9 и
     * 13–18 — +10 минут (по опыту водителей; OSRM пробок не знает). Когда радар
     * выучил час по поездкам водителя, эта прибавка не нужна.
     */
    fun rushHourExtraMin(c: Calendar = Calendar.getInstance()): Int {
        if (isWeekend(c)) return 0
        val h = c.get(Calendar.HOUR_OF_DAY)
        return if (h in 7..8 || h in 13..17) 10 else 0
    }

    fun isWeekend(c: Calendar): Boolean {
        val d = c.get(Calendar.DAY_OF_WEEK)
        return d == Calendar.SATURDAY || d == Calendar.SUNDAY
    }

    private fun usable(samples: List<Sample>) =
        samples.filter { it.osrmMin >= MIN_OSRM_MIN && it.ratio in 0.5..3.0 }

    private fun factorFor(samples: List<Sample>, hour: Int, weekend: Boolean): Factor {
        fun near(s: Sample): Boolean {
            val d = abs(s.hour - hour)
            return minOf(d, 24 - d) <= 1
        }
        val sameKind = samples.filter { near(it) && it.weekend == weekend }
        val anyDay = samples.filter { near(it) }
        val picked = when {
            sameKind.size >= 3 -> sameKind
            anyDay.size >= 3 -> anyDay
            else -> null
        }
        val value = picked?.map { it.ratio }?.let(::median) ?: DEFAULT_FACTOR
        return Factor(value.coerceIn(0.8, 2.2), fromDrivers = picked != null)
    }

    private fun median(values: List<Double>): Double {
        val s = values.sorted()
        return if (s.size % 2 == 1) s[s.size / 2] else (s[s.size / 2 - 1] + s[s.size / 2]) / 2
    }
}
