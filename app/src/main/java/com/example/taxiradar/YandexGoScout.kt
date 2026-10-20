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
    /** Когда спец. возможность последний раз прислала экран Go и был ли на нём хоть какой-то текст. */
    @Volatile private var lastEventAt = 0L
    @Volatile private var lastBlank = false

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
        lastEventAt = System.currentTimeMillis()
        lastBlank = lines.size < 3 // белый экран / вечная загрузка: Go ничего не пишет
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
    // Сдвиги ~20 м (влево, вправо, вверх, вниз): для Go это «новое место» — он сам перезапрашивает цены
    // за 1–2 с, без перезапуска; надбавка на 20 м та же.
    private val shifts = listOf(0.0 to 0.0, 0.0 to 0.00026, 0.0 to -0.00026, 0.00018 to 0.0, -0.00018 to 0.0)

    /** Go завис (вечная загрузка, пропал интернет): убрать его с экрана, остановить и открыть заново. */
    private suspend fun restartGo(c: Context) {
        TripLog.d("Разведчик: Go не отвечает — перезапускаю его")
        rest(c)
        delay(1200)
        try { (c.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager).killBackgroundProcesses(PACKAGE) } catch (_: Exception) {}
        delay(800)
    }

    /** Пауза между кругами: свой чёрный экран почти без подсветки — Go уходит в фон и не рисует карту, телефон не греется. */
    private fun rest(c: Context) {
        try { c.startActivity(Intent(c, ScoutRestActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)) } catch (_: Exception) {}
    }

    private fun notifyBroken(c: Context, text: String?) {
        val nm = c.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        if (text == null) { nm.cancel(7301); return }
        if (android.os.Build.VERSION.SDK_INT >= 26)
            nm.createNotificationChannel(android.app.NotificationChannel("scout", "Разведчик", android.app.NotificationManager.IMPORTANCE_HIGH))
        val tap = android.app.PendingIntent.getActivity(c, 7301, Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS),
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE)
        try {
            nm.notify(7301, androidx.core.app.NotificationCompat.Builder(c, "scout").setSmallIcon(R.drawable.ic_payments)
                .setContentTitle("Разведчик не работает").setContentText(text)
                .setStyle(androidx.core.app.NotificationCompat.BigTextStyle().bigText(text))
                .setContentIntent(tap).setOngoing(true).build())
        } catch (_: SecurityException) {}
    }

    suspend fun run(c: Context) {
        // Экран не гаснет (иначе Go не открыть), но может притухнуть — меньше нагрев.
        @Suppress("DEPRECATION")
        val wake = (c.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager)
            .newWakeLock(android.os.PowerManager.SCREEN_DIM_WAKE_LOCK, "TaxiRadar:scout")
        try { wake.acquire(); loop(c) } finally { if (wake.isHeld) wake.release(); notifyBroken(c, null) }
    }

    private suspend fun loop(c: Context) {
        var lastAddress = ""
        var lastPoint = ""
        var shift = 0
        var failures = 0
        var noCarsBefore = false
        var silent = 0          // Go открывали, а спец. возможность не прислала ни одного экрана
        var a11yOk = true
        var statusAt = 0L
        var resting = false
        while (currentCoroutineContext().isActive && enabled(c)) {
            // Спец. возможность «включена, но не работает» (бывает после обновления или долгой работы).
            val ok = OrderAccessibilityService.isConnected && AccessibilityAccess.isEnabled(c) && silent < 3
            if (ok != a11yOk || System.currentTimeMillis() - statusAt > 60_000) {
                if (ok != a11yOk) TripLog.d("Разведчик: спец. возможность ${if (ok) "снова работает" else "не работает"}")
                a11yOk = ok; statusAt = System.currentTimeMillis()
                CommunityApi.postBlocking(c, "/api/scout/status", JSONObject().put("a11y", ok))
                notifyBroken(c, if (ok) null else "Спец. возможность Taxi Radar включена, но не работает. Выключите и включите её снова или перезагрузите телефон.")
            }
            if (!ok) {
                // Разрешение выдано с компьютера — перезапускаем службу сами: выключить и включить.
                if (AccessibilityAccess.canEnableSelf(c)) {
                    AccessibilityAccess.disableSelf(c); delay(1500); AccessibilityAccess.enableSelf(c)
                    TripLog.d("Разведчик: перезапустил спец. возможность сам")
                }
                silent = 0
                delay(30_000); continue
            }
            val tasks = CommunityApi.postBlocking(c, "/api/scout/tasks", JSONObject().put("limit", 20))
            val points = tasks?.optJSONArray("points")
            if (tasks?.optString("code") == "not_scout") {
                TripLog.d("Разведчик: этот телефон не в списке SCOUT_DEVICE_IDS на сервере")
                delay(60_000); continue
            }
            if (points == null || points.length() == 0) {
                if (!resting) { rest(c); resting = true }
                delay(5_000); continue
            }
            resting = false
            for (i in 0 until points.length()) {
                if (!enabled(c)) return
                val p = points.getJSONObject(i)
                val lat = p.getDouble("lat"); val lon = p.getDouble("lon")
                val point = "$lat,$lon"
                // Та же точка подряд или в прошлый раз «нет машин» — открываем чуть в стороне, Go обновит цены сам.
                if (point == lastPoint || noCarsBefore) shift = (shift + 1) % shifts.size else shift = 0
                val (dLat, dLon) = shifts[shift]
                // Перезапуск Go — только если он завис (две проверки подряд без цен).
                val reload = failures >= 2
                if (reload) { restartGo(c); failures = 0 }
                val before = latest?.seq ?: 0
                val started = System.currentTimeMillis()
                try {
                    c.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("yandextaxi://route?start-lat=${lat + dLat}&start-lon=${lon + dLon}"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or if (reload) Intent.FLAG_ACTIVITY_CLEAR_TASK else 0))
                } catch (e: Exception) {
                    TripLog.d("Разведчик: Яндекс Go не открылся (${e.javaClass.simpleName}) — он установлен?"); delay(30_000); break
                }
                // Ждём новый экран Go с ценами (после перезапуска — дольше).
                var screen: Screen? = null
                val limit = if (reload) 15_000 else 8_000
                while (System.currentTimeMillis() - started < limit) {
                    delay(300)
                    val s = latest ?: continue
                    if (s.seq <= before || s.at < started + 400) continue
                    if (s.address != lastAddress || System.currentTimeMillis() - started > 2_500) { screen = s; break }
                }
                lastPoint = point
                if (screen == null) {
                    noCarsBefore = false
                    silent = if (lastEventAt < started) silent + 1 else 0
                    // Go открылся, но на экране ни строчки (вечная загрузка) — перезапуск сразу, не ждём второй неудачи.
                    val blank = lastEventAt >= started && lastBlank
                    failures = if (blank) 2 else failures + 1
                    TripLog.d("Разведчик: в точке $point Go не показал цены${if (blank) " (пустой экран)" else if (lastEventAt < started) " (экран Go не читается)" else ""}")
                    if (silent >= 3) break
                    continue
                }
                failures = 0; silent = 0
                delay(700) // цены подгружаются чуть позже адреса — берём последний экран
                val final = latest?.takeIf { it.seq >= screen.seq } ?: screen
                lastAddress = final.address
                val values = surges(c, final)
                noCarsBefore = values.values.all { it == null }
                val body = JSONObject().put("lat", lat).put("lon", lon).put("values", JSONObject().apply {
                    values.forEach { (k, v) -> put(k, v ?: JSONObject.NULL) }
                })
                val reply = CommunityApi.postBlocking(c, "/api/scout/result", body)
                TripLog.d("Разведчик: ${final.address.take(40)} → цены ${final.prices}, нет машин ${final.noCars}, надбавка ${reply?.opt("value")}" +
                    " (${System.currentTimeMillis() - started} мс${if (reload) ", Go перезапущен" else if (shift > 0) ", сдвиг" else ""})")
            }
        }
    }
}

