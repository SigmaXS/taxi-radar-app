package com.example.taxiradar

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Резервная копия: поездки, смены, расходы машины и настройки радара — в один файл,
 * чтобы перенести на другой телефон. Подписка, ID устройства и ключи в копию не попадают.
 * Плюс таблица доходов за месяц (CSV — открывается в Excel и Google Таблицах).
 */
object Backup {
    private const val MAIN = "taxi_radar_prefs"
    private const val JOURNAL = "driver_journal"
    /** Что из общих настроек переносим: настройки помощника, расходы, лёгкий режим, тарифы, выученные пробки. */
    private val KEEP = listOf("driver_", "net_", "lite_", "show_", "widget_", "traffic_samples")
    private val SKIP = listOf("yandex", "license", "device", "key")

    private fun dump(c: Context, file: String, filter: (String) -> Boolean): JSONObject {
        val out = JSONObject()
        c.getSharedPreferences(file, Context.MODE_PRIVATE).all.forEach { (k, v) ->
            if (!filter(k) || v == null) return@forEach
            val type = when (v) { is Boolean -> "b"; is Int -> "i"; is Long -> "l"; is Float -> "f"; is String -> "s"; else -> return@forEach }
            out.put(k, JSONObject().put("t", type).put("v", if (v is Float) v.toDouble() else v))
        }
        return out
    }

    private fun keep(k: String) = KEEP.any { k.startsWith(it) } && SKIP.none { k.contains(it, true) }

    fun export(c: Context): String = JSONObject()
        .put("app", "Taxi Radar").put("format", 1).put("created", System.currentTimeMillis())
        .put(JOURNAL, dump(c, JOURNAL) { true })
        .put(MAIN, dump(c, MAIN, ::keep))
        .toString(1)

    /** Восстановить из файла; возвращает число восстановленных поездок или null, если файл не наш. */
    fun restore(c: Context, text: String): Int? {
        val j = try { JSONObject(text) } catch (_: Exception) { return null }
        if (j.optString("app") != "Taxi Radar") return null
        for ((file, filter) in listOf<Pair<String, (String) -> Boolean>>(JOURNAL to { _ -> true }, MAIN to ::keep)) {
            val data = j.optJSONObject(file) ?: continue
            val e = c.getSharedPreferences(file, Context.MODE_PRIVATE).edit()
            data.keys().forEach { k ->
                if (!filter(k)) return@forEach
                val o = data.getJSONObject(k)
                when (o.optString("t")) {
                    "b" -> e.putBoolean(k, o.getBoolean("v")); "i" -> e.putInt(k, o.getInt("v")); "l" -> e.putLong(k, o.getLong("v"))
                    "f" -> e.putFloat(k, o.getDouble("v").toFloat()); "s" -> e.putString(k, o.getString("v"))
                }
            }
            e.commit()
        }
        return DriverJournal.rides(c).size
    }

    /** Месяцы, в которых есть поездки: (год, месяц 0–11), новые первыми. */
    fun months(c: Context): List<Pair<Int, Int>> = DriverJournal.rides(c).map {
        Calendar.getInstance().apply { timeInMillis = it.at }.let { k -> k.get(Calendar.YEAR) to k.get(Calendar.MONTH) }
    }.distinct().sortedWith(compareByDescending<Pair<Int, Int>> { it.first }.thenByDescending { it.second })

    /** Таблица за месяц: поездки и итоги смен. «;» и BOM — чтобы русский Excel открыл сразу. */
    fun monthCsv(c: Context, year: Int, month: Int): String {
        fun t(ru: String, ro: String) = DriverUi.t(c, ru, ro)
        val inMonth = { at: Long -> Calendar.getInstance().apply { timeInMillis = at }.let { it.get(Calendar.YEAR) == year && it.get(Calendar.MONTH) == month } }
        val f = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
        fun cell(v: Any?) = "\"" + (v?.toString() ?: "").replace("\"", "\"\"") + "\""
        val sb = StringBuilder("﻿")
        sb.append(listOf(t("Дата", "Data"), t("Откуда", "De la"), t("Куда", "Până la"), t("Оплата, L", "Plata, L"), t("Расчёт радара, L", "Estimare, L"),
            t("Км", "Km"), t("Подача, км", "Preluare, km"), t("Минут", "Minute"), t("Чистыми, L", "Net, L"), t("Подтверждено", "Confirmat"), t("Район", "Zonă")).joinToString(";") { cell(it) }).append("\r\n")
        val rides = DriverJournal.rides(c).filter { inMonth(it.at) }.sortedBy { it.at }
        rides.forEach { r ->
            sb.append(listOf(f.format(Date(r.at)), r.from, r.to, r.price, r.estimate.takeIf { it > 0 }, "%.1f".format(r.km), "%.1f".format(r.pickup), r.minutes,
                if (r.costsReady) r.net else "", if (r.confirmed) t("да", "da") else t("нет", "nu"), r.area).joinToString(";") { cell(it) }).append("\r\n")
        }
        val ok = rides.filter { it.confirmed }
        sb.append("\r\n").append(cell(t("Итого подтверждено", "Total confirmat"))).append(";;;").append(cell(ok.sumOf { it.price })).append("\r\n")
        sb.append("\r\n").append(listOf(t("Смена: начало", "Tura: început"), t("Конец", "Sfârșit"), t("Часов", "Ore"), t("Перерывы, мин", "Pauze, min"),
            t("Оплата, L", "Plata, L"), t("Чистыми, L", "Net, L"), t("Пробег, км", "Km total"), t("Аренда, L", "Chirie, L")).joinToString(";") { cell(it) }).append("\r\n")
        val all = DriverJournal.rides(c)
        DriverJournal.shifts(c).filter { inMonth(it.start) }.sortedBy { it.start }.forEach { s ->
            val tot = DriverJournal.totals(c, s, all)
            sb.append(listOf(f.format(Date(s.start)), if (s.end > 0) f.format(Date(s.end)) else "", "%.1f".format(s.minutes / 60.0), s.pausedMin,
                tot.gross, tot.net ?: "", "%.1f".format(s.odometerKm), s.rent.toInt()).joinToString(";") { cell(it) }).append("\r\n")
        }
        return sb.toString()
    }
}
