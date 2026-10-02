package com.example.taxiradar

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.util.Log
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * Принятый заказ от экрана «Поехали» до «Заказ завершён». Нужен для двух вещей:
 *  - общая база адресов: где реально стоял телефон при посадке (точка А) и
 *    высадке (точка Б) — точнее Геокодера, вплоть до подъезда. Только у тех,
 *    кто включил «Предупреждать в дороге» (GPS), и без привязки к водителю;
 *  - точность цены: наш расчёт с карточки, навигатор перед «Поехали» и что
 *    Яндекс Про показал в конце поездки.
 *
 * Как выглядит поездка в Яндекс Про (по реальному заказу):
 *  - ожидание у А: «Укажите маршрут | 7 км · 15 мин | … | Поехали»;
 *  - в пути: «Б | <адрес Б> | Завершить | Стоимость по счётчику … | 31 L»;
 *  - пассажир может сменить Б или добавить заезд: «Пассажир добавил или
 *    изменил промежуточную точку»;
 *  - конец: «Оплачено картой | 33,1 MDL | Заказ завершён | Доход | 33,1 MDL».
 */
object TripTracker {

    private data class Trip(
        val route: List<String>,
        val startedAt: Long,
        val navMin: Int,
        var reportId: String? = null,
        /** Адрес Б, который сейчас на экране поездки (пассажир может его сменить). */
        var currentB: String,
        var routeChanged: Boolean = false,
        /** Последнее показание счётчика «31 L» — запасная цена, если конец не разобрали. */
        var meter: Int? = null
    )

    @Volatile
    private var active: Trip? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var lastLogged = ""
    private var lastLoggedAt = 0L

    // Телефон дальше этого от найденного адреса — водитель не у адреса
    // (завершил раньше, нажал не там). Такую точку в базу не пишем.
    private const val NEAR_ADDRESS_KM = 1.0

    /** Экран ожидания у А с выбором маршрута до Б — водитель у пассажира. */
    fun onTripStarted(
        context: Context, route: List<String>, tariff: String, surge: Int,
        estPrice: Int, estKm: Double, estMin: Double, navKm: Double, navMin: Int, navPrice: Int
    ) {
        val app = context.applicationContext
        val trip = Trip(route, System.currentTimeMillis(), navMin, currentB = route.last())
        active = trip
        Log.d("TRIP", "Поездка началась: $route, наш расчёт $estPrice L, навигатор $navPrice L")
        withLocation(app) { loc -> if (loc != null) scope.launch { learn(app, route.first(), loc, trustWithoutReference = true) } }
        scope.launch {
            val r = CommunityApi.postBlocking(
                app, "/api/trips/start", JSONObject()
                    .put("tariff", tariff).put("stops", route.size - 2).put("surge", surge)
                    .put("est_price", estPrice).put("est_km", estKm).put("est_min", estMin)
                    .put("nav_km", navKm).put("nav_min", navMin).put("nav_price", navPrice)
            )
            trip.reportId = r?.optString("id")?.takeIf { it.isNotBlank() }
        }
    }

    private val endWords = listOf(
        "заказ завершён", "заказ завершен", "поездка завершена",
        "comanda finalizată", "comandă finalizată", "comanda a fost finalizată", "cursa s-a încheiat"
    )
    private val changeWords = listOf("изменил промежуточную", "добавил промежуточную", "изменил адрес", "a modificat", "a adăugat")
    private val priceRegex = Regex("""(?i)(\d{1,5})(?:[.,](\d{1,2}))?\s*(?:MDL|lei|лей|L)\b""")
    private val meterRegex = Regex("""^(\d{1,5})(?:[.,]\d{1,2})?\s*(?:L|MDL|lei)$""")
    // Строки на экране поездки, которые не адрес.
    private val notAddress = Regex("""(?i)^(я здесь|уточнить|завершить|звонок|ожидание|поехали|б|\d+|[\d:]+|.*\d\s*(км|м|мин|km|min)\.?)$""")

