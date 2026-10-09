package com.example.taxiradar

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.*

class FloatingWidgetService : Service() {

    companion object {
        // Отключение/бан в админке выбивает работающий радар в течение этого времени.
        private const val LICENSE_RECHECK_MS = 2 * 60_000L

        var isRunning: Boolean = false
        private var instance: FloatingWidgetService? = null

        // Живой GPS водителя для мгновенного старта маршрута (точка А)
        var driverLat: Double = 47.0245
        var driverLon: Double = 28.8353

        fun showOrderData(price: Int, km: Double, min: Int, pickupKm: Double, stops: Int, bonus: Int = 0, doubts: List<String> = emptyList()) {
            instance?.displayOrder(price, km, min, pickupKm, stops, bonus = bonus, doubts = doubts)
        }

        /** Доставка: готовая сумма с карточки и пояснение, из чего она сложилась. */
        fun showDelivery(price: Int, km: Double, min: Int, note: String) {
            instance?.displayOrder(price, km, min, 0.0, 0, note = note)
        }

        fun clearOrder() {
            instance?.cancelOrderDisplay()
        }

        /** Узнали точку Б заказа — сразу показать надбавку и там. */
        fun refreshSurge() {
            instance?.refreshFromOutside()
        }

        /** Переключатель «Предупреждать в дороге» изменили — включаем/выключаем GPS. */
        fun setRoadAlerts() {
            instance?.applyRoadAlerts()
        }

        /** Короткая заметка поверх экрана: например, что известно о клиенте. */
        fun showNote(title: String, body: String, colorRes: Int, showMs: Long = 9_000) {
            instance?.displayNote(title, body, colorRes, showMs)
        }

        /** Ползунок «Размер виджета» в профиле — применяем сразу. */
        fun applyWidgetScale() {
            instance?.applyScale()
        }

        /** После «Поехали»: цена по км и минутам из навигатора Яндекса — ненадолго. */
        fun showRefinedPrice(price: Int, km: Double, min: Int, pickupKm: Double, bonus: Int = 0) {
            val service = instance ?: return
            service.displayOrder(
                price, km, min, pickupKm, stops = 0, bonus = bonus,
                note = service.getString(R.string.widget_refined),
                showMs = 8_000
            )
        }
    }