/** Радар водителя: надбавка разведчика рядом (раз в цикл обновления). */
object ScoutClient {
    /** [scouting] — разведчик работает, но эту точку ещё не проверил или машин нет: «?», а не 0 с сайта. */
    data class Near(val surge: Int?, val far: Boolean, val ageSec: Int, val scouting: Boolean = false,
                    /** Надбавка в точке Б (куда везём); null — разведчик её ещё не проверил. */
                    val surgeB: Int? = null)

    @Volatile private var cache: Pair<Long, Near?>? = null

    suspend fun near(c: Context, lat: Double, lon: Double, b: Pair<Double, Double>? = null, force: Boolean = false): Near? {
        val now = System.currentTimeMillis()
        // Виджет берёт из памяти (ответ не старше 20 с); отдельный опрос раз в 20 с идёт мимо памяти.
        if (!force) cache?.let { if (now - it.first < 20_000) return it.second }
        val body = JSONObject().put("lat", lat).put("lon", lon)
        // Едем по заказу — и точку Б: разведчик проверит её раз в 2–3 минуты, после точек водителей.
        if (b != null) body.put("b_lat", b.first).put("b_lon", b.second)
        val j = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            CommunityApi.postBlocking(c, "/api/scout/near", body)
        }
        val r = j?.takeIf { it.optBoolean("ok") }?.let {
            Near(if (it.isNull("surge")) null else it.optInt("surge"), it.optBoolean("far"), it.optInt("age_s"), it.optBoolean("scouting"),
                if (!it.has("surge_b") || it.isNull("surge_b")) null else it.optInt("surge_b"))
        }
        cache = now to r
        return r
    }
}
