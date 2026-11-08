package com.example.taxiradar

import android.app.Activity
import android.content.Context
import android.graphics.Typeface
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.example.taxiradar.throttle.ThrottleAuto
import com.example.taxiradar.throttle.ThrottleBubbleService
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.card.MaterialCardView

/**
 * Что водитель использует: радар + «Отмена», только радар или только «Отмена».
 * Спрашиваем при первом запуске, менять можно на вкладке «Отмена» и в окне «Лёгкий режим».
 *  • both   — всё работает;
 *  • radar  — «Отмена» выключена, вкладка скрыта;
 *  • cancel — радар выключен (остаётся одна «Отмена», телефон почти не нагружается).
 */
object UsageMode {
    const val BOTH = "both"
    const val RADAR = "radar"
    const val CANCEL = "cancel"

    private const val KEY = "usage_mode"

    /** MainActivity подписывается, чтобы скрыть/показать вкладку «Отмена» после смены режима. */
    @Volatile
    var listener: (() -> Unit)? = null

    private fun p(c: Context) = c.getSharedPreferences("taxi_radar_prefs", Context.MODE_PRIVATE)

    /** Выбран ли режим явно (иначе при запуске спросим). */
    fun chosen(c: Context) = p(c).contains(KEY)

    fun mode(c: Context): String {
        p(c).getString(KEY, null)?.let { return it }
        // Раньше был переключатель «Только Отмена» — его значение переходит в новый режим.
        return if (p(c).getBoolean("cancel_only", false)) CANCEL else BOTH
    }

    /** «Отмену» можно включать. */
    fun cancelAllowed(c: Context) = mode(c) != RADAR

    fun set(c: Context, mode: String) {
        p(c).edit().putString(KEY, mode).apply()
        when (mode) {
            CANCEL -> CancelOnly.set(c, true)
            RADAR -> {
                CancelOnly.set(c, false)
                // Без «Отмены»: выключаем скрипт, кнопку и туннель.
                ThrottleAuto.setEnabled(c, false)
                ThrottleBubbleService.stop(c)
            }
            else -> CancelOnly.set(c, false)
        }
        listener?.invoke()
    }

    fun label(c: Context, mode: String = mode(c)) = when (mode) {
        RADAR -> DriverUi.t(c, "Только радар", "Doar radar")
        CANCEL -> DriverUi.t(c, "Только «Отмена»", "Doar «Anulare»")
        else -> DriverUi.t(c, "Радар + «Отмена»", "Radar + «Anulare»")
    }

    private class Option(val mode: String, val emoji: String, val title: String, val sub: String, val badge: String? = null)

    /** Окно выбора: три крупные карточки. На первом запуске закрыть без выбора нельзя — остаётся «всё включено». */
    fun show(a: Activity, firstRun: Boolean, changed: () -> Unit = {}) {
        fun t(ru: String, ro: String) = DriverUi.t(a, ru, ro)
        val current = mode(a)
        val options = listOf(
            Option(BOTH, "📡🚫", t("Радар + «Отмена»", "Radar + «Anulare»"),
                t("Цена заказа, надбавка, карта и «Отмена» — всё вместе", "Preț, supliment, hartă și «Anulare» — toate împreună"),
                t("Рекомендуем", "Recomandat")),
            Option(RADAR, "📡", t("Только радар", "Doar radar"),
                t("Цена заказа, надбавка и карта. «Отмена» выключена, вкладка скрыта", "Preț, supliment și hartă. «Anularea» e oprită, fila ascunsă")),
            Option(CANCEL, "🚫", t("Только «Отмена»", "Doar «Anulare»"),
                t("Лёгкий режим: радар выключен, телефон почти не нагружается", "Mod ușor: radarul e oprit, telefonul aproape nu se încarcă")),
        )

        val body = LinearLayout(a).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(DriverUi.dp(a, 20), DriverUi.dp(a, 12), DriverUi.dp(a, 20), DriverUi.dp(a, 28))
        }
        body.addView(TextView(a).apply {
            text = t("Что будете использовать?", "Ce veți folosi?")
            textSize = 22f; gravity = Gravity.CENTER; setTypeface(null, Typeface.BOLD)
            setTextColor(a.getColor(R.color.tr_text))
        })
        body.addView(TextView(a).apply {
            text = t("Это можно поменять потом на вкладке «Отмена»", "Se poate schimba mai târziu în fila «Anulare»")
            textSize = 14f; gravity = Gravity.CENTER
            setTextColor(a.getColor(R.color.tr_text_secondary))
            setPadding(0, DriverUi.dp(a, 6), 0, DriverUi.dp(a, 16))
        })

