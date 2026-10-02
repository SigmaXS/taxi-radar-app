package com.example.taxiradar

import android.content.Context
import org.json.JSONObject

/**
 * Места водителей на карте: где дешевле поесть, хорошая мойка, заправка,
 * кофе, туалет, шиномонтаж, где постоять. Добавляют сами водители,
 * остальные отмечают «советую / не советую».
 */
object Places {

    data class Type(val key: String, val emoji: String, val label: Int, val color: Int)

    val TYPES = listOf(
        Type("food", "🍔", R.string.place_food, 0xFFE65100.toInt()),
        Type("coffee", "☕", R.string.place_coffee, 0xFF6D4C41.toInt()),
        Type("wash", "🧽", R.string.place_wash, 0xFF0288D1.toInt()),
        Type("fuel", "⛽", R.string.place_fuel, 0xFF2E7D32.toInt()),
        Type("tire", "🛞", R.string.place_tire, 0xFF455A64.toInt()),
        Type("wc", "🚻", R.string.place_wc, 0xFF5E35B1.toInt()),
        Type("parking", "🅿️", R.string.place_parking, 0xFF1565C0.toInt())
    )

    fun type(key: String) = TYPES.firstOrNull { it.key == key }

    data class Place(
        val id: Long, val type: String, val name: String, val note: String,
        val lat: Double, val lon: Double, val up: Int, val down: Int,
        val mine: Boolean, val vote: Int
    )

    suspend fun list(context: Context, lat: Double, lon: Double): List<Place>? {
        val json = CommunityApi.post(context, "/api/places/list", JSONObject().put("lat", lat).put("lon", lon)) ?: return null
        if (!json.optBoolean("ok")) return null
        val arr = json.optJSONArray("places") ?: return emptyList()
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            Place(
                o.getLong("id"), o.getString("type"), o.optString("name"), o.optString("note"),
                o.getDouble("lat"), o.getDouble("lon"), o.optInt("up"), o.optInt("down"),
                o.optBoolean("mine"), o.optInt("vote")
            )
        }
    }

    /** null — нет связи; иначе ok и текст ошибки, если не приняли. */
    suspend fun add(context: Context, type: String, name: String, note: String, lat: Double, lon: Double): Pair<Boolean, String>? {
        val json = CommunityApi.post(
            context, "/api/places/add",
            JSONObject().put("type", type).put("name", name).put("note", note).put("lat", lat).put("lon", lon)
        ) ?: return null
        return json.optBoolean("ok") to json.optString("message")
    }

    /** 1 — советую, -1 — не советую, 0 — убрать свой голос. */
    suspend fun vote(context: Context, id: Long, vote: Int): Boolean =
        CommunityApi.post(context, "/api/places/vote", JSONObject().put("id", id).put("vote", vote))?.optBoolean("ok") == true

    suspend fun delete(context: Context, id: Long): Boolean =
        CommunityApi.post(context, "/api/places/delete", JSONObject().put("id", id))?.optBoolean("ok") == true
}
