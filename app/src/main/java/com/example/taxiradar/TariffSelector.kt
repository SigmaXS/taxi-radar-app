package com.example.taxiradar

import android.content.Context
import android.view.Gravity
import android.widget.LinearLayout
import com.google.android.material.button.MaterialButton

/** Equal cells with centered, fitted labels; selection survives upgrades. */
object TariffSelector {
    fun add(c: Context, parent: LinearLayout) {
        parent.addView(DriverUi.text(c, DriverUi.t(c, "Спрос в виджете · тариф", "Cererea în widget · categorie"), 15f))
        val row = LinearLayout(c).apply { id = R.id.groupTariffs; isBaselineAligned = false; gravity = Gravity.TOP }
        val ids = listOf(R.id.cbEconom, R.id.cbComfort, R.id.cbComfortPlus)
        val keys = listOf("show_econom", "show_comfort", "show_comfortplus")
        val labels = listOf(R.string.tariff_econom, R.string.tariff_comfort, R.string.tariff_comfort_plus)
        var selected = keys.indexOfFirst { DriverPreferences.prefs(c).getBoolean(it, it == "show_econom") }.coerceAtLeast(0)
        val buttons = labels.mapIndexed { i, label ->
            MaterialButton(c).apply {
                id = ids[i]; setText(label); isAllCaps = false; gravity = Gravity.CENTER
                maxLines = 1; minimumWidth = 0; minWidth = 0
                setPadding(DriverUi.dp(c, 3), 0, DriverUi.dp(c, 3), 0)
                minHeight = DriverUi.dp(c, if (DriverUi.wide(c)) 64 else 52)
                cornerRadius = DriverUi.dp(c, 12)
                layoutParams = LinearLayout.LayoutParams(0, -2, 1f).apply { if (i < 2) marginEnd = DriverUi.dp(c, 4) }
                androidx.core.widget.TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(this, 8, 14, 1, android.util.TypedValue.COMPLEX_UNIT_SP)
                row.addView(this)
            }
        }
        fun paint() = buttons.forEachIndexed { i, b ->
            b.backgroundTintList = android.content.res.ColorStateList.valueOf(c.getColor(if (i == selected) R.color.tr_cyan else R.color.tr_surface_high))
            b.setTextColor(c.getColor(if (i == selected) R.color.tr_on_accent else R.color.tr_text))
            b.isSelected = i == selected
        }
        buttons.forEachIndexed { i, b -> b.setOnClickListener {
            selected = i
            val edit = DriverPreferences.prefs(c).edit()
            keys.forEachIndexed { k, key -> edit.putBoolean(key, k == i) }; edit.apply()
            paint(); FloatingWidgetService.refreshSurge()
        } }
        paint(); parent.addView(row)
        // Доставка — отдельно: её можно показывать вместе с выбранным тарифом такси.
        DriverUi.toggle(c, parent, DriverUi.t(c, "Ещё и надбавка доставки («Д»)", "Și suplimentul la livrare («L»)"),
            DriverUi.t(c, "Под надбавкой такси появится строка «Д +N» — надбавка Доставки рядом с вами. Старт доставки 24 L.",
                "Sub suplimentul taxi apare rândul «L +N» — suplimentul la Livrare lângă dvs. Start livrare 24 L."),
            DriverPreferences.prefs(c).getBoolean("show_express", false)) {
            DriverPreferences.prefs(c).edit().putBoolean("show_express", it).apply(); FloatingWidgetService.refreshSurge()
        }
    }
}
