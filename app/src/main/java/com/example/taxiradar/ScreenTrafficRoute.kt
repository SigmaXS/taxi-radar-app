package com.example.taxiradar

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Independent route metrics; card minutes and kilometres are never substituted here. */
object ScreenTrafficRoute {
    data class Leg(val km: Double, val minutes: Double, val points: List<Pair<Double, Double>>)
    data class Result(val pickup: Leg, val trip: Leg, val calculatedAt: Long) {
        fun applyTo(offer: ScreenOfferParser.Offer) = offer.copy(pickupKm = pickup.km, tripKm = trip.km,
            pickupMinutes = pickup.minutes, tripMinutes = trip.minutes)
    }
    data class Reply(val route: Result? = null, val error: String = "route_unavailable")
    /**
     * [offer] — км с карточки: по ним сервер отличает настоящий адрес от одноимённой улицы в другом месте.
     * [tripOnly] — скриншот: водитель уже не там, подачу берём с карточки, считаем только поездку.
     */
    fun calculate(context: Context, lat: Double, lon: Double, addresses: List<String>, boarding: Double,
                  offer: ScreenOfferParser.Offer? = null, tripOnly: Boolean = false): Reply {
        val body = JSONObject().put("lat", lat).put("lon", lon).put("addresses", JSONArray(addresses.map(BoltAddressNormalizer::normalize))).put("boarding_minutes", boarding)
        if (offer != null) body.put("card_pickup_km", offer.pickupKm).put("card_trip_km", offer.tripKm)
        if (tripOnly) body.put("trip_only", true)
        val response = CommunityApi.postBlocking(context, "/api/offer-route", body, 18) ?: return Reply()
        return parse(response, offer.takeIf { tripOnly })
    }
    fun parse(json: JSONObject, cardPickup: ScreenOfferParser.Offer? = null): Reply {
        if (!json.optBoolean("ok")) return Reply(error = json.optString("code", "route_unavailable"))
        return try {
            // Любой сервис очереди (TomTom, HERE, Mapbox, Google) — формат ответа один.
            require(json.optString("provider").isNotBlank())
            fun leg(name: String): Leg {
                val obj = json.getJSONObject(name)
                val km = obj.getDouble("km"); val min = obj.getDouble("minutes")
                require(km.isFinite() && min.isFinite() && km >= 0 && min >= 0)
                return Leg(km, min, decodePolyline(obj.getString("polyline")))
            }
            val pickup = if (cardPickup != null && json.isNull("pickup")) {
                // Подача — с карточки; линии нет (на скриншоте водитель был в другом месте).
                val a = json.optJSONObject("points")?.optJSONObject("a")
                Leg(cardPickup.pickupKm, cardPickup.pickupMinutes, listOfNotNull(a?.let { it.getDouble("lat") to it.getDouble("lon") }))
            } else leg("pickup")
            Reply(Result(pickup, leg("trip"), json.getLong("calculated_at")))
        } catch (_: Exception) { Reply(error = "route_invalid") }
    }
    fun decodePolyline(encoded: String): List<Pair<Double, Double>> {
        require(encoded.length in 2..200000)
        var index = 0; var lat = 0; var lon = 0
        fun value(): Int {
            var result = 0; var shift = 0
            while (true) {
                require(index < encoded.length && shift <= 30)
                val b = encoded[index++].code - 63
                require(b in 0..63)
                result = result or ((b and 31) shl shift)
                if (b < 32) break
                shift += 5
            }
            return if ((result and 1) != 0) (result ushr 1).inv() else result ushr 1
        }
        val points = mutableListOf<Pair<Double, Double>>()
        while (index < encoded.length) {
            lat += value(); lon += value()
            val p = lat / 1e5 to lon / 1e5
            require(p.first in -90.0..90.0 && p.second in -180.0..180.0)
            points += p
        }
        require(points.size >= 2)
        return points
    }
}
