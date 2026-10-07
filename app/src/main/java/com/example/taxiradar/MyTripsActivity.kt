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

    /** Одна поездка: запись журнала смены (телефон) и/или строка сервера — склеены по номеру поездки. */
    private class Item(val at: Long, val ride: DriverJournal.Ride?, val server: JSONObject?)

    private fun load() {
        Outbox.flush(this)
        lifecycleScope.launch {
            val json = CommunityApi.post(this@MyTripsActivity, "/api/trips/mine")
            val trips = json?.optJSONArray("trips")
            val server = (0 until (trips?.length() ?: 0)).map { trips!!.getJSONObject(it) }
            val local = DriverJournal.rides(this@MyTripsActivity)
            val byKey = server.filter { it.optString("key").isNotBlank() }.associateBy { it.optString("key") }
            val items = (local.map { Item(it.at, it, byKey[it.id]) } +
                server.filter { s -> local.none { it.id == s.optString("key") } }.map { Item(it.optLong("at"), null, it) })
                .sortedByDescending { it.at }
            list.removeAllViews()
            DriverUi.button(this@MyTripsActivity, list, t("+ Добавить поездку вручную", "+ Adaugă cursă manual")) {
                RideEditor.show(this@MyTripsActivity, null) { load() }
            }
            val note = when {
                json == null -> t("Нет связи — показываем то, что сохранено на телефоне. Объяснения цены появятся, когда будет интернет.",
                    "Fără conexiune — arătăm ce e salvat pe telefon. Explicațiile apar când va fi internet.")
                !json.optBoolean("ok") -> json.optString("message").ifBlank { t("Объяснения цены — с активной подпиской.", "Explicațiile — cu abonament activ.") }
                Outbox.size(this@MyTripsActivity) > 0 -> t("Часть поездок ещё отправляется — они появятся здесь целиком, когда дойдут.", "Unele curse încă se trimit — vor apărea complet când ajung.")
                else -> null
            }
            status.visibility = if (note != null || items.isEmpty()) View.VISIBLE else View.GONE
            status.text = note ?: t("Пока поездок нет. Они появятся после первого заказа с запущенным радаром.", "Încă nu sunt curse. Apar după prima comandă cu radarul pornit.")
            items.take(80).forEach { list.addView(card(it)) }
        }
    }

    private fun card(item: Item): View {
        val c = this
        val o = item.server
        val ride = item.ride
        val card = MaterialCardView(c).apply {
            radius = DriverUi.dp(c, 20).toFloat(); strokeWidth = DriverUi.dp(c, 1)
            strokeColor = getColor(if (ride != null && !ride.confirmed) R.color.tr_warning else R.color.tr_console_border)
            setCardBackgroundColor(getColor(R.color.tr_surface))
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = DriverUi.dp(c, 12) }
        }
        val box = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL; setPadding(DriverUi.dp(c, 16), DriverUi.dp(c, 14), DriverUi.dp(c, 16), DriverUi.dp(c, 14)) }
        val date = SimpleDateFormat("dd.MM HH:mm", Locale.getDefault()).format(Date(item.at))
        val tariff = o?.optString("tariff")?.replaceFirstChar { it.uppercase() }.orEmpty()
        box.addView(TextView(c).apply {
            text = listOf(date, tariff, if ((o?.optInt("surge") ?: 0) > 0) "+${o!!.optInt("surge")}" else "").filter { it.isNotBlank() }.joinToString(" · ")
            textSize = 13f; setTextColor(getColor(R.color.tr_text_secondary))
        })
        val from = o?.optString("from")?.takeIf { it.isNotBlank() } ?: ride?.from.orEmpty()
        val to = o?.optString("to")?.takeIf { it.isNotBlank() } ?: ride?.to.orEmpty()
        if (from.isNotBlank() || to.isNotBlank()) box.addView(TextView(c).apply {
            text = "$from → $to"; textSize = 15f; setTypeface(null, Typeface.BOLD); setTextColor(getColor(R.color.tr_text))
            setPadding(0, DriverUi.dp(c, 4), 0, DriverUi.dp(c, 8))
        })
        // Три числа: сколько считал радар, сколько показал Яндекс в конце, сколько вы получили.
        val est = o?.optInt("est_price") ?: ride?.estimate?.takeIf { it > 0 }
        val yandex = o?.takeIf { !it.isNull("real_price") }?.optInt("real_price")
        val got = ride?.takeIf { it.confirmed }?.price
        val row = LinearLayout(c).apply { setPadding(0, DriverUi.dp(c, 4), 0, DriverUi.dp(c, 8)) }
        fun cell(label: String, value: String, color: Int) = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            setPadding(DriverUi.dp(c, 6), DriverUi.dp(c, 8), DriverUi.dp(c, 6), DriverUi.dp(c, 8))
            background = GradientDrawable().apply { cornerRadius = DriverUi.dp(c, 12).toFloat(); setColor(getColor(R.color.tr_surface_high)) }
            addView(TextView(c).apply { text = value; textSize = 18f; gravity = Gravity.CENTER; setTypeface(null, Typeface.BOLD); setTextColor(color) })
            addView(TextView(c).apply { text = label; textSize = 12f; gravity = Gravity.CENTER; setTextColor(getColor(R.color.tr_text_secondary)) })
        }
        val compare = yandex ?: got
        val diffColor = when {
            est == null || compare == null -> getColor(R.color.tr_text)
            abs(compare - est) <= 5 -> getColor(R.color.tr_success)
            abs(compare - est) <= 12 -> getColor(R.color.tr_warning)
            else -> getColor(R.color.tr_danger)
        }
        row.addView(cell(t("Радар", "Radar"), est?.let { "$it L" } ?: "—", getColor(R.color.tr_text)), LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = DriverUi.dp(c, 6) })
        row.addView(cell("Яндекс", yandex?.let { "$it L" } ?: "—", diffColor), LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = DriverUi.dp(c, 6) })
        row.addView(cell(t("Получено", "Încasat"), got?.let { "$it L" } ?: "—", getColor(if (got != null) R.color.tr_success else R.color.tr_text_secondary)), LinearLayout.LayoutParams(0, -2, 1f))
        box.addView(row)
        if (ride != null) {
            if (!ride.confirmed) box.addView(TextView(c).apply {
                text = t("Ждёт проверки: радар записал ${ride.price} L с экрана — подтвердите, сколько получили, и поездка попадёт в итог смены.",
                    "De verificat: radarul a citit ${ride.price} L — confirmați cât ați încasat ca să intre în totalul turei.")
                textSize = 14f; setTextColor(getColor(R.color.tr_warning)); setPadding(0, 0, 0, DriverUi.dp(c, 6))
            }) else if (ride.costsReady) box.addView(TextView(c).apply {
                text = t("Расходы ~${ride.price - ride.net} L · чистыми ~${ride.net} L", "Cheltuieli ~${ride.price - ride.net} L · net ~${ride.net} L")
                textSize = 14f; setTextColor(getColor(R.color.tr_text)); setPadding(0, 0, 0, DriverUi.dp(c, 6))
            })
        }
        o?.optJSONArray("reasons")?.let { reasons ->
            for (i in 0 until reasons.length()) box.addView(TextView(c).apply {
                text = "• " + reasons.getJSONObject(i).optString(if (ro) "ro" else "ru")
                textSize = 14f; setLineSpacing(DriverUi.dp(c, 2).toFloat(), 1f)
                setTextColor(getColor(R.color.tr_text)); setPadding(0, DriverUi.dp(c, 2), 0, DriverUi.dp(c, 2))
            })
        }
        // Кнопки: подтвердить/исправить сумму (журнал смены) и «Цена неверная» (разбор на сервере).
        val buttons = LinearLayout(c).apply { setPadding(0, DriverUi.dp(c, 8), 0, 0) }
        fun btn(label: String, enabled: Boolean, color: Int, action: () -> Unit) = com.google.android.material.button.MaterialButton(c).apply {
            text = label; isAllCaps = false; isEnabled = enabled; cornerRadius = DriverUi.dp(c, 14); minHeight = DriverUi.dp(c, 48)
            backgroundTintList = android.content.res.ColorStateList.valueOf(getColor(R.color.tr_surface_high))
            setTextColor(getColor(color)); setOnClickListener { action() }
        }
        if (ride != null) buttons.addView(btn(if (ride.confirmed) t("Исправить", "Corectează") else t("Подтвердить сумму", "Confirmă suma"), true,
            if (ride.confirmed) R.color.tr_text else R.color.tr_accent) { RideEditor.show(this, ride) { load() } },
            LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = DriverUi.dp(c, 6) })
        if (o != null && yandex != null) {
            val status = o.optString("dispute_status")
            DisputeStatus.text(this, status)?.let { (title, more) ->
                box.addView(TextView(c).apply {
                    text = "$title — $more"; textSize = 14f
                    setTextColor(getColor(if (status == "need_info") R.color.tr_warning else R.color.tr_success))
                    setPadding(0, DriverUi.dp(c, 6), 0, 0)
                })
            }
            val label = if (status.isEmpty()) t("Цена неверная", "Preț greșit") else DisputeStatus.text(this, status)!!.first
            buttons.addView(btn(label, status.isEmpty(), if (status.isEmpty()) R.color.tr_text else R.color.tr_success) { dispute(o.optString("id")) },
                LinearLayout.LayoutParams(0, -2, 1f))
        }
        if (buttons.childCount > 0) box.addView(buttons)
        card.addView(box)
        return card
    }

    private fun dispute(id: String) {
        val reasons = arrayOf("amount", "route", "other")
        val labels = arrayOf(
            t("Яндекс взял другую сумму", "Yandex a luat altă sumă"),
            t("Маршрут был другой", "Traseul a fost altul"),
            t("Другое", "Altceva"))
        var chosen = 0
        val comment = com.google.android.material.textfield.TextInputEditText(this).apply {
            hint = t("Что было не так (необязательно)", "Ce n-a fost bine (opțional)")
            maxLines = 3
        }
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(DriverUi.dp(this@MyTripsActivity, 22), 0, DriverUi.dp(this@MyTripsActivity, 22), 0)
            addView(comment)
        }
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle(t("Почему цена неверная?", "De ce prețul e greșit?"))
            .setSingleChoiceItems(labels, 0) { _, i -> chosen = i }
            .setView(box)
            .setPositiveButton(t("Отправить", "Trimite")) { _, _ ->
                lifecycleScope.launch {
                    val r = CommunityApi.post(this@MyTripsActivity, "/api/trips/dispute", JSONObject()
                        .put("id", id).put("reason", reasons[chosen]).put("comment", comment.text?.toString().orEmpty()))
                    val ok = r?.optBoolean("ok") == true
                    android.widget.Toast.makeText(this@MyTripsActivity,
                        if (ok) t("Спасибо! Разберём, где ошибся расчёт.", "Mulțumim! Vom verifica unde a greșit estimarea.")
                        else t("Не отправилось — нет связи.", "Nu s-a trimis — fără conexiune."),
                        android.widget.Toast.LENGTH_LONG).show()
                    if (ok) load()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
}
