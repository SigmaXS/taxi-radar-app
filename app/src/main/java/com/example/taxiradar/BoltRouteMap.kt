package com.example.taxiradar

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import org.maplibre.android.MapLibre
import org.maplibre.android.annotations.PolylineOptions
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapLibreMapOptions
import org.maplibre.android.maps.MapView

/** On-demand, keyless map. Routing remains on our existing server. */
@Suppress("DEPRECATION")
class BoltRouteMap(c: Context) : FrameLayout(c) {
    companion object { const val STYLE = "https://tiles.openfreemap.org/styles/dark" }
    private fun dp(n: Int) = DriverUi.dp(context, n)
    private var view: MapView? = null
    private var map: MapLibreMap? = null
    private var data: ScreenTrafficRoute.Result? = null
    private var drawn: ScreenTrafficRoute.Result? = null
    private val markerOverlay = object : View(c) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val m = map ?: return
            val r = data ?: return
            if (!ready) return
            val points = listOf(r.pickup.points.first(), r.trip.points.first(), r.trip.points.last())
            val labels = listOf(DriverUi.t(context, "Вы", "Dvs."), "A", "B")
            val colors = listOf(0xFF68778B.toInt(), 0xFF388CFF.toInt(), 0xFFE54B60.toInt())
            val occupied = mutableListOf<android.graphics.RectF>()
            points.forEachIndexed { index, point ->
                if (DriverPreferences.flag(context, "bolt_map_destination_only") && index != 2) return@forEachIndexed
                val screen = m.projection.toScreenLocation(LatLng(point.first, point.second))
                val x = screen.x; val y = screen.y
                paint.color = colors[index]; canvas.drawCircle(x, y, dp(4).toFloat(), paint)
                paint.style = Paint.Style.STROKE; paint.strokeWidth = dp(1).toFloat(); paint.color = Color.WHITE
                canvas.drawCircle(x, y, dp(4).toFloat(), paint); paint.style = Paint.Style.FILL
                // Move labels to opposite sides; the actual point stays small and precise.
                var labelX = x + dp(if (index == 1) 24 else -24)
                var labelY = y + dp(if (index != 0 || y > height * .5f) -22 else 22)
                labelX = labelX.coerceIn(dp(18).toFloat(), (width - dp(18)).toFloat())
                labelY = labelY.coerceIn(dp(16).toFloat(), (height - dp(18)).toFloat())
                repeat(4) {
                    val bounds = android.graphics.RectF(labelX - dp(16), labelY - dp(12), labelX + dp(16), labelY + dp(12))
                    if (occupied.any { android.graphics.RectF.intersects(it, bounds) }) labelY = (labelY + dp(26)).coerceAtMost((height - dp(14)).toFloat())
                }
                occupied += android.graphics.RectF(labelX - dp(16), labelY - dp(12), labelX + dp(16), labelY + dp(12))
                paint.color = colors[index]; paint.strokeWidth = dp(1).toFloat()
                canvas.drawLine(x, y, labelX, labelY, paint)
                val box = android.graphics.RectF(labelX - dp(14), labelY - dp(10), labelX + dp(14), labelY + dp(10))
                canvas.drawRoundRect(box, dp(7).toFloat(), dp(7).toFloat(), paint)
                paint.color = Color.WHITE; paint.textSize = dp(if (index == 0) 8 else 11).toFloat(); paint.typeface = android.graphics.Typeface.DEFAULT_BOLD; paint.textAlign = Paint.Align.CENTER
                canvas.drawText(labels[index], labelX, labelY - (paint.ascent() + paint.descent()) / 2, paint)
            }
        }
    }
    private var ready = false
    private var running = false
    private var closed = false
    private val status = TextView(c).apply {
        text = DriverUi.t(c, "Загрузка карты…", "Se încarcă harta…")
        textSize = 13f; gravity = Gravity.CENTER; setTextColor(Color.WHITE); setBackgroundColor(0xDD142033.toInt())
    }
    init {
        setBackgroundColor(0xFF142033.toInt())
        // TextureView composes correctly inside a floating Android overlay.
        MapLibre.getInstance(c.applicationContext)
        val native = MapView(c, MapLibreMapOptions.createFromAttributes(c).textureMode(true))
        view = native
        addView(native, LayoutParams(-1, -1))
        addView(markerOverlay, LayoutParams(-1, -1))
        addView(status, LayoutParams(-1, -1))
        val attribution = TextView(c).apply {
            text = "OpenFreeMap · © OpenStreetMap · © OpenMapTiles ⓘ"
            textSize = 9f; setTextColor(0xFFBBC8DA.toInt()); setBackgroundColor(0xA5142033.toInt()); gravity = Gravity.CENTER; setPadding(dp(4), dp(2), dp(4), dp(2))
            contentDescription = DriverUi.t(c, "Источники карты и лицензии", "Sursele hărții și licențe")
            setOnClickListener { c.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://openfreemap.org/#attribution")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }
        addView(attribution, LayoutParams(-2, dp(24), Gravity.TOP or Gravity.START).apply { leftMargin = dp(8); topMargin = dp(4) })
        // Attribution is initially visible; collapsed credits remain accessible in one tap.
        attribution.postDelayed({
            if (!closed) {
                attribution.text = "ⓘ"; attribution.textSize = 16f
                attribution.layoutParams = (attribution.layoutParams as LayoutParams).apply { width = dp(32); height = dp(32) }
            }
        }, 5000)
        native.addOnDidBecomeIdleListener { if (!closed && ready) status.visibility = View.GONE }
        native.onCreate(null)
        native.addOnDidFailLoadingMapListener {
            if (!closed) { status.text = DriverUi.t(c, "Карта не загрузилась. Проверьте интернет и откройте её снова.", "Harta nu s-a încărcat. Verificați conexiunea și redeschideți."); status.visibility = View.VISIBLE }
        }
        native.getMapAsync { m ->
            if (!closed) {
                map = m
                m.addOnCameraMoveListener { markerOverlay.invalidate() }
                m.uiSettings.apply {
                    isAttributionEnabled = false // Explicit attribution below, usable from a Service context.
                    isLogoEnabled = false
                    isRotateGesturesEnabled = false
                    isTiltGesturesEnabled = false
                }
                m.setStyle(if (DriverPreferences.flag(c, "bolt_map_light", true)) "https://tiles.openfreemap.org/styles/liberty" else STYLE) {
                    if (!closed) { ready = true; status.visibility = View.GONE; drawRoute() }
                }
            }
        }
    }
    fun show(result: ScreenTrafficRoute.Result) { data = result; drawRoute() }
    fun active(value: Boolean) {
        if (closed || value == running) return
        running = value
        if (value) { view?.onStart(); view?.onResume() }
        else { view?.onPause(); view?.onStop() }
    }
    private fun drawRoute() {
        val m = map ?: return
        val r = data ?: return
        if (!ready || closed || drawn === r) return
        val pickup = r.pickup.points.map { LatLng(it.first, it.second) }
        val trip = r.trip.points.map { LatLng(it.first, it.second) }
        if (pickup.isEmpty() || trip.isEmpty()) return
        drawn = r
        m.clear()
        m.addPolyline(PolylineOptions().addAll(pickup).color(0xFF5797FF.toInt()).width(5f))
        m.addPolyline(PolylineOptions().addAll(trip).color(0xFF2472FF.toInt()).width(6f))
        markerOverlay.invalidate()
        val bounds = LatLngBounds.Builder().includes(pickup + trip).build()
        post { if (!closed && width > 0 && height > 0) m.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, dp(20), dp(24), dp(20), minOf(dp(72), height / 3))) }
    }
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w != oldw || h != oldh) { drawn = null; drawRoute() }
    }
    fun close() {
        if (closed) return
        active(false); closed = true
        view?.onDestroy(); view = null; map = null; data = null; drawn = null
    }
}
