package com.example.taxiradar

import android.content.Context
import android.location.Location
import org.json.JSONObject

/**
 * Аэропорт Кишинёва: сколько водителей Taxi Radar стоят в очереди у терминала
 * и ближайшие прилёты. «Я в очереди» телефон сообщает сам, пока радар
 * запущен и телефон у терминала, — без номера и имени, только ID устройства.
 */
object Airport {

    // Терминал аэропорта Кишинёва (RMO) и стоянка такси перед ним.
    private const val LAT = 46.9350
    private const val LON = 28.9330
    private const val RADIUS_M = 700f

    fun isAtAirport(lat: Double, lon: Double): Boolean {
        val d = FloatArray(1)
        Location.distanceBetween(lat, lon, LAT, LON, d)
        return d[0] < RADIUS_M
    }

    suspend fun ping(context: Context) {
        CommunityApi.post(context, "/api/airport/ping")
    }

    /**
     * status: scheduled / en-route / landed / cancelled.
     * approx — время посадки посчитано по положению самолёта в воздухе.
     */
    data class Flight(
        val flight: String, val from: String, val time: String, val status: String,
        val approx: Boolean, val delayed: Boolean
    )
    data class Status(val queue: Int, val flights: List<Flight>)

    suspend fun status(context: Context): Status? {
        val json = CommunityApi.post(context, "/api/airport/status") ?: return null
        if (!json.optBoolean("ok")) return null
        val arr = json.optJSONArray("flights")
        val flights = (0 until (arr?.length() ?: 0)).map { i ->
            val o: JSONObject = arr!!.getJSONObject(i)
            Flight(
                o.optString("flight"), o.optString("from"), o.optString("time"),
                o.optString("status"), o.optBoolean("approx"), o.optBoolean("delayed")
            )
        }
        return Status(json.optInt("queue"), flights)
    }

    /** Коды аэропортов частых рейсов в Кишинёв → город. */
    private val cities = mapOf(
        "IST" to "Стамбул", "SAW" to "Стамбул", "OTP" to "Бухарест", "BBU" to "Бухарест",
        "FCO" to "Рим", "CIA" to "Рим", "MXP" to "Милан", "BGY" to "Бергамо", "LIN" to "Милан",
        "LTN" to "Лондон", "LGW" to "Лондон", "STN" to "Лондон", "LHR" to "Лондон",
        "MUC" to "Мюнхен", "FRA" to "Франкфурт", "VIE" to "Вена", "WAW" to "Варшава", "WMI" to "Варшава",
        "BCN" to "Барселона", "TLV" to "Тель-Авив", "PRG" to "Прага", "BLQ" to "Болонья",
        "VCE" to "Венеция", "TSF" to "Тревизо", "CDG" to "Париж", "ORY" to "Париж", "BVA" to "Париж",
        "NCE" to "Ницца", "MAD" to "Мадрид", "VLC" to "Валенсия", "LIS" to "Лиссабон", "DUB" to "Дублин",
        "ATH" to "Афины", "AYT" to "Анталья", "HRG" to "Хургада", "SSH" to "Шарм-эш-Шейх", "DXB" to "Дубай",
        "BUD" to "Будапешт", "BER" to "Берлин", "DTM" to "Дортмунд", "EIN" to "Эйндховен",
        "BRU" to "Брюссель", "CRL" to "Брюссель", "TRN" to "Турин", "PSA" to "Пиза", "NAP" to "Неаполь",
        "CTA" to "Катания", "BRI" to "Бари", "VRN" to "Верона", "FLR" to "Флоренция", "GOA" to "Генуя",
        "ZRH" to "Цюрих", "GVA" to "Женева", "AMS" to "Амстердам", "CPH" to "Копенгаген",
        "LCA" to "Ларнака", "HER" to "Ираклион", "BOJ" to "Бургас", "VAR" to "Варна", "SOF" to "София",
        "RMI" to "Римини", "PMO" to "Палермо", "AHO" to "Альгеро", "IAS" to "Яссы", "CLJ" to "Клуж"
    )

    fun city(iata: String) = cities[iata] ?: iata
}
