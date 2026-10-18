package com.example.taxiradar

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.util.MapTileIndex
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import java.io.File

/**
 * Мини-карта маршрута Bolt на том же движке, что карта надбавок (osmdroid, без нативных библиотек —
 * MapLibre добавлял к APK ~20 МБ). Зелёная точка — вы, красная — клиент, флажок — куда везём.
 * Плитки Яндекса по общему ключу (tiles_api_key), без ключа — OpenStreetMap.
 */
class BoltRouteMap(c: Context) : FrameLayout(c) {
    private fun dp(n: Int) = DriverUi.dp(context, n)
    private val map: MapView
    private var data: ScreenTrafficRoute.Result? = null
    private var drawn: ScreenTrafficRoute.Result? = null
    private var running = false
    private var closed = false
    private val status = TextView(c).apply {
        text = DriverUi.t(c, "Загрузка карты…", "Se încarcă harta…")
        textSize = 13f; gravity = Gravity.CENTER; setTextColor(Color.WHITE); setBackgroundColor(0xDD142033.toInt())
    }

    init {
        setBackgroundColor(0xFF142033.toInt())
        Configuration.getInstance().apply {
            userAgentValue = "TaxiRadar/" + runCatching { c.packageManager.getPackageInfo(c.packageName, 0).versionName }.getOrDefault("")
            osmdroidBasePath = File(c.cacheDir, "osmdroid")
            osmdroidTileCache = File(c.cacheDir, "osmdroid/route")
        }
        map = MapView(c).apply {
            setMultiTouchControls(true)
            isTilesScaledToDpi = true
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            val key = AppConfig.load(c).tilesApiKey
            if (key.isNotBlank()) {
                val k = java.net.URLEncoder.encode(key, "UTF-8")
                setTileSource(object : OnlineTileSourceBase("YandexDriving", 0, 20, 256, ".png", arrayOf("https://tiles.api-maps.yandex.ru/v1/tiles/")) {
                    override fun getTileURLString(i: Long) = "${baseUrl}?apikey=$k&lang=ru_RU&l=map&maptype=driving&projection=web_mercator&scale=2" +
                        "&x=${MapTileIndex.getX(i)}&y=${MapTileIndex.getY(i)}&z=${MapTileIndex.getZoom(i)}"
                })
            } else setTileSource(TileSourceFactory.MAPNIK)
        }
        addView(map, LayoutParams(-1, -1))
        addView(status, LayoutParams(-1, -1))
        addView(TextView(c).apply {
            text = if (AppConfig.load(c).tilesApiKey.isNotBlank()) "© Яндекс" else "© OpenStreetMap"
            textSize = 9f; setTextColor(0xFFBBC8DA.toInt()); setBackgroundColor(0xA5142033.toInt()); setPadding(dp(4), dp(2), dp(4), dp(2))
        }, LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.END))
    }

    fun show(result: ScreenTrafficRoute.Result) { data = result; drawRoute() }

    fun active(value: Boolean) {
        if (closed || value == running) return
        running = value
        if (value) map.onResume() else map.onPause()
    }

    private fun dot(color: Int) = GradientDrawable().apply {
        shape = GradientDrawable.OVAL; setColor(color); setStroke(dp(2), Color.WHITE); setSize(dp(14), dp(14))
    }

    private fun drawRoute() {
        val r = data ?: return
        if (closed || drawn === r) return
        val pickup = r.pickup.points.map { GeoPoint(it.first, it.second) }
        val trip = r.trip.points.map { GeoPoint(it.first, it.second) }
        if (trip.isEmpty()) return
        drawn = r
        map.overlays.clear()
        fun line(points: List<GeoPoint>, color: Int, width: Int) {
            if (points.size < 2) return
            map.overlays += Polyline(map).apply { setPoints(points); outlinePaint.color = color; outlinePaint.strokeWidth = dp(width).toFloat() }
        }
        line(pickup, 0xFF9AA5B4.toInt(), 4)
        line(trip, 0xFF2472FF.toInt(), 5)
        fun mark(p: GeoPoint, icon: android.graphics.drawable.Drawable) = Marker(map).apply {
            position = p; this.icon = icon; setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER); setInfoWindow(null)
        }
        val destinationOnly = DriverPreferences.flag(context, "bolt_map_destination_only")
        if (!destinationOnly && pickup.size >= 2) map.overlays += mark(pickup.first(), dot(0xFF2FB344.toInt()))
        if (!destinationOnly) map.overlays += mark(trip.first(), dot(0xFFE53935.toInt()))
        map.overlays += mark(trip.last(), MapIcons.circle(context, "🏁", Color.WHITE, 28))
        val all = pickup + trip
        post {
            if (closed || width <= 0 || height <= 0) return@post
            status.visibility = View.GONE
            if (all.size >= 2) map.zoomToBoundingBox(BoundingBox.fromGeoPoints(all).increaseByScale(1.3f), false)
            else map.controller.setZoom(15.0).also { map.controller.setCenter(all.first()) }
            map.invalidate()
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w != oldw || h != oldh) { drawn = null; drawRoute() }
    }

    fun close() {
        if (closed) return
        active(false); closed = true
        map.onDetach(); data = null; drawn = null
    }
}
