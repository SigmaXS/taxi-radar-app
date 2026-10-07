package com.example.taxiradar

import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DriverToolsTest {
    private val c get() = InstrumentationRegistry.getInstrumentation().targetContext
    @Test fun journalKeepsDraftsSeparateAndAllowsCorrections() {
        c.getSharedPreferences("driver_journal", 0).edit().clear().commit()
        NetEarnings.save(c, NetEarnings.Settings(true, 8.0, 25.0, 20.0))
        DriverJournal.start(c)
        val id = DriverJournal.add(c, 200, 200, 10.0, 2.0, 20, "", false)
        assertFalse(DriverJournal.rides(c).single().confirmed)
        DriverJournal.confirm(c, id, 180, 9.0, 1.0, 25, "Centru")
        val ride = DriverJournal.rides(c).single()
        assertTrue(ride.confirmed)
        assertTrue(ride.costsReady)
        assertEquals("Centru", ride.area)
        assertEquals(124, ride.net)
        DriverJournal.stop(c)
        assertEquals(ride.shift, DriverJournal.selected(c))
        DriverJournal.remove(c, id)
        assertTrue(DriverJournal.rides(c).isEmpty())
    }
    @Test fun settingsShiftAndPermissionGuideOpenWithoutCrashing() {
        for (mode in listOf("settings", "shift")) {
            ActivityScenario.launch<DriverToolsActivity>(Intent(c, DriverToolsActivity::class.java).putExtra("mode", mode)).use { scenario ->
                scenario.onActivity { activity ->
                    assertNotNull(activity.findViewById<android.view.View>(android.R.id.content))
                    if (mode == "settings") {
                        fun switches(view: android.view.View): List<com.google.android.material.materialswitch.MaterialSwitch> = when (view) {
                            is com.google.android.material.materialswitch.MaterialSwitch -> listOf(view)
                            is android.view.ViewGroup -> (0 until view.childCount).flatMap { switches(view.getChildAt(it)) }
                            else -> emptyList()
                        }
                        val sw = switches(activity.findViewById(android.R.id.content)).first()
                        val expected = !sw.isChecked
                        sw.performClick()
                        assertEquals(expected, DriverPreferences.flag(c, "distance", true))
                        sw.performClick()
                    }
                }
                InstrumentationRegistry.getInstrumentation().waitForIdleSync()
                screenshot(mode)
                if (mode == "settings") {
                    scenario.onActivity { PermissionGuide.show(it) }
                    InstrumentationRegistry.getInstrumentation().waitForIdleSync()
                    screenshot("permissions")
                }
            }
        }
    }
    @Test fun tariffChipsStayInOneRowOnNarrowScreen() {
        ActivityScenario.launch<DriverToolsActivity>(Intent(c, DriverToolsActivity::class.java)).use { scenario ->
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            scenario.onActivity { activity ->
                val chips = listOf(R.id.cbEconom, R.id.cbComfort, R.id.cbComfortPlus).map { activity.findViewById<com.google.android.material.button.MaterialButton>(it) }
                assertEquals(chips[0].top, chips[1].top)
                assertEquals(chips[0].top, chips[2].top)
                val group = activity.findViewById<android.view.View>(R.id.groupTariffs)
                assertTrue(chips[2].right <= group.width)
                assertTrue(chips.all { it.width > 0 })
                assertTrue("Tariff names, including +, must fit in full", chips.all { it.paint.measureText(it.text.toString()) <= it.width - it.paddingLeft - it.paddingRight + 1 })
                assertTrue(chips.all { it.gravity and android.view.Gravity.HORIZONTAL_GRAVITY_MASK == android.view.Gravity.CENTER_HORIZONTAL })
                chips[2].performClick()
                assertTrue(DriverPreferences.prefs(c).getBoolean("show_comfortplus", false))
                assertFalse(DriverPreferences.prefs(c).getBoolean("show_econom", true))
                group.requestRectangleOnScreen(android.graphics.Rect(0, 0, group.width, group.height), true)
            }
            screenshot("tariffs")
        }
    }
    @Test fun dashboardOrderLanguageAndExplanations() {
        DriverPreferences.prefs(c).edit().putBoolean("wizard_done", true).putInt("last_app_version", c.packageManager.getPackageInfo(c.packageName, 0).versionCode).commit()
        ActivityScenario.launch<MainActivity>(Intent(c, MainActivity::class.java)).use { scenario ->
            scenario.onActivity { activity ->
                val helper = activity.findViewById<android.view.View>(R.id.cardDriverTools)
                val check = activity.findViewById<android.view.View>(R.id.btnSetupCheck)
                val parent = helper.parent as android.view.ViewGroup
                assertEquals(parent.indexOfChild(helper) + 1, parent.indexOfChild(check))
                assertNull(activity.findViewById<android.view.View>(R.id.groupTariffs))
                val language = activity.findViewById<android.view.View>(R.id.btnLanguage)
                assertEquals(1, (language.parent as android.view.ViewGroup).indexOfChild(language))
                assertTrue(TripDestination.enabled())
                val explanation = PermissionExplanations.forTitle(activity, activity.getString(R.string.chk_overlay))
                assertTrue(explanation.length > 300)
                assertNotEquals(activity.getString(R.string.chk_overlay_ok), explanation)
            }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            screenshot("radar")
        }
    }
    private fun screenshot(name: String) {
        android.os.SystemClock.sleep(500)
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot() ?: return
        val file = java.io.File(c.getExternalFilesDir(null), "review-$name.png")
        file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}
