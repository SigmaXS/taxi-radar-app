package com.example.taxiradar

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.graphics.Typeface
import android.os.Build
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.materialswitch.MaterialSwitch

/**
 * «Лёгкий режим» для слабых телефонов: водитель выбирает, что выключить, и остаётся
 * главное — цена заказа и виджет с надбавкой. Каждая функция проверяет [cuts] у себя;
 * выключенное заметно сразу: пропадает вкладка «Карта», на главной — зелёная плашка.
 */
object LiteMode {
    enum class Feature(val key: String, val short: Pair<String, String>, val title: Pair<String, String>, val why: Pair<String, String>) {
        MAP("map", "карта" to "harta", "Карта" to "Harta",
            "Вкладка с картой надбавок и меток пропадёт" to "Fila cu harta suplimentelor dispare"),
        ROAD("road", "метки на дороге" to "marcaje", "Предупреждения о метках" to "Avertizări despre marcaje",
            "«Через 400 м радар» — нужен постоянный GPS, он садит батарею сильнее всего" to "«Radar peste 400 m» — GPS permanent, consumă cel mai mult"),
        TRIP("trip", "точка Б и журнал" to "punctul B și jurnalul", "Слежение за поездкой" to "Urmărirea cursei",
            "Надбавка в точке Б, запись поездок в смену, учёт пробок" to "Supliment în B, jurnalul turei, trafic"),
        ALERTS("alerts", "уведомления" to "notificări", "Уведомления о надбавке и рейсах" to "Notificări despre supliment și zboruri",
            "Сообщения «надбавка выросла» и «прилетел рейс»" to "Mesajele «suplimentul a crescut» și «a aterizat zborul»"),
        EFFECTS("effects", "анимации" to "animații", "Анимации" to "Animații",
            "Бегущая подсветка кнопок" to "Evidențierea animată a butoanelor"),
        LOGS("logs", "журнал ошибок" to "jurnal erori", "Подробный журнал" to "Jurnal detaliat",
            "Запись всех текстов экрана для разбора ошибок" to "Înregistrarea textelor pentru erori")
    }

    private fun p(c: Context) = c.getSharedPreferences("taxi_radar_prefs", Context.MODE_PRIVATE)
    fun enabled(c: Context) = p(c).getBoolean("lite_mode", false)
    private fun selected(c: Context, f: Feature) = p(c).getBoolean("lite_${f.key}", true)

    /** true — эта функция в лёгком режиме выключена. */
    fun cuts(c: Context, f: Feature) = enabled(c) && selected(c, f)

    /** «Выключено: карта, метки на дороге…» — для плашки на главной. */
    fun summary(c: Context): String {
        val off = Feature.values().filter { cuts(c, it) }.map { DriverUi.t(c, it.short.first, it.short.second) }
        return if (off.isEmpty()) DriverUi.t(c, "Ничего не выключено", "Nimic nu e oprit")
        else DriverUi.t(c, "Выключено: ", "Oprit: ") + off.joinToString(", ")
    }

    /** Мало памяти или старый Android — предлагаем режим сами. */
    fun weakDevice(c: Context): Boolean {
        val am = c.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mem = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
        return am.isLowRamDevice || mem.totalMem < 3L * 1024 * 1024 * 1024 ||
            Runtime.getRuntime().availableProcessors() <= 4 || Build.VERSION.SDK_INT < Build.VERSION_CODES.Q
    }

    fun show(a: Activity, changed: () -> Unit) {
        fun t(ru: String, ro: String) = DriverUi.t(a, ru, ro)
        fun t(p: Pair<String, String>) = t(p.first, p.second)
        val on = enabled(a)
        val body = LinearLayout(a).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(DriverUi.dp(a, 20), DriverUi.dp(a, 8), DriverUi.dp(a, 20), DriverUi.dp(a, 24))
        }
        body.addView(TextView(a).apply {
            text = "🍃  " + t("Лёгкий режим", "Mod ușor")
            textSize = 22f; gravity = Gravity.CENTER; setTypeface(null, Typeface.BOLD)
            setTextColor(a.getColor(R.color.tr_text))
        })
        body.addView(TextView(a).apply {
            text = when {
                weakDevice(a) && !on -> t("Для вашего телефона советуем включить. ", "Recomandat pentru telefonul dvs. ")
                else -> ""
            } + t("Цена заказа и виджет с надбавкой работают всегда. Выберите, что выключить:",
                "Prețul și widgetul cu supliment funcționează mereu. Alegeți ce să opriți:")
            textSize = 14f; gravity = Gravity.CENTER
            setTextColor(a.getColor(R.color.tr_text_secondary))
            setPadding(0, DriverUi.dp(a, 6), 0, DriverUi.dp(a, 14))
        })
        val switches = Feature.values().map { f ->
            val row = LinearLayout(a).apply {
                gravity = Gravity.CENTER_VERTICAL
                setPadding(DriverUi.dp(a, 14), DriverUi.dp(a, 10), DriverUi.dp(a, 8), DriverUi.dp(a, 10))
                background = android.graphics.drawable.GradientDrawable().apply {
                    cornerRadius = DriverUi.dp(a, 14).toFloat(); setColor(a.getColor(R.color.tr_surface_high))
                }
            }
            val texts = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL }
            texts.addView(TextView(a).apply { text = t(f.title); textSize = 15f; setTypeface(null, Typeface.BOLD); setTextColor(a.getColor(R.color.tr_text)) })
            texts.addView(TextView(a).apply { text = t(f.why); textSize = 13f; setTextColor(a.getColor(R.color.tr_text_secondary)) })
            row.addView(texts, LinearLayout.LayoutParams(0, -2, 1f))
            val sw = MaterialSwitch(a).apply { isChecked = selected(a, f); contentDescription = t(f.title) }
            row.addView(sw)
            row.setOnClickListener { sw.toggle() }
            body.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = DriverUi.dp(a, 8) })
            f to sw
        }
        body.addView(TextView(a).apply {
            text = t("Включённый переключатель = эта функция будет выключена.", "Comutator activ = funcția va fi oprită.")
            textSize = 12f; gravity = Gravity.CENTER
            setTextColor(a.getColor(R.color.tr_text_muted))
            setPadding(0, DriverUi.dp(a, 2), 0, DriverUi.dp(a, 12))
        })
        val dialog = BottomSheetDialog(a)
        fun save(enable: Boolean) {
            p(a).edit().apply {
                putBoolean("lite_mode", enable)
                switches.forEach { (f, sw) -> putBoolean("lite_${f.key}", sw.isChecked) }
            }.apply()
            // Радар работает — применяем сразу: GPS для меток выключится/включится.
            FloatingWidgetService.setRoadAlerts()
            dialog.dismiss(); changed()
        }
        DriverUi.button(a, body, if (on) t("Сохранить", "Salvează") else t("Включить лёгкий режим", "Activează modul ușor")) { save(true) }
        if (on) DriverUi.button(a, body, t("Выключить лёгкий режим", "Dezactivează modul ușor")) { save(false) }.apply {
            backgroundTintList = android.content.res.ColorStateList.valueOf(a.getColor(R.color.tr_surface_high))
            setTextColor(a.getColor(R.color.tr_text))
            (layoutParams as LinearLayout.LayoutParams).topMargin = DriverUi.dp(a, 8)
        }
        dialog.setContentView(ScrollView(a).apply { addView(body) })
        dialog.behavior.state = BottomSheetBehavior.STATE_EXPANDED
        dialog.behavior.skipCollapsed = true
        dialog.show()
    }
}
