package com.example.taxiradar

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.util.MapTileIndex
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import java.io.File
import java.util.Locale

/**
 * «Поделиться» → Taxi Radar: скриншот карточки Bolt. Распознаём на телефоне (снимок не сохраняется),
 * считаем настоящую подачу и поездку по дорогам с пробками (сервер, TomTom) и показываем цену,
 * L/час, L/км и мини-карту: зелёная точка — вы, красная — клиент, флажок — куда везём.
 */
class ScreenshotShareActivity : AppCompatActivity() {
    override fun attachBaseContext(newBase: Context) = super.attachBaseContext(AppLanguage.wrap(newBase))
    private fun t(ru: String, ro: String) = DriverUi.t(this, ru, ro)
    private fun n(v: Double, digits: Int = 0) = String.format(Locale.getDefault(), "%.${digits}f", v)

    private lateinit var status: TextView
    private lateinit var result: LinearLayout
    private var map: MapView? = null
    private var ocr: ScreenOcrEngine? = null

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(DriverUi.dp(this@ScreenshotShareActivity, 18), DriverUi.dp(this@ScreenshotShareActivity, 20),
                DriverUi.dp(this@ScreenshotShareActivity, 18), DriverUi.dp(this@ScreenshotShareActivity, 24))
            setBackgroundColor(getColor(R.color.tr_bg))
        }
        setContentView(ScrollView(this).apply { addView(box) })
        box.addView(DriverUi.text(this, t("Разбор скриншота", "Analiza capturii"), 24f))
        status = DriverUi.text(this, t("Читаем карточку…", "Citim oferta…"), 15f, true)
        box.addView(status)
        result = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        box.addView(result)

        val uri = imageUri(intent)
        if (uri == null) { status.text = t("Пришлите картинку: скриншот → «Поделиться» → Taxi Radar.", "Trimiteți o imagine: captură → «Distribuie» → Taxi Radar."); return }
        val bmp = try { load(uri) } catch (_: Exception) { null }
        if (bmp == null) { status.text = t("Не удалось открыть картинку.", "Imaginea nu a putut fi deschisă."); return }
        ocr = ScreenOcrEngine(this).also { engine ->
            engine.read(bmp) { texts ->
                val words = DriverPreferences.text(this, "ocr_words")
                val offer = texts.firstNotNullOfOrNull { ScreenOfferParser.parse(ScreenOfferParser.correct(it, words)) }
                if (offer == null) {
                    status.text = t("Карточку заказа не распознал. Нужен скриншот с карточкой Bolt целиком: цена (если есть), подача и поездка в минутах и км, адреса.",
                        "Oferta nu a fost recunoscută. Este nevoie de captura întregului card Bolt: preț (dacă există), preluare și cursă în minute și km, adrese.")
                } else showOffer(offer)
            }
        }
    }

    private fun imageUri(i: Intent): Uri? = when {
        i.action == Intent.ACTION_SEND && Build.VERSION.SDK_INT >= 33 -> i.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        i.action == Intent.ACTION_SEND -> @Suppress("DEPRECATION") i.getParcelableExtra(Intent.EXTRA_STREAM)
        else -> i.data
    }

    /** Картинка в памяти, не больше ~2000 px по длинной стороне. */
    private fun load(uri: Uri): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 2400) sample *= 2
        return contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) }
    }

    private fun showOffer(offer: ScreenOfferParser.Offer) {
        result.removeAllViews()
        val cardLine = listOfNotNull(
            offer.fare?.let { t("Цена ", "Preț ") + n(it, 0) + " L" } ?: t("Цена скрыта", "Preț ascuns"),
            t("подача ", "preluare ") + n(offer.pickupMinutes) + t(" мин · ", " min · ") + n(offer.pickupKm, 1) + " км",
            t("поездка ", "cursă ") + n(offer.tripMinutes) + t(" мин · ", " min · ") + n(offer.tripKm, 1) + " км"
        ).joinToString(" · ")
        result.addView(DriverUi.text(this, t("Bolt на карточке: ", "Bolt pe card: ") + cardLine, 14f, true))
        result.addView(DriverUi.text(this, "А: " + offer.addresses.getOrElse(0) { "" } + "\nБ: " + offer.addresses.getOrElse(1) { "" }, 13f, true))
        // Пока нет маршрута — считаем по минутам и км с карточки.
        render(offer, null)
        if (offer.addresses.size != 2 || offer.addresses.any { it.isBlank() }) {
            status.text = t("Адреса не прочитаны — настоящее время не посчитать, показываем по данным Bolt.", "Adresele nu au fost citite — afișăm după datele Bolt.")
            return
        }
        status.text = t("Считаем настоящее время по дорогам и пробкам…", "Calculăm timpul real după drumuri și trafic…")
        lifecycleScope.launch {
            val fix = ScreenReaderLocation.fix(this@ScreenshotShareActivity)
            if (fix == null) { status.text = t("Нет вашего местоположения — включите GPS. Пока показываем по данным Bolt.", "Lipsește locația — activați GPS. Afișăm după datele Bolt."); return@launch }
            val boarding = DriverPreferences.number(this@ScreenshotShareActivity, "ocr_extra").coerceIn(0.0, 30.0)
            // Скриншот мог быть сделан в другом месте — подачу берём с карточки, считаем только поездку А→Б.
            val reply = withContext(Dispatchers.IO) { ScreenTrafficRoute.calculate(this@ScreenshotShareActivity, fix.latitude, fix.longitude, offer.addresses, boarding, offer, tripOnly = true) }
            val route = reply.route
            if (route == null) {
                status.text = when (reply.error) {
                    "route_not_found" -> t("Адрес не нашёлся на карте — показываем по данным Bolt.", "Adresa nu a fost găsită — afișăm după datele Bolt.")
                    "route_not_configured" -> t("Сервис маршрутов не настроен на сервере — показываем по данным Bolt.", "Serviciul de trasee nu este configurat — afișăm după datele Bolt.")
                    else -> t("Маршрут не посчитался (связь или сервис) — показываем по данным Bolt.", "Traseul nu a fost calculat — afișăm după datele Bolt.")
                }
                return@launch
            }
            status.text = t("Подача — по карточке Bolt; поездка — по дорогам с пробками.", "Preluarea — după cardul Bolt; cursa — după drumuri cu trafic.")
            render(offer, route)
            showMap(fix.latitude, fix.longitude, route)
        }
    }

    /** Цена (или оценка по тарифам Bolt), L/час и L/км по расходам машины. */
    private fun render(offer: ScreenOfferParser.Offer, route: ScreenTrafficRoute.Result?) {
        (0 until result.childCount).map { result.getChildAt(it) }.filter { it.tag == "calc" }.forEach { result.removeView(it) }
        val routed = route?.applyTo(offer) ?: offer
        val estimate = if (offer.fare == null && offer.multiplierKnown) BoltFareEstimator.estimate(offer.boltCategory, routed.tripKm, routed.tripMinutes, offer.multiplier) else null
        val fare = offer.fare ?: estimate
        val boarding = DriverPreferences.number(this, "ocr_extra").coerceIn(0.0, 30.0)
        val lines = mutableListOf<String>()
        lines += when {
            offer.fare != null -> "~${n(offer.fare)} L"
            estimate != null -> "≈ ${n(estimate)} L " + t("(оценка по тарифам Bolt)", "(estimare după tarifele Bolt)")
            else -> t("Цену не оценить: тариф или спрос не прочитаны", "Prețul nu poate fi estimat")
        }
        if (fare != null && DriverPreferences.costsReady(this)) {
            val netFare = offer.fare != null && (offer.afterCommission || DriverPreferences.flag(this, "ocr_net_fare"))
            val m = ScreenOfferParser.metrics(routed.copy(fare = fare, afterCommission = netFare), DriverPreferences.costs(this), boarding)
            val perHour = if (m.minutes > 0) m.net * 60 / m.minutes else null
            lines += t("После расходов ≈ ", "După cheltuieli ≈ ") + n(m.net) + " L"
            lines += (perHour?.let { "≈ ${n(it)} " + t("L/час", "L/oră") + " · " } ?: "") + "≈ ${n(m.perKm, 1)} L/км"
        } else if (fare != null) lines += t("Заполните расходы машины — тогда покажу L/час и L/км", "Completați cheltuielile mașinii pentru L/oră și L/km")
        if (route != null) {
            val bolt = offer.tripMinutes
            val real = route.trip.minutes
            lines += t("Поездка: Bolt ", "Cursă: Bolt ") + n(bolt) + t(" мин · на деле ≈ ", " min · real ≈ ") + n(real) + t(" мин", " min") +
                if (real - bolt >= 3) t(" (Bolt занизил)", " (Bolt a subestimat)") else ""
        } else lines += t("Время по данным Bolt: ", "Timp după Bolt: ") + n(offer.pickupMinutes + offer.tripMinutes) + t(" мин", " min")
        lines.forEachIndexed { i, s ->
            result.addView(DriverUi.text(this, s, if (i == 0) 30f else 16f).apply {
                tag = "calc"
                if (i == 0) setTextColor(getColor(R.color.tr_success))
            }, i)
        }
    }

    private fun dot(color: Int) = GradientDrawable().apply {
        shape = GradientDrawable.OVAL; setColor(color); setStroke(DriverUi.dp(this@ScreenshotShareActivity, 2), Color.WHITE)
        val s = DriverUi.dp(this@ScreenshotShareActivity, 16); setSize(s, s)
    }

    private fun showMap(lat: Double, lon: Double, route: ScreenTrafficRoute.Result) {
        val key = AppConfig.load(this).tilesApiKey
        if (key.isBlank()) return
        Configuration.getInstance().apply {
            userAgentValue = "TaxiRadar/" + packageManager.getPackageInfo(packageName, 0).versionName
            osmdroidBasePath = File(cacheDir, "osmdroid")
            osmdroidTileCache = File(cacheDir, "osmdroid/yandex")
        }
        val view = map ?: MapView(this).also { m ->
            map = m
            val k = java.net.URLEncoder.encode(key, "UTF-8")
            m.setTileSource(object : OnlineTileSourceBase("YandexDriving", 0, 20, 256, ".png", arrayOf("https://tiles.api-maps.yandex.ru/v1/tiles/")) {
                override fun getTileURLString(i: Long) = "${baseUrl}?apikey=$k&lang=ru_RU&l=map&maptype=driving&projection=web_mercator&scale=2" +
                    "&x=${MapTileIndex.getX(i)}&y=${MapTileIndex.getY(i)}&z=${MapTileIndex.getZoom(i)}"
            })
            m.setMultiTouchControls(true)
            m.isTilesScaledToDpi = true
            result.addView(m, LinearLayout.LayoutParams(-1, DriverUi.dp(this, 320)).apply { topMargin = DriverUi.dp(this@ScreenshotShareActivity, 12) })
        }
        view.overlays.clear()
        fun line(points: List<Pair<Double, Double>>, color: Int) = Polyline(view).apply {
            setPoints(points.map { GeoPoint(it.first, it.second) })
            outlinePaint.color = color; outlinePaint.strokeWidth = DriverUi.dp(this@ScreenshotShareActivity, 5).toFloat()
        }
        view.overlays += line(route.trip.points, Color.parseColor("#2B6CB0"))
        fun mark(p: Pair<Double, Double>, icon: android.graphics.drawable.Drawable, anchorBottom: Boolean = false) = Marker(view).apply {
            position = GeoPoint(p.first, p.second); this.icon = icon; title = null
            setAnchor(Marker.ANCHOR_CENTER, if (anchorBottom) Marker.ANCHOR_BOTTOM else Marker.ANCHOR_CENTER)
            setInfoWindow(null)
        }
        // На скриншоте вы были в другом месте — показываем клиента, флажок и поездку.
        val client = route.trip.points.first()
        val dest = route.trip.points.last()
        view.overlays += mark(client, dot(Color.parseColor("#E53935")))
        view.overlays += mark(dest, MapIcons.circle(this, "🏁", Color.WHITE, 30))
        val all = route.trip.points.map { GeoPoint(it.first, it.second) }
        view.post { view.zoomToBoundingBox(BoundingBox.fromGeoPoints(all).increaseByScale(1.25f), false) }
        view.invalidate()
    }

    override fun onResume() { super.onResume(); map?.onResume() }
    override fun onPause() { map?.onPause(); super.onPause() }
    override fun onDestroy() { ocr?.close(); super.onDestroy() }
}
