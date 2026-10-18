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
        if (fixed.fare == null && routedGeneration != snapshot.generation && fixed.multiplierKnown &&
            fixed.boltCategory != null && fixed.addresses.size == 2 && fixed.addresses.none { it.isBlank() }) {
            val generation = snapshot.generation
            routedGeneration = generation
            routeJob = scope.launch {
                val fix = ScreenReaderLocation.fix(service) ?: return@launch
                if (generation != snapshot.generation) return@launch
                val reply = withContext(Dispatchers.IO) {
                    ScreenTrafficRoute.calculate(service, fix.latitude, fix.longitude, fixed.addresses, 0.0, fixed)
                }
                if (generation != snapshot.generation) return@launch
                route = reply.route
                estimatedFare = BoltFareEstimator.resolve(fixed, route?.trip?.km, route?.trip?.minutes)
                TripLog.d("BOLT/A11Y estimate=$estimatedFare; route=${reply.error}")
                snapshot.current?.let { display(it) }
            }
        }
    }
    private fun display(offer: ScreenOfferParser.Offer) {
        if (!RadarServices.accessibility(service)) return
        val fare = (offer.fare ?: estimatedFare) ?: run {
            if (ownsWidget) FloatingWidgetService.clearBoltNote()
            ownsWidget = false; shown = ""; return
        }
        val km = (route?.pickup?.km ?: offer.pickupKm) + (route?.trip?.km ?: offer.tripKm)
        val alreadyNet = BoltWidgetStyle.alreadyNet(service, offer)
        val left = BoltWidgetStyle.netValue(service, fare, alreadyNet, km)
        val title = (if (offer.fare == null) "≈ " else "") + String.format(Locale.getDefault(), "%.0f L", fare)
        val net = BoltWidgetStyle.netLine(service, fare, alreadyNet, km)
        val rate = if (km > 0 && DriverPreferences.flag(service, "bolt_per_km", true)) String.format(Locale.getDefault(), "%.2f L/km", left / km) else ""
        val body = listOf(net, rate).filter { it.isNotBlank() }.joinToString("\n")
        val changed = shown != "$title|$body"
        if (changed) { shown = "$title|$body"; TripLog.d("BOLT/WIDGET: $shown") }
        if (ScreenReaderService.running) return
        if (changed || SystemClock.elapsedRealtime() - lastShownAt >= 2000) {
            lastShownAt = SystemClock.elapsedRealtime()
            FloatingWidgetService.showBoltNote(title, body, R.color.tr_success, RadarServices.hideMs(service) + 1000)
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
