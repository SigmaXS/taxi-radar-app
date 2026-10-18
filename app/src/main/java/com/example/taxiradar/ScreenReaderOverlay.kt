package com.example.taxiradar

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.*
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
    private fun metricText(size: Float) = MapMetricText(context).apply {
        textSize = size; setTextColor(context.getColor(R.color.tr_text)); includeFontPadding = false
        setPadding(dp(2), dp(2), dp(2), dp(2))
    }
    private fun surface(color: Int, radius: Int, border: Boolean = false) = GradientDrawable().apply {
        setColor(context.getColor(color)); cornerRadius = dp(radius).toFloat()
        if (border) setStroke(dp(1), context.getColor(R.color.tr_stroke))
    }
    val bubble = FrameLayout(c).apply {
        addView(ImageView(c).apply {
            setImageResource(R.drawable.ic_launcher_foreground)
            background = surface(R.color.tr_bolt_bubble, 22)
            scaleType = ImageView.ScaleType.CENTER_CROP
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }, LayoutParams(dp(44), dp(44), Gravity.CENTER))
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
        clipToOutline = true; elevation = dp(6).toFloat(); setPadding(dp(14), dp(4), dp(10), dp(10))
    }
    private val title = text(12f, true).apply { text = "TAXI RADAR"; setTypeface(null, Typeface.BOLD); maxLines = 1 }
    private val net = metricText(22f)
    private val price = metricText(32f).apply { setTypeface(null, Typeface.BOLD); maxLines = 1 }
    private val source = text(12f, true).apply { maxLines = 2 }
    private val profit = metricText(26f).apply { maxLines = 2; setPadding(0, dp(6), 0, dp(2)); setTypeface(null, Typeface.BOLD) }
    /** «Bolt 35 мин · на деле 55 мин» — главное, сколько реально займёт заказ. */
    private val time = metricText(28f).apply { maxLines = 2; setTypeface(null, Typeface.BOLD); setPadding(0, dp(2), 0, dp(4)) }
    private val travel = text(13f, true).apply { maxLines = 3 }
    private val mapStatus = text(12f, true)
    val mapView = FrameLayout(c).apply { visibility = View.GONE }
    private var autoMapOffer: String? = null
    private var autoMapHandled = false
    private val mapPanel = FrameLayout(c).apply { visibility = View.GONE; background = surface(R.color.tr_surface, 16); clipToOutline = true }
    private val mapPrimary = metricText(38f).apply { setTypeface(null, Typeface.BOLD); maxLines = 1 }
    private val mapRate = metricText(26f).apply { setTypeface(null, Typeface.BOLD); maxLines = 1 }
    private val mapMinutes = metricText(28f).apply { setTypeface(null, Typeface.BOLD); maxLines = 1 }
    private val mapNet = metricText(20f).apply { setTextColor(0xFFFF666F.toInt()); setTypeface(null, Typeface.BOLD) }
    private lateinit var mapAction: TextView
    private lateinit var headerView: LinearLayout
    private lateinit var normalContent: ScrollView
    fun mapSummary(primary: String, rate: String, minutes: String, color: Int) {
        mapPrimary.text = primary; mapPrimary.setTextColor(color)
        mapRate.text = rate; mapMinutes.text = minutes
        mapRate.visibility = if (rate.isBlank()) View.GONE else View.VISIBLE
        mapMinutes.visibility = if (minutes.isBlank()) View.GONE else View.VISIBLE
        profit.text = rate; profit.visibility = if (rate.isBlank()) View.GONE else View.VISIBLE
        time.text = minutes; time.visibility = if (minutes.isBlank()) View.GONE else View.VISIBLE
        mapRate.setTextColor(0xFF7DD3FC.toInt()); mapMinutes.setTextColor(0xFFFFD787.toInt())
    }
    private fun mapMode(value: Boolean) {
        if (value && routeData == null) return
        mapRequested = value
        card.setPadding(if (value) 0 else dp(14), if (value) 0 else dp(4), if (value) 0 else dp(10), if (value) 0 else dp(10))
        headerView.visibility = if (value) View.GONE else View.VISIBLE
        normalContent.visibility = if (value) View.GONE else View.VISIBLE
        mapPanel.visibility = if (value) View.VISIBLE else View.GONE
        if (!value) { routeMap?.close(); routeMap = null; mapView.removeAllViews() }
        mapStatus.text = t("Считаем маршрут…", "Calculăm traseul…")
        mapStatus.visibility = if (value && routeData == null) View.VISIBLE else View.GONE
        showRouteMap(); mapChanged(); modeChanged?.invoke(); requestLayout()
    }
    private var routeMap: BoltRouteMap? = null
    private var routeData: ScreenTrafficRoute.Result? = null
    private var closed = false
    private fun showRouteMap() {
        if (!mapRequested || routeData == null || closed) return
        if (routeMap == null) {
            routeMap = BoltRouteMap(context).also { mapView.addView(it, FrameLayout.LayoutParams(-1, -1)) }
        }
        mapView.visibility = View.VISIBLE; mapStatus.visibility = View.GONE
        routeMap?.show(routeData!!)
        routeMap?.active(expanded)
    }
    var mapRequested = false; private set
    var expanded = false; private set
    var modeChanged: (() -> Unit)? = null
    init {
        androidx.core.widget.TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(price, 22, 32, 1, android.util.TypedValue.COMPLEX_UNIT_SP)
        for ((v, max) in listOf(mapPrimary to 38, mapRate to 26, mapMinutes to 28)) {
            androidx.core.widget.TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(v, 14, max, 1, android.util.TypedValue.COMPLEX_UNIT_SP)
        }
        title.ellipsize = android.text.TextUtils.TruncateAt.END
        addView(bubble, LayoutParams(dp(48), dp(48)))
        addView(card, LayoutParams(-1, -2))
        val header = LinearLayout(c).apply { gravity = Gravity.CENTER_VERTICAL }
        headerView = header
        header.addView(title, LinearLayout.LayoutParams(0, dp(40), 1f).apply { gravity = Gravity.CENTER_VERTICAL })
        title.gravity = Gravity.CENTER_VERTICAL
        fun action(label: String, click: () -> Unit) = text(13f).apply {
            text = label; gravity = Gravity.CENTER; minWidth = dp(64); minHeight = dp(44)
            setTextColor(context.getColor(R.color.tr_cyan)); setPadding(dp(8), 0, dp(8), 0)
            setBackgroundResource(android.R.drawable.list_selector_background)
            contentDescription = label; setOnClickListener { click() }
        }
        mapAction = action(t("Карта", "Hartă")) { mapMode(true) }.apply { visibility = View.GONE }
        header.addView(mapAction)
        header.addView(action("✕", minimize).apply { textSize = 26f; setTypeface(null, Typeface.BOLD); setTextColor(0xFFFF666F.toInt()); contentDescription = t("Свернуть", "Ascunde") })
        card.addView(header)
        val content = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL; setPadding(0, 0, dp(4), 0) }
        val plainMetrics = LinearLayout(c).apply { gravity = Gravity.CENTER_VERTICAL }
        plainMetrics.addView(price, LinearLayout.LayoutParams(0, -2, 1.2f))
        plainMetrics.addView(profit, LinearLayout.LayoutParams(0, -2, 1.1f))
        plainMetrics.addView(time, LinearLayout.LayoutParams(0, -2, 1.1f))
        for (v in listOf(profit, time)) {
            v.maxLines = 1
            androidx.core.widget.TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(v, 14, if (v === time) 28 else 26, 1, android.util.TypedValue.COMPLEX_UNIT_SP)
        }
        content.addView(plainMetrics); content.addView(net)
        normalContent = ScrollView(c).apply { isFillViewport = false; addView(content) }
        card.addView(normalContent, LinearLayout.LayoutParams(-1, -2))
        mapPanel.addView(mapView, LayoutParams(-1, -1))
        val footer = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(8), dp(20), dp(8), 0)
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(0x00102036, 0x30102036, 0x66102036))
        }
        val metrics = LinearLayout(c).apply { gravity = Gravity.CENTER_VERTICAL }
        metrics.addView(mapPrimary, LinearLayout.LayoutParams(0, -2, 1.15f))
        metrics.addView(mapRate, LinearLayout.LayoutParams(0, -2, 1.1f))
        metrics.addView(mapMinutes, LinearLayout.LayoutParams(0, -2, 1.1f))
        footer.addView(metrics); footer.addView(mapNet)
        mapPanel.addView(footer, LayoutParams(-1, -2, Gravity.BOTTOM))
        mapPanel.addView(action("✕") { minimize() }.apply { contentDescription = t("Закрыть карту", "Închide harta"); setTextColor(android.graphics.Color.WHITE); textSize = 22f; setTypeface(null, Typeface.BOLD)
            background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL; setColor(0xEEE13444.toInt()); setStroke(dp(2), android.graphics.Color.WHITE)
            }
            minWidth = dp(40); minHeight = dp(40)
        }, LayoutParams(dp(40), dp(40), Gravity.TOP or Gravity.END))
        mapPanel.addView(mapStatus.apply { visibility = View.GONE }, LayoutParams(-1, dp(48), Gravity.TOP))
        card.addView(mapPanel, LinearLayout.LayoutParams(-1, dp(DriverPreferences.number(c, "bolt_map_height", 240.0).toInt().coerceIn(140, 380))))
        val moveGrip = action("⋮⋮") {}.apply { contentDescription = t("Переместить окно", "Mută panoul") }
        mapPanel.addView(moveGrip, LayoutParams(dp(72), dp(36), Gravity.TOP or Gravity.CENTER_HORIZONTAL))
        bindGrip(moveGrip, false)
        bindGrip(title, false)
        val resizeGrip = action("━") {}.apply { contentDescription = t("Изменить высоту окна", "Schimbă înălțimea panoului") }
        mapPanel.addView(resizeGrip, LayoutParams(dp(48), dp(24), Gravity.BOTTOM or Gravity.END))
        bindGrip(resizeGrip, true)
        setExpanded(false)
    }
    var panelMoved: ((Float) -> Unit)? = null
    var panelResized: ((Int) -> Unit)? = null
    private var mapHeight = DriverPreferences.number(context, "bolt_map_height", 240.0).toInt().coerceIn(140, 380)
    private fun bindGrip(v: View, resize: Boolean) {
        var startY = 0f; var previous = 0f; var height = 0
        v.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                android.view.MotionEvent.ACTION_DOWN -> { startY = e.rawY; previous = e.rawY; height = mapHeight; true }
                android.view.MotionEvent.ACTION_MOVE -> {
                    if (resize) {
                        mapHeight = (height + ((e.rawY - startY) / resources.displayMetrics.density).toInt()).coerceIn(140, 380)
                        DriverPreferences.set(context, "bolt_map_height", mapHeight.toDouble())
                        mapPanel.layoutParams = (mapPanel.layoutParams as LinearLayout.LayoutParams).apply { this.height = dp(mapHeight) }
                        panelResized?.invoke(mapHeight); requestLayout()
                    } else { panelMoved?.invoke(e.rawY - previous); previous = e.rawY }
                    true
                }
                android.view.MotionEvent.ACTION_UP -> { v.performClick(); true }
                else -> true
            }
        }
    }
    fun routeUnavailable(message: String) {
        clearMap(message)
        if (mapRequested) mapMode(false)
        travelStatus(message)
    }
    fun setExpanded(value: Boolean) {
        expanded = value
        card.visibility = if (value) View.VISIBLE else View.GONE
        bubble.visibility = if (value) View.GONE else View.VISIBLE
        routeMap?.active(value && mapRequested)
        modeChanged?.invoke(); requestLayout()
    }
    fun netAmount(value: String) {
        val compact = value
        mapNet.text = compact; mapNet.visibility = if (value.isBlank()) View.GONE else View.VISIBLE
        net.text = compact; net.setTextColor(0xFFFF666F.toInt()); net.visibility = if (value.isBlank()) View.GONE else View.VISIBLE
        mapNet.contentDescription = value; net.contentDescription = value
    }
    private fun applyBoltStyle() {
        (card.background as? GradientDrawable)?.setColor(BoltWidgetStyle.background(context))
        val factor = BoltWidgetStyle.scale(context)
        price.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 32f * factor * BoltWidgetStyle.priceScale(context))
        androidx.core.widget.TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(price, (22 * factor * BoltWidgetStyle.priceScale(context)).toInt(), (32 * factor * BoltWidgetStyle.priceScale(context)).toInt(), 1, android.util.TypedValue.COMPLEX_UNIT_SP)
        profit.textSize = 26f * factor; time.textSize = 28f * factor; net.textSize = 20f * factor
    }
    fun hideDetails(showProfit: Boolean, showTime: Boolean, showSource: Boolean) {
        if (!showProfit) profit.visibility = View.GONE
        if (!showTime) time.visibility = View.GONE
        if (!showSource) { source.visibility = View.GONE; travel.visibility = View.GONE }
        else source.visibility = View.VISIBLE
    }
    override fun onMeasure(w: Int, h: Int) {
        val available = resources.displayMetrics.heightPixels * .55 - dp(DriverPreferences.number(context, "bolt_panel_y", 8.0).toInt())
        val limit = minOf((resources.displayMetrics.heightPixels * (if (mapRequested) .46 else .32)).toInt(), available.toInt().coerceAtLeast(dp(160)))
        val desired = minOf(dp(mapHeight), (limit - if (mapRequested) 0 else dp(46)).coerceAtLeast(dp(100)))
        (mapPanel.layoutParams as? LinearLayout.LayoutParams)?.height = desired
        super.onMeasure(w, if (expanded) MeasureSpec.makeMeasureSpec(limit, MeasureSpec.AT_MOST) else h)
    }
    fun offer(offer: ScreenOfferParser.Offer, shownPrice: String, priceSource: String, netText: String, timeText: String = "") {
        applyBoltStyle()
        if (autoMapOffer != offer.identity) {
            autoMapOffer = offer.identity
            autoMapHandled = false
        }
        title.text = "TAXI RADAR" + (offer.tariff?.let { " · $it" } ?: "")
        price.visibility = if (shownPrice.isNotBlank()) View.VISIBLE else View.GONE
        price.text = shownPrice
        // Скрытую цену (наша оценка) — жёлтым; цену с карточки — обычным.
        price.setTextColor(context.getColor(if (offer.fare == null) R.color.tr_accent else R.color.tr_text))
        source.text = priceSource
        profit.text = netText
        profit.visibility = if (netText.isBlank()) View.GONE else View.VISIBLE
        profit.setTextColor(context.getColor(R.color.tr_accent))
        time.text = timeText
        time.visibility = if (timeText.isBlank()) View.GONE else View.VISIBLE
        time.setTextColor(context.getColor(R.color.tr_accent))
        travel.text = t("Считаем маршрут и прогноз времени…", "Calculăm traseul și durata estimată…")
        bubble.contentDescription = t("Taxi Radar: показать заказ ", "Taxi Radar: afișează oferta ") + shownPrice
    }
    private fun fmt(n: Double) = if (n == n.toInt().toDouble()) n.toInt().toString() else String.format(java.util.Locale.getDefault(), "%.1f", n)
    fun travelStatus(message: String) { if (mapRequested && routeData == null) { mapStatus.text = message; mapStatus.visibility = if (message.isBlank()) View.GONE else View.VISIBLE }; travel.text = message; travel.visibility = if (message.isBlank()) View.GONE else View.VISIBLE }
    fun reading() {
        title.text = "TAXI RADAR"
        net.visibility = View.GONE
        price.visibility = View.GONE; profit.visibility = View.GONE; time.visibility = View.GONE
        source.text = t("Читаем новое предложение…", "Citim oferta nouă…")
        travelStatus(""); clearMap(); if (mapRequested) mapMode(false)
    }
    fun clearMap(message: String = "") {
        mapAction.visibility = View.GONE
        routeData = null; routeMap?.close(); routeMap = null; mapView.removeAllViews(); mapView.visibility = View.GONE
        mapStatus.text = message; mapStatus.visibility = if (mapRequested && message.isNotBlank()) View.VISIBLE else View.GONE
    }
    fun route(result: ScreenTrafficRoute.Result, boardingMinutes: Double = 0.0) {
        routeData = result
        mapAction.visibility = View.VISIBLE
        // Подробности — одной мелкой строкой; главное (время, L/км, L/час) уже крупно выше.
        travelStatus(t("подача ${fmt(result.pickup.km)} км · поездка ${fmt(result.trip.km)} км", "preluare ${fmt(result.pickup.km)} km · cursă ${fmt(result.trip.km)} km"))
        if (!autoMapHandled && DriverPreferences.flag(context, "bolt_map_auto", true)) {
            autoMapHandled = true; mapMode(true)
        } else showRouteMap()
    }
    fun waiting() { autoMapOffer = null; setExpanded(false); bubble.contentDescription = t("Taxi Radar: ожидание заказа", "Taxi Radar: așteptarea ofertei") }
    fun close() { closed = true; routeMap?.close(); routeMap = null }
}
