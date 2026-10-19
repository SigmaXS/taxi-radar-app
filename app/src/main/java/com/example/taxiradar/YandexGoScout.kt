package com.example.taxiradar

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.currentCoroutineContext
import org.json.JSONObject
import kotlin.math.roundToInt

/**
 * Разведчик надбавки на отдельном телефоне: Яндекс Go + Taxi Radar в режиме «Разведчик».
 * Сайт Яндекса сейчас отдаёт только старт 30/45/65, а Go — настоящую цену «от N».
 * По очереди с сервера (точки, где стоят водители) открываем Go ссылкой yandextaxi://route,
 * читаем цены с экрана (спец. возможность), вычитаем скидку аккаунта и старт тарифа → надбавка.
 * Ничего не нажимаем и не заказываем.
 */
object YandexGoScout {
    const val PACKAGE = "ru.yandex.taxi"

    data class Screen(val at: Long, val seq: Long, val address: String, val prices: Map<String, Int>, val noCars: Set<String>)

    @Volatile var latest: Screen? = null; private set
    private var seq = 0L

    fun enabled(c: Context) = DriverPreferences.flag(c, "scout_mode", false)
    fun discount(c: Context, tariff: String) = DriverPreferences.number(c, "scout_disc_$tariff", 0.0).coerceIn(0.0, 90.0)

    // «Тариф «Эконом», Высокий спрос, От 45 L, Подача 5 мин» — так Go подписывает карточки тарифов.
    private val tariffLine = Regex("""(?iu)(?:тариф|tarif|tariff)\s*«([^»]+)»(.*)""")
    private val fromPrice = Regex("""(?iu)(?:от|de la|from)[\s  ]*(\d{1,4})(?:[.,]\d{1,2})?[\s  ]*(?:L|MDL|lei|лей)""")
    private val noCarsText = Regex("""(?iu)(нет свободных машин|no available cars|nu sunt mașini)""")

    private fun key(name: String): String? {
        val n = name.trim().lowercase()
        return when {
            n.startsWith("комфорт+") || n.startsWith("comfort+") || n.startsWith("confort+") -> "comfortplus"
            n.startsWith("комфорт") || n.startsWith("comfort") || n.startsWith("confort") -> "comfort"
            n.startsWith("эконом") || n.startsWith("econom") -> "econom"
            else -> null // «Самый быстрый», доставка и т.п. — не наши тарифы
        }
    }

    /** Разбор экрана Go (тексты и подписи элементов). Пустые цены — Go ещё грузит или машин нет. */
    fun parse(lines: List<String>, now: Long): Screen {
        val prices = mutableMapOf<String, Int>()
        val seen = mutableSetOf<String>()
        for (line in lines) {
            val m = tariffLine.find(line) ?: continue
            val k = key(m.groupValues[1]) ?: continue
            seen += k
            fromPrice.find(m.groupValues[2])?.groupValues?.get(1)?.toIntOrNull()?.let { prices[k] = it }
        }
        val address = lines.firstOrNull { it.startsWith("Ваш адрес", true) || it.startsWith("Adresa", true) || it.startsWith("Your address", true) }
            ?.substringAfter(',')?.trim()?.trimEnd(',').orEmpty()
        // Тариф есть на экране, а цены нет («—», «Нет свободных машин») — машин этого тарифа рядом нет.
        val noCars = if (lines.any { noCarsText.containsMatchIn(it) } || seen.isNotEmpty()) seen - prices.keys else emptySet()
        return Screen(now, 0, address, prices, noCars)
    }

    /** Из спец. возможности: экран Go сменился. */
    fun onScreen(root: AccessibilityNodeInfo?) {
        root ?: return
        val lines = ArrayList<String>()
        var visited = 0
        fun walk(n: AccessibilityNodeInfo?, depth: Int) {
            if (n == null || depth > 40 || ++visited > 800) return
            listOfNotNull(n.contentDescription?.toString(), n.text?.toString()).map { it.trim() }.filter { it.isNotEmpty() }.distinct().forEach { lines += it }
            for (i in 0 until n.childCount) walk(n.getChild(i), depth + 1)
        }
        walk(root, 0)
        val s = parse(lines, System.currentTimeMillis())
        if (s.prices.isEmpty() && s.noCars.isEmpty()) return
        latest = s.copy(seq = ++seq)
    }

