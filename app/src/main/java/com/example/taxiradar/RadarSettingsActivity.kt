package com.example.taxiradar

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class RadarSettingsActivity : AppCompatActivity() {
    override fun attachBaseContext(newBase: Context) = super.attachBaseContext(AppLanguage.wrap(newBase))
    private fun t(ru: String, ro: String) = DriverUi.t(this, ru, ro)
    private fun d(n: Int) = DriverUi.dp(this, n)
    private fun round(color: Int, radius: Int, stroke: Int? = null) = GradientDrawable().apply {
        setColor(getColor(color)); cornerRadius = d(radius).toFloat()
        if (stroke != null) setStroke(d(1), getColor(stroke))
    }

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(d(16), d(12), d(16), d(24))
            setBackgroundColor(getColor(R.color.tr_bg))
        }
        setContentView(ScrollView(this).apply { setBackgroundColor(getColor(R.color.tr_bg)); addView(root) })

        // Шапка: стрелка назад и заголовок в одну строку — вместо большой жёлтой кнопки.
        val top = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(0, 0, 0, d(14)) }
        top.addView(ImageButton(this).apply {
            setImageResource(R.drawable.ic_arrow_back); imageTintList = ColorStateList.valueOf(getColor(R.color.tr_text))
            background = round(R.color.tr_surface, 22, R.color.tr_console_border)
            contentDescription = t("Назад", "Înapoi"); setOnClickListener { finish() }
        }, LinearLayout.LayoutParams(d(44), d(44)).apply { marginEnd = d(14) })
        top.addView(TextView(this).apply {
            text = t("Bolt и Яндекс", "Bolt și Yandex"); textSize = 22f; setTypeface(null, Typeface.BOLD)
            setTextColor(getColor(R.color.tr_text))
        })
        root.addView(top)

        // С какими сервисами работаете — компактный переключатель на три части.
        val head = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        head.addView(TextView(this).apply {
            text = t("С какими сервисами работаете", "Cu ce servicii lucrați"); textSize = 14f
            setTextColor(getColor(R.color.tr_text_secondary))
        }, LinearLayout.LayoutParams(0, -2, 1f))
        head.addView(DriverUi.info(this, t("Сервисы", "Servicii"),
            t("Выбор включает обработку заказов выбранного сервиса. В режиме «Оба» источник определяется по приложению водителя. Расходы машины и смена остаются общими.",
                "Selectează serviciile pentru citirea ofertelor. În modul «Ambele», sursa se identifică după aplicația șoferului. Cheltuielile și tura rămân comune.")))
        root.addView(head, LinearLayout.LayoutParams(-1, -2).apply { marginStart = d(4) })

        val segments = LinearLayout(this).apply {
            setPadding(d(4), d(4), d(4), d(4)); background = round(R.color.tr_surface, 16, R.color.tr_console_border)
        }
        val choices = listOf("bolt" to "Bolt", "yandex" to t("Яндекс", "Yandex"), "both" to t("Оба", "Ambele"))
        val views = mutableMapOf<String, TextView>()
        fun paint() {
            val selected = RadarServices.selection(this)
            views.forEach { (key, v) ->
                val on = key == selected
                v.background = if (on) round(R.color.ic_teal_bg, 12, R.color.ic_teal) else null
                v.setTextColor(getColor(if (on) R.color.ic_teal else R.color.tr_text_secondary))
                v.setTypeface(null, if (on) Typeface.BOLD else Typeface.NORMAL)
            }
        }
        choices.forEach { (key, label) ->
            val v = TextView(this).apply {
                text = label; textSize = 15f; gravity = Gravity.CENTER; minHeight = d(44); isClickable = true
                setOnClickListener { RadarServices.select(this@RadarSettingsActivity, key); paint() }
            }
            views[key] = v
            segments.addView(v, LinearLayout.LayoutParams(0, -2, 1f))
        }
        paint()
        root.addView(segments, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = d(22) })

        // Настройки сервисов и журнал — строки одного вида, спокойные цвета вместо ярких кнопок.
        root.addView(TextView(this).apply {
            text = t("Настройки", "Setări"); textSize = 14f; setTextColor(getColor(R.color.tr_text_secondary))
            setPadding(d(4), 0, 0, d(8))
        })
        DriverUi.navigationCard(this, root, "Bolt", t("Способ чтения, цена и выгодность, время скрытия, диагностика", "Citire, preț și rentabilitate, ascundere și diagnostic"),
            R.drawable.ic_nav_radar, R.color.ic_green, R.color.ic_green_bg) {
            startActivity(Intent(this, ScreenReaderActivity::class.java))
        }
        DriverUi.navigationCard(this, root, t("Яндекс", "Yandex"), t("Тарифы и надбавки, выгодность и вид виджета", "Categorii și suplimente, rentabilitate și aspectul widgetului"),
            R.drawable.ic_nav_radar, R.color.ic_orange, R.color.ic_orange_bg) {
            startActivity(Intent(this, DriverToolsActivity::class.java).putExtra("mode", "settings"))
        }
        DriverUi.navigationCard(this, root, t("Отправить журнал", "Trimite jurnalul"), t("Файл .txt для разбора ошибок", "Fișier .txt pentru analiza erorilor"),
            R.drawable.ic_share, R.color.ic_slate, R.color.ic_slate_bg) { TripLog.share(this) }
    }
}
