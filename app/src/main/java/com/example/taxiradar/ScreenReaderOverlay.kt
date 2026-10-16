package com.example.taxiradar

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.*
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.PolylineOptions

/** Compact price-first card; waiting is a bubble, never an empty map. */
class ScreenReaderOverlay(c: Context, private val minimize: () -> Unit,
    private val open: () -> Unit, private val settings: () -> Unit, private val stop: () -> Unit,
    private val mapChanged: () -> Unit) : FrameLayout(c) {
    private fun dp(n: Int) = DriverUi.dp(context, n)
    private fun t(ru: String, ro: String) = DriverUi.t(context, ru, ro)
    private fun text(size: Float, muted: Boolean = false) = TextView(context).apply {
        textSize = size; setTextColor(context.getColor(if (muted) R.color.tr_text_secondary else R.color.tr_text))
        includeFontPadding = false
    }
    private fun surface(color: Int, radius: Int, border: Boolean = false) = GradientDrawable().apply {
        setColor(context.getColor(color)); cornerRadius = dp(radius).toFloat()
        if (border) setStroke(dp(1), context.getColor(R.color.tr_stroke))
    }
    val bubble = text(17f).apply {
        text = "TR"; gravity = Gravity.CENTER; setTypeface(null, Typeface.BOLD)
        setTextColor(context.getColor(R.color.tr_on_accent)); background = surface(R.color.tr_accent, 28)
        contentDescription = t("Taxi Radar: раскрыть последний заказ. Долгое нажатие — настройки и остановка.", "Taxi Radar: deschide ultima ofertă. Apăsare lungă — setări și oprire.")
        setOnClickListener { open() }
        setOnLongClickListener {
            PopupMenu(context, this).apply {
                menu.add(t("Настройки", "Setări")).setOnMenuItemClickListener { settings(); true }
                menu.add(t("Остановить чтение", "Oprește citirea")).setOnMenuItemClickListener { stop(); true }
            }.show(); true
        }
    }
    private val card = LinearLayout(c).apply {
        orientation = LinearLayout.VERTICAL; background = surface(R.color.tr_surface, 20, true)
        elevation = dp(6).toFloat(); setPadding(dp(14), dp(4), dp(10), dp(10))
    }
    private val title = text(12f, true).apply { text = "TAXI RADAR"; setTypeface(null, Typeface.BOLD); maxLines = 1 }
    private val price = text(32f).apply { setTypeface(null, Typeface.BOLD); maxLines = 1 }
    private val source = text(12f, true).apply { maxLines = 2 }
    private val profit = text(24f).apply { maxLines = 2; setPadding(0, dp(6), 0, dp(2)); setTypeface(null, Typeface.BOLD) }
    /** «Bolt 35 мин · на деле 55 мин» — главное, сколько реально займёт заказ. */
    private val time = text(20f).apply { maxLines = 2; setTypeface(null, Typeface.BOLD); setPadding(0, dp(2), 0, dp(4)) }
    private val travel = text(13f, true).apply { maxLines = 3 }
    private val mapStatus = text(12f, true)
    val mapView = FrameLayout(c).apply { visibility = View.GONE }
    private var googleView: MapView? = null
    private var googleMap: GoogleMap? = null
    private var routeData: ScreenTrafficRoute.Result? = null
    private var closed = false
    private fun mapsConfigured(): Boolean = runCatching {
        context.packageManager.getApplicationInfo(context.packageName, android.content.pm.PackageManager.GET_META_DATA)
            .metaData?.getString("com.google.android.geo.API_KEY").orEmpty().isNotBlank()
    }.getOrDefault(false)
    private fun showGoogleRoute() {
        if (!mapRequested || routeData == null || closed) return
        if (!mapsConfigured()) {
            mapStatus.text = t("Для карты нужен ключ Google Maps в сборке", "Harta necesită cheia Google Maps în aplicație")
            mapStatus.visibility = View.VISIBLE
            return
        }
        if (googleView == null) {
            googleView = MapView(context).also { view ->
                mapView.addView(view, FrameLayout.LayoutParams(-1, -1))
                view.onCreate(null); view.onStart(); view.onResume()
                view.getMapAsync { map ->
                    if (!closed) { googleMap = map; map.uiSettings.isMapToolbarEnabled = false; showGoogleRoute() }
                }
            }
        }
        val map = googleMap ?: return
        val r = routeData ?: return
        map.clear()
        val pickup = r.pickup.points.map { LatLng(it.first, it.second) }
        val trip = r.trip.points.map { LatLng(it.first, it.second) }
        map.addPolyline(PolylineOptions().addAll(pickup).color(context.getColor(R.color.tr_accent)).width(dp(3).toFloat()))
        map.addPolyline(PolylineOptions().addAll(trip).color(context.getColor(R.color.tr_cyan)).width(dp(3).toFloat()))
        listOf(pickup.first(), trip.first(), trip.last()).forEachIndexed { index, p ->
            map.addMarker(MarkerOptions().position(p).title(listOf(t("Вы", "Dvs."), t("Подача", "Preluare"), t("Назначение", "Destinație"))[index]))
        }
        val bounds = LatLngBounds.builder().apply { (pickup + trip).forEach { include(it) } }.build()
        mapView.visibility = View.VISIBLE; mapStatus.visibility = View.GONE
        mapView.post { if (!closed && mapView.width > 0 && mapView.height > 0) map.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, dp(18))) }
    }
    var mapRequested = true; private set
    var expanded = false; private set
    var modeChanged: (() -> Unit)? = null
    init {
        androidx.core.widget.TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(price, 22, 32, 1, android.util.TypedValue.COMPLEX_UNIT_SP)
        title.ellipsize = android.text.TextUtils.TruncateAt.END
        addView(bubble, LayoutParams(dp(56), dp(56)))
        addView(card, LayoutParams(-1, -2))
        val header = LinearLayout(c).apply { gravity = Gravity.CENTER_VERTICAL }
        header.addView(title, LinearLayout.LayoutParams(0, dp(40), 1f).apply { gravity = Gravity.CENTER_VERTICAL })
        title.gravity = Gravity.CENTER_VERTICAL
        fun action(label: String, click: () -> Unit) = text(13f).apply {
            text = label; gravity = Gravity.CENTER; minWidth = dp(64); minHeight = dp(44)
            setTextColor(context.getColor(R.color.tr_cyan)); setPadding(dp(8), 0, dp(8), 0)
            setBackgroundResource(android.R.drawable.list_selector_background)
            contentDescription = label; setOnClickListener { click() }
        }
        header.addView(action(t("Карта", "Hartă")) {
            mapRequested = !mapRequested
            mapView.visibility = View.GONE
            mapStatus.visibility = if (mapRequested) View.VISIBLE else View.GONE
            showGoogleRoute(); mapChanged(); requestLayout()
        })
        header.addView(action(t("Свернуть", "Ascunde"), minimize))
        card.addView(header)
        val content = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL; setPadding(0, 0, dp(4), 0) }
        content.addView(price); content.addView(profit); content.addView(time); content.addView(source); content.addView(travel)
        content.addView(mapView, LinearLayout.LayoutParams(-1, dp(140)).apply { topMargin = dp(8) })
        content.addView(mapStatus.apply { visibility = View.GONE })
        card.addView(ScrollView(c).apply { isFillViewport = false; addView(content) }, LinearLayout.LayoutParams(-1, -2))
        setExpanded(false)
    }
    fun setExpanded(value: Boolean) {
        expanded = value
        card.visibility = if (value) View.VISIBLE else View.GONE
        bubble.visibility = if (value) View.GONE else View.VISIBLE
        modeChanged?.invoke(); requestLayout()
    }
    override fun onMeasure(w: Int, h: Int) {
        val limit = (resources.displayMetrics.heightPixels * .32).toInt()
        super.onMeasure(w, if (expanded) MeasureSpec.makeMeasureSpec(limit, MeasureSpec.AT_MOST) else h)
    }
    fun offer(offer: ScreenOfferParser.Offer, shownPrice: String, priceSource: String, netText: String, timeText: String = "") {
        title.text = "TAXI RADAR" + (offer.tariff?.let { " · $it" } ?: "")
        price.visibility = if (shownPrice.isNotBlank()) View.VISIBLE else View.GONE
        price.text = shownPrice
        // Скрытую цену (наша оценка) — жёлтым; цену с карточки — обычным.
        price.setTextColor(context.getColor(if (offer.fare == null) R.color.tr_accent else R.color.tr_text))
        source.text = priceSource
        profit.text = netText
        profit.setTextColor(context.getColor(R.color.tr_accent))
        time.text = timeText
        time.visibility = if (timeText.isBlank()) View.GONE else View.VISIBLE
        time.setTextColor(context.getColor(R.color.tr_accent))
        travel.text = t("Считаем маршрут и прогноз времени…", "Calculăm traseul și durata estimată…")
        bubble.contentDescription = t("Taxi Radar: показать заказ ", "Taxi Radar: afișează oferta ") + shownPrice
    }
    private fun fmt(n: Double) = if (n == n.toInt().toDouble()) n.toInt().toString() else String.format(java.util.Locale.getDefault(), "%.1f", n)
    fun travelStatus(message: String) { travel.text = message; travel.visibility = if (message.isBlank()) View.GONE else View.VISIBLE }
    fun clearMap(message: String = "") {
        routeData = null; googleMap?.clear(); mapView.visibility = View.GONE
        mapStatus.text = message; mapStatus.visibility = if (mapRequested && message.isNotBlank()) View.VISIBLE else View.GONE
    }
    fun route(result: ScreenTrafficRoute.Result, boardingMinutes: Double = 0.0) {
        routeData = result
        // Подробности — одной мелкой строкой; главное (время, L/км, L/час) уже крупно выше.
        travelStatus(t("подача ${fmt(result.pickup.km)} км · поездка ${fmt(result.trip.km)} км", "preluare ${fmt(result.pickup.km)} km · cursă ${fmt(result.trip.km)} km"))
        showGoogleRoute()
    }
    fun waiting() { bubble.contentDescription = t("Taxi Radar: ожидание заказа", "Taxi Radar: așteptarea ofertei") }
    fun close() { closed = true; googleView?.onPause(); googleView?.onStop(); googleView?.onDestroy(); googleView = null; googleMap = null }
}
