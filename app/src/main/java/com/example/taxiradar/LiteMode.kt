package com.example.taxiradar

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.os.Build
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * «Лёгкий режим» для слабых телефонов: водитель выбирает, что выключить, и остаётся
 * главное — цена заказа и виджет. Каждая функция проверяет [cuts] у себя.
 */
object LiteMode {
    enum class Feature(val key: String, val ru: String, val ro: String) {
        ROAD("road", "Предупреждения о метках на дороге — постоянный GPS, больше всего садит батарею",
            "Avertizări despre marcaje — GPS permanent, consumă cel mai mult"),
        TRIP("trip", "Разбор экрана во время поездки: надбавка в точке Б, журнал смены, учёт пробок",
            "Analiza ecranului în cursă: supliment în B, jurnalul turei, trafic"),
        SURGE("surge", "Частое обновление надбавки — раз в 3 минуты вместо 1",
            "Actualizare frecventă a suplimentului — o dată la 3 minute în loc de 1"),
        ALERTS("alerts", "Уведомления о росте надбавки и прилёте рейсов",
            "Notificări despre supliment și aterizări"),
        EFFECTS("effects", "Анимации и подсветка кнопок", "Animații și evidențierea butoanelor"),
        LOGS("logs", "Подробный журнал для разбора ошибок", "Jurnal detaliat pentru erori")
    }

    private fun p(c: Context) = c.getSharedPreferences("taxi_radar_prefs", Context.MODE_PRIVATE)
    fun enabled(c: Context) = p(c).getBoolean("lite_mode", false)
    private fun selected(c: Context, f: Feature) = p(c).getBoolean("lite_${f.key}", true)

    /** true — эта функция в лёгком режиме выключена. */
    fun cuts(c: Context, f: Feature) = enabled(c) && selected(c, f)

    /** Мало памяти или старый Android — предлагаем режим сами. */
    fun weakDevice(c: Context): Boolean {
        val am = c.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mem = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
        return am.isLowRamDevice || mem.totalMem < 3L * 1024 * 1024 * 1024 ||
            Runtime.getRuntime().availableProcessors() <= 4 || Build.VERSION.SDK_INT < Build.VERSION_CODES.Q
    }

    fun show(activity: Activity, changed: () -> Unit) {
        fun t(ru: String, ro: String) = DriverUi.t(activity, ru, ro)
        val features = Feature.values()
        val checked = BooleanArray(features.size) { selected(activity, features[it]) }
        val on = enabled(activity)
        val intro = when {
            on -> t("Лёгкий режим включён. Отмеченное выключено — цена заказа и виджет работают как обычно.",
                "Modul ușor este activ. Ce e bifat e oprit — prețul și widgetul funcționează normal.")
            weakDevice(activity) -> t("Для вашего телефона советуем лёгкий режим. Отметьте, что выключить, — цена заказа и виджет останутся.",
                "Pentru telefonul dvs. recomandăm modul ușor. Bifați ce să opriți — prețul și widgetul rămân.")
            else -> t("Если телефон тормозит или греется, отметьте, что выключить. Цена заказа и виджет останутся.",
                "Dacă telefonul se blochează sau se încălzește, bifați ce să opriți. Prețul și widgetul rămân.")
        }
        MaterialAlertDialogBuilder(activity)
            .setMultiChoiceItems(features.map { t(it.ru, it.ro) }.toTypedArray(), checked) { _, i, v -> checked[i] = v }
            .setPositiveButton(if (on) t("Сохранить", "Salvează") else t("Включить", "Activează")) { _, _ -> apply(activity, true, features, checked); changed() }
            .apply { if (on) setNeutralButton(t("Выключить режим", "Dezactivează")) { _, _ -> apply(activity, false, features, checked); changed() } }
            .setNegativeButton(R.string.cancel, null)
            .setCustomTitle(android.widget.TextView(activity).apply {
                text = t("Лёгкий режим", "Mod ușor") + "\n" + intro
                textSize = 15f
                setTextColor(activity.getColor(R.color.tr_text))
                val pad = DriverUi.dp(activity, 22)
                setPadding(pad, pad, pad, DriverUi.dp(activity, 8))
            })
            .show()
    }

    private fun apply(c: Context, on: Boolean, features: Array<Feature>, checked: BooleanArray) {
        p(c).edit().apply {
            putBoolean("lite_mode", on)
            features.forEachIndexed { i, f -> putBoolean("lite_${f.key}", checked[i]) }
        }.apply()
        // Радар работает — сразу применяем: GPS для меток выключится/включится.
        FloatingWidgetService.setRoadAlerts()
    }
}
