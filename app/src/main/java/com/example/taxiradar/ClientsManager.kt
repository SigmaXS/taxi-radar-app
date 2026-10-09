package com.example.taxiradar

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Отметки водителей о клиентах. Номер ловим, когда водитель звонит клиенту
 * из Яндекс Про (звонилка открывается с настоящим номером), проверяем на
 * сервере и тут же даём отметить — кнопками прямо в уведомлении.
 */
object ClientsManager {

    /** Отметки: ключ на сервере → подпись. Первые четыре — жалобы. */
    val TAGS = linkedMapOf(
        "slow" to R.string.tag_slow,
        "noshow" to R.string.tag_noshow,
        "rude" to R.string.tag_rude,
        "unpaid" to R.string.tag_unpaid,
        "ok" to R.string.tag_ok,
        "card" to R.string.tag_card,
        "plus" to R.string.tag_plus
    )
    val NEGATIVE = setOf("slow", "noshow", "rude", "unpaid")

    /** Отзыв своими словами. [at] — мс, 0 если дата не разобралась. */
    data class Review(val text: String, val at: Long, val mine: Boolean, val admin: Boolean)

    data class Summary(val tags: Map<String, Int>, val mine: Set<String>, val reviews: List<Review> = emptyList()) {
        val myReview: String? get() = reviews.firstOrNull { it.mine }?.text
    }


    data class Recent(val number: String, val at: Long)

    private const val PREFS = "taxi_radar_prefs"
    private const val KEY_RECENT = "recent_clients"
    private const val CHANNEL = "clients"
    private const val NOTIFICATION_ID = 202

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // ---------- сервер ----------

    suspend fun check(context: Context, number: String): Summary? =
        parse(CommunityApi.post(context, "/api/clients/check", JSONObject().put("phone", number)))

    suspend fun tag(context: Context, number: String, tag: String, on: Boolean): Summary? =
        parse(
            CommunityApi.post(
                context, "/api/clients/tag",
                JSONObject().put("phone", number).put("tag", tag).put("on", on)
            )
        )

    /** Свой отзыв о клиенте; пустой текст — удалить свой отзыв. */
    suspend fun review(context: Context, number: String, text: String): Summary? =
        parse(CommunityApi.post(context, "/api/clients/review", JSONObject().put("phone", number).put("text", text)))

