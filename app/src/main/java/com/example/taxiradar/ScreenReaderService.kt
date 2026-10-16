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
    private var bubbleX = 12
    private var bubbleY = 12
    private var busy = false
    private var closed = false
    private var lastFrame = 0L
    private var lastOffer = 0L
    private var lastRoute = ""
    private var lastRouteAt = 0L
    private var routeGeneration = 0L
    private var routeJob: Job? = null
    private var trafficRoute: ScreenTrafficRoute.Result? = null
    private var routeMessage = ""
    private fun t(ru: String, ro: String) = DriverUi.t(this, ru, ro)
    override fun onBind(intent: Intent?) = null
    override fun onCreate() {
        super.onCreate()
        running = true
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
        if (consent == null || !LicenseManager(this).isLicensed()) { stopSelf(); return START_NOT_STICKY }
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
            reader!!.setOnImageAvailableListener({ capture(it) }, handler)
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
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        @Suppress("DEPRECATION")
        val type = if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE
        wm.addView(view, WindowManager.LayoutParams(DriverUi.dp(this, 56), WindowManager.LayoutParams.WRAP_CONTENT, type,
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
                        bubbleX = (originX + dx.toInt()).coerceIn(0, (dm.widthPixels - DriverUi.dp(this, 56)).coerceAtLeast(0))
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
        p.width = if (view.expanded) minOf(resources.displayMetrics.widthPixels - DriverUi.dp(this, 24), DriverUi.dp(this, 560)) else DriverUi.dp(this, 56)
        p.x = if (view.expanded) DriverUi.dp(this, 12) else bubbleX
        p.y = if (view.expanded) DriverUi.dp(this, 8) else bubbleY
        try { (getSystemService(WINDOW_SERVICE) as WindowManager).updateViewLayout(view, p) } catch (_: IllegalArgumentException) { }
    }
    private fun updateMode() { overlay?.setExpanded(visibility.expanded) }
    private fun capture(r: ImageReader) {
        val image = try { r.acquireLatestImage() } catch (_: Exception) { null } ?: return
        var bitmap: Bitmap? = null
        try {
            val now = SystemClock.elapsedRealtime()
            if (busy || closed || now - lastFrame < 1400) return
            if (!LicenseManager(this).isLicensed()) { stopSelf(); return }
            lastFrame = now
            val plane = image.planes[0]
            val paddedWidth = image.width + (plane.rowStride - plane.pixelStride * image.width) / plane.pixelStride
            val full = Bitmap.createBitmap(paddedWidth, image.height, Bitmap.Config.ARGB_8888)
            full.copyPixelsFromBuffer(plane.buffer)
            val location = IntArray(2)
            overlay?.getLocationOnScreen(location)
            val panelBottom = location[1] + (overlay?.height ?: 0) + DriverUi.dp(this, 8)
            val configured = (image.height * DriverPreferences.number(this, "ocr_top", 10.0).coerceIn(8.0, 80.0) / 100).toInt()
            val top = maxOf(configured, panelBottom).coerceIn(0, image.height - 1)
            try { bitmap = Bitmap.createBitmap(full, 0, top, image.width, image.height - top) }
            finally { full.recycle() }
        } catch (_: Exception) { bitmap?.recycle(); return }
        finally { image.close() }
        val frame = bitmap ?: return
        busy = true
        ocr.read(frame) { texts ->
            try {
                if (!closed) {
                    val candidates = texts.mapNotNull { text ->
                        ScreenOfferParser.parse(ScreenOfferParser.correct(text, DriverPreferences.text(this, "ocr_words")))
                    }
                    val offer = candidates.maxByOrNull { (if (it.fare != null) 100 else 0) + it.addresses.count { address -> address.isNotBlank() } * 10 + if (it.multiplierKnown) 1 else 0 }
                    if (offer != null) renderOffer(offer)
                    else {
                        visibility.missing(SystemClock.elapsedRealtime())
                        if (!visibility.expanded) { currentOffer = null; overlay?.waiting(); clearRoute() }
                        updateMode()
                    }
                }
            } finally { frame.recycle(); busy = false }
        }
    }
    private fun boarding() = DriverPreferences.number(this, "ocr_extra").coerceIn(0.0, 30.0)
    private fun renderOffer(offer: ScreenOfferParser.Offer) {
        currentOffer = offer
        lastOffer = SystemClock.elapsedRealtime()
        visibility.update(offer.identity, lastOffer)
        updateMode()
        updateRoute(offer)
        renderMetrics(offer)
    }
    private fun renderMetrics(offer: ScreenOfferParser.Offer) {
        val route = trafficRoute
        // Пока маршрута нет (или сервис не ответил) — считаем по минутам и км с карточки Bolt, не ждём.
        val routed = route?.applyTo(offer) ?: offer
        val estimate = if (offer.fare == null && offer.multiplierKnown)
            BoltFareEstimator.estimate(offer.boltCategory, routed.tripKm, routed.tripMinutes, offer.multiplier) else null
        val fare = offer.fare ?: estimate
        val shownPrice = if (offer.fare == null && estimate != null) "≈ ${whole(estimate)} MDL" else ""
        val priceNote = when {
            offer.fare == null && !offer.multiplierKnown -> t("Коэффициент спроса не прочитан: цену не оцениваем", "Multiplicatorul nu este citit: fără estimare de preț")
            offer.fare == null && estimate == null -> t("Тариф не распознан: цену не оцениваем", "Categoria nu este recunoscută: fără estimare de preț")
            offer.fare == null -> t("Оценка по тарифам Bolt · ×", "Estimare după tarifele Bolt · ×") + number(offer.multiplier)
            else -> t("Цена из Bolt", "Preț din Bolt")
        }
        val boltMin = offer.pickupMinutes + offer.tripMinutes
        val timeNote = if (route == null) t("время по данным Bolt", "timp după Bolt")
        else {
            val real = route.pickup.minutes + route.trip.minutes
            t("Bolt ${whole(boltMin)} мин · на деле ≈ ${whole(real)} мин", "Bolt ${whole(boltMin)} min · real ≈ ${whole(real)} min") +
                if (real - boltMin >= 3) t(" (Bolt занизил)", " (Bolt a subestimat)") else ""
        }
        val profit = when {
            fare == null -> t("Для расчёта нужна цена и известный тариф", "Calculul necesită un preț și o categorie cunoscută")
            !DriverPreferences.costsReady(this) -> t("Заполните расходы машины — покажу L/час и L/км", "Completați cheltuielile mașinii pentru L/oră și L/km")
            routed.pickupKm + routed.tripKm <= 0 || routed.pickupMinutes + routed.tripMinutes + boarding() <= 0 -> ""
            else -> {
                val netFare = offer.fare != null && (offer.afterCommission || DriverPreferences.flag(this, "ocr_net_fare"))
                val m = ScreenOfferParser.metrics(routed.copy(fare = fare, afterCommission = netFare), DriverPreferences.costs(this), boarding())
                "≈ ${whole(m.net * 60 / m.minutes)} " + t("L/час", "L/oră") + " · ≈ ${number(m.perKm)} L/km\n" +
                    "≈ ${whole(m.net)} MDL " + t("чистыми", "net")
            }
        }
        overlay?.offer(offer, shownPrice, "$priceNote · $timeNote", profit)
        if (route != null) overlay?.route(route, boarding())
        // Почему нет настоящего времени — одной строкой, без повторов.
        else overlay?.travelStatus(listOfNotNull(
            t("Bolt: подача ${whole(offer.pickupMinutes)} мин · поездка ${whole(offer.tripMinutes)} мин", "Bolt: preluare ${whole(offer.pickupMinutes)} min · cursă ${whole(offer.tripMinutes)} min"),
            routeMessage.takeIf { it.isNotBlank() }).joinToString("\n"))
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
            clearRoute(); routeMessage = t("Адрес не прочитан — маршрут не рассчитан", "Adresă necitită — traseu necalculat"); return
        }
        val key = offer.addresses.joinToString("|") + "|" + boarding()
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
                    ScreenTrafficRoute.calculate(this@ScreenReaderService, fix.latitude, fix.longitude, offer.addresses, boarding())
                }
                if (closed || generation != routeGeneration) return@launch
                trafficRoute = reply.route
                routeMessage = if (reply.route == null) routeError(reply.error) else ""
                if (reply.route == null) lastRouteAt = SystemClock.elapsedRealtime() - 45000
            }
            if (trafficRoute == null) overlay?.clearMap(routeMessage)
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
