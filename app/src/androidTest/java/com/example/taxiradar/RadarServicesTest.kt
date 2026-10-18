package com.example.taxiradar

import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RadarServicesTest {
    private val c get() = InstrumentationRegistry.getInstrumentation().targetContext
    @Test fun selectionsAndReadingMethodsAreIndependent() {
        try {
            RadarServices.select(c, "yandex"); assertTrue(RadarServices.yandex(c)); assertFalse(RadarServices.bolt(c))
            RadarServices.select(c, "bolt"); assertFalse(RadarServices.yandex(c)); assertTrue(RadarServices.bolt(c))
            RadarServices.method(c, "accessibility"); assertTrue(RadarServices.accessibility(c)); assertFalse(RadarServices.ocr(c))
            RadarServices.method(c, "ocr"); assertFalse(RadarServices.accessibility(c)); assertTrue(RadarServices.ocr(c))
            RadarServices.select(c, "both"); assertTrue(RadarServices.yandex(c)); assertTrue(RadarServices.ocr(c))
        } finally { RadarServices.select(c, "both"); RadarServices.method(c, "accessibility") }
    }
    @Test fun finalScreenIsAutoConfirmedOnlyOnceAndStillEditable() {
        c.getSharedPreferences("driver_journal", 0).edit().clear().commit()
        DriverJournal.start(c)
        val screen = listOf("Заказ завершён", "1234 L")
        TripTracker.onScreen(c, "ru.yandex.taximeter", screen)
        TripTracker.onScreen(c, "ru.yandex.taximeter", screen)
        val ride = DriverJournal.rides(c).single()
        assertTrue(ride.confirmed); assertEquals(1234, ride.price)
        DriverJournal.confirm(c, ride.id, 1200, 0.0, 0.0, 1, "")
        assertEquals(1200, DriverJournal.rides(c).single().price)
    }
    @Test fun boltNetIsSeparateAndDoesNotDeductCommissionTwice() {
        DriverPreferences.set(c, "bolt_show_net", true)
        DriverPreferences.set(c, "ocr_with_costs", false)
        DriverPreferences.set(c, "ocr_commission", 20.0)
        assertTrue(BoltWidgetStyle.netLine(c, 200.0, false, 10.0).contains("160 L"))
        assertTrue(BoltWidgetStyle.netLine(c, 200.0, true, 10.0).contains("200 L"))
        DriverPreferences.set(c, "bolt_show_net", false)
        assertEquals("", BoltWidgetStyle.netLine(c, 200.0, false, 10.0))
        DriverPreferences.set(c, "bolt_show_net", true)
        DriverPreferences.set(c, "ocr_net_fare", true)
        val hidden = ScreenOfferParser.Offer(null, 1.0, 10.0, 3.0, 20.0, emptyList(), false)
        assertFalse("A hidden fare estimate is always gross", BoltWidgetStyle.alreadyNet(c, hidden))
        assertEquals(160.0, BoltWidgetStyle.netValue(c, 200.0, BoltWidgetStyle.alreadyNet(c, hidden), 11.0), .001)
        DriverPreferences.set(c, "ocr_net_fare", false)
        ActivityScenario.launch<android.app.Activity>(Intent(c, ScreenReaderActivity::class.java).putExtra("widget_only", true)).use {
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            assertNotNull(it)
        }
        ActivityScenario.launch<android.app.Activity>(Intent(c, DriverToolsActivity::class.java).putExtra("mode", "costs")).use {
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            assertNotNull(it)
        }
    }
    @Test fun newSettingsPagesOpenAndExportIsAReadableTxtAttachment() {
        for (screen in listOf(RadarSettingsActivity::class.java, ScreenReaderActivity::class.java)) {
            ActivityScenario.launch<android.app.Activity>(Intent(c, screen)).use { scenario ->
                scenario.onActivity { assertNotNull(it.findViewById<android.view.View>(android.R.id.content)) }
                InstrumentationRegistry.getInstrumentation().waitForIdleSync()
                val shot = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
                if (shot != null) {
                    java.io.File(c.cacheDir, "${screen.simpleName}.png").outputStream().use { shot.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
                    shot.recycle()
                }
            }
        }
        AppConfig.save(c, org.json.JSONObject().put("bolt_ocr_corrections", "M0L => MDL"))
        assertEquals("206 MDL", ScreenOfferParser.correct("206 M0L", RadarServices.corrections(c)))
        val f = java.io.File(c.cacheDir, "diagnostics/test.txt").apply { parentFile!!.mkdirs(); writeText("Диагностика Bolt") }
        val uri = androidx.core.content.FileProvider.getUriForFile(c, "${c.packageName}.updates", f)
        assertEquals("Диагностика Bolt", c.contentResolver.openInputStream(uri)!!.bufferedReader().use { it.readText() })
        assertEquals("text/plain", c.contentResolver.getType(uri))
    }
}
