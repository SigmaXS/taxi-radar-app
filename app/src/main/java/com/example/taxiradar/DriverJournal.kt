package com.example.taxiradar

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** Local journal. Guessed/computer-read receipts need confirmation before entering totals. */
object DriverJournal {
    /** id — номер поездки (тот же client_id на сервере), from/to — откуда и куда. */
    data class Ride(val id: String, val at: Long, val shift: String, val price: Int, val estimate: Int, val km: Double, val pickup: Double, val minutes: Int, val confirmed: Boolean, val area: String, val net: Int, val costsReady: Boolean = false, val from: String = "", val to: String = "",
                    /** "card", "cash" или "" — как платил клиент (с экрана Яндекс Про). */
                    val payment: String = "")
    private fun p(c: Context) = c.getSharedPreferences("driver_journal", Context.MODE_PRIVATE)
    @Synchronized fun rides(c: Context): List<Ride> = try {
        val a = JSONArray(p(c).getString("rides", "[]"))
        (0 until a.length()).map { i -> a.getJSONObject(i).let {
            Ride(it.getString("id"), it.getLong("at"), it.optString("shift"), it.getInt("price"), it.optInt("estimate"), it.optDouble("km"), it.optDouble("pickup"), it.optInt("minutes"), it.optBoolean("confirmed"), it.optString("area"), it.optInt("net"), it.optBoolean("costsReady"), it.optString("from"), it.optString("to"), it.optString("payment"))
        } }
    } catch (_: Exception) { emptyList() }

    @Synchronized private fun write(c: Context, list: List<Ride>) {
        val a = JSONArray()
        val old = rides(c)
        list.takeLast(2000).forEach { a.put(JSONObject().put("id", it.id).put("at", it.at).put("shift", it.shift).put("price", it.price).put("estimate", it.estimate).put("km", it.km).put("pickup", it.pickup).put("minutes", it.minutes).put("confirmed", it.confirmed).put("area", it.area).put("net", it.net).put("costsReady", it.costsReady).put("from", it.from).put("to", it.to).put("payment", it.payment)) }
        p(c).edit().putString("rides", a.toString()).apply()
        // Поездки закрытой смены поменялись (подтвердили, удалили) — обновить её сохранённые итоги.
        val oldById = old.associateBy { it.id }
        val changed = (list.filter { oldById[it.id] != it }.map { it.shift } +
            old.filter { o -> list.none { it.id == o.id } }.map { it.shift }).toSet()
        if (changed.isNotEmpty()) refreshSnapshots(c, changed, list)
    }
    @Synchronized fun add(c: Context, price: Int, estimate: Int, km: Double, pickup: Double, minutes: Int, area: String, confirmed: Boolean,
                          id: String = UUID.randomUUID().toString(), from: String = "", to: String = "", payment: String = ""): String {
        if (rides(c).any { it.id == id }) return id
        val net = OrderEconomics.calculate(price, km, pickup, minutes, 0.0, DriverPreferences.costs(c)).net
        write(c, rides(c) + Ride(id, System.currentTimeMillis(), active(c), price, estimate, km, pickup, minutes, confirmed, area, net, DriverPreferences.costsReady(c), from.take(120), to.take(120), payment))
        return id
    }
    @Synchronized fun confirm(c: Context, id: String, price: Int, km: Double, pickup: Double, minutes: Int, area: String) {
        write(c, rides(c).map { if (it.id == id) it.copy(price = price, km = km, pickup = pickup, minutes = minutes, area = area, confirmed = true, costsReady = DriverPreferences.costsReady(c), net = OrderEconomics.calculate(price, km, pickup, minutes, 0.0, DriverPreferences.costs(c)).net) else it })
    }
    @Synchronized fun remove(c: Context, id: String) {
        write(c, rides(c).filterNot { it.id == id })
        // Копия на сервере остаётся — запомним, чтобы «Мои поездки» её больше не показывали.
        p(c).edit().putStringSet("hidden", hidden(c) + id).apply()
    }
    fun hidden(c: Context): Set<String> = p(c).getStringSet("hidden", emptySet()).orEmpty().toSet()
    fun active(c: Context) = p(c).getString("active", "").orEmpty()
    fun selected(c: Context) = active(c).ifEmpty { p(c).getString("last", "").orEmpty() }
    fun start(c: Context) { if (active(c).isEmpty()) p(c).edit().putString("active", UUID.randomUUID().toString()).putLong("started", System.currentTimeMillis()).putLong("ended", 0).putLong("paused_ms", 0).putLong("paused_at", 0).putFloat("rent", DriverPreferences.number(c, "rent").toFloat())
        .putFloat("per_km", DriverPreferences.costs(c).perKm.toFloat()).putBoolean("auto_paused", false).apply()
        if (active(c).isNotEmpty()) TripLog.d("Смена открыта") }
    fun stop(c: Context) { close(c, System.currentTimeMillis()) }

