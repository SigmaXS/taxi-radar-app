package com.example.taxiradar

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.SystemClock
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.util.GeoPoint
import org.osmdroid.util.MapTileIndex
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import java.io.File

/**
 * Карта спроса во вкладке «Карта»: водитель нажимает на точку — одним
 * запросом узнаём надбавку по трём тарифам и ставим туда флажок с цифрой,
 * чтобы «прощупать» соседние районы. Запросы — не чаще раза в секунду.
 */
class SurgeMapController(private val activity: AppCompatActivity, root: View) {

    private val map: MapView
    // Надбавка по всем тарифам в точке флажка — показываем по нажатию на флажок.
    private var lastResult: String? = null
    private val noKey: TextView
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var requestInFlight = false
    private var lastRequestAt = 0L
    private var myMarker: Marker? = null
    private var centeredOnce = false

    // Метки водителей на карте.
    private lateinit var chipRoad: com.google.android.material.chip.Chip
    private lateinit var chipAddr: com.google.android.material.chip.Chip
    private var reportsJob: kotlinx.coroutines.Job? = null
    private var reports: List<RoadReports.Report> = emptyList()
    private val reportMarkers = mutableListOf<Marker>()

    // Места водителей: еда, мойка, заправка…
    private lateinit var chipPlaces: com.google.android.material.chip.Chip
    private var places: List<Places.Place> = emptyList()
    private val placeMarkers = mutableListOf<Marker>()
    private var lastBearing: Float? = null

