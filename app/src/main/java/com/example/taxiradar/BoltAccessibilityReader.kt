package com.example.taxiradar

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.util.Locale
import kotlinx.coroutines.*

/** Opt-in test reader: only Bolt's driver package, never clicks or accepts orders. */
class BoltAccessibilityReader(private val service: AccessibilityService) {
    private val handler = Handler(Looper.getMainLooper())
    private val snapshot = BoltOfferSnapshot()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main + CoroutineExceptionHandler { _, error ->
        TripLog.d("BOLT/A11Y route unavailable: ${error.javaClass.simpleName}")
    })
    private var route: ScreenTrafficRoute.Result? = null
    private var routeJob: Job? = null
    private var routedGeneration = -1L
    private var estimatedFare: Double? = null
    private var scheduled = false
    private var lastText = ""
    private var loggedAt = 0L
    private var lastOfferAt = 0L
    private var shown = ""
    private var ownsWidget = false
    private var watching = false
    private var lastShownAt = 0L
    private val watchdog: Runnable = object : Runnable {
        override fun run() {
            if (!RadarServices.accessibility(service)) { watching = false; reset(); return }
            if (snapshot.current != null && SystemClock.elapsedRealtime() - lastOfferAt >= RadarServices.hideMs(service)) reset()
            if (!scheduled) { scheduled = true; handler.post(read) }
            handler.postDelayed(this, 700)
        }
    }
    companion object {
        /** Минута на посадку пассажира между подачей и поездкой. */
        const val BOARDING_MIN = 1.0
        fun supported(pkg: String) = pkg == "ee.mtakso.driver" || pkg.startsWith("ee.mtakso.driver.")
    }
    private fun t(ru: String, ro: String) = DriverUi.t(service, ru, ro)
    fun event(event: AccessibilityEvent) {
        if (!RadarServices.accessibility(service)) { reset(); return }
        if (!supported(event.packageName?.toString().orEmpty())) {
            // System overlays and our own widget do not end an offer.
            if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
                event.packageName?.toString()?.contains("taximeter") == true) reset()
            return
        }
        if (!watching) { watching = true; handler.post(watchdog) }
    }
    private val read: Runnable = Runnable {
        scheduled = false
        if (!RadarServices.accessibility(service)) { reset(); return@Runnable }
        if (!LicenseManager(service).isLicensed()) return@Runnable
        val root = service.rootInActiveWindow
        if (root == null || !supported(root.packageName?.toString().orEmpty())) {
            if (snapshot.current == null) { watching = false; handler.removeCallbacks(watchdog) }
            return@Runnable
        }
        val nodes = ArrayList<String>()
        var visited = 0
        fun walk(node: AccessibilityNodeInfo?, depth: Int) {
            if (node == null || depth > 35 || ++visited > 500) return
            val text = listOfNotNull(node.text?.toString(), node.contentDescription?.toString()).distinct().joinToString("\n").trim()
            if (text.isNotBlank()) nodes += text.take(1000)
            for (i in 0 until node.childCount) walk(node.getChild(i), depth + 1)
        }
        walk(root, 0)
        val text = nodes.joinToString("\n").take(12000)
        val now = SystemClock.elapsedRealtime()
        TripLog.init(service)
        if (text != lastText && now - loggedAt >= 2000) {
            lastText = text; loggedAt = now
            TripLog.d("BOLT/A11Y nodes=$visited text=${text.length}: " + text.replace('\n', '|').take(6000))
        } else if (text.isBlank() && now - loggedAt >= 15000) {
            loggedAt = now; TripLog.d("BOLT/A11Y: текст недоступен; нужен резервный OCR")
        }
        val corrected = ScreenOfferParser.correct(text, RadarServices.corrections(service))
        val offer = ScreenOfferParser.parse(corrected)
            ?: ScreenOfferParser.parse(corrected.lines().distinct().joinToString("\n"))
        if (offer == null) {
            // Leave brief incomplete frames alone; return the radar after the card is gone.
            if (snapshot.current != null && now - lastOfferAt >= RadarServices.hideMs(service)) reset()
            return@Runnable
        }
        lastOfferAt = now
        val fresh = snapshot.accept(offer)
        if (fresh) {
            routeJob?.cancel(); route = null; routedGeneration = -1; estimatedFare = null
            shown = ""
            TripLog.d("BOLT/A11Y предложение #${snapshot.generation}: цена=${offer.fare}; км=${offer.pickupKm}+${offer.tripKm}; мин=${offer.pickupMinutes}+${offer.tripMinutes}; спрос=${if (offer.multiplierKnown) offer.multiplier else "?"}")
        }
        val fixed = snapshot.current ?: return@Runnable
        display(fixed)
        // Маршрут (настоящее время по пробкам) — для любого заказа, как только прочитаны оба адреса;
        // цена с карточки при этом показывается сразу, время уточнится через секунду-две.
        if (routedGeneration != snapshot.generation && fixed.addresses.size == 2 && fixed.addresses.none { it.isBlank() }) {
            val generation = snapshot.generation
            routedGeneration = generation
            val started = SystemClock.elapsedRealtime()
            routeJob = scope.launch {
                // Не ждём новый GPS: виджет и так знает, где водитель (точке не больше 2 минут).
                val recent = FloatingWidgetService.isRunning && System.currentTimeMillis() - FloatingWidgetService.lastFixAt < 120_000
                val lat: Double; val lon: Double
                if (recent) { lat = FloatingWidgetService.driverLat; lon = FloatingWidgetService.driverLon }
                else { val fix = ScreenReaderLocation.fix(service) ?: return@launch; lat = fix.latitude; lon = fix.longitude }
                if (generation != snapshot.generation) return@launch
                val reply = withContext(Dispatchers.IO) {
                    ScreenTrafficRoute.calculate(service, lat, lon, fixed.addresses, BOARDING_MIN, fixed)
                }
                if (generation != snapshot.generation) return@launch
                route = reply.route
                TripLog.d("BOLT/A11Y маршрут за ${SystemClock.elapsedRealtime() - started} мс: ${reply.error.takeIf { reply.route == null } ?: "ок"}")
                estimatedFare = BoltFareEstimator.resolve(fixed, route?.trip?.km, route?.trip?.minutes)
                    ?: if (fixed.fare == null) BoltFareEstimator.estimate(fixed.boltCategory, route?.trip?.km ?: fixed.tripKm, route?.trip?.minutes ?: fixed.tripMinutes, 1.0) else null
                TripLog.d("BOLT/A11Y estimate=$estimatedFare; route=${reply.error}")
                snapshot.current?.let { display(it) }
            }
        }
    }
    private fun display(offer: ScreenOfferParser.Offer) {
        if (!RadarServices.accessibility(service)) return
        // Скрытую цену до ответа маршрута оцениваем по км и минутам карточки — не ждём.
        val fare = offer.fare ?: estimatedFare ?: BoltFareEstimator.estimate(offer.boltCategory, offer.tripKm, offer.tripMinutes, if (offer.multiplierKnown) offer.multiplier else 1.0)
            ?: run {
                if (ownsWidget) FloatingWidgetService.clearBoltNote()
                ownsWidget = false; shown = ""; return
            }
        val r = route
        val km = (r?.pickup?.km ?: offer.pickupKm) + (r?.trip?.km ?: offer.tripKm)
        // Весь заказ: подача + 1 мин на посадку + поездка; по пробкам (TomTom), пока нет ответа — по Bolt.
        val minutes = (r?.pickup?.minutes ?: offer.pickupMinutes) + BOARDING_MIN + (r?.trip?.minutes ?: offer.tripMinutes)
        val alreadyNet = BoltWidgetStyle.alreadyNet(service, offer)
        val left = BoltWidgetStyle.netValue(service, fare, alreadyNet, km)
        val loc = Locale.getDefault()
        val estimate = offer.fare == null
        val priceText = (if (estimate) (if (offer.multiplierKnown || estimatedFare != null) "≈ " else t("от ≈ ", "de la ≈ ")) else "") + String.format(loc, "%.0f L", fare)
        // Цена крупно, рядом мельче — сколько останется (если включено «чистыми»).
        val title = android.text.SpannableStringBuilder(priceText).apply {
            if (DriverPreferences.flag(service, "bolt_show_net", true) && left != fare) {
                val start = length
                append(t("  чистыми ≈ ", "  net ≈ ") + String.format(loc, "%.0f L", left))
                setSpan(android.text.style.RelativeSizeSpan(0.42f), start, length, 0)
                setSpan(android.text.style.ForegroundColorSpan(service.getColor(R.color.tr_text)), start, length, 0)
            }
        }
        val body = android.text.SpannableStringBuilder().apply {
            val start = length
            append(t("≈ ${String.format(loc, "%.0f", minutes)} мин весь заказ", "≈ ${String.format(loc, "%.0f", minutes)} min toată cursa"))
            if (r == null) append(t(" · по Bolt", " · după Bolt"))
            setSpan(android.text.style.ForegroundColorSpan(service.getColor(R.color.tr_accent)), start, length, 0)
            setSpan(android.text.style.StyleSpan(android.graphics.Typeface.BOLD), start, length, 0)
            setSpan(android.text.style.RelativeSizeSpan(1.25f), start, length, 0)
            val rates = listOfNotNull(
                if (km > 0 && DriverPreferences.flag(service, "bolt_per_km", true)) String.format(loc, "%.1f L/km", left / km) else null,
                if (minutes > 0) String.format(loc, "%.0f ", left * 60 / minutes) + t("L/час", "L/oră") else null)
            if (rates.isNotEmpty()) append("\n" + rates.joinToString(" · "))
        }
        val key = "$title|$body"
        val changed = shown != key
        if (changed) { shown = key; TripLog.d("BOLT/WIDGET: ${key.replace('\n', ' ')}") }
        if (ScreenReaderService.running) return
        if (changed || SystemClock.elapsedRealtime() - lastShownAt >= 2000) {
            lastShownAt = SystemClock.elapsedRealtime()
            FloatingWidgetService.showBoltNote(title, body, if (estimate) R.color.tr_accent else R.color.tr_success, RadarServices.hideMs(service) + 1000)
        }
        ownsWidget = FloatingWidgetService.isRunning
    }
    private fun reset() {
        if (snapshot.current == null && !ownsWidget) return
        snapshot.clear(); shown = ""; routeJob?.cancel(); route = null; estimatedFare = null; routedGeneration = -1
        if (ownsWidget) FloatingWidgetService.clearBoltNote()
        ownsWidget = false
    }
    fun close() { handler.removeCallbacksAndMessages(null); reset(); scope.cancel() }
}
