package com.example.taxiradar

import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.card.MaterialCardView
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** «События»: где и когда начнут выходить люди — «Сегодня» и «Ближайшие дни», напоминание перед концом. */
class EventsActivity : AppCompatActivity() {
    private lateinit var list: LinearLayout
    private fun t(ru: String, ro: String) = DriverUi.t(this, ru, ro)
    override fun attachBaseContext(newBase: Context) = super.attachBaseContext(AppLanguage.wrap(newBase))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = DriverUi.dp(this, 18)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(getColor(R.color.tr_bg)) }
        val header = LinearLayout(this).apply { setPadding(pad, DriverUi.dp(this@EventsActivity, 12), pad, 0) }
        DriverUi.button(this, header, t("‹  Назад", "‹  Înapoi")) { finish() }.apply {
            backgroundTintList = android.content.res.ColorStateList.valueOf(getColor(R.color.tr_surface_high)); setTextColor(getColor(R.color.tr_text))
        }
        root.addView(header)
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(pad, DriverUi.dp(this@EventsActivity, 12), pad, DriverUi.dp(this@EventsActivity, 24)) }
        body.addView(DriverUi.text(this, t("События", "Evenimente"), 28f))
        body.addView(DriverUi.text(this, t("Концерты, матчи, выставки: где и когда начнут выходить люди. Нажмите «Напомнить» — придёт уведомление за ${EventReminders.BEFORE_MIN} минут до окончания.",
            "Concerte, meciuri, expoziții: unde și când ies oamenii. Apăsați «Amintește» — primiți notificare cu ${EventReminders.BEFORE_MIN} minute înainte de final."), 14f, true))
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        body.addView(list)
        root.addView(ScrollView(this).apply { addView(body) }, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
        load()
    }

    private fun load() {
        list.removeAllViews()
        list.addView(DriverUi.text(this, t("Загружаю…", "Se încarcă…"), 15f, true))
        lifecycleScope.launch {
            val json = CommunityApi.post(this@EventsActivity, "/api/events/list")
            list.removeAllViews()
            val arr = json?.optJSONArray("events")
            if (json == null || !json.optBoolean("ok") || arr == null) {
                list.addView(DriverUi.text(this@EventsActivity, if (json == null) t("Нет связи — попробуйте позже.", "Fără conexiune.") else json.optString("message"), 15f, true)); return@launch
            }
            val events = (0 until arr.length()).map { EventReminders.parse(arr.getJSONObject(it)) }
            EventReminders.sync(this@EventsActivity, events)
            val endOfToday = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59); set(Calendar.SECOND, 59) }.timeInMillis
            val today = events.filter { it.starts <= endOfToday && it.status != "deleted" }
            val later = events.filter { it.starts > endOfToday && it.status != "deleted" }
            if (events.isEmpty()) list.addView(DriverUi.text(this@EventsActivity, t("Пока событий нет. Как только администратор добавит — они появятся здесь.", "Încă nu sunt evenimente."), 15f, true))
            if (today.isNotEmpty()) { list.addView(section(t("Сегодня", "Azi"))); today.forEach { list.addView(card(it)) } }
            if (later.isNotEmpty()) { list.addView(section(t("Ближайшие дни", "Zilele următoare"))); later.forEach { list.addView(card(it)) } }
        }
    }

    private fun section(text: String) = TextView(this).apply {
        this.text = text; textSize = 18f; setTypeface(null, Typeface.BOLD); setTextColor(getColor(R.color.tr_accent))
        setPadding(0, DriverUi.dp(this@EventsActivity, 14), 0, DriverUi.dp(this@EventsActivity, 6))
    }

    private fun card(e: EventReminders.Event): View {
        val c = this
        val cancelled = e.status == "cancelled"
        val card = MaterialCardView(c).apply {
            radius = DriverUi.dp(c, 20).toFloat(); strokeWidth = DriverUi.dp(c, 1); strokeColor = getColor(if (cancelled) R.color.tr_danger else R.color.tr_console_border)
            setCardBackgroundColor(getColor(R.color.tr_surface))
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = DriverUi.dp(c, 12) }
            alpha = if (cancelled) 0.6f else 1f
        }
        val box = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL; setPadding(DriverUi.dp(c, 16), DriverUi.dp(c, 14), DriverUi.dp(c, 16), DriverUi.dp(c, 14)) }
        val day = SimpleDateFormat("EEE d MMM", Locale.getDefault()).format(Date(e.starts))
        val hm = SimpleDateFormat("HH:mm", Locale.getDefault())
        box.addView(TextView(c).apply {
            text = (if (cancelled) t("ОТМЕНЕНО · ", "ANULAT · ") else "") + e.title
            textSize = 17f; setTypeface(null, Typeface.BOLD); setTextColor(getColor(if (cancelled) R.color.tr_danger else R.color.tr_text))
        })
        box.addView(TextView(c).apply {
            text = "📍 ${e.place}\n🕒 $day, ${hm.format(Date(e.starts))} — " + t("окончание ~", "final ~") + hm.format(Date(e.ends)) +
                (e.people?.let { "\n👥 ~$it " + t("человек", "persoane") } ?: "") + (if (e.note.isNotBlank()) "\n${e.note}" else "")
            textSize = 14f; setLineSpacing(DriverUi.dp(c, 2).toFloat(), 1f); setTextColor(getColor(R.color.tr_text)); setPadding(0, DriverUi.dp(c, 6), 0, DriverUi.dp(c, 8))
        })
        if (!cancelled) {
            val row = LinearLayout(c)
            fun btn(label: String, color: Int, action: () -> Unit) = com.google.android.material.button.MaterialButton(c).apply {
                text = label; isAllCaps = false; cornerRadius = DriverUi.dp(c, 14); minHeight = DriverUi.dp(c, 48)
                backgroundTintList = android.content.res.ColorStateList.valueOf(getColor(R.color.tr_surface_high)); setTextColor(getColor(color)); setOnClickListener { action() }
            }
            if (e.lat != null && e.lon != null) row.addView(btn(t("На карте", "Pe hartă"), R.color.tr_text) {
                try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("geo:${e.lat},${e.lon}?q=${e.lat},${e.lon}(${Uri.encode(e.title)})"))) } catch (_: Exception) {}
            }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = DriverUi.dp(c, 6) })
            val on = EventReminders.isOn(c, e.id)
            val ended = e.ends - EventReminders.BEFORE_MIN * 60_000L < System.currentTimeMillis()
            if (!ended) row.addView(btn(if (on) t("✓ Напомню", "✓ Amintesc") else t("Напомнить", "Amintește"), if (on) R.color.tr_success else R.color.tr_accent) {
                EventReminders.set(c, e, !on); load()
            }, LinearLayout.LayoutParams(0, -2, 1f))
            box.addView(row)
        }
        card.addView(box)
        return card
    }
}