    /** Закрыть смену и положить её в архив: начало, конец, аренда, пробег по одометру. */
    private fun close(c: Context, end: Long) {
        val id = active(c)
        if (paused(c)) resume(c, end)
        if (id.isNotEmpty()) archive(c, Shift(id, p(c).getLong("started", end), end, rent(c), DriverPreferences.number(c, "shift_km"), pausedMinutes(c), perKm(c)))
        p(c).edit().putString("last", id).putString("active", "").putLong("ended", end).apply()
        if (id.isNotEmpty()) { refreshSnapshots(c, setOf(id), rides(c)); TripLog.d("Смена закрыта") }
    }

    // ---------- архив смен ----------

    /** minutes — вся смена; pausedMin — перерывы; workMinutes — рабочее время без перерывов. */
    /** perKm — цена км на момент смены: поменяли расходы — прошлые смены не пересчитываются. */
    data class Shift(val id: String, val start: Long, val end: Long, val rent: Double, val odometerKm: Double, val pausedMin: Long = 0, val perKm: Double = 0.0) {
        val minutes: Long get() = ((if (end > 0) end else System.currentTimeMillis()) - start).coerceAtLeast(0) / 60000
        val workMinutes: Long get() = (minutes - pausedMin).coerceAtLeast(0)
    }

    /** Итог смены: оплата, чистыми (если заданы расходы), расходы, пробег, поездки. */
    data class ShiftTotals(val shift: Shift, val rides: Int, val toCheck: Int, val gross: Int, val net: Int?, val paidKm: Double,
                           val cash: Int = 0, val card: Int = 0)

    @Synchronized private fun archive(c: Context, s: Shift) {
        val a = try { JSONArray(p(c).getString("shifts", "[]")) } catch (_: Exception) { JSONArray() }
        val list = (0 until a.length()).map { a.getJSONObject(it) }.filter { it.optString("id") != s.id } +
            JSONObject().put("id", s.id).put("start", s.start).put("end", s.end).put("rent", s.rent).put("km", s.odometerKm).put("paused", s.pausedMin).put("perKm", s.perKm)
        p(c).edit().putString("shifts", JSONArray(list.takeLast(400)).toString()).apply()
    }

    /** Все смены: архив и текущая (end = 0), новые первыми. */
    fun shifts(c: Context): List<Shift> {
        val a = try { JSONArray(p(c).getString("shifts", "[]")) } catch (_: Exception) { JSONArray() }
        val list = (0 until a.length()).map { a.getJSONObject(it) }.map {
            Shift(it.getString("id"), it.getLong("start"), it.getLong("end"), it.optDouble("rent", 0.0), it.optDouble("km", 0.0), it.optLong("paused"), it.optDouble("perKm", 0.0))
        }.toMutableList()
        if (active(c).isNotEmpty()) list += Shift(active(c), p(c).getLong("started", 0), 0, rent(c), DriverPreferences.number(c, "shift_km"), pausedMinutes(c), perKm(c))
        // Смена, закрытая до появления архива (1.17), — восстанавливаем из того, что помнили.
        val last = p(c).getString("last", "").orEmpty()
        if (active(c).isEmpty() && last.isNotEmpty() && list.none { it.id == last } && p(c).getLong("ended", 0) > 0)
            list += Shift(last, p(c).getLong("started", 0), p(c).getLong("ended", 0), rent(c), DriverPreferences.number(c, "shift_km"))
        return list.sortedByDescending { it.start }
    }

    private fun perKm(c: Context) = p(c).getFloat("per_km", 0f).toDouble()

    /** Сохранённые итоги закрытых смен: из журнала старые поездки уходят, а итоги смены остаются. */
    private fun snapshots(c: Context): JSONObject = try { JSONObject(p(c).getString("snapshots", "{}")) } catch (_: Exception) { JSONObject() }

    private fun refreshSnapshots(c: Context, ids: Set<String>, all: List<Ride>) {
        val shifts = shifts(c).filter { it.id in ids && it.end > 0 }
        if (shifts.isEmpty()) return
        val snap = snapshots(c)
        for (s in shifts) {
            val t = live(c, s, all)
            snap.put(s.id, JSONObject().put("rides", t.rides).put("toCheck", t.toCheck).put("gross", t.gross).put("net", t.net ?: JSONObject.NULL)
                .put("paidKm", t.paidKm).put("cash", t.cash).put("card", t.card))
        }
        // Не копим бесконечно: только смены из архива.
        val keep = shifts(c).map { it.id }.toSet()
        val trimmed = JSONObject().also { o -> snap.keys().forEach { k -> if (k in keep) o.put(k, snap.get(k)) } }
        p(c).edit().putString("snapshots", trimmed.toString()).apply()
    }