    /** Надбавка по тарифам из цен Go: убрать скидку аккаунта, вычесть старт тарифа. null — машин нет / не прочитано. */
    fun surges(c: Context, s: Screen): Map<String, Int?> {
        val base = mapOf("econom" to 30, "comfort" to 45, "comfortplus" to 65) + AppConfig.load(c).surgeBase.filterKeys { it in setOf("econom", "comfort", "comfortplus") }
        return listOf("econom", "comfort", "comfortplus").associateWith { k ->
            val price = s.prices[k] ?: return@associateWith null
            val full = (price / (1 - discount(c, k) / 100)).roundToInt()
            // Ниже старта — скидка задана неверно: не выдумываем, отдаём «нет данных».
            (full - (base[k] ?: return@associateWith null)).takeIf { it >= 0 }
        }
    }

    /** Цикл разведчика (из службы радара): точки с сервера → Go в каждой → результат на сервер. */
    suspend fun run(c: Context) {
        var lastAddress = ""
        var lastPoint = ""
        var sinceReload = 0
        var failed = false
        while (currentCoroutineContext().isActive && enabled(c)) {
            val tasks = CommunityApi.postBlocking(c, "/api/scout/tasks", JSONObject().put("limit", 20))
            val points = tasks?.optJSONArray("points")
            if (tasks?.optString("code") == "not_scout") {
                TripLog.d("Разведчик: этот телефон не в списке SCOUT_DEVICE_IDS на сервере")
                delay(60_000); continue
            }
            if (points == null || points.length() == 0) { delay(10_000); continue }
            for (i in 0 until points.length()) {
                if (!enabled(c)) return
                val p = points.getJSONObject(i)
                val lat = p.getDouble("lat"); val lon = p.getDouble("lon")
                val point = "$lat,$lon"
                // Другая точка — ссылка в уже открытый Go: адрес и цены меняются за 1–2 с.
                // Та же точка, раз в 10 проверок или после сбоя — открываем Go заново: иначе он держит старые цены
                // (утром «нет машин», хотя Эконом уже появился).
                val reload = point == lastPoint || failed || ++sinceReload >= 10
                if (reload) sinceReload = 0
                val before = latest?.seq ?: 0
                val started = System.currentTimeMillis()
                try {
                    c.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("yandextaxi://route?start-lat=$lat&start-lon=$lon"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or if (reload) Intent.FLAG_ACTIVITY_CLEAR_TASK else 0))
                } catch (e: Exception) {
                    TripLog.d("Разведчик: Яндекс Go не открылся (${e.javaClass.simpleName}) — он установлен?"); delay(30_000); break
                }
                // Ждём новый адрес и цены; при полном перезапуске адрес может совпасть с прошлым — тогда ждём дольше.
                var screen: Screen? = null
                while (System.currentTimeMillis() - started < 10_000) {
                    delay(300)
                    val s = latest ?: continue
                    if (s.seq <= before || s.at < started + 400) continue
                    if (s.address != lastAddress || System.currentTimeMillis() - started > (if (reload) 4_000 else 2_500)) { screen = s; break }
                }
                lastPoint = point
                if (screen == null) { failed = true; TripLog.d("Разведчик: в точке $point Go не показал цены"); continue }
                failed = false
                delay(700) // цены подгружаются чуть позже адреса — берём последний экран
                val final = latest?.takeIf { it.seq >= screen.seq } ?: screen
                lastAddress = final.address
                val values = surges(c, final)
                val body = JSONObject().put("lat", lat).put("lon", lon).put("values", JSONObject().apply {
                    values.forEach { (k, v) -> put(k, v ?: JSONObject.NULL) }
                })
                val reply = CommunityApi.postBlocking(c, "/api/scout/result", body)
                TripLog.d("Разведчик: ${final.address.take(40)} → цены ${final.prices}, нет машин ${final.noCars}, надбавка ${reply?.opt("value")}" +
                    " (${System.currentTimeMillis() - started} мс${if (reload) ", заново" else ""})")
            }
        }
    }
}

/** Радар водителя: надбавка разведчика рядом (раз в цикл обновления). */
object ScoutClient {
    /** [scouting] — разведчик работает, но эту точку ещё не проверил или машин нет: «?», а не 0 с сайта. */
    data class Near(val surge: Int?, val far: Boolean, val ageSec: Int, val scouting: Boolean = false)

    @Volatile private var cache: Pair<Long, Near?>? = null

    suspend fun near(c: Context, lat: Double, lon: Double): Near? {
        val now = System.currentTimeMillis()
        cache?.let { if (now - it.first < 30_000) return it.second }
        val j = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            CommunityApi.postBlocking(c, "/api/scout/near", JSONObject().put("lat", lat).put("lon", lon))
        }
        val r = j?.takeIf { it.optBoolean("ok") }?.let {
            Near(if (it.isNull("surge")) null else it.optInt("surge"), it.optBoolean("far"), it.optInt("age_s"), it.optBoolean("scouting"))
        }
        cache = now to r
        return r
    }
}
