package com.example.taxiradar

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.card.MaterialCardView
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

/**
 * «Мои поездки»: поездки, которые радар видел от «Поехали» до «Заказ завершён»,
 * с сервера. У каждой — расчёт радара, итог Яндекса и почему они разошлись.
 */
class MyTripsActivity : AppCompatActivity() {
    private lateinit var list: LinearLayout
    private lateinit var status: TextView
    private fun t(ru: String, ro: String) = DriverUi.t(this, ru, ro)
    private val ro get() = t("ru", "ro") == "ro"

    override fun attachBaseContext(newBase: Context) = super.attachBaseContext(AppLanguage.wrap(newBase))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = DriverUi.dp(this, 18)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(getColor(R.color.tr_bg)) }
        val header = LinearLayout(this).apply { setPadding(pad, DriverUi.dp(this@MyTripsActivity, 12), pad, 0) }
        DriverUi.button(this, header, t("‹  Назад", "‹  Înapoi")) { finish() }.apply {
            backgroundTintList = android.content.res.ColorStateList.valueOf(getColor(R.color.tr_surface_high))
            setTextColor(getColor(R.color.tr_text))
        }
        root.addView(header)
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(pad, DriverUi.dp(this@MyTripsActivity, 12), pad, DriverUi.dp(this@MyTripsActivity, 24)) }
        body.addView(DriverUi.text(this, t("Мои поездки", "Cursele mele"), 28f))
        body.addView(DriverUi.text(this, t(
            "Поездки, которые радар видел от «Поехали» до «Заказ завершён». Под каждой — сколько считал радар, сколько взял Яндекс и почему цена отличается.",
            "Cursele văzute de radar de la «Pornim» până la «Comandă finalizată». Sub fiecare — estimarea radarului, prețul Yandex și de ce diferă."), 14f, true))
        status = DriverUi.text(this, t("Загружаю…", "Se încarcă…"), 15f, true)
        body.addView(status)
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        body.addView(list)
        root.addView(ScrollView(this).apply { addView(body) }, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
        load()
    }

    private fun load() {
        lifecycleScope.launch {
            val json = CommunityApi.post(this@MyTripsActivity, "/api/trips/mine")
            val trips = json?.optJSONArray("trips")
            list.removeAllViews()
            when {
                json == null -> status.text = t("Нет связи с сервером — попробуйте позже.", "Fără conexiune la server — încercați mai târziu.")
                json.optBoolean("ok").not() -> status.text = json.optString("message").ifBlank { t("Нужна активная подписка.", "Este nevoie de abonament activ.") }
                trips == null || trips.length() == 0 -> status.text = t(
                    "Пока поездок нет. Они появятся после первого заказа с запущенным радаром (с версии 1.17).",
                    "Încă nu sunt curse. Apar după prima comandă cu radarul pornit (de la versiunea 1.17).")
                else -> {
                    status.visibility = View.GONE
                    for (i in 0 until trips.length()) list.addView(card(trips.getJSONObject(i)))
                }
            }
        }
    }

    private fun card(o: JSONObject): View {
        val c = this
        val card = MaterialCardView(c).apply {
            radius = DriverUi.dp(c, 20).toFloat(); strokeWidth = DriverUi.dp(c, 1); strokeColor = getColor(R.color.tr_console_border)
            setCardBackgroundColor(getColor(R.color.tr_surface))
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = DriverUi.dp(c, 12) }
        }
        val box = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL; setPadding(DriverUi.dp(c, 16), DriverUi.dp(c, 14), DriverUi.dp(c, 16), DriverUi.dp(c, 14)) }
        val date = SimpleDateFormat("dd.MM HH:mm", Locale.getDefault()).format(Date(o.optLong("at")))
        val tariff = o.optString("tariff").replaceFirstChar { it.uppercase() }
        box.addView(TextView(c).apply {
            text = "$date · $tariff" + (if (o.optInt("surge") > 0) " · +${o.optInt("surge")}" else "")
            textSize = 13f; setTextColor(getColor(R.color.tr_text_secondary))
        })
        val from = o.optString("from"); val to = o.optString("to")
        if (from.isNotBlank() || to.isNotBlank()) box.addView(TextView(c).apply {
            text = "$from → $to"; textSize = 15f; setTypeface(null, Typeface.BOLD); setTextColor(getColor(R.color.tr_text))
            setPadding(0, DriverUi.dp(c, 4), 0, DriverUi.dp(c, 8))
        })
        // Три числа в ряд: радар, Яндекс, разница.
        val est = o.optInt("est_price")
        val real = if (o.isNull("real_price")) null else o.optInt("real_price")
        val row = LinearLayout(c).apply { setPadding(0, DriverUi.dp(c, 4), 0, DriverUi.dp(c, 8)) }
        fun cell(label: String, value: String, color: Int) = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            setPadding(DriverUi.dp(c, 6), DriverUi.dp(c, 8), DriverUi.dp(c, 6), DriverUi.dp(c, 8))
            background = GradientDrawable().apply { cornerRadius = DriverUi.dp(c, 12).toFloat(); setColor(getColor(R.color.tr_surface_high)) }
            addView(TextView(c).apply { text = value; textSize = 18f; gravity = Gravity.CENTER; setTypeface(null, Typeface.BOLD); setTextColor(color) })
            addView(TextView(c).apply { text = label; textSize = 12f; gravity = Gravity.CENTER; setTextColor(getColor(R.color.tr_text_secondary)) })
        }
        val diffColor = when {
            real == null -> getColor(R.color.tr_text_secondary)
            abs(real - est) <= 5 -> getColor(R.color.tr_success)
            abs(real - est) <= 12 -> getColor(R.color.tr_warning)
            else -> getColor(R.color.tr_danger)
        }
        row.addView(cell(t("Радар", "Radar"), "$est L", getColor(R.color.tr_text)), LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = DriverUi.dp(c, 6) })
        row.addView(cell("Яндекс", if (real == null) "—" else "$real L", getColor(R.color.tr_text)), LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = DriverUi.dp(c, 6) })
        row.addView(cell(t("Разница", "Diferență"), if (real == null) "—" else "${if (real - est > 0) "+" else ""}${real - est} L", diffColor), LinearLayout.LayoutParams(0, -2, 1f))
        box.addView(row)
        val reasons = o.optJSONArray("reasons")
        if (reasons != null) for (i in 0 until reasons.length()) {
            val r = reasons.getJSONObject(i)
            box.addView(TextView(c).apply {
                text = "• " + r.optString(if (ro) "ro" else "ru")
                textSize = 14f; setLineSpacing(DriverUi.dp(c, 2).toFloat(), 1f)
                setTextColor(getColor(R.color.tr_text))
                setPadding(0, DriverUi.dp(c, 2), 0, DriverUi.dp(c, 2))
            })
        }
        card.addView(box)
        return card
    }
}
