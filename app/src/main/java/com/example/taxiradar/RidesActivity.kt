package com.example.taxiradar

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.concurrent.thread

/**
 * «Попутчики»: лента заявок из бота @MI_transferBot (группы Telegram, Viber, makler.md)
 * с поиском по маршруту «откуда → куда». Данные — с сервера бота, только чтение.
 */
class RidesActivity : AppCompatActivity() {

    companion object {
        private const val API = "https://transfer-production-2342.up.railway.app/api"
        private const val API_KEY = "taxiradar-app"
        private const val PAGE = 20
        private const val PREFS = "rides"
    }

    private lateinit var etFrom: MaterialAutoCompleteTextView
    private lateinit var etTo: MaterialAutoCompleteTextView
    private lateinit var toggle: MaterialButtonToggleGroup
    private lateinit var tvStatus: TextView
    private lateinit var list: LinearLayout
    private lateinit var btnMore: MaterialButton

    private var offset = 0
    private var loading = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_rides)

        etFrom = findViewById(R.id.etRidesFrom)
        etTo = findViewById(R.id.etRidesTo)
        toggle = findViewById(R.id.toggleRidesKind)
        tvStatus = findViewById(R.id.tvRidesStatus)
        list = findViewById(R.id.layoutRidesList)
        btnMore = findViewById(R.id.btnRidesMore)

        findViewById<View>(R.id.btnRidesBack).setOnClickListener { finish() }
        findViewById<View>(R.id.btnRidesSearch).setOnClickListener { search() }
        findViewById<View>(R.id.btnRidesSwap).setOnClickListener {
            val a = etFrom.text.toString()
            etFrom.setText(etTo.text.toString(), false)
            etTo.setText(a, false)
            search()
        }
        btnMore.setOnClickListener { load(append = true) }
        toggle.addOnButtonCheckedListener { _, _, isChecked -> if (isChecked) search() }

        val prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        etFrom.setText(prefs.getString("from", ""), false)
        etTo.setText(prefs.getString("to", ""), false)
        toggle.check(if (prefs.getString("kind", "passenger") == "driver") R.id.btnKindCars else R.id.btnKindPassengers)

        loadPlaces()
        search()
    }

    private fun kind(): String = if (toggle.checkedButtonId == R.id.btnKindCars) "driver" else "passenger"

    private fun search() {
        getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("from", etFrom.text.toString().trim())
            .putString("to", etTo.text.toString().trim())
            .putString("kind", kind())
            .apply()
        load(append = false)
    }

    /** Подсказки городов в полях «Откуда» / «Куда». */
    private fun loadPlaces() {
        thread {
            val json = runCatching { get("$API/places?key=$API_KEY") }.getOrNull() ?: return@thread
            val all = json.optJSONArray("all") ?: return@thread
            val ru = List(all.length()) { all.getString(it) }
            // Румынские названия: «Chișinău · Кишинёв» — можно начать вводить и так, и так,
            // сервер понимает оба написания.
            val ro = json.optJSONObject("ro")
            val both = ru.mapNotNull { name -> ro?.optString(name)?.takeIf { it.isNotBlank() }?.let { "$it · $name" } }
            val names = if (getString(R.string.lang_button) != "RU") both + ru else ru + both
            val regions = listOf(getString(R.string.rides_to_pmr), getString(R.string.rides_to_md),
                getString(R.string.rides_to_ua), getString(R.string.rides_to_eu))
            runOnUiThread {
                etFrom.setSimpleItems(names.toTypedArray())
                etTo.setSimpleItems((regions + names).toTypedArray())
            }
        }
    }

    private fun load(append: Boolean) {
        if (loading) return
        loading = true
        if (!append) {
            offset = 0
            list.removeAllViews()
            tvStatus.setText(R.string.rides_loading)
        }
        btnMore.visibility = View.GONE
        val from = etFrom.text.toString().trim()
        val to = toSpec(etTo.text.toString().trim())
        val url = "$API/rides?key=$API_KEY&kind=${kind()}&offset=$offset&limit=$PAGE" +
            "&from=${enc(from)}&to=${enc(to)}&both=1"
        thread {
            val result = runCatching { get(url) }
            runOnUiThread {
                loading = false
                result.onSuccess { show(it) }.onFailure {
                    tvStatus.text = getString(R.string.rides_error)
                }
            }
        }
    }

    /** «В ПМР» → «@pmr» и т. п.; город — как есть. */
    private fun toSpec(text: String): String = when (text) {
        getString(R.string.rides_to_pmr) -> "@pmr"
        getString(R.string.rides_to_md) -> "@md"
        getString(R.string.rides_to_ua) -> "@ua"
        getString(R.string.rides_to_eu) -> "@eu"
        else -> text
    }

    private fun show(json: JSONObject) {
        val total = json.optInt("total")
        val items = json.optJSONArray("items")
        val n = items?.length() ?: 0
        for (i in 0 until n) list.addView(card(items!!.getJSONObject(i)))
        offset += n
        val what = getString(if (kind() == "driver") R.string.rides_what_cars else R.string.rides_what_passengers)
        tvStatus.text = if (total == 0) getString(R.string.rides_empty, what, json.optString("route"))
        else getString(R.string.rides_found, total, what, json.optString("route"))
        btnMore.visibility = if (offset < total) View.VISIBLE else View.GONE
        if (offset < total) btnMore.text = getString(R.string.rides_more, total - offset)
    }

    private fun card(o: JSONObject): View {
        val v = LayoutInflater.from(this).inflate(R.layout.item_ride, list, false)
        val from = o.optString("from").ifEmpty { "?" }
        val to = o.optString("to").ifEmpty { "?" }
        val carrier = o.optBoolean("is_carrier")
        v.findViewById<TextView>(R.id.tvRideRoute).text =
            if (carrier && o.isNull("to")) "$from → ${getString(R.string.rides_many_directions)}" else "$from → $to"
        val head = if (o.optString("kind") == "driver")
            getString(if (carrier) R.string.rides_head_carrier else R.string.rides_head_car)
        else getString(R.string.rides_head_passenger)
        val extra = listOfNotNull(
            o.optString("when").takeIf { it.isNotEmpty() }?.let { "🕐 $it" },
            o.optInt("people").takeIf { it > 0 }?.let { "👤 $it" },
            o.optInt("seats").takeIf { it > 0 }?.let { "💺 $it" },
        ).joinToString("   ")
        v.findViewById<TextView>(R.id.tvRideHead).text = head
        v.findViewById<TextView>(R.id.tvRideWhen).text = extra
        v.findViewById<TextView>(R.id.tvRideText).apply {
            val t = listOf(o.optString("text"), o.optString("comment")).filter { it.isNotBlank() && it != "null" }
                .joinToString("\n")
            text = t
            visibility = if (t.isBlank()) View.GONE else View.VISIBLE
        }
        val author = o.optString("author").takeIf { it.isNotBlank() && it != "null" }
        v.findViewById<TextView>(R.id.tvRideSource).text =
            listOfNotNull(o.optString("source").takeIf { it.isNotBlank() }, author).joinToString(" · ")

        val phone = o.optString("phone").takeIf { it.isNotBlank() && it != "null" }
        v.findViewById<MaterialButton>(R.id.btnRideCall).apply {
            visibility = if (phone != null) View.VISIBLE else View.GONE
            setOnClickListener { open(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone"))) }
        }
        // «Написать» — автору в Telegram (если у него есть @username)
        val tg = o.optString("telegram").removePrefix("@").takeIf { Regex("[A-Za-z0-9_]{4,32}").matches(it) }
        v.findViewById<MaterialButton>(R.id.btnRideOpen).apply {
            visibility = if (tg != null) View.VISIBLE else View.GONE
            setOnClickListener { open(Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/$tg"))) }
        }
        // «Группа» — сообщение в Telegram-группе, Viber-группа или объявление на сайте
        // Ссылки приходят из чужих объявлений — открываем только обычные веб-ссылки.
        val group = o.optString("group_link").takeIf { it.startsWith("https://") || it.startsWith("http://") }
        v.findViewById<MaterialButton>(R.id.btnRideGroup).apply {
            visibility = if (group != null) View.VISIBLE else View.GONE
            setText(when (o.optString("group_kind")) {
                "telegram" -> R.string.rides_group_telegram
                "site" -> R.string.rides_group_site
                else -> R.string.rides_group_viber
            })
            setOnClickListener { open(Intent(Intent.ACTION_VIEW, Uri.parse(group))) }
        }
        // Кнопки в одну строку: пустую строку прячем целиком
        (v.findViewById<View>(R.id.btnRideCall).parent as View).visibility =
            if (phone == null && tg == null) View.GONE else View.VISIBLE
        return v
    }

    private fun open(intent: Intent) {
        runCatching { startActivity(intent) }
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private fun get(url: String): JSONObject {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 15000
        c.readTimeout = 20000
        try {
            val body = c.inputStream.bufferedReader().use { it.readText() }
            return JSONObject(body)
        } finally {
            c.disconnect()
        }
    }
}