    private val isoFormat = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US).apply {
        timeZone = java.util.TimeZone.getTimeZone("UTC")
    }

    private fun parse(json: JSONObject?): Summary? {
        if (json == null || !json.optBoolean("ok")) return null
        val tags = mutableMapOf<String, Int>()
        json.optJSONObject("tags")?.let { t -> t.keys().forEach { tags[it] = t.optInt(it) } }
        val mine = mutableSetOf<String>()
        json.optJSONArray("mine")?.let { a -> for (i in 0 until a.length()) mine += a.optString(i) }
        val reviews = mutableListOf<Review>()
        json.optJSONArray("reviews")?.let { a ->
            for (i in 0 until a.length()) {
                val r = a.optJSONObject(i) ?: continue
                val at = try {
                    synchronized(isoFormat) { isoFormat.parse(r.optString("ts").take(19))?.time } ?: 0L
                } catch (e: Exception) {
                    0L
                }
                reviews += Review(r.optString("text"), at, r.optBoolean("mine"), r.optBoolean("admin"))
            }
        }
        return Summary(tags, mine, reviews)
    }

    /** «⚠ долго выходит · 3 водителя» / «✓ всё ок · 💳 оплата картой» / «Отметок нет». */
    fun describe(context: Context, s: Summary?): String {
        if (s == null) return context.getString(R.string.clients_no_connection)
        val bad = s.tags.filterKeys { it in NEGATIVE }
        val good = s.tags.filterKeys { it !in NEGATIVE }
        val parts = mutableListOf<String>()
        if (bad.isNotEmpty()) {
            parts += "⚠ " + bad.entries.sortedByDescending { it.value }
                .joinToString(", ") { context.getString(TAGS.getValue(it.key)) + " (${it.value})" }
        }
        if (good.isNotEmpty()) {
            parts += good.entries.sortedByDescending { it.value }
                .joinToString(", ") { icon(it.key) + context.getString(TAGS.getValue(it.key)) + " (${it.value})" }
        }
        if (s.reviews.isNotEmpty()) parts += "💬 " + context.resources.getQuantityString(R.plurals.clients_reviews, s.reviews.size, s.reviews.size)
        return if (parts.isEmpty()) context.getString(R.string.clients_no_tags) else parts.joinToString(" · ")
    }

    /** Отзывы строками: «Ждал 10 минут» · 12 сент. */
    fun describeReviews(context: Context, s: Summary): String =
        s.reviews.joinToString("\n\n") { r ->
            val date = if (r.at > 0) android.text.format.DateFormat.format("d MMM", r.at).toString() else ""
            val who = when {
                r.admin -> context.getString(R.string.clients_review_admin)
                r.mine -> context.getString(R.string.clients_review_mine)
                else -> context.getString(R.string.clients_review_driver)
            }
            "«${r.text}»\n$who${if (date.isNotEmpty()) " · $date" else ""}"
        }

    private fun icon(tag: String) = when (tag) {
        "ok" -> "✓ "
        "card" -> "💳 "
        "plus" -> "⭐ "
        else -> ""
    }

    // ---------- последние клиенты (только на этом телефоне) ----------

    fun recent(context: Context): List<Recent> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_RECENT, null) ?: return emptyList()
        return try {
            val a = JSONArray(raw)
            (0 until a.length()).map { i -> a.getJSONObject(i).let { Recent(it.getString("n"), it.getLong("t")) } }
        } catch (e: Exception) {
            emptyList()
        }
    }

    @Synchronized
    fun remember(context: Context, number: String) {
        val list = (listOf(Recent(number, System.currentTimeMillis())) + recent(context).filter { it.number != number }).take(15)
        val a = JSONArray()
        list.forEach { a.put(JSONObject().put("n", it.number).put("t", it.at)) }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_RECENT, a.toString()).apply()
    }

    // ---------- звонок клиенту из Яндекс Про ----------

    /**
     * Водитель звонит клиенту: проверяем номер, показываем результат на виджете
     * и в уведомлении с кнопками отметок. cardPayment — Яндекс Про показал
     * «Оплата картой» в этом заказе: отмечаем это сами.
     */
    fun onClientCall(context: Context, number: String, payment: String?) {
        val cardPayment = payment == "card"
        val app = context.applicationContext
        remember(app, number)
        scope.launch {
            var summary = check(app, number)
            if (cardPayment && summary != null && "card" !in summary.mine) {
                summary = tag(app, number, "card", true) ?: summary
            }
            // Как платит клиент в этом заказе — сразу видно в уведомлении.
            val payLine = when (payment) {
                "card" -> DriverUi.t(app, "💳 Оплата картой", "💳 Plată cu cardul")
                "cash" -> DriverUi.t(app, "💵 Наличные", "💵 Numerar")
                else -> null
            }
            val text = listOfNotNull(payLine, describe(app, summary)).joinToString("\n")
            val warn = summary?.tags?.keys?.any { it in NEGATIVE } == true
            FloatingWidgetService.showNote(
                app.getString(if (warn) R.string.clients_widget_warn else R.string.clients_widget_title),
                text,
                if (warn) R.color.tr_danger else R.color.tr_success
            )
            notify(app, number, text)
        }
    }

    private fun notify(context: Context, number: String, text: String) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, context.getString(R.string.clients_channel), NotificationManager.IMPORTANCE_HIGH)
            )
        }
        fun action(tag: String, code: Int): NotificationCompat.Action {
            val intent = Intent(context, ClientTagReceiver::class.java)
                .putExtra("number", number).putExtra("tag", tag)
            val pi = PendingIntent.getBroadcast(
                context, code, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            return NotificationCompat.Action(0, context.getString(TAGS.getValue(tag)), pi)
        }
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, ClientsActivity::class.java).putExtra("number", number),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_person_search)
            .setContentTitle(context.getString(R.string.clients_notif_title, PhoneNumbers.tail(number)))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text + "\n" + context.getString(R.string.clients_notif_hint)))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            // Больше трёх кнопок Android не показывает: две частые отметки и «Ещё отметки…» —
            // окно со всеми отметками сразу.
            .addAction(action("noshow", 2))
            .addAction(action("ok", 3))
            .addAction(NotificationCompat.Action(0, DriverUi.t(context, "Ещё отметки…", "Alte etichete…"),
                PendingIntent.getActivity(context, 4, Intent(context, ClientsActivity::class.java)
                    .putExtra("number", number).putExtra("tags", true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)))
            .build()
        try {
            nm.notify(NOTIFICATION_ID, n)
        } catch (e: SecurityException) {
            // Уведомления запрещены — результат всё равно был на виджете.
        }
    }

    /** Отметка кнопкой из уведомления. */
    fun tagFromNotification(context: Context, number: String, tag: String, done: () -> Unit) {
        val app = context.applicationContext
        scope.launch {
            val summary = tag(app, number, tag, true)
            withContext(Dispatchers.Main) {
                val nm = app.getSystemService(NotificationManager::class.java)
                val msg = if (summary != null) app.getString(R.string.clients_tagged, app.getString(TAGS.getValue(tag)))
                else app.getString(R.string.clients_no_connection)
                val n = NotificationCompat.Builder(app, CHANNEL)
                    .setSmallIcon(R.drawable.ic_person_search)
                    .setContentTitle(app.getString(R.string.clients_notif_title, PhoneNumbers.tail(number)))
                    .setContentText(msg)
                    .setTimeoutAfter(4000)
                    .setAutoCancel(true)
                    .build()
                try {
                    nm?.notify(NOTIFICATION_ID, n)
                } catch (e: SecurityException) {
                }
                done()
            }
        }
    }
}

/** Кнопки «Долго выходит / Не вышел / Всё ок» в уведомлении о клиенте. */
class ClientTagReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val number = intent.getStringExtra("number") ?: return
        val tag = intent.getStringExtra("tag") ?: return
        val pending = goAsync()
        ClientsManager.tagFromNotification(context, number, tag) { pending.finish() }
    }
}
