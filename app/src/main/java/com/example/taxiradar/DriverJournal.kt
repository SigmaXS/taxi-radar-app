package com.example.taxiradar

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** Local journal. Guessed/computer-read receipts need confirmation before entering totals. */
object DriverJournal {
    /** id — номер поездки (тот же client_id на сервере), from/to — откуда и куда. */
    data class Ride(val id: String, val at: Long, val shift: String, val price: Int, val estimate: Int, val km: Double, val pickup: Double, val minutes: Int, val confirmed: Boolean, val area: String, val net: Int, val costsReady: Boolean = false, val from: String = "", val to: String = "")
    private fun p(c: Context) = c.getSharedPreferences("driver_journal", Context.MODE_PRIVATE)
    @Synchronized fun rides(c: Context): List<Ride> = try {
        val a = JSONArray(p(c).getString("rides", "[]"))
        (0 until a.length()).map { i -> a.getJSONObject(i).let {
            Ride(it.getString("id"), it.getLong("at"), it.optString("shift"), it.getInt("price"), it.optInt("estimate"), it.optDouble("km"), it.optDouble("pickup"), it.optInt("minutes"), it.optBoolean("confirmed"), it.optString("area"), it.optInt("net"), it.optBoolean("costsReady"), it.optString("from"), it.optString("to"))
        } }
    } catch (_: Exception) { emptyList() }

    @Synchronized private fun write(c: Context, list: List<Ride>) {
        val a = JSONArray()
        list.takeLast(500).forEach { a.put(JSONObject().put("id", it.id).put("at", it.at).put("shift", it.shift).put("price", it.price).put("estimate", it.estimate).put("km", it.km).put("pickup", it.pickup).put("minutes", it.minutes).put("confirmed", it.confirmed).put("area", it.area).put("net", it.net).put("costsReady", it.costsReady).put("from", it.from).put("to", it.to)) }
        p(c).edit().putString("rides", a.toString()).apply()
    }
    @Synchronized fun add(c: Context, price: Int, estimate: Int, km: Double, pickup: Double, minutes: Int, area: String, confirmed: Boolean,
                          id: String = UUID.randomUUID().toString(), from: String = "", to: String = ""): String {
        if (rides(c).any { it.id == id }) return id
        val net = OrderEconomics.calculate(price, km, pickup, minutes, 0.0, DriverPreferences.costs(c)).net
        write(c, rides(c) + Ride(id, System.currentTimeMillis(), active(c), price, estimate, km, pickup, minutes, confirmed, area, net, DriverPreferences.costsReady(c), from.take(120), to.take(120)))
        return id
    }
    @Synchronized fun confirm(c: Context, id: String, price: Int, km: Double, pickup: Double, minutes: Int, area: String) {
        write(c, rides(c).map { if (it.id == id) it.copy(price = price, km = km, pickup = pickup, minutes = minutes, area = area, confirmed = true, costsReady = DriverPreferences.costsReady(c), net = OrderEconomics.calculate(price, km, pickup, minutes, 0.0, DriverPreferences.costs(c)).net) else it })
    }
    @Synchronized fun remove(c: Context, id: String) { write(c, rides(c).filterNot { it.id == id }) }
    fun active(c: Context) = p(c).getString("active", "").orEmpty()
    fun selected(c: Context) = active(c).ifEmpty { p(c).getString("last", "").orEmpty() }
    fun start(c: Context) { if (active(c).isEmpty()) p(c).edit().putString("active", UUID.randomUUID().toString()).putLong("started", System.currentTimeMillis()).putLong("ended", 0).putLong("paused_ms", 0).putLong("paused_at", 0).putFloat("rent", DriverPreferences.number(c, "rent").toFloat()).apply() }
    fun stop(c: Context) { close(c, System.currentTimeMillis()) }

    /** Закрыть смену и положить её в архив: начало, конец, аренда, пробег по одометру. */
    private fun close(c: Context, end: Long) {
        val id = active(c)
        if (paused(c)) resume(c, end)
        if (id.isNotEmpty()) archive(c, Shift(id, p(c).getLong("started", end), end, rent(c), DriverPreferences.number(c, "shift_km"), pausedMinutes(c)))
        p(c).edit().putString("last", id).putString("active", "").putLong("ended", end).apply()
    }

    // ---------- архив смен ----------

    /** minutes — вся смена; pausedMin — перерывы; workMinutes — рабочее время без перерывов. */
    data class Shift(val id: String, val start: Long, val end: Long, val rent: Double, val odometerKm: Double, val pausedMin: Long = 0) {
        val minutes: Long get() = ((if (end > 0) end else System.currentTimeMillis()) - start).coerceAtLeast(0) / 60000
        val workMinutes: Long get() = (minutes - pausedMin).coerceAtLeast(0)
    }