    /** Любой экран Яндекс Про, кроме карточки заказа. [pkg] — чьё окно сейчас на экране. */
    fun onScreen(context: Context, pkg: String, texts: List<String>) {
        val trip = active ?: return
        // Свернули Яндекс Про — чужие экраны не читаем и не пишем в лог.
        if (!pkg.contains("taximeter", true) && !pkg.contains("yandex", true)) return
        val now = System.currentTimeMillis()
        if (now - trip.startedAt > 3 * 3600_000L) {
            active = null
            return
        }
        val joined = texts.joinToString(" | ")
        if (joined != lastLogged && now - lastLoggedAt > 2000) {
            lastLogged = joined
            lastLoggedAt = now
            joined.chunked(3000).forEachIndexed { i, part -> Log.d("TRIP_DEBUG", "Экран поездки [${i + 1}]: $part") }
        }

        val lines = texts.flatMap { it.split("\n") }.map { it.trim() }.filter { it.isNotEmpty() }
        val lower = lines.map { it.lowercase() }
        if (lower.any { l -> changeWords.any { l.contains(it) } }) trip.routeChanged = true
        // «Б» и следующая строка-адрес — куда едем сейчас.
        for (i in 0 until lines.size - 1) {
            if (lines[i] != "Б" && lines[i] != "B") continue
            val next = lines[i + 1]
            if (next.length > 5 && next.any { it.isLetter() } && !notAddress.matches(next)) {
                if (!next.equals(trip.currentB, true) && !RouteFareCalculator.addressKey(trip.route.last()).equals(RouteFareCalculator.addressKey(next), true)) {
                    Log.d("TRIP", "Адрес Б сменился: «${trip.currentB}» → «$next»")
                    trip.currentB = next
                    trip.routeChanged = true
                }
                break
            }
        }
        lines.firstNotNullOfOrNull { meterRegex.find(it) }?.groupValues?.get(1)?.toIntOrNull()?.let { trip.meter = it }

        if (now - trip.startedAt < 30_000) return
        if (lower.none { l -> endWords.any { l.contains(it) } }) return
        val price = lines.firstNotNullOfOrNull { l ->
            priceRegex.find(l)?.let { m ->
                val whole = m.groupValues[1].toIntOrNull() ?: return@let null
                if (m.groupValues[2].isNotEmpty() && m.groupValues[2].padEnd(2, '0').toInt() >= 50) whole + 1 else whole
            }
        } ?: trip.meter
        active = null
        Log.d("TRIP", "Поездка закончилась: Яндекс $price L, маршрут менялся: ${trip.routeChanged}, Б = «${trip.currentB}»")

        val app = context.applicationContext
        val elapsedMin = (now - trip.startedAt) / 60_000.0
        val finish: (String?) -> Unit = { note ->
            scope.launch {
                val id = trip.reportId ?: return@launch
                CommunityApi.postBlocking(
                    app, "/api/trips/finish", JSONObject()
                        .put("id", id).put("real_price", price ?: JSONObject.NULL)
                        .put("note", note ?: JSONObject.NULL)
                )
            }
        }
        val changedNote = if (trip.routeChanged) "маршрут менялся" else null
        val located = withLocation(app) { loc ->
            if (loc == null) {
                finish(changedNote)
                return@withLocation
            }
            scope.launch {
                // Адрес Яндекс не знает — верим точке, только если ехали хотя бы
                // половину прогноза и маршрут не менялся (иначе могли высадить раньше).
                val trust = !trip.routeChanged && elapsedMin >= trip.navMin * 0.5
                val near = learn(app, trip.currentB, loc, trustWithoutReference = trust)
                finish(changedNote ?: if (near == false) "завершён не у Б" else null)
            }
        }
        if (!located) finish(changedNote)
    }

    /**
     * Записывает точку адреса. Если адрес находится (у нас или у Яндекса), а
     * телефон от него дальше [NEAR_ADDRESS_KM], — не записывает: водитель не у
     * адреса. Возвращает true/false (рядом/далеко) или null, если сравнить не с чем.
     */
    private fun learn(context: Context, address: String, loc: Location, trustWithoutReference: Boolean): Boolean? {
        val key = RouteFareCalculator.addressKey(address)
        if (key.length < 4) return null
        val ref = RouteFareCalculator.locate(context, address)
        val near = ref?.let { RouteFareCalculator.distanceKm(it.first, it.second, loc.latitude, loc.longitude) <= NEAR_ADDRESS_KM }
        if (near == false || (near == null && !trustWithoutReference)) {
            Log.d("TRIP", "Точку «$key» не пишем: ${if (near == false) "телефон далеко от адреса" else "не с чем сравнить"}")
            return near
        }
        val r = CommunityApi.postBlocking(
            context, "/api/geocode/learn",
            JSONObject().put("q", key).put("lat", loc.latitude).put("lon", loc.longitude)
        )
        Log.d("TRIP", "Точка адреса «$key»: ${r?.optString("result")}")
        return near
    }

    /**
     * Точная точка GPS — только с разрешения водителя. false — точку не берём
     * вовсе; иначе [block] получит точку или null, если она неточная.
     */
    @SuppressLint("MissingPermission")
    private fun withLocation(context: Context, block: (Location?) -> Unit): Boolean {
        if (!RoadReports.alertsEnabled(context)) return false
        if (context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return false
        if (!AppConfig.load(context).sharedGeocoder) return false
        return try {
            LocationServices.getFusedLocationProviderClient(context)
                .getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                .addOnSuccessListener { loc ->
                    // Неточная точка (в здании, у стены) хуже, чем никакой.
                    block(loc?.takeIf { it.hasAccuracy() && it.accuracy <= 40f })
                }
                .addOnFailureListener { block(null) }
            true
        } catch (e: Exception) {
            Log.e("TRIP", "GPS: ${e.message}")
            false
        }
    }
}
