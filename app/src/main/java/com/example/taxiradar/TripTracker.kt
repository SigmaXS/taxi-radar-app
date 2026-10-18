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

    // ---------- способ оплаты: «Оплата картой» / «Наличные» на экранах заказа ----------

    /** "card" или "cash" и когда видели. */
    @Volatile private var payment: Pair<String, Long>? = null
    private val cardExact = setOf("оплата картой", "картой", "card", "cu cardul", "plata cu cardul", "card payment")
    private val cashExact = setOf("наличные", "наличными", "оплата наличными", "numerar", "cash", "plata cash")

    /** Смотрим любой экран Яндекс Про. Настройки оплаты водителя («Наличными · Действует до…») не путаем с заказом. */
    fun notePayment(lines: List<String>) {
        val lower = lines.map { it.trim().lowercase() }
        if (lower.any { it.contains("действует до") || it.contains("можно менять") || it.contains("тарифы и опции") || it.contains("вы меняли оплату") || it.contains("valabil până") ||
                it.contains("valid until") || it.contains("active until") || it.contains("changed payment") || it.contains("cash or card") }) return
        val now = System.currentTimeMillis()
        when {
            lower.any { it in cardExact || it.contains("оплата картой") || it.contains("оплачено картой") || it.contains("cu cardul") || it.contains("paid by card") || it.contains("card payment") } -> payment = "card" to now
            lower.any { it in cashExact || it.contains("оплата наличными") || it.contains("получите наличными") || it.contains("collect in cash") || it.contains("încasați numerar") || it.contains("incasati numerar") } -> payment = "cash" to now
        }
    }

    /** Идёт поездка (видели начало, конца ещё не было). */
    fun inTrip(): Boolean = active != null

    /** Способ оплаты текущего заказа, если видели его недавно (за [withinMs]). */
    fun recentPayment(withinMs: Long = 2 * 3600_000L): String? =
        payment?.takeIf { System.currentTimeMillis() - it.second < withinMs }?.first

    /** Новая карточка заказа — оплата прошлого клиента больше не наша. */
    fun resetPayment() { payment = null }

    // ---------- последняя карточка заказа: время «взяли заказ» и откуда-куда ----------

    private data class Card(var at: Long, var from: String = "", var to: String = "", var tariff: String = "")
    @Volatile private var lastCard: Card? = null

    /** Карточка заказа на экране. Последний её показ ≈ момент, когда заказ взяли. */
    fun noteCardSeen() {
        val now = System.currentTimeMillis()
        val c = lastCard
        // Та же карточка перерисовывается — время обновляем; новая — начинаем заново.
        if (c != null && now - c.at < 60_000) c.at = now else lastCard = Card(now)
    }

    fun noteCardRoute(from: String, to: String, tariff: String) {
        lastCard?.let { it.from = from.take(120); it.to = to.take(120); it.tariff = tariff }
    }

    // Цена с экрана завершения в любом порядке: «55,7 MDL», «55.7 L», «MDL 55.7», «lei 55».
    // Строки с «+» (личная цель «+ MDL 460.0») пропускаем.
    private val priceBefore = Regex("""(?iu)(?:^|\s)(?:MDL|lei|лей|L)\s*(\d{1,5})(?:[.,](\d{1,2}))?\b""")
    private fun parsePrice(lines: List<String>): Int? = lines.filterNot { it.trimStart().startsWith("+") }.firstNotNullOfOrNull { l ->
        (priceRegex.find(l) ?: priceBefore.find(l))?.let { m ->
            val whole = m.groupValues[1].toIntOrNull() ?: return@let null
            if (m.groupValues[2].isNotEmpty() && m.groupValues[2].padEnd(2, '0').toInt() >= 50) whole + 1 else whole
        }
    }

    // Поездка, начало которой радар не заметил (без Б, короткая, с заездами): запишем по «Заказ завершён».
    private var lastOrphan = ""
    private var lastOrphanAt = 0L

    private data class Trip(
        val route: List<String>,
        val startedAt: Long,
        val navMin: Int,
        val estPrice: Int,
        val navKm: Double,
        val pickupKm: Double,
        /** Номер поездки: им же помечена запись в журнале смены и строка на сервере. */
        val key: String = java.util.UUID.randomUUID().toString(),
        /** Адрес Б, который сейчас на экране поездки (пассажир может его сменить). */
        var currentB: String,
        var routeChanged: Boolean = false,
        /** Последнее показание счётчика «31 L» — запасная цена, если конец не разобрали. */
        var meter: Int? = null
    )

    @Volatile
    private var activeTrip: Trip? = null
    private var restored = false
    private var prefsCtx: Context? = null

    /** Текущая поездка. Хранится и в настройках: телефон мог выгрузить радар посреди поездки. */
    private var active: Trip?
        get() {
            if (!restored) prefsCtx?.let { restore(it) }
            return activeTrip
        }
        set(v) { activeTrip = v; restored = true; prefsCtx?.let { save(it, v) } }

    private fun prefs(c: Context) = c.getSharedPreferences("trip_active", Context.MODE_PRIVATE)

    private fun save(c: Context, t: Trip?) {
        val e = prefs(c).edit()
        if (t == null) { e.clear().apply(); return }
        e.putString("json", JSONObject()
            .put("route", org.json.JSONArray(t.route)).put("startedAt", t.startedAt).put("navMin", t.navMin)
            .put("estPrice", t.estPrice).put("navKm", t.navKm).put("pickupKm", t.pickupKm).put("key", t.key)
            .put("currentB", t.currentB).put("routeChanged", t.routeChanged).put("meter", t.meter ?: -1)
            .toString()).apply()
    }

    private fun restore(c: Context) {
        restored = true
        val j = runCatching { JSONObject(prefs(c).getString("json", null) ?: return) }.getOrNull() ?: return
        val r = j.optJSONArray("route") ?: return
        activeTrip = runCatching {
            Trip((0 until r.length()).map { r.getString(it) }, j.getLong("startedAt"), j.getInt("navMin"), j.getInt("estPrice"),
                j.getDouble("navKm"), j.getDouble("pickupKm"), j.getString("key"), j.getString("currentB"),
                j.optBoolean("routeChanged"), j.optInt("meter", -1).takeIf { it >= 0 })
        }.getOrNull()
        TripLog.d("Восстановили поездку после перезапуска: ${activeTrip?.route}")
    }

    /** Помним контекст, чтобы восстановить поездку после перезапуска радара. */
    private fun bind(context: Context) { if (prefsCtx == null) { prefsCtx = context.applicationContext; TripLog.init(context) } }

    /**
     * Конец поездки радар не увидел (начался следующий заказ, прошло 3 часа):
     * пишем её в смену черновиком по счётчику или расчёту и закрываем строку на сервере,
     * чтобы она не висела «не завершена».
     */
    private fun closeUnseen(app: Context, trip: Trip, now: Long) {
        val endAt = minOf(now, trip.startedAt + (trip.navMin.coerceAtLeast(5) * 2) * 60_000L)
        val min = ((endAt - trip.startedAt) / 60_000.0)
        DriverJournal.add(app, trip.meter ?: trip.estPrice, trip.estPrice, trip.navKm, trip.pickupKm,
            min.toInt().coerceAtLeast(1), "", confirmed = false,
            id = trip.key, from = trip.route.first(), to = trip.currentB,
            payment = recentPayment().orEmpty())
        Outbox.send(
            app, "/api/trips/finish", JSONObject()
                .put("client_id", trip.key).put("at", endAt)
                .put("real_price", JSONObject.NULL).put("real_min", JSONObject.NULL)
                .put("note", "конец не увиден")
        )
        TripLog.d("Конец поездки не видели — записали черновиком: ${trip.route}")
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var lastLogged = ""
    private var lastLoggedAt = 0L
    private var lastTripLogAt = 0L

    // Телефон дальше этого от найденного адреса — водитель не у адреса
    // (завершил раньше, нажал не там). Такую точку в базу не пишем.
    private const val NEAR_ADDRESS_KM = 1.0

    /** Экран ожидания у А с выбором маршрута до Б — водитель у пассажира. */
    fun onTripStarted(
        context: Context, route: List<String>, tariff: String, surge: Int,
        estPrice: Int, estKm: Double, estMin: Double, navKm: Double, navMin: Int, navPrice: Int
    ) {
        val app = context.applicationContext
        bind(app)
        val prev = active
        val nowMs = System.currentTimeMillis()
        if (prev != null && nowMs - prev.startedAt < 3 * 3600_000L) {
            if (prev.route == route) return
            // Тот же пассажир у той же точки А, просто другой Б (клиент поменял адрес) — та же поездка.
            if (nowMs - prev.startedAt < 20 * 60_000L &&
                RouteFareCalculator.addressKey(prev.route.first()).equals(RouteFareCalculator.addressKey(route.first()), true)) {
                if (!prev.currentB.equals(route.last(), true)) {
                    prev.currentB = route.last(); prev.routeChanged = true; active = prev
                }
                return
            }
            closeUnseen(app, prev, nowMs)
        } else if (prev != null) {
            closeUnseen(app, prev, nowMs)
        }
        DriverJournal.autoStart(app)
        DriverJournal.resume(app)
        val trip = Trip(route, System.currentTimeMillis(), navMin, estPrice, navKm, OrderPreview.current()?.takeIf { it.route == route }?.pickup ?: 0.0, currentB = route.last())
        active = trip
        TripLog.d("Поездка началась: $route, наш расчёт $estPrice L, навигатор $navPrice L")
        withLocation(app) { loc -> if (loc != null) scope.launch { learn(app, route.first(), loc, trustWithoutReference = true) } }
        // Через очередь: при плохой связи начало поездки дошлётся позже, а не потеряется.
        Outbox.send(
            app, "/api/trips/start", JSONObject()
                .put("client_id", trip.key).put("at", trip.startedAt)
                .put("tariff", tariff).put("stops", route.size - 2).put("surge", surge)
                .put("est_price", estPrice).put("est_km", estKm).put("est_min", estMin)
                .put("nav_km", navKm).put("nav_min", navMin).put("nav_price", navPrice)
                // Для «Моих поездок» водителя: откуда и куда (сервер хранит 60 дней).
                .put("from", route.first().take(120)).put("to", route.last().take(120))
        )
    }

    private val endWords = listOf(
        "заказ завершён", "заказ завершен", "поездка завершена",
        "comanda finalizată", "comandă finalizată", "comanda a fost finalizată", "cursa s-a încheiat",
        "order completed", "trip completed", "ride completed"
    )
    private val changeWords = listOf("изменил промежуточную", "добавил промежуточную", "изменил адрес", "a modificat", "a adăugat")
    private val priceRegex = Regex("""(?iu)(\d{1,5})(?:[.,](\d{1,2}))?\s*(?:MDL|lei|лей|L)\b""")
    private val meterRegex = Regex("""^(\d{1,5})(?:[.,]\d{1,2})?\s*(?:L|MDL|lei)$""")
    // Строки на экране поездки, которые не адрес.
    private val notAddress = Regex("""(?iu)^(я здесь|уточнить|завершить|звонок|ожидание|поехали|б|\d+|[\d:]+|.*\d\s*(км|м|мин|km|min)\.?)$""")

    /** Любой экран Яндекс Про, кроме карточки заказа. [pkg] — чьё окно сейчас на экране. */
    fun onScreen(context: Context, pkg: String, texts: List<String>) {
        bind(context)
        val trip = active ?: run { onOrphanEnd(context, pkg, texts); return }
        // Свернули Яндекс Про — чужие экраны не читаем и не пишем в лог.
        if (!pkg.contains("taximeter", true) && !pkg.contains("yandex", true)) return
        val now = System.currentTimeMillis()
        if (now - trip.startedAt > 3 * 3600_000L) {
            active = null
            closeUnseen(context.applicationContext, trip, now)
            return
        }
        val joined = texts.joinToString(" | ")
        if (joined != lastLogged && now - lastLoggedAt > 2000) {
            lastLogged = joined
            lastLoggedAt = now
            joined.chunked(3000).forEachIndexed { i, part -> Log.d("TRIP_DEBUG", "Экран поездки [${i + 1}]: $part") }
            // В журнал радара — коротко и не чаще раза в 15 с: видно, какой экран был вместо «Заказ завершён».
            if (now - lastTripLogAt > 15_000) { lastTripLogAt = now; TripLog.d("Экран поездки: " + joined.take(300)) }
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
                    TripLog.d("Адрес Б сменился: «${trip.currentB}» → «$next»")
                    trip.currentB = next
                    trip.routeChanged = true
                    prefsCtx?.let { save(it, trip) }
                }
                break
            }
        }
        val meterNow = lines.firstNotNullOfOrNull { meterRegex.find(it) }?.groupValues?.get(1)?.toIntOrNull()
        if (meterNow != null && meterNow != trip.meter) { trip.meter = meterNow; prefsCtx?.let { save(it, trip) } }

        if (now - trip.startedAt < 30_000) return
        if (lower.none { l -> endWords.any { l.contains(it) } }) return
        val finalPrice = parsePrice(lines)
        val price = finalPrice ?: trip.meter
        active = null
        lastCard = null
        // Экран «Заказ завершён» ещё повисит — страховка «поездки без начала» не должна записать её второй раз.
        lastOrphan = "${price ?: trip.estPrice}|"; lastOrphanAt = now
        TripLog.d("Поездка закончилась: Яндекс $price L, маршрут менялся: ${trip.routeChanged}, Б = «${trip.currentB}»")

        val app = context.applicationContext
        val elapsedMin = (now - trip.startedAt) / 60_000.0
        // Only a final amount on the completion screen is auto-confirmed; estimates stay drafts.
        DriverJournal.add(app, price ?: trip.estPrice, trip.estPrice, trip.navKm, trip.pickupKm,
            elapsedMin.toInt().coerceAtLeast(1), "", confirmed = finalPrice != null,
            id = trip.key, from = trip.route.first(), to = trip.currentB,
            payment = recentPayment(now - trip.startedAt + 30 * 60_000L).orEmpty())
        val finish: (String?) -> Unit = { note ->
            Outbox.send(
                app, "/api/trips/finish", JSONObject()
                    .put("client_id", trip.key).put("at", now)
                    .put("real_price", price ?: JSONObject.NULL)
                    .put("real_min", elapsedMin)
                    .put("note", note ?: JSONObject.NULL)
            )
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
            TripLog.d("Точку «$key» не пишем: ${if (near == false) "телефон далеко от адреса" else "не с чем сравнить"}")
            return near
        }
        val r = CommunityApi.postBlocking(
            context, "/api/geocode/learn",
            JSONObject().put("q", key).put("lat", loc.latitude).put("lon", loc.longitude)
        )
        TripLog.d("Точка адреса «$key»: ${r?.optString("result")}")
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

    /**
     * «Заказ завершён» с суммой, а начала поездки радар не видел (заказ без Б, короткий,
     * с заездами, радар включили посреди поездки). Записываем в смену хотя бы сумму —
     * итоговую сумму подтверждаем автоматически; водитель может исправить её в «Моих поездках».
     */
    private fun onOrphanEnd(context: Context, pkg: String, texts: List<String>) {
        if (!pkg.contains("taximeter", true) && !pkg.contains("yandex", true)) return
        val lines = texts.flatMap { it.split("\n") }.map { it.trim() }.filter { it.isNotEmpty() }
        val lower = lines.map { it.lowercase() }
        if (lower.none { l -> endWords.any { l.contains(it) } }) return
        val price = parsePrice(lines) ?: run { TripLog.d("Экран завершения без цены: " + lines.take(12).joinToString(" | ").take(300)); return }
        val now = System.currentTimeMillis()
        // Экран «Заказ завершён» висит и перерисовывается — одна запись на заказ.
        val key = "$price|" + lines.take(6).joinToString("|")
        if (now - lastOrphanAt < 10 * 60_000L && (key == lastOrphan || lastOrphan.startsWith("$price|"))) return
        lastOrphan = key; lastOrphanAt = now
        val app = context.applicationContext
        bind(app)
        // Время заказа — от последней карточки (≈ взяли заказ) до экрана завершения, вместе с подачей.
        val card = lastCard?.takeIf { now - it.at in 0..3 * 3600_000L }
        lastCard = null
        val minutes = card?.let { ((now - it.at) / 60_000).toInt() }?.coerceAtLeast(1) ?: 1
        DriverJournal.autoStart(app)
        DriverJournal.add(app, price, 0, 0.0, 0.0, minutes, "", confirmed = true,
            from = card?.from.orEmpty(), to = card?.to.orEmpty(), payment = recentPayment().orEmpty())
        TripLog.d("Поездка по экрану завершения: $price L, ${if (card != null) "$minutes мин от взятия заказа" else "время неизвестно"}, " +
            "оплата ${recentPayment() ?: "?"}${if (card?.from?.isNotEmpty() == true) ", ${card.from} → ${card.to}" else ""}")
    }
}
