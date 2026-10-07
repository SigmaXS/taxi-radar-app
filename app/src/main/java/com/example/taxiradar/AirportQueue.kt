package com.example.taxiradar

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Очередь в аэропорту с экрана Яндекс Про: «Ожидание в очереди · Комфорт · 3 ч · 31 - 35».
 * «31 - 35» — место водителя в очереди (кто только встал — в конце, значит это ≈ длина очереди),
 * «3 ч» — прогноз ожидания. Свёрнутый Яндекс Про показывает «Ожидание в очереди ~2 ч» —
 * тоже берём, тариф — последний увиденный. Отправляем на сервер (можно выключить в настройках).
 */
object AirportQueue {
    data class Reading(val tariff: String, val waitMin: Int?, val placeFrom: Int?, val placeTo: Int?)

    private val queueWords = Regex("(?iu)очеред|coad[aă]|queue")
    private val tariffs = listOf(
        "comfortplus" to Regex("(?iu)^(комфорт\\s*\\+|комфорт плюс|confort\\s*\\+|comfort\\s*\\+)$"),
        "comfort" to Regex("(?iu)^(комфорт|confort|comfort)$"),
        "econom" to Regex("(?iu)^(эконом|econom|economy)$")
    )
    // «3 ч», «2 ч 15 мин», «45 мин», «~2 ч», «1 h 20 min», «2 ore»
    private val waitRegex = Regex("(?iu)(?:~|≈|около\\s*)?(\\d{1,2})\\s*(?:ч|h|час|ore|oră|ora)\\.?(?:\\s*(\\d{1,2})\\s*(?:мин|min))?|(\\d{1,3})\\s*(?:мин|min)")
    private val placeRegex = Regex("^(\\d{1,3})\\s*[-–—]\\s*(\\d{1,3})$")

    fun mentionsQueue(s: String) = queueWords.containsMatchIn(s)

    private var lastTariff: String? = null
    private var lastSentKey = ""
    private var lastSentAt = 0L
    private var lastRawAt = 0L

    private fun waitOf(s: String): Int? = waitRegex.find(s)?.let { m ->
        if (m.groupValues[3].isNotEmpty()) m.groupValues[3].toInt()
        else m.groupValues[1].toInt() * 60 + (m.groupValues[2].toIntOrNull() ?: 0)
    }

    /** Разобрать строки экрана (или свёрнутого виджета Яндекса). */
    fun parse(lines: List<String>): List<Reading> {
        if (lines.none { queueWords.containsMatchIn(it) }) return emptyList()
        val out = mutableListOf<Reading>()
        for (i in lines.indices) {
            val tariff = tariffs.firstOrNull { it.second.matches(lines[i].trim()) }?.first ?: continue
            var wait: Int? = null; var from: Int? = null; var to: Int? = null
            for (j in i + 1 until minOf(lines.size, i + 5)) {
                val l = lines[j].trim()
                if (tariffs.any { it.second.matches(l) }) break
                placeRegex.matchEntire(l)?.let { from = it.groupValues[1].toInt(); to = it.groupValues[2].toInt() }
                    ?: run { if (wait == null) wait = waitOf(l) }
            }
            if (wait != null || from != null) out += Reading(tariff, wait, from, to)
        }
        if (out.isEmpty()) {
            // Свёрнутый Яндекс Про: только «Ожидание в очереди ~2 ч» — тариф последний известный.
            val w = lines.firstOrNull { queueWords.containsMatchIn(it) && waitOf(it) != null }?.let(::waitOf)
                ?: lines.firstNotNullOfOrNull { waitOf(it) }.takeIf { lines.any { l -> queueWords.containsMatchIn(l) } }
            val t = lastTariff
            if (w != null && t != null) out += Reading(t, w, null, null)
        } else lastTariff = out.first().tariff
        return out
    }

    /** Строки с экрана Яндекс Про: нашли очередь — отправить (не чаще раза в 3 минуты, если не изменилась). */
    fun onScreen(context: Context, lines: List<String>) {
        if (!DriverPreferences.flag(context, "share_queue", true)) return
        val readings = parse(lines)
        val now = System.currentTimeMillis()
        // Сырой текст экрана очереди — раз в 6 часов, чтобы по нему настраивать чтение.
        val raw = lines.any { queueWords.containsMatchIn(it) } && now - lastRawAt > 6 * 3600_000L
        if (readings.isEmpty() && !raw) return
        val key = readings.joinToString { "${it.tariff}:${it.waitMin}:${it.placeFrom}" }
        if (!raw && key == lastSentKey && now - lastSentAt < 3 * 60_000L) return
        if (!raw && now - lastSentAt < 60_000L) return
        lastSentKey = key; lastSentAt = now
        if (raw) lastRawAt = now
        val body = JSONObject().put("readings", JSONArray(readings.map {
            JSONObject().put("tariff", it.tariff).put("wait_min", it.waitMin ?: JSONObject.NULL)
                .put("place_from", it.placeFrom ?: JSONObject.NULL).put("place_to", it.placeTo ?: JSONObject.NULL)
        }))
        if (raw) body.put("raw", lines.take(60).joinToString(" | ").take(3000))
        Thread { CommunityApi.postBlocking(context.applicationContext, "/api/airport/queue", body) }.start()
    }
}
