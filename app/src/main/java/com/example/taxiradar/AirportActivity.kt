package com.example.taxiradar

import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * «Аэропорт»: очередь водителей радара у терминала и табло прилётов —
 * 3 последних севших и 3 ближайших, остальные рейсы дня по кнопке.
 */
class AirportActivity : AppCompatActivity() {

    private var poll: Job? = null
    private var showAll = false
    private var last: Airport.Status? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_airport)
        findViewById<View>(R.id.btnAirportBack).setOnClickListener { finish() }
        findViewById<View>(R.id.btnAirportOfficial).setOnClickListener {
            startActivity(Intent(this, AirportBoardActivity::class.java))
        }
        findViewById<View>(R.id.btnAirportAll).setOnClickListener {
            showAll = !showAll
            render(last)
        }
    }

    override fun onResume() {
        super.onResume()
        poll = lifecycleScope.launch {
            while (isActive) {
                last = Airport.status(this@AirportActivity)
                render(last)
                delay(60_000)
            }
        }
    }

    override fun onPause() {
        poll?.cancel()
        super.onPause()
    }

    private fun render(s: Airport.Status?) {
        val tvQueue = findViewById<TextView>(R.id.tvAirportQueue)
        val list = findViewById<LinearLayout>(R.id.layoutFlights)
        val empty = findViewById<TextView>(R.id.tvNoFlights)
        val btnAll = findViewById<MaterialButton>(R.id.btnAirportAll)
        list.removeAllViews()
        if (s == null) {
            tvQueue.text = "—"
            empty.visibility = View.VISIBLE
            empty.setText(R.string.clients_no_connection)
            btnAll.visibility = View.GONE
            return
        }
        tvQueue.text = s.queue.toString()
        renderTaxiQueue(s)
        empty.visibility = if (s.flights.isEmpty()) View.VISIBLE else View.GONE
        empty.setText(R.string.airport_no_flights)

        val now = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date())
        val landed = s.flights.filter { it.status == "landed" || (it.status == "cancelled" && it.time < now) }
        val upcoming = s.flights - landed.toSet()
        val lastLanded = landed.takeLast(3)
        val next = upcoming.take(3)
        val rest = upcoming.drop(3)

        var first = true
        fun section(title: Int, flights: List<Airport.Flight>) {
            if (flights.isEmpty()) return
            list.addView(sectionLabel(getString(title), first))
            first = false
            flights.forEachIndexed { i, f -> list.addView(row(f, divider = i > 0)) }
        }
        section(R.string.airport_landed_title, lastLanded)
        section(R.string.airport_next_title, next)
        if (showAll) section(R.string.airport_later_title, rest)

        btnAll.visibility = if (rest.isEmpty()) View.GONE else View.VISIBLE
        btnAll.text = getString(if (showAll) R.string.airport_hide_all else R.string.airport_show_all, rest.size)
    }

    /**
     * Очередь такси по тарифам (по экрану Яндекс Про у водителей в аэропорту):
     * «Комфорт: ~31–35 машин · ожидание ~3 ч · 4 мин назад». Нет свежих — так и пишем.
     */
    private fun renderTaxiQueue(s: Airport.Status) {
        fun t(ru: String, ro: String) = DriverUi.t(this, ru, ro)
        val anchor = (findViewById<TextView>(R.id.tvAirportQueue).parent as View).parent as View
        val parent = anchor.parent as LinearLayout
        val old = parent.findViewWithTag<View>("taxiQueue")
        if (old != null) parent.removeView(old)
        val card = com.google.android.material.card.MaterialCardView(this).apply {
            tag = "taxiQueue"; radius = DriverUi.dp(this@AirportActivity, 20).toFloat()
            setCardBackgroundColor(getColor(R.color.tr_surface)); strokeColor = getColor(R.color.tr_accent); strokeWidth = DriverUi.dp(this@AirportActivity, 1)
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = DriverUi.dp(this@AirportActivity, 12) }
        }
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; val p = DriverUi.dp(this@AirportActivity, 16); setPadding(p, p, p, p) }
        box.addView(TextView(this).apply {
            text = t("Очередь такси в аэропорту", "Coada de taxi la aeroport"); textSize = 18f; setTypeface(null, Typeface.BOLD); setTextColor(getColor(R.color.tr_text))
        })
        val names = mapOf("econom" to t("Эконом", "Econom"), "comfort" to t("Комфорт", "Confort"), "comfortplus" to t("Комфорт+", "Confort+"))
        for ((k, label) in names) {
            val q = s.taxi[k]
            val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(0, DriverUi.dp(this@AirportActivity, 10), 0, 0) }
            row.addView(TextView(this).apply { text = label; textSize = 16f; setTypeface(null, Typeface.BOLD); setTextColor(getColor(R.color.tr_text)) },
                LinearLayout.LayoutParams(DriverUi.dp(this@AirportActivity, 100), -2))
            val stale = q != null && q.ageMin > 20
            row.addView(TextView(this).apply {
                text = if (q == null) t("нет свежих данных", "fără date recente") else listOfNotNull(
                    when { q.carsTo != null && q.carsFrom != null && q.carsTo != q.carsFrom -> t("~${q.carsFrom}–${q.carsTo} машин", "~${q.carsFrom}–${q.carsTo} mașini")
                           q.carsTo != null -> t("~${q.carsTo} машин", "~${q.carsTo} mașini"); else -> null },
                    q.waitMin?.let { w -> t("ожидание ~${if (w >= 60) "${w / 60} ч${if (w % 60 > 0) " ${w % 60} мин" else ""}" else "$w мин"}", "așteptare ~${if (w >= 60) "${w / 60} h${if (w % 60 > 0) " ${w % 60} min" else ""}" else "$w min"}") },
                    if (stale) t("данные устарели (${q.ageMin} мин)", "date vechi (${q.ageMin} min)") else t("${q.ageMin} мин назад", "acum ${q.ageMin} min")
                ).joinToString(" · ")
                textSize = 15f
                setTextColor(getColor(when { q == null -> R.color.tr_text_secondary; stale -> R.color.tr_warning; else -> R.color.tr_text }))
            }, LinearLayout.LayoutParams(0, -2, 1f))
            box.addView(row)
        }
        box.addView(TextView(this).apply {
            text = t("Цифры — с экрана «Ожидание в очереди» в Яндекс Про у водителей Taxi Radar, которые стоят в аэропорту. «Машин» — место последнего вставшего в очередь. Сопоставьте с прилётами ниже — решать вам.",
                "Cifrele — de pe ecranul «Așteptare în coadă» din Yandex Pro al șoferilor Taxi Radar din aeroport. «Mașini» — locul ultimului venit în coadă. Comparați cu sosirile de mai jos.")
            textSize = 12f; setTextColor(getColor(R.color.tr_text_secondary)); setPadding(0, DriverUi.dp(this@AirportActivity, 10), 0, 0)
        })
        card.addView(box)
        parent.addView(card, parent.indexOfChild(anchor))
    }

    private fun sectionLabel(text: String, first: Boolean) = TextView(this).apply {
        this.text = text
        setTextColor(getColor(R.color.tr_accent))
        textSize = 12f
        setTypeface(typeface, Typeface.BOLD)
        val d = resources.displayMetrics.density
        setPadding(0, if (first) 0 else (14 * d).toInt(), 0, (2 * d).toInt())
    }

    private fun row(f: Airport.Flight, divider: Boolean): View {
        val d = resources.displayMetrics.density
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        if (divider) box.addView(View(this).apply {
            setBackgroundColor(getColor(R.color.tr_stroke))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (1 * d).toInt())
        })
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, (10 * d).toInt(), 0, (10 * d).toInt())
        }
        val muted = f.status == "landed" || f.status == "cancelled"
        row.addView(TextView(this).apply {
            text = (if (f.approx) "≈" else "") + f.time.takeLast(5)
            setTextColor(getColor(if (f.delayed && !muted) R.color.tr_warning else if (muted) R.color.tr_text_secondary else R.color.tr_text))
            textSize = 17f
            setTypeface(typeface, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams((68 * d).toInt(), LinearLayout.LayoutParams.WRAP_CONTENT)
        })
        row.addView(TextView(this).apply {
            text = f.flight
            setTextColor(getColor(R.color.tr_text_secondary))
            textSize = 13f
            typeface = Typeface.MONOSPACE
            layoutParams = LinearLayout.LayoutParams((72 * d).toInt(), LinearLayout.LayoutParams.WRAP_CONTENT)
        })
        row.addView(TextView(this).apply {
            text = Airport.city(f.from)
            setTextColor(getColor(if (muted) R.color.tr_text_secondary else R.color.tr_text))
            textSize = 15f
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        row.addView(TextView(this).apply {
            text = statusText(f)
            setTextColor(
                getColor(
                    when {
                        f.status == "landed" -> R.color.tr_success
                        f.status == "cancelled" -> R.color.tr_danger
                        f.delayed -> R.color.tr_warning
                        else -> R.color.tr_text_secondary
                    }
                )
            )
            textSize = 13f
        })
        box.addView(row)
        return box
    }

    private fun statusText(f: Airport.Flight) = when {
        f.status == "landed" -> getString(R.string.airport_landed)
        f.status == "cancelled" -> getString(R.string.airport_cancelled)
        f.status == "en-route" -> getString(R.string.airport_in_air)
        f.delayed -> getString(R.string.airport_delayed)
        else -> ""
    }
}
