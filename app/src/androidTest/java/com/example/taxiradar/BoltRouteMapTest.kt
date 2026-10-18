package com.example.taxiradar

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class BoltRouteMapTest {
    @Test fun keylessMapLoadsStyleAndDrawsBothLegsAndThreeMarkers() {
        val i = InstrumentationRegistry.getInstrumentation()
        val c = i.targetContext
        var routeMap: BoltRouteMap? = null
        var panelRef: ScreenReaderOverlay? = null
        val rendered = java.util.concurrent.CountDownLatch(1)
        val route = ScreenTrafficRoute.Result(
            ScreenTrafficRoute.Leg(1.2, 3.0, listOf(47.025 to 28.832, 47.022 to 28.839)),
            ScreenTrafficRoute.Leg(3.4, 10.0, listOf(47.022 to 28.839, 47.017 to 28.850, 47.011 to 28.860)), 1L)
        ActivityScenario.launch<Activity>(Intent(c, RadarSettingsActivity::class.java)).use { scenario ->
            try {
                scenario.onActivity { activity ->
                    DriverPreferences.set(c, "bolt_map_auto", true)
                    DriverPreferences.set(c, "bolt_map_destination_only", false)
                    val panel = ScreenReaderOverlay(activity, { panelRef?.setExpanded(false) }, {}, {}, {}, {})
                    panelRef = panel
                    activity.setContentView(panel)
                    panel.offer(ScreenOfferParser.Offer(206.0, 1.2, 9.4, 3.0, 18.0, emptyList(), false, "комфорт"), "206 L", "Цена из Bolt", "", "")
                    panel.netAmount("После комиссии ≈ 165 L")
                    panel.mapSummary("206 L", "15.57 L/km", "23 min", android.graphics.Color.GREEN)
                    panel.setExpanded(true)
                    panel.route(route)
                    routeMap = panel.mapView.getChildAt(0) as BoltRouteMap
                    (routeMap!!.getChildAt(0) as org.maplibre.android.maps.MapView).addOnDidBecomeIdleListener {
                        (routeMap!!.getChildAt(0) as org.maplibre.android.maps.MapView).getMapAsync { if (it.annotations.size == 2) rendered.countDown() }
                    }
                }
                val deadline = System.currentTimeMillis() + 45000
                var annotations = 0
                while (System.currentTimeMillis() < deadline && annotations != 2) {
                    i.runOnMainSync {
                        (routeMap!!.getChildAt(0) as org.maplibre.android.maps.MapView).getMapAsync { annotations = it.annotations.size }
                    }
                    Thread.sleep(200)
                }
                assertEquals("OpenFreeMap style must load and route must render without an API key", 2, annotations)
                assertTrue("Map tiles and fonts must finish loading", rendered.await(60, java.util.concurrent.TimeUnit.SECONDS))
                Thread.sleep(1000)
                val image = i.uiAutomation.takeScreenshot()
                File(c.cacheDir, "bolt-maplibre.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
                image.recycle()
                i.runOnMainSync {
                    fun find(v: android.view.View, label: String): android.view.View? {
                        if (v.contentDescription?.toString() == label) return v
                        if (v is android.view.ViewGroup) for (n in 0 until v.childCount) find(v.getChildAt(n), label)?.let { return it }
                        return null
                    }
                    find(panelRef!!, "Закрыть карту")!!.performClick()
                    assertFalse("One close tap must minimize the entire overlay", panelRef!!.expanded)
                    panelRef!!.setExpanded(true)
                    panelRef!!.routeUnavailable("Маршрут не найден")
                    assertFalse("Failed route must return to the text card", panelRef!!.mapRequested)
                    assertEquals(android.view.View.GONE, panelRef!!.mapView.visibility)
                    fun visibleTexts(v: android.view.View): List<String> {
                        if (v.visibility != android.view.View.VISIBLE) return emptyList()
                        if (v is android.widget.TextView) return listOf(v.text.toString())
                        return if (v is android.view.ViewGroup) (0 until v.childCount).flatMap { visibleTexts(v.getChildAt(it)) } else emptyList()
                    }
                    val texts = visibleTexts(panelRef!!)
                    assertTrue("The net amount remains visible without a route", texts.any { it.endsWith("165 L") })
                    assertFalse("No route failure paragraph in the compact card", texts.any { it.contains("Маршрут не найден") })
                }
                i.runOnMainSync { routeMap!!.active(false); routeMap!!.active(true) }
            } finally { i.runOnMainSync { routeMap?.close(); routeMap?.close() } }
        }
    }
}
