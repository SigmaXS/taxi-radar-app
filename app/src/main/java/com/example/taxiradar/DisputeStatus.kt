package com.example.taxiradar

import android.app.Activity
import android.content.Context
import android.content.Intent
import org.json.JSONObject

/** Статус обращения «Цена неверная»: что ответил администратор и что это значит для водителя. */
object DisputeStatus {
    fun text(c: Context, status: String): Pair<String, String>? {
        fun t(ru: String, ro: String) = DriverUi.t(c, ru, ro)
        return when (status) {
            "" -> null
            "received" -> t("✓ Принято", "✓ Primit") to t("разбираемся, где разошёлся расчёт", "verificăm unde a greșit estimarea")
            "fixed_address" -> t("✓ Адрес исправлен", "✓ Adresa corectată") to t("точка адреса поправлена — дальше цена будет верной у всех", "punctul adresei e corectat — prețul va fi corect pentru toți")
            "correct" -> t("✓ Расчёт верный", "✓ Estimarea e corectă") to t("разница из-за того, что случилось в поездке (ожидание, пробка, объезд)", "diferența vine din cursă (așteptare, trafic, ocolire)")
            "need_info" -> t("Нужно уточнение", "Avem nevoie de detalii") to t("пришлите скриншот карточки заказа или итога в чат поддержки", "trimiteți o captură a ofertei sau a totalului la suport")
            else -> t("✓ Проверено", "✓ Verificat") to t("администратор посмотрел обращение", "administratorul a verificat")
        }
    }

    /**
     * На главной: если по обращению появился ответ — короткое сообщение «По вашей поездке: …».
     * Проверяем не чаще раза в полчаса; уже показанные ответы не повторяем.
     */
    suspend fun checkNews(a: Activity, show: (String) -> Unit) {
        val prefs = a.getSharedPreferences("taxi_radar_prefs", Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        if (now - prefs.getLong("dispute_checked_at", 0) < 30 * 60_000L) return
        prefs.edit().putLong("dispute_checked_at", now).apply()
        val trips = CommunityApi.post(a, "/api/trips/mine")?.optJSONArray("trips") ?: return
        val seen = try { JSONObject(prefs.getString("dispute_seen", "{}")) } catch (_: Exception) { JSONObject() }
        var news: String? = null
        for (i in 0 until trips.length()) {
            val t = trips.getJSONObject(i)
            val st = t.optString("dispute_status")
            if (st.isEmpty() || st == "received" || seen.optString(t.optString("id")) == st) continue
            seen.put(t.optString("id"), st)
            if (news == null) news = DriverUi.t(a, "По вашей поездке: ", "Despre cursa dvs.: ") + text(a, st)!!.first
        }
        prefs.edit().putString("dispute_seen", seen.toString()).apply()
        news?.let(show)
    }

    fun open(a: Activity) = a.startActivity(Intent(a, MyTripsActivity::class.java))
}