    init {
        // Старый кеш с плитками OSM «Access blocked» больше не нужен.
        File(activity.cacheDir, "osmdroid/tiles").deleteRecursively()
        Configuration.getInstance().apply {
            userAgentValue = "TaxiRadar/" + activity.packageManager.getPackageInfo(activity.packageName, 0).versionName
            osmdroidBasePath = File(activity.cacheDir, "osmdroid")
            osmdroidTileCache = File(activity.cacheDir, "osmdroid/yandex")
        }

        noKey = root.findViewById(R.id.tvMapNoKey)
        map = root.findViewById(R.id.mapView)
        map.setMultiTouchControls(true)
        map.isTilesScaledToDpi = true
        map.zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
        applyTiles(AppConfig.load(activity).tilesApiKey)
        if (AppConfig.load(activity).tilesApiKey.isBlank()) {
            // Ключ ещё не скачан (или только что добавлен в Railway) — пробуем получить.
            scope.launch {
                LicenseManager(activity).fetchAppConfig()?.let {
                    AppConfig.save(activity, it)
                    applyTiles(AppConfig.load(activity).tilesApiKey)
                }
            }
        }
        map.minZoomLevel = 9.0
        map.maxZoomLevel = 19.0
        map.controller.setZoom(14.5)
        map.controller.setCenter(GeoPoint(FloatingWidgetService.driverLat, FloatingWidgetService.driverLon))

        map.overlays.add(MapEventsOverlay(object : MapEventsReceiver {
            override fun singleTapConfirmedHelper(p: GeoPoint): Boolean {
                probe(p)
                return true
            }

            // Долгое нажатие — поставить метку в этой точке (например, проблемный адрес).
            override fun longPressHelper(p: GeoPoint): Boolean {
                showAddReport(p)
                return true
            }
        }))

        setupRotation(root)
        setupSearch(root)
        val layers = root.findViewById<View>(R.id.layoutMapLayers)
        root.findViewById<View>(R.id.btnMapLayers).setOnClickListener {
            layers.visibility = if (layers.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }

        root.findViewById<View>(R.id.btnMapMyLocation).setOnClickListener { centerOnMe() }
        root.findViewById<View>(R.id.fabReport).setOnClickListener {
            showAddReport(myMarker?.position ?: GeoPoint(FloatingWidgetService.driverLat, FloatingWidgetService.driverLon))
        }
        val prefs = activity.getSharedPreferences("taxi_radar_prefs", Context.MODE_PRIVATE)
        chipRoad = root.findViewById(R.id.chipLayerRoad)
        chipAddr = root.findViewById(R.id.chipLayerAddr)
        chipRoad.isChecked = prefs.getBoolean("layer_road", true)
        chipAddr.isChecked = prefs.getBoolean("layer_addr", true)
        val onLayer = { _: android.widget.CompoundButton, _: Boolean ->
            prefs.edit().putBoolean("layer_road", chipRoad.isChecked).putBoolean("layer_addr", chipAddr.isChecked).apply()
            renderReports()
        }
        chipRoad.setOnCheckedChangeListener(onLayer)
        chipAddr.setOnCheckedChangeListener(onLayer)

        chipPlaces = root.findViewById(R.id.chipLayerPlaces)
        chipPlaces.isChecked = prefs.getBoolean("layer_places", true)
        chipPlaces.setOnCheckedChangeListener { _, on ->
            prefs.edit().putBoolean("layer_places", on).apply()
            renderPlaces()
        }
        // Тёмная карта, как ночной режим навигатора (по умолчанию включена).
        val chipDark = root.findViewById<com.google.android.material.chip.Chip>(R.id.chipMapDark)
        chipDark.isChecked = prefs.getBoolean("map_dark", true)
        applyNight(chipDark.isChecked)
        chipDark.setOnCheckedChangeListener { _, on ->
            prefs.edit().putBoolean("map_dark", on).apply()
            applyNight(on)
        }
    }

    /**
     * Вращение двумя пальцами. Компас появляется, когда карта повёрнута:
     * стрелка показывает на север, нажатие — север снова вверх.
     */
    private fun setupRotation(root: View) {
        val compass = root.findViewById<View>(R.id.btnMapCompass)
        map.overlays.add(org.osmdroid.views.overlay.gestures.RotationGestureOverlay(map).apply { isEnabled = true })
        // Оверлей без рисования: на каждой перерисовке сверяем угол карты с компасом.
        map.overlays.add(object : org.osmdroid.views.overlay.Overlay() {
            override fun draw(c: Canvas?, osmv: MapView?, shadow: Boolean) {
                if (shadow) return
                val angle = map.mapOrientation
                val rotated = angle % 360f != 0f
                compass.rotation = angle
                val want = if (rotated) View.VISIBLE else View.GONE
                if (compass.visibility != want) compass.post { compass.visibility = want }
            }
        })
        compass.setOnClickListener {
            map.mapOrientation = 0f
            map.invalidate()
        }
    }

    /** Поиск адреса: флажок переезжает туда и показывает спрос в этом месте. */
    private fun setupSearch(root: View) {
        val input = root.findViewById<android.widget.EditText>(R.id.etMapSearch)
        input.setOnEditorActionListener { v, actionId, event ->
            val enter = event?.keyCode == android.view.KeyEvent.KEYCODE_ENTER && event.action == android.view.KeyEvent.ACTION_DOWN
            if (actionId != android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH && !enter) return@setOnEditorActionListener false
            val query = v.text.toString().trim()
            if (query.length < 3) return@setOnEditorActionListener true
            activity.getSystemService(android.view.inputmethod.InputMethodManager::class.java)
                ?.hideSoftInputFromWindow(v.windowToken, 0)
            v.clearFocus()
            scope.launch {
                val found = kotlinx.coroutines.withContext(Dispatchers.IO) {
                    runCatching { RouteFareCalculator.locate(activity, query) }.getOrNull()
                }
                if (found == null) {
                    android.widget.Toast.makeText(activity, R.string.map_search_not_found, android.widget.Toast.LENGTH_SHORT).show()
                    return@launch
                }
                val point = GeoPoint(found.first, found.second)
                map.controller.setZoom(16.0)
                map.controller.animateTo(point)
                probe(point)
            }
            true
        }
    }

    /**
     * Ночная подложка без платных сервисов: инвертируем яркость плиток Яндекса
     * и поворачиваем оттенок обратно — тёмный фон, цвета дорог и воды узнаваемы.
     */
    private fun applyNight(on: Boolean) {
        val overlay = map.overlayManager.tilesOverlay
        if (!on) {
            overlay.setColorFilter(null)
        } else {
            val invert = android.graphics.ColorMatrix(floatArrayOf(
                -1f, 0f, 0f, 0f, 255f,
                0f, -1f, 0f, 0f, 255f,
                0f, 0f, -1f, 0f, 255f,
                0f, 0f, 0f, 1f, 0f
            ))
            // Поворот оттенка на 180°: после инверсии вода снова синяя, парки — зелёные.
            val hue = android.graphics.ColorMatrix(floatArrayOf(
                -0.574f, 1.43f, 0.144f, 0f, 0f,
                0.426f, 0.43f, 0.144f, 0f, 0f,
                0.426f, 1.43f, -0.856f, 0f, 0f,
                0f, 0f, 0f, 1f, 0f
            ))
            // Чуть темнее и мягче, чтобы не слепило ночью.
            val dim = android.graphics.ColorMatrix().apply { setScale(0.82f, 0.82f, 0.88f, 1f) }
            invert.postConcat(hue)
            invert.postConcat(dim)
            overlay.setColorFilter(android.graphics.ColorMatrixColorFilter(invert))
        }
        map.setBackgroundColor(if (on) 0xFF15161A.toInt() else 0xFFF2F2F2.toInt())
        map.invalidate()
    }

    /** Вкладку открыли — оживляем карту; при первом показе — к водителю. */
    fun onShow() {
        map.onResume()
        if (!centeredOnce) {
            centeredOnce = true
            centerOnMe()
        }
        reportsJob?.cancel()
        reportsJob = scope.launch {
            while (true) {
                loadReports()
                loadPlaces()
                delay(60_000)
            }
        }
    }

    /** Водитель только что разрешил геолокацию — показываем, где он. */
    fun onLocationGranted() = centerOnMe()

    fun onHide() {
        reportsJob?.cancel()
        map.onPause()
    }

    // ---------- метки водителей ----------


    private suspend fun loadReports() {
        val c = map.mapCenter
        RoadReports.list(activity, c.latitude, c.longitude)?.let {
            reports = it
            renderReports()
        }
    }

    private fun renderReports() {
        reportMarkers.forEach { map.overlays.remove(it) }
        reportMarkers.clear()
        for (r in reports) {
            val type = RoadReports.type(r.type) ?: continue
            if (type.road && !chipRoad.isChecked) continue
            if (!type.road && !chipAddr.isChecked) continue
            val m = Marker(map).apply {
                position = GeoPoint(r.lat, r.lon)
                icon = MapIcons.circle(activity, type.emoji, type.color)
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                setInfoWindow(null)
                setOnMarkerClickListener { _, _ -> showReport(r); true }
            }
            reportMarkers += m
            // Метки — под флажком спроса и точкой «я здесь».
            map.overlays.add(1, m)
        }
        map.invalidate()
    }

    private fun showAddReport(point: GeoPoint) {
        // Последний пункт — не метка, а «Место водителей» (кебаб, мойка, заправка…).
        val items = (RoadReports.TYPES.map { "${it.emoji}  ${activity.getString(it.label)}" } +
                activity.getString(R.string.place_add_item)).toTypedArray()
        com.google.android.material.dialog.MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.map_add_title)
            .setItems(items) { _, which ->
                if (which == RoadReports.TYPES.size) {
                    showAddPlace(point)
                    return@setItems
                }
                val type = RoadReports.TYPES[which]
                scope.launch {
                    val r = RoadReports.add(activity, type.key, point.latitude, point.longitude)
                    val msg = when {
                        r == null -> activity.getString(R.string.clients_no_connection)
                        !r.first -> r.second
                        else -> activity.getString(R.string.map_report_added, activity.getString(type.label))
                    }
                    android.widget.Toast.makeText(activity, msg, android.widget.Toast.LENGTH_SHORT).show()
                    if (r?.first == true) loadReports()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showReport(r: RoadReports.Report) {
        val type = RoadReports.type(r.type) ?: return
        val ago = if (r.createdMs > 0) android.text.format.DateUtils.getRelativeTimeSpanString(
            r.createdMs, System.currentTimeMillis(), android.text.format.DateUtils.MINUTE_IN_MILLIS
        ) else ""
        val vote = { still: Boolean ->
            scope.launch {
                val ok = RoadReports.vote(activity, r.id, still)
                android.widget.Toast.makeText(
                    activity,
                    activity.getString(if (ok) R.string.map_vote_thanks else R.string.clients_no_connection),
                    android.widget.Toast.LENGTH_SHORT
                ).show()
                loadReports()
            }
            Unit
        }
        val b = com.google.android.material.dialog.MaterialAlertDialogBuilder(activity)
            .setTitle("${type.emoji}  ${activity.getString(type.label)}")
            .setMessage(activity.getString(R.string.map_report_ago, ago))
        if (r.mine) {
            b.setPositiveButton(R.string.map_report_remove) { _, _ -> vote(false) }
        } else {
            b.setPositiveButton(R.string.map_still_here) { _, _ -> vote(true) }
            b.setNeutralButton(R.string.map_not_here) { _, _ -> vote(false) }
        }
        b.setNegativeButton(R.string.close, null).show()
    }

    // ---------- места водителей ----------

    private suspend fun loadPlaces() {
        val c = map.mapCenter
        Places.list(activity, c.latitude, c.longitude)?.let {
            places = it
            renderPlaces()
        }
    }

    private fun renderPlaces() {
        placeMarkers.forEach { map.overlays.remove(it) }
        placeMarkers.clear()
        if (chipPlaces.isChecked) {
            for (p in places) {
                val type = Places.type(p.type) ?: continue
                val m = Marker(map).apply {
                    position = GeoPoint(p.lat, p.lon)
                    icon = MapIcons.pin(activity, type.emoji, type.color)
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    setInfoWindow(null)
                    setOnMarkerClickListener { _, _ -> showPlace(p); true }
                }
                placeMarkers += m
                map.overlays.add(1, m)
            }
        }
        map.invalidate()
    }

    private fun showAddPlace(point: GeoPoint) {
        val d = activity.resources.displayMetrics.density
        val pad = (20 * d).toInt()
        var selected = 0
        val box = android.widget.LinearLayout(activity).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(pad, pad / 2, pad, 0)
        }
        val group = com.google.android.material.chip.ChipGroup(activity).apply { isSingleSelection = true; isSelectionRequired = true }
        Places.TYPES.forEachIndexed { i, t ->
            group.addView(com.google.android.material.chip.Chip(activity, null, com.google.android.material.R.attr.chipStyle).apply {
                text = "${t.emoji} ${activity.getString(t.label)}"
                isCheckable = true
                isChecked = i == 0
                id = View.generateViewId()
                setOnClickListener { selected = i }
            })
        }
        box.addView(group)
        val name = android.widget.EditText(activity).apply {
            hint = activity.getString(R.string.place_name_hint)
            filters = arrayOf(android.text.InputFilter.LengthFilter(60))
            isSingleLine = true
        }
        val note = android.widget.EditText(activity).apply {
            hint = activity.getString(R.string.place_note_hint)
            filters = arrayOf(android.text.InputFilter.LengthFilter(200))
            minLines = 2
        }
        box.addView(name)
        box.addView(note)
        com.google.android.material.dialog.MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.place_add_title)
            .setView(android.widget.ScrollView(activity).apply { addView(box) })
            .setPositiveButton(R.string.clients_save) { _, _ ->
                val type = Places.TYPES[selected]
                scope.launch {
                    val r = Places.add(activity, type.key, name.text.toString().trim(), note.text.toString().trim(), point.latitude, point.longitude)
                    val msg = when {
                        r == null -> activity.getString(R.string.clients_no_connection)
                        !r.first -> r.second
                        else -> activity.getString(R.string.place_added)
                    }
                    android.widget.Toast.makeText(activity, msg, android.widget.Toast.LENGTH_SHORT).show()
                    if (r?.first == true) loadPlaces()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showPlace(p: Places.Place) {
        val type = Places.type(p.type) ?: return
        val text = buildString {
            append(activity.getString(type.label))
            if (p.note.isNotBlank()) append("\n\n").append(p.note)
            append("\n\n👍 ${p.up}   👎 ${p.down}")
        }
        val act = { block: suspend () -> Boolean ->
            scope.launch {
                val ok = block()
                android.widget.Toast.makeText(
                    activity,
                    activity.getString(if (ok) R.string.map_vote_thanks else R.string.clients_no_connection),
                    android.widget.Toast.LENGTH_SHORT
                ).show()
                loadPlaces()
            }
            Unit
        }
        val b = com.google.android.material.dialog.MaterialAlertDialogBuilder(activity)
            .setTitle("${type.emoji}  ${p.name}")
            .setMessage(text)
        if (p.mine) {
            b.setPositiveButton(R.string.place_delete) { _, _ -> act { Places.delete(activity, p.id) } }
        } else {
            b.setPositiveButton(if (p.vote == 1) R.string.place_voted_up else R.string.place_up) { _, _ ->
                act { Places.vote(activity, p.id, if (p.vote == 1) 0 else 1) }
            }
            b.setNeutralButton(if (p.vote == -1) R.string.place_voted_down else R.string.place_down) { _, _ ->
                act { Places.vote(activity, p.id, if (p.vote == -1) 0 else -1) }
            }
        }
        b.setNegativeButton(R.string.close, null).show()
    }

    fun destroy() {
        scope.cancel()
        map.onDetach()
    }

    /**
     * Подложка — Яндекс Tiles API (бесплатно, в т.ч. для коммерческого
     * использования). Серверы OpenStreetMap для распространяемых приложений
     * использовать нельзя — они отвечают 403 «Access blocked».
     */
    private fun applyTiles(apiKey: String) {
        if (apiKey.isBlank()) {
            noKey.visibility = View.VISIBLE
            map.setUseDataConnection(false)
            map.overlayManager.tilesOverlay.isEnabled = false
            map.setBackgroundColor(ContextCompat.getColor(activity, R.color.tr_bg))
            return
        }
        noKey.visibility = View.GONE
        map.setUseDataConnection(true)
        map.overlayManager.tilesOverlay.isEnabled = true
        val key = java.net.URLEncoder.encode(apiKey, "UTF-8")
        map.setTileSource(object : OnlineTileSourceBase(
            "YandexDriving", 0, 20, 256, ".png",
            arrayOf("https://tiles.api-maps.yandex.ru/v1/tiles/")
        ) {
            override fun getTileURLString(pMapTileIndex: Long): String =
                "${baseUrl}?apikey=$key&lang=ru_RU&l=map&maptype=driving&projection=web_mercator&scale=2" +
                        "&x=${MapTileIndex.getX(pMapTileIndex)}" +
                        "&y=${MapTileIndex.getY(pMapTileIndex)}" +
                        "&z=${MapTileIndex.getZoom(pMapTileIndex)}"
        })
        map.invalidate()
    }

    @SuppressLint("MissingPermission")
    private fun centerOnMe() {
        val fallback = GeoPoint(FloatingWidgetService.driverLat, FloatingWidgetService.driverLon)
        val granted = ContextCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
        if (!granted) {
            showMe(fallback)
            return
        }
        LocationServices.getFusedLocationProviderClient(activity).lastLocation
            .addOnSuccessListener { loc ->
                lastBearing = loc?.takeIf { it.hasBearing() && it.hasSpeed() && it.speed > 1.5f }?.bearing
                showMe(if (loc != null) GeoPoint(loc.latitude, loc.longitude) else fallback)
            }
            .addOnFailureListener { showMe(fallback) }
    }

    private fun showMe(point: GeoPoint) {
        myMarker?.let { map.overlays.remove(it) }
        myMarker = Marker(map).apply {
            position = point
            icon = MapIcons.me(activity, lastBearing)
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            setInfoWindow(null)
            setOnMarkerClickListener { _, _ -> probe(point); true }
        }
        // Своё местоположение — под флажком.
        map.overlays.add(1, myMarker)
        map.controller.animateTo(point)
        probe(point)
    }

    /** Флажок, который водитель переставляет по карте: тап — переехал, можно и перетащить. */
    private var flag: Marker? = null
    private var pendingPoint: GeoPoint? = null

    private fun moveFlag(point: GeoPoint, label: String, hot: Boolean) {
        val marker = flag ?: Marker(map).apply {
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            setInfoWindow(null)
            isDraggable = true
            setOnMarkerClickListener { _, _ ->
                lastResult?.let { android.widget.Toast.makeText(activity, it, android.widget.Toast.LENGTH_LONG).show() }
                true
            }
            setOnMarkerDragListener(object : Marker.OnMarkerDragListener {
                override fun onMarkerDrag(marker: Marker) {}
                override fun onMarkerDragStart(marker: Marker) {}
                override fun onMarkerDragEnd(marker: Marker) = probe(marker.position)
            })
            map.overlays.add(this)
            flag = this
        }
        marker.position = point
        marker.icon = flagDrawable(label, hot)
        map.invalidate()
    }

    /**
     * Проверяем всегда последнюю выбранную точку: если водитель быстро тыкает
     * по карте, промежуточные точки пропускаются, а запросы идут не чаще раза в секунду.
     */
    private fun probe(point: GeoPoint) {
        moveFlag(point, "…", hot = false)
        lastResult = null
        pendingPoint = point
        if (!requestInFlight) runNextProbe()
    }

    private fun runNextProbe() {
        val point = pendingPoint ?: return
        pendingPoint = null
        requestInFlight = true

        scope.launch {
            val wait = 1000 - (SystemClock.elapsedRealtime() - lastRequestAt)
            if (wait > 0) delay(wait)
            lastRequestAt = SystemClock.elapsedRealtime()
            YandexTaxiSurgeChecker.updateBases(AppConfig.load(activity).surgeBase)
            val surges = YandexTaxiSurgeChecker.getSurgeAll(point.longitude, point.latitude)
            requestInFlight = false

            // Пока шёл запрос, флажок уже переставили — этот ответ устарел.
            if (pendingPoint != null) {
                runNextProbe()
                return@launch
            }
            if (surges == null) {
                moveFlag(point, "?", hot = false)
                lastResult = activity.getString(R.string.map_error)
                return@launch
            }
            val econom = surges["econom"] ?: 0
            val comfort = surges["business"] ?: 0
            val comfortPlus = surges["comfortplus"] ?: 0

            // Только тарифы, включённые в «Спрос в виджете» (ничего — значит Эконом).
            val prefs = activity.getSharedPreferences("taxi_radar_prefs", Context.MODE_PRIVATE)
            data class Row(val short: Int, val full: Int, val value: Int)
            val rows = listOfNotNull(
                Row(R.string.tariff_econom_short, R.string.tariff_econom, econom).takeIf { prefs.getBoolean("show_econom", true) },
                Row(R.string.tariff_comfort_short, R.string.tariff_comfort, comfort).takeIf { prefs.getBoolean("show_comfort", false) },
                Row(R.string.tariff_comfort_plus_short, R.string.tariff_comfort_plus, comfortPlus).takeIf { prefs.getBoolean("show_comfortplus", false) }
            ).ifEmpty { listOf(Row(R.string.tariff_econom_short, R.string.tariff_econom, econom)) }
            lastResult = rows.joinToString("\n") { activity.getString(it.full) + " " + fmt(it.value) }
            moveFlag(
                point,
                rows.joinToString("\n") { activity.getString(it.short) + " " + fmt(it.value) },
                hot = rows.any { it.value > 0 }
            )
        }
    }

    private fun fmt(surge: Int): String = if (surge > 0) "+$surge" else "0"

    /** Пузырёк с цифрой и «хвостиком» вниз — хвостик указывает точно в проверяемую точку. */
    private fun flagDrawable(text: String, hot: Boolean): Drawable {
        val density = activity.resources.displayMetrics.density
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 17 * density
            typeface = Typeface.DEFAULT_BOLD
            color = if (hot) Color.WHITE else ContextCompat.getColor(activity, R.color.tr_on_accent)
        }
        val padH = 12 * density
        val padV = 8 * density
        val tail = 9 * density
        // «Э +35»: буква тарифа мелко, надбавка крупно — как на виджете.
        val labelPaint = Paint(textPaint).apply { textSize = textPaint.textSize * 0.65f }
        fun split(line: String): Pair<String, String> {
            val i = line.indexOf(' ')
            return if (i > 0) line.substring(0, i + 1) to line.substring(i + 1) else "" to line
        }
        fun measure(line: String) = split(line).let { (l, v) -> labelPaint.measureText(l) + textPaint.measureText(v) }
        // Несколько тарифов — по строке на каждый: флажок выше, но не шире.
        val lines = text.split("\n")
        val lineH = textPaint.textSize * 1.25f
        val width = (lines.maxOf { measure(it) } + 2 * padH).toInt().coerceAtLeast((44 * density).toInt())
        val bubble = lineH * lines.size - (lineH - textPaint.textSize) + 2 * padV
        val height = (bubble + tail).toInt()
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ContextCompat.getColor(activity, if (hot) R.color.tr_surge else R.color.tr_accent)
        }
        val radius = minOf(bubble / 2f, 18 * density)
        canvas.drawRoundRect(RectF(0f, 0f, width.toFloat(), bubble), radius, radius, bg)
        val tailPath = android.graphics.Path().apply {
            moveTo(width / 2f - tail, bubble - 1)
            lineTo(width / 2f + tail, bubble - 1)
            lineTo(width / 2f, height.toFloat())
            close()
        }
        canvas.drawPath(tailPath, bg)
        lines.forEachIndexed { i, line ->
            val cy = padV + textPaint.textSize / 2f + i * lineH
            val baseline = cy - (textPaint.descent() + textPaint.ascent()) / 2
            val (label, value) = split(line)
            val x = (width - measure(line)) / 2f
            canvas.drawText(label, x, baseline, labelPaint)
            canvas.drawText(value, x + labelPaint.measureText(label), baseline, textPaint)
        }
        return BitmapDrawable(activity.resources, bitmap)
    }
}
