package com.example.taxiradar

import android.app.*
import android.content.*
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.*
import android.view.*
import android.widget.*
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import java.util.Locale

/** Frames remain in memory. Service is never restarted without a fresh Android capture consent. */
class ScreenReaderService : Service() {
    companion object { @Volatile var running = false; private set }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var ocr: ScreenOcrEngine
    private var projection: MediaProjection? = null
    private var reader: ImageReader? = null
    private var display: VirtualDisplay? = null
    private var overlay: ScreenReaderOverlay? = null
    private val visibility = ScreenOfferVisibility()
    private var currentOffer: ScreenOfferParser.Offer? = null
    private val offerSnapshot = BoltOfferSnapshot()
    private val frameStability = ScreenFrameStability()
    private var screenRevision = 0L
    private var recognizedRevision = -1L
    private var lockedScreenRevision = -1L
    private data class PendingFrame(val bitmap: Bitmap, val revision: Long, val startedAt: Long)
    private var pendingFrame: PendingFrame? = null
    private var bubbleX = 12
    private var bubbleY = 12
    private var busy = false
    private var closed = false
    private var lastFrame = 0L
    private var lastSample = 0L
    private var lastOcrDiagnostic = 0L
    private var lastOffer = 0L
    private var lastRoute = ""
    private var lastRouteAt = 0L
    private var routeGeneration = 0L
    private var routeJob: Job? = null
    private var trafficRoute: ScreenTrafficRoute.Result? = null
    private var routeMessage = ""
    private var routeScreenshotMode = false
    private val hideWatcher = object : Runnable {
        override fun run() {
            if (closed) return
            if (!RadarServices.ocr(this@ScreenReaderService)) { stopSelf(); return }
            val now = SystemClock.elapsedRealtime()
            if (now - lastOffer >= RadarServices.hideMs(this@ScreenReaderService)) {
                visibility.missing(now, RadarServices.hideMs(this@ScreenReaderService))
                updateMode()
            }
            handler.postDelayed(this, 500)
        }
    }
    private fun t(ru: String, ro: String) = DriverUi.t(this, ru, ro)
    override fun onBind(intent: Intent?) = null
    override fun onCreate() {
        super.onCreate()
        running = true
        if (!DriverPreferences.flag(this, "bolt_duration_default_v2")) {
            DriverPreferences.set(this, "bolt_time", true)
            DriverPreferences.set(this, "bolt_duration_default_v2", true)
        }
        if (!DriverPreferences.flag(this, "bolt_top_layout_v2")) {
            DriverPreferences.set(this, "bolt_panel_y", 8.0)
            DriverPreferences.set(this, "bolt_top_layout_v2", true)
        }
        ocr = ScreenOcrEngine(this)
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(NotificationChannel("screen_reader", "Taxi Radar OCR", NotificationManager.IMPORTANCE_LOW))
    }
    override fun onStartCommand(intent: Intent?, flags: Int, id: Int): Int {
        if (intent?.action == "stop") { stopSelf(); return START_NOT_STICKY }
        if (projection != null) return START_NOT_STICKY
        val consent = if (Build.VERSION.SDK_INT >= 33) intent?.getParcelableExtra("capture", Intent::class.java) else {
            @Suppress("DEPRECATION")
            intent?.getParcelableExtra<Intent>("capture")
        }
        if (consent == null || !LicenseManager(this).isLicensed() || !RadarServices.ocr(this)) { stopSelf(); return START_NOT_STICKY }
        val stop = PendingIntent.getService(this, 607, Intent(this, ScreenReaderService::class.java).setAction("stop"), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(this, "screen_reader").setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentTitle("Taxi Radar · OCR")
            .setContentText(t("Чтение выбранной области экрана", "Citirea zonei selectate a ecranului"))
            .setOngoing(true).addAction(0, t("Остановить", "Oprește"), stop).build()
        if (Build.VERSION.SDK_INT >= 29) startForeground(607, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION or
            (if (ScreenReaderLocation.permitted(this)) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0)) else startForeground(607, n)
        try {
            showPanel()
            val dm = resources.displayMetrics
            val manager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            projection = manager.getMediaProjection(intent?.getIntExtra("result", Activity.RESULT_OK) ?: Activity.RESULT_OK, consent)
            projection!!.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() { stopSelf() }
            }, handler)
            reader = ImageReader.newInstance(dm.widthPixels, dm.heightPixels, PixelFormat.RGBA_8888, 2)
            display = projection!!.createVirtualDisplay("TaxiRadarOCR", dm.widthPixels, dm.heightPixels, dm.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, reader!!.surface, null, handler)
            updateMode()
            reader!!.setOnImageAvailableListener({ capture(it) }, handler)
            handler.post(hideWatcher)
        } catch (_: Exception) {
            Toast.makeText(this, t("Не удалось начать захват. Запустите режим заново.", "Captura nu a pornit. Reporniți modul."), Toast.LENGTH_LONG).show()
            stopSelf()
        }
        return START_NOT_STICKY
    }
    private fun showPanel() {
        val view = ScreenReaderOverlay(this,
            minimize = { visibility.minimize(); updateMode() },
            open = { visibility.toggle(SystemClock.elapsedRealtime()); updateMode() },
            settings = { startActivity(Intent(this, ScreenReaderActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) },
            stop = { stopSelf() },
            mapChanged = { trafficRoute?.let { overlay?.route(it, boarding()) } ?: currentOffer?.let { updateRoute(it) } })
        overlay = view
        view.modeChanged = { updateWindow() }
        view.panelMoved = { delta ->
            val density = resources.displayMetrics.density
            val old = DriverPreferences.number(this, "bolt_panel_y", 8.0)
            DriverPreferences.set(this, "bolt_panel_y", (old + delta / density).coerceIn(8.0, resources.displayMetrics.heightPixels / density * .25))
            updateWindow(); view.requestLayout()
        }
        view.panelResized = { updateWindow() }
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        @Suppress("DEPRECATION")
        val type = if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE
        wm.addView(view, WindowManager.LayoutParams(DriverUi.dp(this, 48), WindowManager.LayoutParams.WRAP_CONTENT, type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT).apply {
                gravity = Gravity.TOP or Gravity.START; x = bubbleX; y = bubbleY
            })
        var downX = 0f; var downY = 0f; var originX = 0; var originY = 0; var dragged = false; var held = false
        val longClick = Runnable { held = true; view.bubble.performLongClick() }
        view.bubble.setOnTouchListener { button, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX; downY = event.rawY; originX = bubbleX; originY = bubbleY; dragged = false; held = false
                    handler.postDelayed(longClick, android.view.ViewConfiguration.getLongPressTimeout().toLong())
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downX; val dy = event.rawY - downY
                    if (kotlin.math.abs(dx) + kotlin.math.abs(dy) > DriverUi.dp(this, 6)) { dragged = true; handler.removeCallbacks(longClick) }
                    if (dragged) {
                        val dm = resources.displayMetrics
                        bubbleX = (originX + dx.toInt()).coerceIn(0, (dm.widthPixels - DriverUi.dp(this, 48)).coerceAtLeast(0))
                        bubbleY = (originY + dy.toInt()).coerceIn(0, (dm.heightPixels * .22).toInt())
                        updateWindow()
                    }
                }
                MotionEvent.ACTION_UP -> { handler.removeCallbacks(longClick); if (!dragged && !held) button.performClick() }
                MotionEvent.ACTION_CANCEL -> handler.removeCallbacks(longClick)
            }
            true
        }
    }
    private fun updateWindow() {
        val view = overlay ?: return
        val p = view.layoutParams as? WindowManager.LayoutParams ?: return
        p.width = if (view.expanded) minOf(resources.displayMetrics.widthPixels - DriverUi.dp(this, 24), (DriverUi.dp(this, 560) * BoltWidgetStyle.scale(this)).toInt()) else DriverUi.dp(this, 48)
        p.x = if (view.expanded) DriverUi.dp(this, 12) else bubbleX
        p.y = if (view.expanded) DriverUi.dp(this, DriverPreferences.number(this, "bolt_panel_y", 8.0).toInt()) else bubbleY
        try { (getSystemService(WINDOW_SERVICE) as WindowManager).updateViewLayout(view, p) } catch (_: IllegalArgumentException) { }
    }
    private fun updateMode() {
        overlay?.apply {
            setExpanded(this@ScreenReaderService.visibility.expanded && currentOffer?.let { effectiveFare(it) } != null)
            this.visibility = if (this@ScreenReaderService.visibility.current == null && BoltWidgetStyle.plusOnly(this@ScreenReaderService) && FloatingWidgetService.isRunning) View.GONE else View.VISIBLE
        }
    }
    private fun capture(r: ImageReader) {
        val image = try { r.acquireLatestImage() } catch (_: Exception) { null } ?: return
        var bitmap: Bitmap? = null
        try {
            val now = SystemClock.elapsedRealtime()
            if (closed || now - lastSample < 300) return
            lastSample = now
            if (!LicenseManager(this).isLicensed()) { stopSelf(); return }
            val screenshotMode = DriverPreferences.flag(this, "ocr_screenshot_test")
            val foreground = OrderAccessibilityService.foregroundPackage
            if (!screenshotMode && foreground.isNotBlank() && !BoltAccessibilityReader.supported(foreground)) {
                if (currentOffer != null || visibility.current != null) {
                    ocr.cancelCurrent(); screenRevision++
                    currentOffer = null; offerSnapshot.clear(); frozenEstimate = null; clearRoute()
                    visibility.absent(); updateMode()
                    pendingFrame?.bitmap?.recycle(); pendingFrame = null
                }
                return
            }
            if (screenshotMode != routeScreenshotMode) {
                routeScreenshotMode = screenshotMode
                clearRoute(); frozenEstimate = null
                currentOffer?.let { updateRoute(it); renderMetrics(it) }
            }
            val plane = image.planes[0]
            val paddedWidth = image.width + (plane.rowStride - plane.pixelStride * image.width) / plane.pixelStride
            val location = IntArray(2)
            overlay?.getLocationOnScreen(location)
            val panelVisible = overlay?.visibility == View.VISIBLE
            val panelBottom = if (panelVisible) location[1] + (overlay?.height ?: 0) + DriverUi.dp(this, 8) else 0
            val configured = (image.height * DriverPreferences.number(this, "ocr_top", 10.0).coerceIn(8.0, 80.0) / 100).toInt()
            // Tiny sample directly from the image: scene changes stay observable while native OCR is busy.
            val pixels = IntArray(48 * 80) { index ->
                val x = (index % 48) * image.width / 48
                val y = (index / 48) * image.height / 80
                val offset = y * plane.rowStride + x * plane.pixelStride
                (255 shl 24) or ((plane.buffer.get(offset).toInt() and 255) shl 16) or
                    ((plane.buffer.get(offset + 1).toInt() and 255) shl 8) or (plane.buffer.get(offset + 2).toInt() and 255)
            }
            val ignored = BooleanArray(pixels.size) { index ->
                val x = (index % 48) * image.width / 48
                val y = (index / 48) * image.height / 80
                y < maxOf(configured, image.height * 40 / 100) || y > image.height * 94 / 100 ||
                    (panelVisible && x >= location[0] - 8 && x <= location[0] + (overlay?.width ?: 0) + 8 &&
                        y >= location[1] - 8 && y <= panelBottom)
            }
            if (frameStability.changed(pixels, ignored)) {
                screenRevision++
                ocr.cancelCurrent()
                // Never show the previous proposal's route/price while the new scene is being read.
                if (currentOffer != null) {
                    clearRoute(); frozenEstimate = null; lockedUntil = 0
                    currentOffer = null; offerSnapshot.clear(); visibility.absent(); overlay?.reading(); updateMode()
                }
            }
            if (currentOffer != null && visibility.wasMinimized(currentOffer!!.identity)) {
                // Keep only the cheap scene-change detector alive. No OCR or route retry for a dismissed offer.
                lastOffer = now; visibility.update(currentOffer!!.identity, now)
                return
            }
            if (currentOffer != null && screenRevision == recognizedRevision) {
                // Same screenshot: no new OCR, expiry or route request, regardless of panel size.
                visibility.update(currentOffer!!.identity, now)
                lastOffer = now
                if (trafficRoute == null) updateRoute(currentOffer!!)
                return
            }
            if (busy && pendingFrame?.revision == screenRevision) return
            if (!busy && now - lastFrame < 600) return
            val full = Bitmap.createBitmap(paddedWidth, image.height, Bitmap.Config.ARGB_8888)
            full.copyPixelsFromBuffer(plane.buffer)
            val top = configured.coerceIn(0, image.height - 1)
            try {
                val crop = Bitmap.createBitmap(full, 0, top, image.width, image.height - top)
                bitmap = crop.copy(Bitmap.Config.ARGB_8888, true)
                if (crop !== full) crop.recycle()
                if (panelVisible) android.graphics.Canvas(bitmap!!).drawRect(
                    (location[0] - 8).toFloat(), (location[1] - top - 8).toFloat(),
                    (location[0] + (overlay?.width ?: 0) + 8).toFloat(), (panelBottom - top).toFloat(),
                    android.graphics.Paint().apply { color = android.graphics.Color.BLACK })
            }
            finally { full.recycle() }
        } catch (_: Exception) { bitmap?.recycle(); return }
        finally { image.close() }
        val frame = bitmap ?: return
        val work = PendingFrame(frame, screenRevision, SystemClock.elapsedRealtime())
        if (busy) {
            pendingFrame?.bitmap?.recycle()
            pendingFrame = work
        } else readFrame(work)
    }
    private fun readFrame(work: PendingFrame) {
        val frame = work.bitmap
        val capturedRevision = work.revision
        lastFrame = work.startedAt
        busy = true
        ocr.read(frame, preview = { quick ->
            if (!closed && capturedRevision == screenRevision && quick.fare != null) {
                val fresh = offerSnapshot.accept(quick)
                if (fresh) { clearRoute(); frozenEstimate = null }
                offerSnapshot.current?.let { renderOffer(it) }
            }
        }) { texts ->
            try {
                if (!closed && capturedRevision == screenRevision) {
                    val candidates = texts.mapNotNull { text ->
                        ScreenOfferParser.parse(ScreenOfferParser.correct(text, RadarServices.corrections(this)))
                    }
                    val offer = candidates.maxByOrNull { (if (it.fare != null) 100 else 0) + it.addresses.count { address -> address.isNotBlank() } * 10 + if (it.multiplierKnown) 1 else 0 }
                    val readAt = SystemClock.elapsedRealtime()
                    if (readAt - lastOcrDiagnostic > 10000) {
                        lastOcrDiagnostic = readAt
                        TripLog.init(this)
                        TripLog.d("BOLT/OCR scene=$capturedRevision: ${readAt - work.startedAt} мс; вариантов=${texts.size}; карточка=${offer != null}; адресов=${offer?.addresses?.count { it.isNotBlank() } ?: 0}")
                    }
                    val shown = currentOffer
                    // Повторные кадры только дополняют снимок предложения.
                    if (offer != null) {
                        lastOffer = SystemClock.elapsedRealtime()
                        val fresh = offerSnapshot.accept(offer)
                        val merged = offerSnapshot.current!!
                        if (fresh) { clearRoute(); frozenEstimate = null }
                        lockedUntil = SystemClock.elapsedRealtime() + RadarServices.hideMs(this)
                        if (fresh || merged != shown) renderOffer(merged)
                        else {
                            visibility.update(shown!!.identity, SystemClock.elapsedRealtime())
                            if (trafficRoute == null) updateRoute(shown)
                        }
                        if (merged.addresses.size == 2 && merged.addresses.none { it.isBlank() } &&
                            (merged.fare != null || (merged.multiplierKnown && merged.boltCategory != null))) {
                            recognizedRevision = capturedRevision
                            lockedScreenRevision = capturedRevision
                        }
                    }
                    else {
                        // A failed engine pass alone is not proof that the source card disappeared.
                        if (currentOffer != null && screenRevision == lockedScreenRevision) return@read
                        if (SystemClock.elapsedRealtime() >= lockedUntil) visibility.missing(SystemClock.elapsedRealtime(), RadarServices.hideMs(this))
                        if (visibility.current == null) { currentOffer = null; offerSnapshot.clear(); frozenEstimate = null; overlay?.waiting(); clearRoute() }
                        updateMode()
                    }
                }
            } finally {
                frame.recycle(); busy = false
                val next = pendingFrame
                pendingFrame = null
                if (next != null) {
                    if (!closed && next.revision == screenRevision) readFrame(next) else next.bitmap.recycle()
                }
            }
        }
    }
    private fun boarding() = DriverPreferences.number(this, "ocr_extra").coerceIn(0.0, 30.0)
    /** До какого времени держим показанный расчёт (тот же заказ не перечитываем). */
    private var lockedUntil = 0L
    private var frozenEstimate: Double? = null
    private var loggedCard = ""

    private fun renderOffer(offer: ScreenOfferParser.Offer) {
        // В «Журнал радара» — что прочиталось с карточки (без картинок): по нему видно, почему заказ не посчитался.
        val card = "цена ${offer.fare ?: "скрыта"} · подача ${offer.pickupMinutes} мин ${offer.pickupKm} км · поездка ${offer.tripMinutes} мин ${offer.tripKm} км · " +
            "×${if (offer.multiplierKnown) offer.multiplier else "?"} · ${offer.boltCategory ?: offer.tariff ?: "тариф?"} · А «${offer.addresses.getOrElse(0) { "" }.take(60)}» · Б «${offer.addresses.getOrElse(1) { "" }.take(60)}»"
        if (card != loggedCard) { loggedCard = card; TripLog.init(this); TripLog.d("Bolt: $card") }
        currentOffer = offer
        lastOffer = SystemClock.elapsedRealtime()
        renderMetrics(offer)
        updateMode()
        updateRoute(offer)
    }
    private fun effectiveFare(offer: ScreenOfferParser.Offer): Double? {
        offer.fare?.let { return it }
        frozenEstimate?.let { return it }
        val route = trafficRoute ?: return null
        return BoltFareEstimator.resolve(offer, route.trip.km, route.trip.minutes)
            ?.also { frozenEstimate = it }
    }
    private fun renderMetrics(offer: ScreenOfferParser.Offer) {
        val fare = effectiveFare(offer) ?: run {
            visibility.absent(); overlay?.waiting(); updateMode(); return
        }
        visibility.update(offer.identity, SystemClock.elapsedRealtime())
        val routed = trafficRoute?.applyTo(offer) ?: offer
        val km = routed.pickupKm + routed.tripKm
        val minutes = routed.pickupMinutes + routed.tripMinutes + boarding()
        val alreadyNet = BoltWidgetStyle.alreadyNet(this, offer)
        val left = BoltWidgetStyle.netValue(this, fare, alreadyNet, km)
        val rate = if (DriverPreferences.flag(this, "bolt_per_km", true) && km > 0) "${number(left / km)} L/km" else ""
        val showTime = DriverPreferences.flag(this, "bolt_time", true)
        // Until routing responds use the card duration; both are explicitly approximate.
        val minutesText = if (showTime) "≈ ${whole(minutes)} " + t("мин", "min") else ""
        val priceText = (if (offer.fare == null) "≈ " else "") + "${whole(fare)} L"
        overlay?.offer(offer, priceText, "", rate, minutesText)
        overlay?.netAmount(BoltWidgetStyle.netLine(this, fare, alreadyNet, km))
        overlay?.mapSummary(priceText, rate, minutesText, getColor(R.color.tr_success))
        trafficRoute?.let { overlay?.route(it, boarding()) }
        updateMode()
    }
    private fun whole(n: Double) = String.format(Locale.getDefault(), "%.0f", n)
    private fun number(n: Double) = String.format(Locale.getDefault(), "%.2f", n)
    private fun routeError(code: String) = when (code) {
        "route_not_configured" -> t("Сервис маршрутов не настроен на сервере", "Serviciul de trasee nu este configurat pe server")
        "route_rate_limit" -> t("Повторный расчёт через несколько секунд", "Recalculare peste câteva secunde")
        "route_not_found" -> t("Маршрут по адресам не найден", "Traseul pentru adrese nu a fost găsit")
        else -> t("Прогноз недоступен: проверьте связь и сервис маршрутов", "Estimare indisponibilă: verificați conexiunea și serviciul de trasee")
    }
    private fun updateRoute(offer: ScreenOfferParser.Offer) {
        if (offer.addresses.size != 2 || offer.addresses.any { it.isBlank() }) {
            clearRoute(); routeMessage = t("Адрес не прочитан — маршрут не рассчитан", "Adresă necitită — traseu necalculat"); overlay?.routeUnavailable(routeMessage); return
        }
        val screenshot = DriverPreferences.flag(this, "ocr_screenshot_test")
        val key = offer.addresses.joinToString("|") + "|" + boarding() + "|" + screenshot
        if (key == lastRoute && trafficRoute != null) return
        if (key == lastRoute && SystemClock.elapsedRealtime() - lastRouteAt < 60000) return
        clearRoute()
        lastRoute = key; lastRouteAt = SystemClock.elapsedRealtime()
        routeMessage = t("Считаем подачу и поездку с учётом пробок…", "Calculăm preluarea și cursa ținând cont de trafic…")
        overlay?.clearMap(routeMessage)
        val generation = routeGeneration
        routeJob = scope.launch {
            val fix = ScreenReaderLocation.fix(this@ScreenReaderService)
            if (closed || generation != routeGeneration) return@launch
            if (fix == null) {
                routeMessage = t("Нужно свежее местоположение: включите GPS и доступ к геолокации", "Este necesară o poziție recentă: activați GPS și accesul la locație")
            } else {
                val reply = withContext(Dispatchers.IO) {
                    ScreenTrafficRoute.calculate(this@ScreenReaderService, fix.latitude, fix.longitude, offer.addresses, boarding(), offer, tripOnly = screenshot)
                }
                if (closed || generation != routeGeneration) return@launch
                trafficRoute = reply.route
                TripLog.init(this@ScreenReaderService)
                TripLog.d("BOLT/OCR/ROUTE screenshot=$screenshot; A=${offer.addresses[0]}; B=${offer.addresses[1]}: ${if (reply.route == null) reply.error else "OK ${reply.route.pickup.minutes}+${reply.route.trip.minutes} мин"}")
                routeMessage = if (reply.route == null) routeError(reply.error) else ""
                if (reply.route == null) lastRouteAt = SystemClock.elapsedRealtime() - 45000
            }
            if (trafficRoute == null) overlay?.routeUnavailable(routeMessage)
            currentOffer?.let { renderMetrics(it) }
        }
    }
    private fun clearRoute() {
        routeGeneration++; routeJob?.cancel(); routeJob = null; lastRoute = ""; trafficRoute = null
        overlay?.clearMap()
    }
    override fun onConfigurationChanged(newConfig: Configuration) { super.onConfigurationChanged(newConfig); stopSelf() }
    override fun onDestroy() {
        closed = true
        pendingFrame?.bitmap?.recycle(); pendingFrame = null
        running = false
        handler.removeCallbacksAndMessages(null)
        scope.cancel()
        reader?.setOnImageAvailableListener(null, null)
        display?.release(); reader?.close(); projection?.stop(); projection = null
        ocr.close()
        overlay?.close()
        overlay?.let { try { (getSystemService(WINDOW_SERVICE) as WindowManager).removeView(it) } catch (_: Exception) {} }
        overlay = null
        super.onDestroy()
    }
}