        val dialog = BottomSheetDialog(a)
        var picked = false
        options.forEach { o ->
            val selected = o.mode == current && !firstRun
            val card = MaterialCardView(a).apply {
                radius = DriverUi.dp(a, 20).toFloat()
                cardElevation = 0f // без тени: лишняя нагрузка на отрисовку
                setCardBackgroundColor(a.getColor(R.color.tr_surface_high))
                strokeWidth = DriverUi.dp(a, if (selected) 2 else 1)
                strokeColor = a.getColor(if (selected) R.color.tr_accent else R.color.tr_stroke)
                isClickable = true; isFocusable = true
            }
            val row = LinearLayout(a).apply {
                gravity = Gravity.CENTER_VERTICAL
                setPadding(DriverUi.dp(a, 16), DriverUi.dp(a, 16), DriverUi.dp(a, 16), DriverUi.dp(a, 16))
            }
            row.addView(TextView(a).apply {
                text = o.emoji; textSize = 28f; gravity = Gravity.CENTER
                minWidth = DriverUi.dp(a, 56)
            })
            val texts = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL }
            val head = LinearLayout(a).apply { gravity = Gravity.CENTER_VERTICAL }
            head.addView(TextView(a).apply {
                text = o.title; textSize = 17f; setTypeface(null, Typeface.BOLD)
                setTextColor(a.getColor(R.color.tr_text))
            })
            o.badge?.let { b ->
                head.addView(TextView(a).apply {
                    text = b; textSize = 11f; setTypeface(null, Typeface.BOLD)
                    setTextColor(a.getColor(R.color.tr_on_accent))
                    setPadding(DriverUi.dp(a, 8), DriverUi.dp(a, 2), DriverUi.dp(a, 8), DriverUi.dp(a, 2))
                    background = android.graphics.drawable.GradientDrawable().apply {
                        cornerRadius = DriverUi.dp(a, 10).toFloat(); setColor(a.getColor(R.color.tr_accent))
                    }
                }, LinearLayout.LayoutParams(-2, -2).apply { marginStart = DriverUi.dp(a, 8) })
            }
            texts.addView(head)
            texts.addView(TextView(a).apply {
                text = o.sub; textSize = 13f
                setTextColor(a.getColor(R.color.tr_text_secondary))
                setPadding(0, DriverUi.dp(a, 3), 0, 0)
            })
            row.addView(texts, LinearLayout.LayoutParams(0, -2, 1f))
            card.addView(row)
            card.setOnClickListener {
                picked = true
                set(a, o.mode)
                dialog.dismiss()
                changed()
            }
            body.addView(card, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = DriverUi.dp(a, 12) })
        }

        dialog.setContentView(ScrollView(a).apply { addView(body) })
        dialog.behavior.state = BottomSheetBehavior.STATE_EXPANDED
        dialog.behavior.skipCollapsed = true
        if (firstRun) {
            dialog.setCancelable(false)
            dialog.setCanceledOnTouchOutside(false)
        }
        dialog.setOnDismissListener {
            // Закрыли без выбора (только не на первом запуске): ничего не меняем.
            if (!picked && firstRun) set(a, BOTH)
        }
        dialog.show()
    }
}

/**
 * Батарея как ошибка: на Xiaomi/Redmi/POCO оболочка сама сбрасывает системное исключение, хотя в настройках «Нет ограничений»
 * стоит, и приложение видело ложную ошибку. Работе это не мешает, поэтому там батарею ошибкой не считаем.
 */
object BatteryCheck {
    private val xiaomi = setOf("xiaomi", "redmi", "poco", "blackshark")

    fun isXiaomi(): Boolean =
        android.os.Build.MANUFACTURER.lowercase() in xiaomi || android.os.Build.BRAND.lowercase() in xiaomi

    /** true — это настоящая проблема, которую надо показать красным. */
    fun isProblem(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
        return !pm.isIgnoringBatteryOptimizations(context.packageName) && !isXiaomi()
    }
}