    /** Итог смены: оплата, чистыми (если заданы расходы), расходы, пробег, поездки. */
    data class ShiftTotals(val shift: Shift, val rides: Int, val toCheck: Int, val gross: Int, val net: Int?, val paidKm: Double)

    @Synchronized private fun archive(c: Context, s: Shift) {
        val a = try { JSONArray(p(c).getString("shifts", "[]")) } catch (_: Exception) { JSONArray() }
        val list = (0 until a.length()).map { a.getJSONObject(it) }.filter { it.optString("id") != s.id } +
            JSONObject().put("id", s.id).put("start", s.start).put("end", s.end).put("rent", s.rent).put("km", s.odometerKm).put("paused", s.pausedMin)
        p(c).edit().putString("shifts", JSONArray(list.takeLast(400)).toString()).apply()
    }

    /** Все смены: архив и текущая (end = 0), новые первыми. */
    fun shifts(c: Context): List<Shift> {
        val a = try { JSONArray(p(c).getString("shifts", "[]")) } catch (_: Exception) { JSONArray() }
        val list = (0 until a.length()).map { a.getJSONObject(it) }.map {
            Shift(it.getString("id"), it.getLong("start"), it.getLong("end"), it.optDouble("rent", 0.0), it.optDouble("km", 0.0), it.optLong("paused"))
        }.toMutableList()
        if (active(c).isNotEmpty()) list += Shift(active(c), p(c).getLong("started", 0), 0, rent(c), DriverPreferences.number(c, "shift_km"), pausedMinutes(c))
        // Смена, закрытая до появления архива (1.17), — восстанавливаем из того, что помнили.
        val last = p(c).getString("last", "").orEmpty()
        if (active(c).isEmpty() && last.isNotEmpty() && list.none { it.id == last } && p(c).getLong("ended", 0) > 0)
            list += Shift(last, p(c).getLong("started", 0), p(c).getLong("ended", 0), rent(c), DriverPreferences.number(c, "shift_km"))
        return list.sortedByDescending { it.start }
    }

    fun totals(c: Context, s: Shift, all: List<Ride> = rides(c)): ShiftTotals {
        val mine = all.filter { it.shift == s.id }
        val ok = mine.filter { it.confirmed }
        val gross = ok.sumOf { it.price }
        val paidKm = ok.sumOf { it.km }
        val net = if (DriverPreferences.costsReady(c) && ok.all { it.costsReady }) {
            val empty = (s.odometerKm - ok.sumOf { it.km + it.pickup }).coerceAtLeast(0.0)
            (ok.sumOf { it.net } - s.rent - empty * DriverPreferences.costs(c).perKm).toInt()
        } else null
        return ShiftTotals(s, ok.size, mine.size - ok.size, gross, net, paidKm)
    }
    // ---------- перерыв ----------

    fun paused(c: Context) = active(c).isNotEmpty() && p(c).getLong("paused_at", 0) > 0
    fun pause(c: Context) { if (active(c).isNotEmpty() && !paused(c)) p(c).edit().putLong("paused_at", System.currentTimeMillis()).apply() }
    fun resume(c: Context, at: Long = System.currentTimeMillis()) {
        val since = p(c).getLong("paused_at", 0)
        if (since <= 0) return
        p(c).edit().putLong("paused_ms", p(c).getLong("paused_ms", 0) + (at - since).coerceAtLeast(0)).putLong("paused_at", 0).apply()
    }
    /** Перерывы текущей (или последней) смены, вместе с идущим сейчас. */
    fun pausedMinutes(c: Context): Long {
        val since = p(c).getLong("paused_at", 0)
        val running = if (since > 0) System.currentTimeMillis() - since else 0
        return (p(c).getLong("paused_ms", 0) + running).coerceAtLeast(0) / 60000
    }

    fun elapsedMinutes(c: Context): Long = if (selected(c).isEmpty()) 0 else ((if (active(c).isEmpty()) p(c).getLong("ended", 0) else System.currentTimeMillis()) - p(c).getLong("started", 0)).coerceAtLeast(0) / 60000
    fun rent(c: Context) = p(c).getFloat("rent", 0f).toDouble()

    /**
     * «Начинать смену автоматически»: первая поездка без открытой смены открывает её.
     * Смену, забытую со вчера (старше 16 часов), закрываем временем её последней поездки.
     */
    fun autoStart(c: Context) {
        if (!DriverPreferences.flag(c, "auto_shift", true)) return
        val now = System.currentTimeMillis()
        if (active(c).isNotEmpty()) {
            if (now - p(c).getLong("started", now) < 16 * 3600_000L) return
            val last = rides(c).filter { it.shift == active(c) }.maxOfOrNull { it.at } ?: p(c).getLong("started", now)
            close(c, last)
        }
        start(c)
        DriverPreferences.set(c, "shift_km", 0.0)
    }
}
