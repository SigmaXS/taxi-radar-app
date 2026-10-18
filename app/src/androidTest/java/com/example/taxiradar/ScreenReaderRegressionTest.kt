package com.example.taxiradar

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.view.View
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.json.JSONObject

@RunWith(AndroidJUnit4::class)
class ScreenReaderRegressionTest {
    @Test fun switchingScreensDiscardsTheCancelledRead() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val images = listOf(1, 2).map { n -> instrumentation.context.assets.open("screen-offers/offer-$n.png").use { BitmapFactory.decodeStream(it) } }
        lateinit var engine: ScreenOcrEngine
        val done = CountDownLatch(2)
        var cancelled = listOf("not completed")
        var current = emptyList<String>()
        instrumentation.runOnMainSync {
            engine = ScreenOcrEngine(instrumentation.targetContext)
            engine.read(images[0]) { cancelled = it; done.countDown() }
            engine.cancelCurrent()
            engine.read(images[1]) { current = it; done.countDown() }
        }
        try {
            assertTrue("New screenshot did not finish", done.await(90, TimeUnit.SECONDS))
            assertTrue("Previous screenshot must not be delivered", cancelled.isEmpty())
            assertTrue("Current screenshot must be the 357 MDL offer", current.mapNotNull { ScreenOfferParser.parse(it) }.any { it.fare == 357.0 })
        } finally {
            // Do not recycle input pixels until asynchronous recognition has stopped.
            instrumentation.runOnMainSync { engine.close() }
            if (done.count == 0L) images.forEach { it.recycle() }
        }
    }
    @Test fun routeResponseIsIndependentAndRejectsProviderErrors() {
        val leg = JSONObject().put("km", 12.2).put("minutes", 36.0).put("polyline", "_p~iF~ps|U_ulLnnqC_mqNvxq`@")
        val response = JSONObject().put("ok", true).put("provider", "google").put("calculated_at", 123L).put("pickup", leg).put("trip", leg)
        val result = ScreenTrafficRoute.parse(response).route!!
        assertEquals(36.0, result.trip.minutes, .001)
        assertEquals(3, result.trip.points.size)
        assertNull(ScreenTrafficRoute.parse(JSONObject().put("ok", false).put("code", "route_not_configured")).route)
        leg.put("minutes", -1)
        assertNull(ScreenTrafficRoute.parse(response).route)
    }
    @Test fun nativeOcrReadsAllThreeUserScreenshots() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val target = instrumentation.targetContext
        lateinit var engine: ScreenOcrEngine
        instrumentation.runOnMainSync { engine = ScreenOcrEngine(target) }
        try {
            for (i in 1..4) {
                val image = instrumentation.context.assets.open("screen-offers/offer-$i.png").use { BitmapFactory.decodeStream(it) }
                val done = CountDownLatch(1)
                var candidates = emptyList<ScreenOfferParser.Offer>()
                instrumentation.runOnMainSync {
                    engine.read(image) { texts ->
                        File(target.cacheDir, "ocr-candidates-$i.txt").writeText(texts.joinToString("\n---NEXT ENGINE---\n"))
                        candidates = texts.mapNotNull { ScreenOfferParser.parse(it) }
                        done.countDown()
                    }
                }
                assertTrue("OCR timeout screenshot $i", done.await(90, TimeUnit.SECONDS))
                image.recycle()
                val offer = candidates.maxByOrNull { if (it.fare == null) 0 else 1 }
                assertNotNull("No complete card recognized in screenshot $i", offer)
                when (i) {
                    1 -> { assertNull(offer!!.fare); assertEquals(12.2, offer.tripKm, .001) }
                    2 -> assertEquals(357.0, offer!!.fare!!, .001)
                    3 -> { assertEquals(206.0, offer!!.fare!!, .001); assertEquals(3.0, offer.pickupMinutes, .001) }
                    4 -> {
                        assertEquals(1733.0, offer!!.fare!!, .001)
                        assertEquals(102.0, offer.tripMinutes, .001)
                        assertTrue("Pickup POI must survive without a house number", offer.addresses[0].isNotBlank())
                        assertTrue("Destination town must survive without a house number", offer.addresses[1].isNotBlank())
                    }
                }
            }
        } finally { instrumentation.runOnMainSync { engine.close() } }
    }
    @Test fun cardRendersCollapsedAndExpandedWithoutBlankMap() {
        val i = InstrumentationRegistry.getInstrumentation()
        i.runOnMainSync {
            val c = i.targetContext
            DriverPreferences.set(c, "bolt_map_auto", false)
            val view = ScreenReaderOverlay(c, {}, {}, {}, {}, {})
            try {
                fun save(name: String, width: Int) {
                    view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(c.resources.displayMetrics.heightPixels, View.MeasureSpec.AT_MOST))
                    view.layout(0, 0, view.measuredWidth, view.measuredHeight)
                    val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
                    view.draw(Canvas(bitmap))
                    File(c.cacheDir, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    bitmap.recycle()
                }
                assertFalse(view.expanded); assertEquals(View.GONE, view.mapView.visibility)
                save("screen-reader-bubble.png", DriverUi.dp(c, 56))
                view.offer(ScreenOfferParser.Offer(206.0, 1.2, 9.4, 3.0, 18.0, emptyList(), false, "комфорт"),
                    "206,00 MDL", "Цена из карточки заказа", "После расходов ≈ 164,00 MDL\n15,47 MDL/km · 7,81 MDL/min")
                view.travelStatus("Google Routes ещё не настроен на сервере")
                view.setExpanded(true)
                save("screen-reader-card.png", c.resources.displayMetrics.widthPixels - DriverUi.dp(c, 24))
                assertTrue(view.expanded); assertEquals(View.GONE, view.mapView.visibility)
                assertTrue(view.height <= (c.resources.displayMetrics.heightPixels * .32).toInt())
                view.offer(ScreenOfferParser.Offer(null, 1.6, 12.2, 3.0, 32.0, emptyList(), false, "комфорт"),
                    "≈ 212,64 MDL", "Расчётная цена Bolt · ×1,20 · Google Routes", "≈ 9,98 MDL/km · чистыми\n≈ 141,71 MDL · 3,30 MDL/min")
                view.route(ScreenTrafficRoute.Result(
                    ScreenTrafficRoute.Leg(2.0, 5.0, listOf(47.02 to 28.85, 47.01 to 28.86)),
                    ScreenTrafficRoute.Leg(12.2, 36.0, listOf(47.01 to 28.86, 47.05 to 28.88)), 0), 2.0)
                save("screen-reader-estimate.png", c.resources.displayMetrics.widthPixels - DriverUi.dp(c, 24))
            } finally { view.close() }
        }
    }
}