    fun totals(c: Context, s: Shift, all: List<Ride> = rides(c)): ShiftTotals {
        val t = live(c, s, all)
        if (s.end <= 0) return t
        // Часть поездок старой смены уже ушла из журнала — берём сохранённые итоги.
        val o = snapshots(c).optJSONObject(s.id) ?: return t
        if (t.rides + t.toCheck >= o.optInt("rides") + o.optInt("toCheck")) return t
        return ShiftTotals(s, o.optInt("rides"), o.optInt("toCheck"), o.optInt("gross"), if (o.isNull("net")) null else o.optInt("net"),
            o.optDouble("paidKm", 0.0), o.optInt("cash"), o.optInt("card"))
    }

    private fun live(c: Context, s: Shift, all: List<Ride>): ShiftTotals {
        val mine = all.filter { it.shift == s.id }
        val ok = mine.filter { it.confirmed }
        val gross = ok.sumOf { it.price }
        val paidKm = ok.sumOf { it.km }
        val net = if (DriverPreferences.costsReady(c) && ok.all { it.costsReady }) {
            val empty = (s.odometerKm - ok.sumOf { it.km + it.pickup }).coerceAtLeast(0.0)
            (ok.sumOf { it.net } - s.rent - empty * (if (s.perKm > 0) s.perKm else DriverPreferences.costs(c).perKm)).toInt()
        } else null
        return ShiftTotals(s, ok.size, mine.size - ok.size, gross, net, paidKm,
            ok.filter { it.payment == "cash" }.sumOf { it.price }, ok.filter { it.payment == "card" }.sumOf { it.price })
    }
    // ---------- перерыв ----------

    fun paused(c: Context) = active(c).isNotEmpty() && p(c).getLong("paused_at", 0) > 0
    fun pause(c: Context) { if (active(c).isNotEmpty() && !paused(c)) p(c).edit().putLong("paused_at", System.currentTimeMillis()).apply() }
    fun resume(c: Context, at: Long = System.currentTimeMillis()) {
        val since = p(c).getLong("paused_at", 0)
        if (since <= 0) return
        p(c).edit().putLong("paused_ms", p(c).getLong("paused_ms", 0) + (at - since).coerceAtLeast(0)).putLong("paused_at", 0)
            .putBoolean("auto_paused", false).apply()
    }

    // ---------- радар выключен — смена на перерыве; не работал больше 6 часов — смена закрыта ----------

    private const val IDLE_CLOSE_MS = 6 * 3600_000L

    /** Радар работает: отметка раз в цикл обновления. */
    fun radarAlive(c: Context) { p(c).edit().putLong("alive_at", System.currentTimeMillis()).apply() }

    /** Последний признак работы: отметка радара, последняя поездка смены или её начало. */
    private fun lastActivity(c: Context): Long = maxOf(p(c).getLong("alive_at", 0), p(c).getLong("started", 0),
        rides(c).filter { it.shift == active(c) }.maxOfOrNull { it.at } ?: 0)

    /** Радар запустили: долго не работал — закрываем смену; выключали ненадолго — снимаем с перерыва. */
    fun radarStarted(c: Context) {
        val now = System.currentTimeMillis()
        if (active(c).isNotEmpty()) {
            val last = lastActivity(c)
            when {
                now - last > IDLE_CLOSE_MS -> { TripLog.d("Радар не работал ${(now - last) / 3600_000} ч — смену закрыли"); close(c, last) }
                p(c).getBoolean("auto_paused", false) -> resume(c, now)
                // Радар выгрузила система (без «стоп») — время простоя считаем перерывом.
                p(c).getLong("alive_at", 0) > 0 && now - p(c).getLong("alive_at", 0) > 10 * 60_000L && !paused(c) ->
                    p(c).edit().putLong("paused_ms", p(c).getLong("paused_ms", 0) + now - p(c).getLong("alive_at", 0)).apply()
            }
        }
        TripLog.d("Радар запущен")
        radarAlive(c)
    }

    /** Радар остановили — смена встаёт на перерыв (снимется сама при запуске или поездке). */
    fun radarStopped(c: Context) {
        if (active(c).isNotEmpty() && !paused(c)) { pause(c); p(c).edit().putBoolean("auto_paused", true).apply() }
        radarAlive(c)
        TripLog.d("Радар остановлен")
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
            val last = lastActivity(c)
            if (now - p(c).getLong("started", now) < 16 * 3600_000L && now - last < IDLE_CLOSE_MS) return
            close(c, last)
        }
        start(c)
        DriverPreferences.set(c, "shift_km", 0.0)
    }
}
