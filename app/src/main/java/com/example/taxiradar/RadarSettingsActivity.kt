package com.example.taxiradar

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.content.res.ColorStateList
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class RadarSettingsActivity : AppCompatActivity() {
    override fun attachBaseContext(newBase: Context) = super.attachBaseContext(AppLanguage.wrap(newBase))
    private fun t(ru: String, ro: String) = DriverUi.t(this, ru, ro)

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(DriverUi.dp(this@RadarSettingsActivity, 18), DriverUi.dp(this@RadarSettingsActivity, 24), DriverUi.dp(this@RadarSettingsActivity, 18), DriverUi.dp(this@RadarSettingsActivity, 24))
            setBackgroundColor(getColor(R.color.tr_bg))
        }
        setContentView(ScrollView(this).apply { addView(root) })
        DriverUi.button(this, root, t("‹ Назад", "‹ Înapoi")) { finish() }
        root.addView(DriverUi.text(this, t("Bolt / Яндекс", "Bolt / Yandex"), 28f).apply {
            val label = android.text.SpannableString(text)
            label.setSpan(android.text.style.ForegroundColorSpan(getColor(R.color.tr_success)), 0, 4, 0)
            label.setSpan(android.text.style.ForegroundColorSpan(getColor(R.color.tr_yandex_settings)), 7, label.length, 0)
            text = label
        })
        val mode = DriverUi.card(this, root, t("С какими сервисами работаете?", "Cu ce servicii lucrați?"),
            t("Выбор включает обработку заказов выбранного сервиса. В режиме «Оба» источник определяется по приложению водителя. Расходы машины и смена остаются общими.",
                "Selectează serviciile pentru citirea ofertelor. În modul «Ambele», sursa se identifică după aplicația șoferului. Cheltuielile și tura rămân comune."))
        mode.removeViewAt(2) // Keep the explanation behind the question button.
        mode.removeViewAt(1)
        val buttons = mutableMapOf<String, RadioButton>()
        val first = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        mode.addView(first)
        fun choice(key: String, label: String, parent: LinearLayout) {
            val button = RadioButton(this).apply {
                text = label; textSize = 14f; minHeight = DriverUi.dp(this@RadarSettingsActivity, 48)
                isChecked = RadarServices.selection(this@RadarSettingsActivity) == key
                setOnClickListener {
                    RadarServices.select(this@RadarSettingsActivity, key)
                    buttons.forEach { (k, v) -> v.isChecked = k == key }
                }
            }
            buttons[key] = button
            parent.addView(button, LinearLayout.LayoutParams(if (parent === first) 0 else -2, -2, if (parent === first) 1f else 0f).apply { gravity = android.view.Gravity.CENTER_HORIZONTAL })
        }
        choice("bolt", t("Только Bolt", "Doar Bolt"), first)
        choice("yandex", t("Только Яндекс", "Doar Yandex"), first)
        choice("both", t("Bolt и Яндекс", "Bolt și Yandex"), mode)
        fun service(title: String, description: String, color: Int, action: () -> Unit) {
            val box = DriverUi.card(this, root, title, description, R.drawable.ic_nav_radar)
            DriverUi.button(this, box, t("Настроить ", "Setează ") + title + " ›", action).apply {
                backgroundTintList = ColorStateList.valueOf(getColor(color)); setTextColor(getColor(R.color.tr_on_accent))
            }
        }
        service("Bolt", t("Способ чтения, цена и выгодность, время скрытия, диагностика.", "Citire, preț și rentabilitate, ascundere și diagnostic."), R.color.tr_success) {
            startActivity(Intent(this, ScreenReaderActivity::class.java))
        }
        service(t("Яндекс", "Yandex"), t("Тарифы и надбавки, выгодность и вид виджета. Ваши прежние настройки сохранены.", "Categorii și suplimente, rentabilitate și aspectul widgetului. Setările existente sunt păstrate."), R.color.tr_yandex_settings) {
            startActivity(Intent(this, DriverToolsActivity::class.java).putExtra("mode", "settings"))
        }
        DriverUi.button(this, root, t("Отправить журнал .txt", "Trimite jurnalul .txt")) { TripLog.share(this) }
    }
}
