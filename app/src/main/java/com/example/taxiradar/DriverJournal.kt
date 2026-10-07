package com.example.taxiradar

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** Local journal. Guessed/computer-read receipts need confirmation before entering totals. */
object DriverJournal {
    data class Ride(val id: String, val at: Long, val shift: String, val price: Int, val estimate: Int, val km: Double, val pickup: Double, val minutes: Int, val confirmed: Boolean, val area: String, val net: Int, val costsReady: Boolean = false)
    private fun p(c: Context) = c.getSharedPreferences("driver_journal", Context.MODE_PRIVATE)
    @Synchronized fun rides(c: Context): List<Ride> = try {
        val a = JSONArray(p(c).getString("rides", "[]"))
        (0 until a.length()).map { i -> a.getJSONObject(i).let {
            Ride(it.getString("id"), it.getLong("at"), it.optString("shift"), it.getInt("price"), it.optInt("estimate"), it.optDouble("km"), it.optDouble("pickup"), it.optInt("minutes"), it.optBoolean("confirmed"), it.optString("area"), it.optInt("net"), it.optBoolean("costsReady"))
        } }
    } catch (_: Exception) { emptyList() }

    @Synchronized private fun write(c: Context, list: List<Ride>) {
        val a = JSONArray()
        list.takeLast(500).forEach { a.put(JSONObject().put("id", it.id).put("at", it.at).put("shift", it.shift).put("price", it.price).put("estimate", it.estimate).put("km", it.km).put("pickup", it.pickup).put("minutes", it.minutes).put("confirmed", it.confirmed).put("area", it.area).put("net", it.net).put("costsReady", it.costsReady)) }
        p(c).edit().putString("rides", a.toString()).apply()
    }
    @Synchronized fun add(c: Context, price: Int, estimate: Int, km: Double, pickup: Double, minutes: Int, area: String, confirmed: Boolean): String {
        val id = UUID.randomUUID().toString()
        val net = OrderEconomics.calculate(price, km, pickup, minutes, 0.0, DriverPreferences.costs(c)).net
        write(c, rides(c) + Ride(id, System.currentTimeMillis(), active(c), price, estimate, km, pickup, minutes, confirmed, area, net, DriverPreferences.costsReady(c)))
        return id
    }
    @Synchronized fun confirm(c: Context, id: String, price: Int, km: Double, pickup: Double, minutes: Int, area: String) {
        write(c, rides(c).map { if (it.id == id) it.copy(price = price, km = km, pickup = pickup, minutes = minutes, area = area, confirmed = true, costsReady = DriverPreferences.costsReady(c), net = OrderEconomics.calculate(price, km, pickup, minutes, 0.0, DriverPreferences.costs(c)).net) else it })
    }
    @Synchronized fun remove(c: Context, id: String) { write(c, rides(c).filterNot { it.id == id }) }
    fun active(c: Context) = p(c).getString("active", "").orEmpty()
    fun selected(c: Context) = active(c).ifEmpty { p(c).getString("last", "").orEmpty() }
    fun start(c: Context) { if (active(c).isEmpty()) p(c).edit().putString("active", UUID.randomUUID().toString()).putLong("started", System.currentTimeMillis()).putLong("ended", 0).putFloat("rent", DriverPreferences.number(c, "rent").toFloat()).apply() }
    fun stop(c: Context) { p(c).edit().putString("last", active(c)).putString("active", "").putLong("ended", System.currentTimeMillis()).apply() }
    fun elapsedMinutes(c: Context): Long = if (selected(c).isEmpty()) 0 else ((if (active(c).isEmpty()) p(c).getLong("ended", 0) else System.currentTimeMillis()) - p(c).getLong("started", 0)).coerceAtLeast(0) / 60000
    fun rent(c: Context) = p(c).getFloat("rent", 0f).toDouble()
}
