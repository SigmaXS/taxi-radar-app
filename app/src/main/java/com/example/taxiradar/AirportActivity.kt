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