    private var windowManager: WindowManager? = null
    private var floatingView: View? = null
    private var tvWidgetSurge: TextView? = null
    private var tvWidgetSub: TextView? = null
    private var btnPlus: TextView? = null
    private var reportMenu: View? = null
    private lateinit var licenseManager: LicenseManager

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var locationCallback: LocationCallback? = null

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var autoUpdateJob: Job? = null
    private var orderDisplayJob: Job? = null
    private var isShowingOrder: Boolean = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLanguage.wrap(newBase))
    }

    override fun onCreate() {
        CrashReporter.install(this)
        super.onCreate()
        instance = this
        isRunning = true
        licenseManager = LicenseManager(this)
        startInForeground()

        initFusedGps()

        try {
            windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            floatingView = LayoutInflater.from(this).inflate(R.layout.layout_floating_widget, null)
            tvWidgetSurge = floatingView?.findViewById(R.id.tvWidgetSurge)
            tvWidgetSub = floatingView?.findViewById(R.id.tvWidgetSub)
            setupReportMenu()
            applyScale()

            val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }

            val layoutParams: WindowManager.LayoutParams = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                layoutType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.CENTER_VERTICAL or Gravity.END
                x = 20
                y = 0
            }

            setupTouchListener(layoutParams)
            windowManager?.addView(floatingView, layoutParams)

            startAutoUpdateLoop()
        } catch (e: Exception) {
            e.printStackTrace()
            stopSelf()
        }
    }

    fun displayOrder(
        price: Int, km: Double, min: Int, pickupKm: Double, stops: Int,
        bonus: Int = 0,
        note: String? = null,
        showMs: Long = 16_000,
        /** Пусто — цена по маршруту Яндекса (точно); иначе — почему она примерная. */
        doubts: List<String>? = null
    ) {
        orderDisplayJob?.cancel()
        isShowingOrder = true
        val net = NetEarnings.compute(this, price, km, pickupKm)

        orderDisplayJob = serviceScope.launch {
            withContext(Dispatchers.Main) {
                setOrderSize(true)
                tvWidgetSurge?.text = "~$price L"
                tvWidgetSurge?.setTextColor(getColor(R.color.tr_success))
                // Надёжность: «✓ точно» — км и минуты Яндекса; «≈ примерно: …» — главная причина.
                val source = when {
                    note != null -> note
                    doubts == null -> DriverUi.t(this@FloatingWidgetService, if (OrderPreview.current()?.yandex == true) "По маршруту Яндекса" else "Расчётная цена", if (OrderPreview.current()?.yandex == true) "După ruta Yandex" else "Preț estimat")
                    // Пояснения о точности не пишем — только «≈» перед км, если цена примерная.
                    else -> null
                }
                val approx = doubts != null && doubts.isNotEmpty()
                val details = listOfNotNull(
                    if (DriverPreferences.flag(this@FloatingWidgetService, "source", true)) source else null,
                    if (DriverPreferences.flag(this@FloatingWidgetService, "distance", true) && km > 0) (if (approx) "≈ " else "") + getString(R.string.widget_km, formatKm(km)) else null,
                    if (DriverPreferences.flag(this@FloatingWidgetService, "minutes", true) && min > 0) getString(R.string.widget_min, min) else null,
                    if (DriverPreferences.flag(this@FloatingWidgetService, "pickup") && pickupKm > 0) DriverUi.t(this@FloatingWidgetService, "Подача ${formatKm(pickupKm)} км", "Preluare ${formatKm(pickupKm)} km") else null,
                    if (DriverPreferences.flag(this@FloatingWidgetService, "bonus") && bonus > 0) DriverUi.t(this@FloatingWidgetService, "Надбавка +$bonus L (в цене)", "Supliment +$bonus L (inclus)") else null,
                    // Надбавка уже в цене, а водитель видит её на кнопке «Принять» —
                    // отдельной строкой не пишем, иначе кружок становится слишком широким.
                    if (DriverPreferences.flag(this@FloatingWidgetService, "stops", true) && stops > 0) resources.getQuantityString(R.plurals.widget_stops, stops, stops) else null
                ).joinToString(" · ")
                val sub = SpannableStringBuilder()
                if (net != null) {
                    sub.append(getString(R.string.widget_net, net), StyleSpan(Typeface.BOLD), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    sub.setSpan(ForegroundColorSpan(WidgetStyle.textColor(this@FloatingWidgetService)), 0, sub.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    if (details.isNotEmpty()) sub.append("\n")
                }
                sub.append(details)
                val prefs = this@FloatingWidgetService
                if (DriverPreferences.flag(prefs, "profit") || DriverPreferences.flag(prefs, "per_km") || DriverPreferences.flag(prefs, "per_hour")) {
                    if (!DriverPreferences.costsReady(prefs)) sub.append("\n" + DriverUi.t(prefs, "Оценки нет: заполните расходы машины", "Fără evaluare: completați cheltuielile"))
                    else {
                        val r = OrderEconomics.calculate(price, km, pickupKm, min, pickupKm / DriverPreferences.number(prefs, "pickup_speed", 25.0).coerceAtLeast(5.0) * 60, DriverPreferences.costs(prefs))
                        // «После расходов ≈ 65 L · подача 3 км · ≈ 110 L/час» и сверка с желаемыми показателями.
                        val values = mutableListOf<String>()
                        if (DriverPreferences.flag(prefs, "profit")) values += DriverUi.t(prefs, "После расходов ≈ ${r.net} L", "După cheltuieli ≈ ${r.net} L")
                        if (DriverPreferences.flag(prefs, "profit") && pickupKm > 0) values += DriverUi.t(prefs, "подача ${formatKm(pickupKm)} км", "preluare ${formatKm(pickupKm)} km")
                        if (DriverPreferences.flag(prefs, "per_km") && r.perKm != null) values += "~${formatKm(r.perKm)} L/km"
                        if ((DriverPreferences.flag(prefs, "per_hour") || DriverPreferences.flag(prefs, "profit")) && r.perHour != null) values += DriverUi.t(prefs, "≈ ${r.perHour} L/час", "≈ ${r.perHour} L/oră")
                        if (values.isNotEmpty()) sub.append("\n" + values.joinToString(" · "))
                        if (DriverPreferences.flag(prefs, "profit")) {
                            val misses = mutableListOf<String>()
                            val hourGoal = DriverPreferences.number(prefs, "hour_target", 120.0)
                            val orderGoal = DriverPreferences.number(prefs, "order_min_net", 0.0)
                            val pickupGoal = DriverPreferences.number(prefs, "pickup_max_km", 0.0)
                            if (hourGoal > 0 && r.perHour != null && r.perHour < hourGoal) misses += DriverUi.t(prefs, "в час < ${hourGoal.toInt()}", "pe oră < ${hourGoal.toInt()}")
                            if (orderGoal > 0 && r.net < orderGoal) misses += DriverUi.t(prefs, "за заказ < ${orderGoal.toInt()}", "pe cursă < ${orderGoal.toInt()}")
                            if (pickupGoal > 0 && pickupKm > pickupGoal) misses += DriverUi.t(prefs, "подача > ${formatKm(pickupGoal)} км", "preluare > ${formatKm(pickupGoal)} km")
                            val start = sub.length
                            sub.append("\n" + if (misses.isEmpty()) DriverUi.t(prefs, "✓ Подходит под ваши цели", "✓ Corespunde țintelor") else DriverUi.t(prefs, "Ниже цели: ", "Sub țintă: ") + misses.joinToString(", "))
                            sub.setSpan(ForegroundColorSpan(getColor(if (misses.isEmpty()) R.color.tr_success else R.color.tr_warning)), start, sub.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                            if (misses.isNotEmpty()) tvWidgetSurge?.setTextColor(getColor(R.color.tr_warning))
                        }
                    }
                }
                tvWidgetSub?.text = sub
                tvWidgetSub?.visibility = if (sub.isEmpty()) View.GONE else View.VISIBLE
            }

            // Карточка заказа — 16 секунд (время на принятие), уточнённая цена — короче.
            delay(showMs)

            isShowingOrder = false
            refreshData()
        }
    }

    /** Цена заказа — крупнее обычного, чтобы читалась с одного взгляда. */
    private fun setOrderSize(order: Boolean) {
        orderSize = order
        // Цена заказа — заметно крупнее надбавки между заказами: её видно с одного взгляда.
        tvWidgetSurge?.textSize = (if (order) 38f * WidgetStyle.orderScale(this) else 28f * WidgetStyle.valueScale(this)) * scale
        // Цена заказа — всегда в полную яркость.
        if (order) floatingView?.alpha = 1f
        tvWidgetSub?.textSize = (if (order) 13f else 11f) * scale
    }

    private var scale = 1f
    private var orderSize = false

    /** Размер из настроек: шрифт, отступы и ширина кружка растут вместе. */
    fun applyScale() {
        scale = WidgetSize.scale(this)
        val dp = resources.displayMetrics.density * scale
        floatingView?.findViewById<View>(R.id.widgetPill)?.apply {
            setPadding((16 * dp).toInt(), (10 * dp).toInt(), (16 * dp).toInt(), (10 * dp).toInt())
            minimumWidth = (72 * dp).toInt()
        }
        val available = (resources.displayMetrics.widthPixels - 64 * resources.displayMetrics.density).toInt().coerceAtLeast(150)
        tvWidgetSub?.maxWidth = minOf((250 * dp).toInt(), available)
        tvWidgetSurge?.maxWidth = available
        floatingView?.findViewById<View>(R.id.widgetPill)?.background = android.graphics.drawable.GradientDrawable().apply {
            cornerRadius = 22 * dp
            setColor(WidgetStyle.bgColor(this@FloatingWidgetService))
            setStroke(resources.displayMetrics.density.toInt().coerceAtLeast(1), WidgetStyle.strokeColor(this@FloatingWidgetService))
        }
        tvWidgetSub?.setTextColor(WidgetStyle.subColor(this))
        // «+» (метки) можно спрятать — кружок меньше. Меню меток тогда открывается нажатием на кружок.
        btnPlus?.visibility = if (WidgetStyle.showPlus(this)) View.VISIBLE else View.GONE
        (btnPlus?.background?.mutate() as? android.graphics.drawable.GradientDrawable)?.setColor((WidgetStyle.bgColor(this) and 0x00FFFFFF) or (0xF2 shl 24))
        val ps = WidgetStyle.plusScale(this)
        btnPlus?.textSize = 20f * scale * ps
        btnPlus?.layoutParams = (btnPlus?.layoutParams as? ViewGroup.MarginLayoutParams)?.apply {
            width = (30 * dp * ps).toInt()
            height = (30 * dp * ps).toInt()
            topMargin = (-9 * dp).toInt()
        }
        (reportMenu as? ViewGroup)?.let { menu ->
            for (i in 0 until menu.childCount) (menu.getChildAt(i) as? TextView)?.textSize = 30f * scale
        }
        (tvWidgetSub?.layoutParams as? ViewGroup.MarginLayoutParams)?.topMargin = (3 * dp).toInt()
        setOrderSize(orderSize)
        floatingView?.requestLayout()
    }

    fun displayNote(title: String, body: String, colorRes: Int, showMs: Long) {
        orderDisplayJob?.cancel()
        isShowingOrder = true
        orderDisplayJob = serviceScope.launch {
            withContext(Dispatchers.Main) {
                setOrderSize(false)
                tvWidgetSurge?.text = title
                tvWidgetSurge?.setTextColor(getColor(colorRes))
                tvWidgetSub?.text = body
                tvWidgetSub?.visibility = View.VISIBLE
            }
            delay(showMs)
            isShowingOrder = false
            refreshData()
        }
    }

    fun cancelOrderDisplay() {
        orderDisplayJob?.cancel()
        isShowingOrder = false
        refreshData()
    }

    // ---------- метки на дороге (как в Waze) ----------

    private var roadJob: Job? = null
    @Volatile
    private var roadReports: List<RoadReports.Report> = emptyList()
    // Сколько метров было до метки на прошлой точке GPS и какое предупреждение уже было:
    // 1 — «через 400 м», 2 — «через 100 м». Отъехали дальше 700 м — забываем.
    private val lastDistance = mutableMapOf<Long, Int>()
    private val alertStage = mutableMapOf<Long, Int>()
    private val askedReports = mutableSetOf<Long>()

    /** Раз в 1,5 минуты подгружаем метки вокруг водителя (только дорожные). */
    private fun startRoadReports() {
        roadJob?.cancel()
        roadJob = serviceScope.launch {
            while (isActive) {
                RoadReports.list(this@FloatingWidgetService, driverLat, driverLon)?.let { list ->
                    roadReports = list.filter { RoadReports.type(it.type)?.road == true && !it.mine }
                }
                // Стоим у терминала аэропорта — «я в очереди» (сервер держит 5 минут).
                if (Airport.isAtAirport(driverLat, driverLon)) Airport.ping(this@FloatingWidgetService)
                delay(90_000)
            }
        }
    }

    /**
     * Едем К метке — предупреждаем дважды: за 400 м и за 100 м (уведомление
     * со звуком и, если не висит цена заказа, на виджете). Удаляемся от
     * метки или стоим — молчим. Проехали её — «Ещё здесь?» с кнопками.
     */
    private fun checkRoadReports(lat: Double, lon: Double) {
        val dist = FloatArray(1)
        for (r in roadReports) {
            android.location.Location.distanceBetween(lat, lon, r.lat, r.lon, dist)
            val meters = dist[0].toInt()
            val prev = lastDistance.put(r.id, meters)
            if (meters > 700) {
                alertStage.remove(r.id)
                continue
            }
            // Приближаемся: за последние секунды стали ближе хотя бы на 15 м.
            val approaching = prev != null && meters < prev - 15
            val stage = alertStage[r.id] ?: 0
            val next = when {
                approaching && meters <= 150 && stage < 2 -> 2
                approaching && meters <= 450 && stage < 1 -> 1
                else -> 0
            }
            if (next > 0) {
                alertStage[r.id] = next
                warnAhead(r, meters)
            }
            // Были у самой метки и отъезжаем — спрашиваем, на месте ли она.
            if (prev != null && prev < 120 && meters > prev && r.id !in askedReports && !r.voted) {
                askedReports += r.id
                RoadReports.askStillHere(this, r)
            }
        }
    }

    private fun warnAhead(r: RoadReports.Report, meters: Int) {
        val type = RoadReports.type(r.type) ?: return
        val rounded = maxOf(50, (meters / 50) * 50)
        RoadReports.notifyAhead(this, r, rounded)
        if (!isShowingOrder) {
            displayNote(
                "${type.emoji} " + getString(R.string.road_ahead, getString(type.label)),
                getString(R.string.road_ahead_dist, rounded),
                R.color.tr_warning, 7_000
            )
        }
    }

    // ---------- «+» на виджете: отметить радар, полицию, опасность ----------

    private var menuCloseJob: Job? = null

    private fun setupReportMenu() {
        btnPlus = floatingView?.findViewById(R.id.btnWidgetPlus)
        reportMenu = floatingView?.findViewById(R.id.layoutReportMenu)
        mapOf(R.id.btnRepRadar to "radar", R.id.btnRepPolice to "police", R.id.btnRepDanger to "danger").forEach { (id, key) ->
            floatingView?.findViewById<View>(id)?.setOnClickListener {
                toggleReportMenu(false)
                sendReport(key)
            }
        }
    }

    /** Цвет «+» (значок и обводка) — как у цифры надбавки. */
    private fun tintPlus(colorRes: Int) = tintPlusColor(getColor(colorRes))

    private fun tintPlusColor(color: Int) {
        btnPlus?.setTextColor(color)
        (btnPlus?.background?.mutate() as? android.graphics.drawable.GradientDrawable)
            ?.setStroke((1.5f * resources.displayMetrics.density * scale).toInt().coerceAtLeast(1), color)
    }

    private fun toggleReportMenu(open: Boolean) {
        reportMenu?.visibility = if (open) View.VISIBLE else View.GONE
        btnPlus?.text = if (open) "✕" else "+"
        menuCloseJob?.cancel()
        // Открыли и забыли — само закроется, чтобы не мешало навигатору.
        if (open) menuCloseJob = serviceScope.launch {
            delay(8_000)
            withContext(Dispatchers.Main) { toggleReportMenu(false) }
        }
    }

    private fun sendReport(key: String) {
        val type = RoadReports.type(key) ?: return
        serviceScope.launch {
            if (!awaitFix()) {
                withContext(Dispatchers.Main) {
                    displayNote(getString(R.string.widget_report_no_fix), getString(R.string.widget_report_no_fix_hint), R.color.tr_warning, 5_000)
                }
                return@launch
            }
            val r = RoadReports.add(this@FloatingWidgetService, key, driverLat, driverLon)
            withContext(Dispatchers.Main) {
                if (r?.first == true) {
                    displayNote(
                        "${type.emoji} " + getString(R.string.widget_report_sent, getString(type.label)),
                        getString(R.string.widget_report_sent_hint), R.color.tr_success, 4_000
                    )
                } else {
                    displayNote(
                        getString(R.string.widget_report_failed),
                        r?.second?.takeIf { it.isNotBlank() } ?: getString(R.string.chk_key_no_network),
                        R.color.tr_danger, 5_000
                    )
                }
            }
        }
    }

    private fun formatKm(km: Double): String =
        if (km < 10) String.format(java.util.Locale.US, "%.1f", km) else Math.round(km).toString()

    @SuppressLint("MissingPermission")
    private fun initFusedGps() {
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // Для предупреждений «через 100 м» нужен GPS: по вышкам и Wi-Fi точка
        // гуляет на сотню метров. Раз в 4 секунды, а не каждые 1,5 с, как когда-то
        // (тогда телефон грелся). GPS и так включён — им ведёт навигатор Яндекса.
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 4000L).apply {
            setMinUpdateIntervalMillis(3000L)
            setWaitForAccurateLocation(false)
        }.build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val loc = result.lastLocation ?: return
                driverLat = loc.latitude
                driverLon = loc.longitude
                lastFixAt = System.currentTimeMillis()
                checkRoadReports(loc.latitude, loc.longitude)
            }
        }

        this.locationRequest = locationRequest
        forceRequestSingleFix()
        applyRoadAlerts()
    }

    private var locationRequest: LocationRequest? = null
    private var trackingLocation = false

    /**
     * Постоянно следим за GPS только если водитель включил «Предупреждать в
     * дороге» и дал геолокацию. Иначе — один экономный запрос раз в минуту,
     * перед обновлением надбавки (forceRequestSingleFix).
     */
    @SuppressLint("MissingPermission")
    fun applyRoadAlerts() {
        val granted = checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED ||
                checkSelfPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
        val want = granted && RoadReports.alertsEnabled(this) && !LiteMode.cuts(this, LiteMode.Feature.ROAD)
        val callback = locationCallback ?: return
        if (want && !trackingLocation) {
            // Разрешение могли дать уже после запуска радара — добавляем службе тип «location».
            try {
                promoteToForeground()
            } catch (e: Exception) {
                e.printStackTrace()
            }
            try {
                fusedLocationClient.requestLocationUpdates(locationRequest!!, callback, Looper.getMainLooper())
                trackingLocation = true
                startRoadReports()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        } else if (!want && trackingLocation) {
            fusedLocationClient.removeLocationUpdates(callback)
            trackingLocation = false
            roadJob?.cancel()
        }
    }

    private fun hasLocationPermission() =
        checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED ||
                checkSelfPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED

    /** Когда последний раз узнали, где водитель. 0 — ещё ни разу. */
    @Volatile
    private var lastFixAt = 0L

    @SuppressLint("MissingPermission")
    private fun forceRequestSingleFix() {
        if (!hasLocationPermission()) return
        try {
            fusedLocationClient.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null)
                .addOnSuccessListener { loc ->
                    if (loc != null && System.currentTimeMillis() - loc.time in 0..120000) {
                        driverLat = loc.latitude
                        driverLon = loc.longitude
                        lastFixAt = loc.time
                    }
                }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /** Свежая точка перед запросом надбавки: ждём ответ GPS до 6 секунд. */
    @SuppressLint("MissingPermission")
    private suspend fun awaitFix(): Boolean {
        if (!hasLocationPermission()) return false
        val got = kotlinx.coroutines.withTimeoutOrNull(6000) {
            kotlinx.coroutines.suspendCancellableCoroutine<android.location.Location?> { cont ->
                try {
                    fusedLocationClient.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null)
                        .addOnSuccessListener { if (cont.isActive) cont.resumeWith(Result.success(it)) }
                        .addOnFailureListener { if (cont.isActive) cont.resumeWith(Result.success(null)) }
                } catch (e: Exception) {
                    if (cont.isActive) cont.resumeWith(Result.success(null))
                }
            }
        }
        // Сервисы Google молчат (мультимедиа машин: нет Wi-Fi и вышек для «экономного»
        // режима) — берём место напрямую у GPS устройства.
        val fix = got?.takeIf { System.currentTimeMillis() - it.time in 0..120000 } ?: deviceGpsFix()
        if (fix != null) {
            driverLat = fix.latitude
            driverLon = fix.longitude
            lastFixAt = fix.time
        }
        // Точка не старше 15 минут — годится; иначе надбавка была бы не про это место.
        return System.currentTimeMillis() - lastFixAt < 2 * 60_000
    }

    /**
     * Место от самого устройства, без сервисов Google: свежая точка любого
     * провайдера (не старше 15 минут), иначе ждём одну точку от GPS до 8 секунд.
     */
    @SuppressLint("MissingPermission")
    private suspend fun deviceGpsFix(): android.location.Location? {
        if (!hasLocationPermission()) return null
        val lm = getSystemService(android.location.LocationManager::class.java) ?: return null
        val fresh = try {
            lm.getProviders(true).mapNotNull { lm.getLastKnownLocation(it) }
                .filter { System.currentTimeMillis() - it.time < 2 * 60_000 }
                .maxByOrNull { it.time }
        } catch (e: Exception) {
            null
        }
        if (fresh != null) return fresh
        val provider = listOf(android.location.LocationManager.GPS_PROVIDER, android.location.LocationManager.NETWORK_PROVIDER)
            .firstOrNull { runCatching { lm.isProviderEnabled(it) }.getOrDefault(false) } ?: return null
        return kotlinx.coroutines.withTimeoutOrNull(8000) {
            kotlinx.coroutines.suspendCancellableCoroutine<android.location.Location?> { cont ->
                val listener = object : android.location.LocationListener {
                    override fun onLocationChanged(location: android.location.Location) {
                        lm.removeUpdates(this)
                        if (cont.isActive) cont.resumeWith(Result.success(location))
                    }
                    @Deprecated("Deprecated in Java")
                    override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) {}
                    override fun onProviderEnabled(provider: String) {}
                    override fun onProviderDisabled(provider: String) {}
                }
                try {
                    lm.requestLocationUpdates(provider, 0L, 0f, listener, Looper.getMainLooper())
                } catch (e: Exception) {
                    if (cont.isActive) cont.resumeWith(Result.success(null))
                }
                cont.invokeOnCancellation { lm.removeUpdates(listener) }
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupTouchListener(params: WindowManager.LayoutParams) {
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var isMoved = false

        floatingView?.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isMoved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - initialTouchX).toInt()
                    val dy = (event.rawY - initialTouchY).toInt()

                    if (Math.abs(dx) > 10 || Math.abs(dy) > 10) {
                        isMoved = true
                    }

                    params.x = initialX - dx
                    params.y = initialY + dy
                    windowManager?.updateViewLayout(floatingView, params)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    // Нажатие в любом месте кружка — меню «+»: попасть пальцем на ходу легко.
                    if (!isMoved) {
                        val opening = reportMenu?.visibility != View.VISIBLE
                        toggleReportMenu(opening)
                        // Заодно обновить надбавку — как раньше по нажатию на кружок.
                        if (opening) refreshData()
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun startAutoUpdateLoop() {
        autoUpdateJob?.cancel()
        autoUpdateJob = serviceScope.launch {
            while (isActive) {
                if (!licenseManager.checkDeviceStatusCached(LICENSE_RECHECK_MS)) {
                    withContext(Dispatchers.Main) { stopSelf() }
                    break
                }
                if (!isShowingOrder) {
                    fetchSurgeForAll()
                }
                // Что не ушло при плохой связи — досылаем.
                Outbox.flush(this@FloatingWidgetService)
                // Вышла новая версия — уведомление в шторке (раз в 3 часа сверяемся).
                UpdateNotifier.check(this@FloatingWidgetService, licenseManager)
                // Едем по заказу — надбавка «здесь» и в Б чаще, раз в 30 секунд.
                delay(DriverPreferences.number(this@FloatingWidgetService, "refresh", 60.0).toLong().coerceIn(30, 300) * 1000)
            }
        }
        serviceScope.launch {
            val alerts = DriverAlerts()
            while (isActive) {
                if (LiteMode.cuts(this@FloatingWidgetService, LiteMode.Feature.ALERTS)) { delay(180000); continue }
                val fresh = DriverPreferences.flag(this@FloatingWidgetService, "surge_alert") && awaitFix()
                alerts.check(this@FloatingWidgetService, if (fresh) driverLat else null, if (fresh) driverLon else null, DriverPreferences.selectedTariff(this@FloatingWidgetService))
                delay(180000)
            }
        }
    }

    fun refreshFromOutside() = refreshData()

    private fun refreshData() {
        if (isShowingOrder) return
        serviceScope.launch {
            val isLicenseValid = licenseManager.checkDeviceStatusCached(LICENSE_RECHECK_MS)
            if (!isLicenseValid) {
                withContext(Dispatchers.Main) { stopSelf() }
                return@launch
            }
            fetchSurgeForAll()
        }
    }

    private val surgeMutex = kotlinx.coroutines.sync.Mutex()
    private data class SurgeSnapshot(val value: Int, val at: Long, val lat: Double, val lon: Double)
    private val surgeSnapshots = mutableMapOf<String, SurgeSnapshot>()
    private suspend fun fetchSurgeForAll() {
        if (isShowingOrder || !surgeMutex.tryLock()) return
        try { fetchDemand() } finally { surgeMutex.unlock() }
    }

    private suspend fun fetchDemand() {
        if (isShowingOrder) return
        if (!DriverPreferences.flag(this, "surge", true)) {
            withContext(Dispatchers.Main) { if (!isShowingOrder) {
                setOrderSize(false)
                tvWidgetSurge?.text = DriverUi.t(this@FloatingWidgetService, "Радар", "Radar")
                tvWidgetSub?.visibility = View.GONE
            } }
            return
        }

        withContext(Dispatchers.Main) {
            if (!isShowingOrder) {
                setOrderSize(false)
                tvWidgetSurge?.text = "…"
                tvWidgetSub?.visibility = View.GONE
            }
        }

        // Без своей точки надбавку не показываем: раньше бралась запасная точка
        // на Буюканах, и водитель видел чужой спрос («+55», когда у него 0).
        if (!awaitFix()) {
            withContext(Dispatchers.Main) {
                if (!isShowingOrder) {
                    setOrderSize(false)
                    tvWidgetSurge?.text = "📍"
                    tvWidgetSurge?.setTextColor(getColor(R.color.tr_text_muted))
                    tvWidgetSub?.setText(if (hasLocationPermission()) R.string.widget_no_fix else R.string.widget_no_location)
                    tvWidgetSub?.visibility = View.VISIBLE
                }
            }
            return
        }
        YandexTaxiSurgeChecker.updateBases(AppConfig.load(this).surgeBase)

        val prefs = getSharedPreferences("taxi_radar_prefs", Context.MODE_PRIVATE)
        val showComfort = !prefs.getBoolean("show_econom", true) && prefs.getBoolean("show_comfort", false)
        val showComfortPlus = !prefs.getBoolean("show_econom", true) && !showComfort && prefs.getBoolean("show_comfortplus", false)
        val showEconom = !showComfort && !showComfortPlus
        // Доставку можно включить вместе с одним тарифом такси — отдельной строкой ниже.
        val showExpress = prefs.getBoolean("show_express", false)

        var hasSurge = false

        // Каждый тариф — «буква + надбавка» в одну строку: «Э+35  К0».
        // Столбиком из трёх строк кружок закрывал полэкрана на маленьких телефонах.
        val tariffs = listOfNotNull(
            if (showEconom) R.string.tariff_econom_short to "econom" else null,
            if (showComfort) R.string.tariff_comfort_short to "comfort" else null,
            if (showComfortPlus) R.string.tariff_comfort_plus_short to "comfortplus" else null
        )
        var staleAge: Long? = null
        var missing = false
        var hereValue: Int? = null
        val lat = driverLat; val lon = driverLon
        val parts = tariffs.map { (label, key) ->
            val s = YandexTaxiSurgeChecker.getSurgePrice(lon, lat, key)
            val now = System.currentTimeMillis()
            if (s != null) surgeSnapshots[key] = SurgeSnapshot(s, now, lat, lon)
            val old = surgeSnapshots[key]?.takeIf { now - it.at <= 10 * 60000 && RouteFareCalculator.distanceKm(lat, lon, it.lat, it.lon) < 0.2 }
            if (s == null) { missing = true; staleAge = old?.let { (now - it.at) / 60000 } }
            val shown = s ?: old?.value
            if (hereValue == null) hereValue = shown
            val value = when { shown == null -> "?"; shown > 0 -> { hasSurge = true; WidgetStyle.value(this, shown) }; else -> "0" }
            getString(label) to value
        }
        // Буква тарифа — мелко («Э», «К+»), сама надбавка — крупно: её видно с одного взгляда.
        var displayText: CharSequence = if (parts.isEmpty()) "0" else SpannableStringBuilder().apply {
            parts.forEachIndexed { i, (label, value) ->
                if (i > 0) append("  ")
                val start = length
                append(WidgetStyle.label(this@FloatingWidgetService, label))
                setSpan(android.text.style.RelativeSizeSpan(WidgetStyle.labelScale(this@FloatingWidgetService)), start, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                append(value)
            }
        }

        // Едем по заказу (мультимедиа машины) — две строки: «здесь +15» и «Б +35».
        val dest = TripDestination.current().takeIf { DriverPreferences.flag(this, "destination", true) }
        val key = tariffs.firstOrNull()?.second
        if (dest != null && key != null) {
            val here = hereValue
            val atB = YandexTaxiSurgeChecker.getSurgePrice(dest.second, dest.first, key)
            fun line(sb: SpannableStringBuilder, label: String, v: Int?) {
                val hot = v != null && v > 0
                val start = sb.length
                sb.append(WidgetStyle.label(this@FloatingWidgetService, label))
                sb.setSpan(android.text.style.RelativeSizeSpan(WidgetStyle.labelScale(this@FloatingWidgetService)), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                val vStart = sb.length
                sb.append(if (v == null) "?" else if (hot) WidgetStyle.value(this@FloatingWidgetService, v) else "0")
                sb.setSpan(
                    ForegroundColorSpan(if (hot) WidgetStyle.surgeColor(this@FloatingWidgetService) else WidgetStyle.calmColor(this@FloatingWidgetService)),
                    start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                if (vStart == start) return
            }
            displayText = SpannableStringBuilder().also {
                line(it, getString(R.string.widget_here), here)
                it.append(WidgetStyle.separator(this@FloatingWidgetService))
                line(it, getString(R.string.widget_point_b), atB)
            }
            hasSurge = (here ?: 0) > 0 || (atB ?: 0) > 0
            if (here == null || atB == null) missing = true
        } else if (DriverPreferences.flag(this, "destination", true) && TripDestination.enabled() && TripDestination.pending != null && key != null) {
            // Б на экране есть, но ещё не найден на карте — показываем, что радар о нём знает.
            displayText = SpannableStringBuilder(displayText).append("\n").also {
                val start = it.length
                it.append(getString(R.string.widget_point_b) + " ?")
                it.setSpan(ForegroundColorSpan(getColor(R.color.tr_text_muted)), start, it.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }

        // Нет спроса рядом — напомнить включить «Только наличными» (раз в 15 мин, пока не включат).
        if (!isShowingOrder) CashReminder.check(this, hereValue)

        // Доставка — своей строкой: «Д +20» (в любом режиме, и в поездке с «Я / Б»).
        if (showExpress) {
            val d = YandexTaxiSurgeChecker.getSurgePrice(lon, lat, "express")
            val now = System.currentTimeMillis()
            if (d != null) surgeSnapshots["express"] = SurgeSnapshot(d, now, lat, lon)
            val shown = d ?: surgeSnapshots["express"]?.takeIf { now - it.at <= 10 * 60000 }?.value
            if (shown != null && shown > 0) hasSurge = true
            displayText = SpannableStringBuilder(displayText).also { sb ->
                sb.append(WidgetStyle.separator(this@FloatingWidgetService))
                val start = sb.length
                sb.append(WidgetStyle.label(this@FloatingWidgetService, getString(R.string.tariff_delivery_short)))
                sb.setSpan(android.text.style.RelativeSizeSpan(WidgetStyle.labelScale(this@FloatingWidgetService)), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                sb.append(when { shown == null -> "?"; shown > 0 -> WidgetStyle.value(this@FloatingWidgetService, shown); else -> "0" })
                sb.setSpan(ForegroundColorSpan(if (shown != null && shown > 0) WidgetStyle.surgeColor(this@FloatingWidgetService) else WidgetStyle.calmColor(this@FloatingWidgetService)), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }

        withContext(Dispatchers.Main) {
            if (!isShowingOrder) {
                setOrderSize(false)
                tvWidgetSurge?.text = displayText
                // Подпись — только если что-то не так; «проверено в 23:29» не нужно:
                // надбавка и так обновляется сама.
                if (missing) {
                    tvWidgetSub?.text = if (staleAge != null) DriverUi.t(this@FloatingWidgetService, "Старые данные · $staleAge мин", "Date vechi · $staleAge min")
                    else DriverUi.t(this@FloatingWidgetService, "Нет данных · проверьте связь", "Fără date · verificați conexiunea")
                    tvWidgetSub?.visibility = View.VISIBLE
                } else {
                    tvWidgetSub?.visibility = View.GONE
                }
                val style = this@FloatingWidgetService
                tvWidgetSurge?.setTextColor(if (hasSurge) WidgetStyle.surgeColor(style) else WidgetStyle.calmColor(style))
                tintPlusColor(if (hasSurge) WidgetStyle.surgeColor(style) else WidgetStyle.calmColor(style))
                // Приглушать без спроса: кружок полупрозрачный, при надбавке — снова яркий.
                floatingView?.alpha = if (WidgetStyle.dim(style) && !hasSurge) WidgetStyle.dimAlpha(style) / 100f else 1f
            }
        }
    }

    private fun startInForeground() {
        val channelId = "floating_surge_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Taxi Radar Widget",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }

        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Taxi Radar")
            .setContentText(getString(R.string.notif_text))
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        foregroundNotification = notification
        promoteToForeground()
    }

    private var foregroundNotification: Notification? = null

    /**
     * Android 14+: тип «location» разрешён, только если водитель дал доступ к
     * местоположению, — иначе SecurityException и радар падает при каждом
     * запуске. Без GPS служба — «specialUse» (кружок поверх окон).
     */
    private fun promoteToForeground() {
        val notification = foregroundNotification ?: return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(101, notification)
            return
        }
        val location = checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED ||
                checkSelfPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
        val type = android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE or
                (if (location) android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0)
        try {
            startForeground(101, notification, type)
        } catch (e: Exception) {
            // Разрешение отозвали между проверкой и стартом — хотя бы без GPS.
            startForeground(101, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        isRunning = false
        serviceScope.cancel()
        autoUpdateJob?.cancel()
        orderDisplayJob?.cancel()
        roadJob?.cancel()
        menuCloseJob?.cancel()
        locationCallback?.let { fusedLocationClient.removeLocationUpdates(it) }
        if (floatingView?.isAttachedToWindow == true) {
            windowManager?.removeView(floatingView)
        }
    }
}
