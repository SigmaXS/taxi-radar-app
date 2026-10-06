package com.example.taxiradar

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.media.AudioManager
import android.media.ToneGenerator
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.*

class OrderAccessibilityService : AccessibilityService() {

    companion object {
        // Система реально подключила службу. После обновления APK тумблер в
        // настройках остаётся включённым, а служба — нет; так это и видно.
        @Volatile
        var isConnected: Boolean = false
            private set
    }

    private val scope = CoroutineScope(Dispatchers.IO)
    private var lastTriggerTime = 0L
    private var lastCapturedOrderKey = ""

    private var lastKnownSurge = -1
    private var activeOrderKey = ""

    /**
     * Надбавки к цене с карточки заказа:
     *  surge — «+55 L» (на фиолетовых заказах — прямо на кнопке «Принять»),
     *  paidPickup — «Платная подача +15 MDL» на дальней подаче.
     */
    private data class Bonus(val surge: Int, val paidPickup: Int) {
        val total get() = surge + paidPickup
        fun max(o: Bonus?) = if (o == null) this else Bonus(maxOf(surge, o.surge), maxOf(paidPickup, o.paidPickup))
    }

    // «+55 L», «+15 MDL», «+ 35 лей», «+20 lei». Без единицы — только 5…150
    // («+1» у кнопки радара и «+2» скрытых строк адреса — не надбавка).
    private val plusAmountRegex = Regex(
        """\+\s*(\d{1,3})(?:[.,]\d+)?\s*(L|Л|лей|lei|MDL)?(?![\p{L}\d])""", RegexOption.IGNORE_CASE
    )
    private val paidPickupRegex = Regex("""(?i)(платн\S*\s+подач|pl[aă]t\S*\s+(?:a\s+)?(?:prelu|deplas)|preluare\s+pl[aă]t|paid\s+pick)""")

    // Надбавка, которую уже видели у заказа (ключ — адреса). Цифры на кнопке
    // появляются не сразу и мигают — берём наибольшую за последние 15 минут,
    // её же прибавляем и после «Поехали».
    private val bonusByRoute = LinkedHashMap<String, Pair<Bonus, Long>>()

    private fun rememberedBonus(routeKey: String): Bonus? = synchronized(bonusByRoute) {
        bonusByRoute[routeKey]?.takeIf { System.currentTimeMillis() - it.second < 15 * 60_000 }?.first
    }

    private fun rememberBonus(routeKey: String, bonus: Bonus): Bonus = synchronized(bonusByRoute) {
        val merged = bonus.max(bonusByRoute[routeKey]?.takeIf { System.currentTimeMillis() - it.second < 15 * 60_000 }?.first)
        bonusByRoute.remove(routeKey)
        bonusByRoute[routeKey] = merged to System.currentTimeMillis()
        while (bonusByRoute.size > 20) bonusByRoute.remove(bonusByRoute.keys.first())
        merged
    }

    private fun parseBonus(lines: List<String>): Bonus {
        var paid = 0
        val usedLines = mutableSetOf<Int>()
        lines.forEachIndexed { i, line ->
            if (!paidPickupRegex.containsMatchIn(line)) return@forEachIndexed
            usedLines += i
            // Сумма — в той же строке или в одной из двух следующих.
            for (j in i..minOf(i + 2, lines.lastIndex)) {
                val m = plusAmountRegex.find(lines[j]) ?: continue
                paid = maxOf(paid, m.groupValues[1].toInt())
                usedLines += j
                break
            }
        }
        var surge = 0
        lines.forEachIndexed { i, line ->
            if (i in usedLines) return@forEachIndexed
            for (m in plusAmountRegex.findAll(line)) {
                val v = m.groupValues[1].toInt()
                val hasUnit = m.groupValues[2].isNotEmpty()
                if ((hasUnit && v in 1..500) || (!hasUnit && v in 5..150)) surge = maxOf(surge, v)
            }
        }
        return Bonus(surge, paid)
    }

    /**
     * Тексты для поиска надбавки: и text, и contentDescription (надпись на
     * кнопке «Принять» часто только в описании), со всех окон Яндекс Про.
     */
    private fun collectBonusLines(fallbackRoot: AccessibilityNodeInfo): List<String> {
        val out = mutableListOf<String>()
        fun walk(n: AccessibilityNodeInfo?) {
            if (n == null) return
            n.text?.toString()?.let { out += it.split("\n") }
            n.contentDescription?.toString()?.let { out += it.split("\n") }
            for (i in 0 until n.childCount) walk(n.getChild(i))
        }
        val roots = try {
            windows.mapNotNull { it.root }.filter {
                val pkg = it.packageName?.toString().orEmpty()
                pkg != packageName && (pkg.contains("taximeter", true) || pkg.contains("yandex", true))
            }
        } catch (e: Exception) {
            emptyList()
        }
        if (roots.isEmpty()) walk(fallbackRoot) else roots.forEach { walk(it) }
        return out.map { it.trim() }.filter { it.isNotEmpty() }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        isConnected = true
        Log.d("ORDER_DEBUG", "★★★ Служба ПОДКЛЮЧЕНА системой ★★★")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val pkg = event.packageName?.toString() ?: ""

        if (isDialer(pkg)) {
            try {
                onDialerEvent()
            } catch (e: Exception) {
                Log.e("CLIENTS", "Звонилка: ${e.message}")
            }
            return
        }

        if (!pkg.contains("taximeter", ignoreCase = true) && !pkg.contains("yandex", ignoreCase = true)) {
            return
        }

        // Экран Яндекс Про меняется десятки раз в секунду. Разбираем его один
        // раз, когда он «успокоился» (150 мс без новых событий), — иначе служба
        // гоняла процессор и грела телефон.
        handler.removeCallbacks(processScreen)
        handler.postDelayed(processScreen, 150)
    }

    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private val processScreen = Runnable { processYandexScreen() }

    private fun processYandexScreen() {
        try {
            val root = rootInActiveWindow ?: return
            val allNodes = mutableListOf<NodeData>()
            collectNodes(root, allNodes)

            if (allNodes.isEmpty()) return
            val allTexts = allNodes.map { it.text }

            lastYandexEventAt = System.currentTimeMillis()
            dialerFromYandex = false
            // Способ оплаты виден на экране поездки — запоминаем для отметки клиента.
            if (allTexts.any { it.contains("Оплата картой", true) || it.contains("cu cardul", true) || it.contains("Card payment", true) }) {
                cardPaymentSeenAt = lastYandexEventAt
            }


            // «Принять» — русский Яндекс Про, «Acceptă» / «Accept» — румынский и английский.
            val hasAccept = allTexts.any {
                it.contains("Принять", ignoreCase = true) || it.contains("Accept", ignoreCase = true)
            }
            if (!hasAccept) {
                maybeLearnTraffic(allNodes)
                TripTracker.onScreen(this, root.packageName?.toString().orEmpty(), allTexts)
                return
            }
            // Полный список текстов — только для карточки заказа (для разбора ошибок).
            // Строка лога обрезается после ~4 КБ — пишем кусками.
            allTexts.mapIndexed { i, t -> "[$i]\"$t\"" }.joinToString(" | ").chunked(3000).forEachIndexed { i, part ->
                Log.d("ORDER_DEBUG", "Все тексты с экрана (${allTexts.size} шт) [${i + 1}]: $part")
            }
            // Новая карточка заказа: способ оплаты прошлого клиента больше не наш.
            lastOrderAt = System.currentTimeMillis()
            cardPaymentSeenAt = 0L

            val cardLines = allNodes.flatMap { it.text.split("\n") }.map { it.trim() }.filter { it.isNotEmpty() }
            if (isDeliveryCard(cardLines)) {
                Log.d("ORDER_DEBUG", "Карточка Доставки — не считаем")
                FloatingWidgetService.clearOrder()
                return
            }

            var addrA = ""
            var addrB = ""

            // Внутри одной ноды текст может содержать реальные переносы строк
            // (например, целый узел "А\nулица Индепенденцей, 42/2\nБ\nсело
            // Бубуечь..."), поэтому разбиваем каждую ноду на отдельные строки
            // и ищем маркеры А/Б уже по строкам, а не по нодам целиком.
            val allLines = allNodes.flatMap { it.text.split("\n") }
                .map { it.trim() }
                .filter { it.isNotEmpty() }

            // Индексы нужны, чтобы найти заезды: на карточке они идут строками
            // без буквы между адресом А и маркером Б.
            var addrAIndex = -1
            var markerBIndex = -1
            for (i in allLines.indices) {
                val t = allLines[i]

                if (isMarkerFor(t, 'А') && i + 1 < allLines.size) {
                    val next = allLines[i + 1]
                    if (addrA.isEmpty() && next.length > 3 && !isServiceWord(next)) {
                        addrA = next
                        addrAIndex = i + 1
                    }
                }

                if (isMarkerFor(t, 'Б') && i + 1 < allLines.size) {
                    val next = allLines[i + 1]
                    if (addrB.isEmpty() && next.length > 3 && !isServiceWord(next)) {
                        addrB = next
                        markerBIndex = i
                    }
                }
            }

            val stops = if (addrAIndex in 0 until markerBIndex) {
                allLines.subList(addrAIndex + 1, markerBIndex)
                    .filter { it.length > 3 && !isServiceWord(it) && it.any { c -> c.isLetter() } }
                    .map { stripHiddenLinesSuffix(it).take(60) }
                    .filter { looksLikeStop(it, addrA, allLines.getOrNull(markerBIndex + 1).orEmpty()) }
                    .distinct()
            } else {
                emptyList()
            }

            // Приложение иногда обрезает длинный адрес и дописывает "+N" (сколько
            // строк скрыто) — убираем этот хвост, чтобы не мешал геокодированию.
            addrA = stripHiddenLinesSuffix(addrA)
            addrB = stripHiddenLinesSuffix(addrB)

            Log.d("ORDER_DEBUG", "После основного поиска: addrA=\"$addrA\" заезды=$stops addrB=\"$addrB\"")

            // Заказ без точки Б: на карточке только «А». Цены нет — запоминаем
            // тариф и надбавку, посчитаем после «Поехали» по маршруту Яндекса.
            if (addrAIndex >= 0 && allLines.none { isMarkerFor(it, 'Б') }) {
                rememberNoDestination(root, allTexts, allLines, addrA)
                return
            }

            if (addrA.isEmpty() || addrB.isEmpty()) {
                // Резерв только для случая, когда метки А/Б не нашлись, но адреса
                // на экране всё же есть. Если кандидатов меньше двух РАЗНЫХ строк
                // (например, карточка вообще без адреса назначения — только
                // подача + бонус, как у "приоритетных" заказов) — оставляем
                // addrB пустым, дальше сработает защита ниже и цена просто не
                // покажется, вместо того чтобы считать маршрут до случайного текста.
                val candidates = mutableListOf<String>()
                for (line in allLines) {
                    if (line.length >= 4 && !isServiceWord(line) && !line.contains("·") &&
                        line.any { it.isLetter() } && line != addrA
                    ) {
                        candidates.add(line)
                    }
                }
                if (addrA.isEmpty()) addrA = candidates.getOrNull(0) ?: ""
                if (addrB.isEmpty()) addrB = candidates.firstOrNull { it != addrA } ?: ""
            }

            if (addrA.isEmpty() || addrB.isEmpty()) {
                Log.e("ORDER_DEBUG", "ОСТАНОВКА: addrA или addrB пустые после fallback (addrA=\"$addrA\" addrB=\"$addrB\")")
                return
            }

            if (addrA.length > 60) addrA = addrA.substring(0, 60)
            if (addrB.length > 60) addrB = addrB.substring(0, 60)

            val route = listOf(addrA) + stops + addrB
            val routeKey = route.joinToString(" -> ")
            // Мини-карта то и дело перерисовывается, и на части снимков подписей
            // нет. Раз увидели их у этого заказа — помним, иначе расчёт «без
            // подписей» перебил бы на виджете точную цену.
            val cardRoute = parseCardRoute(allTexts)?.also { cardRoutes[routeKey] = it to System.currentTimeMillis() }
                ?: cardRoutes[routeKey]?.takeIf { System.currentTimeMillis() - it.second < 10 * 60_000 }?.first
            while (cardRoutes.size > 20) cardRoutes.remove(cardRoutes.keys.first())
            // Подписи маршрута на мини-карте появляются через секунду — тогда
            // ключ заказа меняется, и цена сразу пересчитывается по ним.
            // Надбавка: с кнопки «Принять» и «Платная подача». Наибольшая из виденных у этого заказа.
            val seenBonus = parseBonus(collectBonusLines(root))
            val bonus = rememberBonus(routeKey, seenBonus)
            Log.d("ORDER_DEBUG", "Надбавка на экране: +${seenBonus.surge}, платная подача +${seenBonus.paidPickup}; для заказа: +${bonus.total}")
            // Надбавка появилась или выросла — это повод сразу пересчитать цену.
            val orderKey = route.joinToString(" -> ") + (cardRoute?.let { " | ${it.km} км ${it.min} мин" } ?: "") +
                " | +${bonus.total}"
            val now = System.currentTimeMillis()
            if (orderKey == lastCapturedOrderKey && (now - lastTriggerTime < 3_000)) {
                Log.d("ORDER_DEBUG", "Пропуск: дубликат заказа в течение 3 сек")
                return
            }

            lastTriggerTime = now
            lastCapturedOrderKey = orderKey

            // По-румынски тарифы — «Econom», «Confort», «Confort+».
            val tariff = when {
                allTexts.any {
                    it.contains("комфорт+", true) || it.contains("comfort+", true) || it.contains("confort+", true)
                } -> "Комфорт+"
                allTexts.any {
                    it.contains("комфорт", true) || it.contains("comfort", true) || it.contains("confort", true)
                } -> "Комфорт"
                else -> "Эконом"
            }

            val surgeBonus = bonus.total
            if (routeKey != activeOrderKey) {
                activeOrderKey = routeKey
                lastKnownSurge = surgeBonus
            } else if (surgeBonus != lastKnownSurge) {
                playTickSound()
                lastKnownSurge = surgeBonus
            }

            if (!YandexApiKey.ready(this)) {
                Log.e("FARE_CALC", "Нет ни своего ключа Яндекса, ни общего геокодера — цену не считаем")
                return
            }
            val apiKey = YandexApiKey.get(this).orEmpty()

            val pickupKm = parsePickupKm(allLines)

            val calcId = ++calcSeq
            scope.launch {
                try {
                    calculateAndShow(route, tariff, surgeBonus, apiKey, pickupKm, cardRoute, calcId)
                } catch (e: Exception) {
                    Log.e("FARE_CALC", "Исключение внутри scope.launch: ${e.message}", e)
                }
            }

        } catch (e: Exception) {
            Log.e("ORDER_DEBUG", "Ошибка onAccessibilityEvent: ${e.message}")
        }
    }

    // ---------- звонок клиенту ----------

    private var lastYandexEventAt = 0L
    private var lastOrderAt = 0L
    private var cardPaymentSeenAt = 0L
    private var dialerFromYandex = false
    private var lastClientNumber: String? = null
    private var lastClientAt = 0L

    private fun isDialer(pkg: String): Boolean {
        val p = pkg.lowercase()
        return p.contains("dialer") || p.contains("incallui") || p == "com.android.contacts" ||
                p == "com.android.phone" || p == "com.samsung.android.contacts"
    }

    /**
     * Водитель нажал в Яндекс Про «Позвонить» — открылась звонилка с настоящим
     * номером клиента. Берём номер, только если звонилку открыли прямо из
     * Яндекс Про (не позже 10 с после его экрана) и недавно был заказ, —
     * личные звонки водителя не трогаем.
     */
    private fun onDialerEvent() {
        val now = System.currentTimeMillis()
        if (!dialerFromYandex) {
            if (now - lastYandexEventAt > 10_000 || now - lastOrderAt > 3 * 3600_000L) return
            dialerFromYandex = true
        }
        val root = rootInActiveWindow ?: return
        // Сначала поле набора номера — в нём ровно то, что набирается.
        val fromDigits = mutableListOf<String>()
        val allNumbers = linkedSetOf<String>()
        fun walk(node: AccessibilityNodeInfo?) {
            if (node == null) return
            val text = (node.text ?: node.contentDescription)?.toString()
            if (!text.isNullOrBlank()) {
                val found = PhoneNumbers.findAll(text)
                allNumbers += found
                if (node.viewIdResourceName?.contains("digits", ignoreCase = true) == true) fromDigits += found
            }
            for (i in 0 until node.childCount) walk(node.getChild(i))
        }
        walk(root)
        // Поля набора нет — берём номер, только если он на экране один
        // (иначе можно спутать с контактами из «избранного»).
        val number = fromDigits.firstOrNull() ?: allNumbers.singleOrNull() ?: return
        if (number == lastClientNumber && now - lastClientAt < 10 * 60_000) return
        lastClientNumber = number
        lastClientAt = now
        Log.d("CLIENTS", "Звонок клиенту ${PhoneNumbers.tail(number)} из Яндекс Про")
        ClientsManager.onClientCall(this, number, cardPayment = now - cardPaymentSeenAt < 2 * 3600_000L)
    }

    /** Последний посчитанный заказ — ждём, примет ли его водитель. */
    private data class PendingTrip(
        val at: Long,
        val pickupKm: Double,
        val osrmKm: Double,
        val osrmMin: Double,
        // Для пересчёта цены по навигатору Яндекса после «Поехали».
        val tariff: String,
        val surgeBonus: Int,
        val cityKm: Double,
        val outOfCityKm: Double,
        // Для базы адресов и отчёта о точности цены.
        val route: List<String>,
        val estPrice: Int,
        val estMin: Double
    )

    // Несколько последних: во время поездки может прийти заказ «в цепочку»,
    // и он не должен затереть поездку, которую мы ещё ждём.
    private val pendingTrips = java.util.concurrent.CopyOnWriteArrayList<PendingTrip>()

    private fun rememberTrip(trip: PendingTrip) {
        // Та же карточка пересчитывается много раз — держим одну запись.
        pendingTrips.removeAll { it.osrmKm == trip.osrmKm && it.osrmMin == trip.osrmMin }
        pendingTrips.add(trip)
        while (pendingTrips.size > 3) pendingTrips.removeAt(0)
    }

    // Строка маршрута целиком: «2,8 km · 8 min», «600 м · 3 мин», «12 км · 25 мин.».
    private val etaLineRegex = Regex("""^(\d+(?:[.,]\d+)?)\s*(км|м|km|m)\s*·\s*(\d+)\s*(мин|min)\.?$""")

    /**
     * В навигаторе Яндекс Про строки «2,8 km · 8 min» — это его прогноз с
     * пробками. Сначала там варианты дороги ДО пассажира (экран «Показать
     * маршрут» / «Indică traseul»), а когда поездка началась — маршрут до Б.
     * Адресов на этих экранах нет, поэтому поездку узнаём по километрам:
     * первая строка, где км совпадают с нашим маршрутом А→Б (±25%), — это
     * начало поездки. Подача по километрам почти всегда другая и отсеивается.
     */
    private fun maybeLearnTraffic(nodes: List<NodeData>) {
        maybePriceNoDestination(nodes.flatMap { it.text.split("\n") }.map { it.trim() }.filter { it.isNotEmpty() })
        pendingTrips.removeAll { System.currentTimeMillis() - it.at > 60 * 60_000 || it.osrmKm < 1.5 }
        if (pendingTrips.isEmpty()) return
        val etas = nodes.flatMap { it.text.split("\n") }
            .mapNotNull { etaLineRegex.find(it.trim()) }
            .map { m ->
                val value = m.groupValues[1].replace(',', '.').toDouble()
                val km = if (m.groupValues[2] == "км" || m.groupValues[2] == "km") value else value / 1000.0
                km to m.groupValues[3].toInt()
            }
        if (etas.isEmpty()) return
        // Поездки короче ~1,5 км по километрам не отличить от подачи — на них не учимся (отсеяны выше).
        var trip: PendingTrip? = null
        var match: Pair<Double, Int>? = null
        for (t in pendingTrips.reversed()) {
            match = etas.firstOrNull { (km, _) ->
                kotlin.math.abs(km - t.osrmKm) <= t.osrmKm * 0.25 &&
                        // Совпало ещё и с подачей — непонятно, что это; пропускаем.
                        !(t.pickupKm > 0 && kotlin.math.abs(km - t.pickupKm) <= 0.3)
            }
            if (match != null) {
                trip = t
                break
            }
        }
        if (trip == null || match == null) {
            Log.d("TRAFFIC", "Жду начала поездки: на экране $etas, ждём ${pendingTrips.map { it.osrmKm }} км")
            return
        }
        val (yandexKm, yandexMin) = match
        pendingTrips.remove(trip)
        val now = java.util.Calendar.getInstance()
        TrafficModel.add(
            this,
            TrafficModel.Sample(
                at = System.currentTimeMillis(),
                hour = now.get(java.util.Calendar.HOUR_OF_DAY),
                weekend = TrafficModel.isWeekend(now),
                osrmMin = trip.osrmMin,
                yandexMin = yandexMin,
                osrmKm = trip.osrmKm,
                yandexKm = yandexKm
            )
        )
        Log.d("TRAFFIC", "Запомнили поездку: OSRM ${"%.1f".format(trip.osrmMin)} мин / Яндекс $yandexMin мин, $yandexKm км")

        // Уточнённая цена: тариф и надбавка — с карточки заказа, км и минуты —
        // из навигатора Яндекса. Городские/загородные км делим в той же
        // пропорции, что и в нашем маршруте.
        val ourKm = trip.cityKm + trip.outOfCityKm
        val scale = if (ourKm > 0) yandexKm / ourKm else 1.0
        // Надбавка — наибольшая, что была на карточке до «Принять» (цифры на
        // кнопке появляются не сразу), плюс платная подача.
        val surge = maxOf(trip.surgeBonus, rememberedBonus(trip.route.joinToString(" -> "))?.total ?: 0)
        val refined = RouteFareCalculator.price(
            trip.tariff, trip.cityKm * scale, trip.outOfCityKm * scale, yandexMin.toDouble(), surge
        )
        Log.d("TRAFFIC", "Уточнённая цена по навигатору: $refined L (надбавка +$surge)")
        FloatingWidgetService.showRefinedPrice(refined, yandexKm, yandexMin, trip.pickupKm, bonus = surge)
        TripTracker.onTripStarted(
            this, trip.route, trip.tariff, surge,
            estPrice = trip.estPrice, estKm = trip.osrmKm, estMin = trip.estMin,
            navKm = yandexKm, navMin = yandexMin, navPrice = refined
        )
    }

    // ---------- заказ без точки Б ----------

    /** Принятый (или показанный) заказ без Б: тариф и надбавка с карточки. */
    private data class NoDestOrder(
        val at: Long, val tariff: String, val surge: Int, val pickupKm: Double, val addrA: String
    )

    @Volatile
    private var noDest: NoDestOrder? = null

    private fun tariffOf(texts: List<String>): String = when {
        texts.any { it.contains("комфорт+", true) || it.contains("comfort+", true) || it.contains("confort+", true) } -> "Комфорт+"
        texts.any { it.contains("комфорт", true) || it.contains("comfort", true) || it.contains("confort", true) } -> "Комфорт"
        else -> "Эконом"
    }

    private fun rememberNoDestination(root: AccessibilityNodeInfo, texts: List<String>, lines: List<String>, addrA: String) {
        val key = "nodest|" + addrA
        val bonus = rememberBonus(key, parseBonus(collectBonusLines(root)))
        val order = NoDestOrder(System.currentTimeMillis(), tariffOf(texts), bonus.total, parsePickupKm(lines), stripHiddenLinesSuffix(addrA).take(60))
        if (noDest?.addrA != order.addrA || noDest?.surge != order.surge) {
            Log.d("FARE_CALC", "Заказ без точки Б: ${order.tariff}, надбавка +${order.surge}, А=«${order.addrA}» — посчитаю после «Поехали»")
        }
        noDest = order
        FloatingWidgetService.clearOrder()
    }

    // Отдельные подписи на экране поездки: «7 км», «15 мин».
    private val soloKmRegex = Regex("""^(\d+(?:[.,]\d+)?)\s*(км|km)$""", RegexOption.IGNORE_CASE)
    private val soloMinRegex = Regex("""^(\d+)\s*(мин|min)\.?$""", RegexOption.IGNORE_CASE)

    /**
     * Экран после принятия заказа без Б. Маршрут до Б Яндекс показывает либо
     * вариантами «7 км · 15 мин» над кнопкой «Поехали», либо в пути: «Б»,
     * адрес, «7 км», «15 мин». Берём его, считаем по тарифу + надбавка с карточки.
     */
    private fun maybePriceNoDestination(lines: List<String>) {
        val order = noDest ?: return
        if (System.currentTimeMillis() - order.at > 60 * 60_000) {
            noDest = null
            return
        }
        // Адрес Б — строка после маркера «Б».
        val bIdx = lines.indexOfFirst { it == "Б" || it == "B" }
        val addrB = lines.getOrNull(bIdx + 1)?.takeIf {
            bIdx >= 0 && it.length > 5 && it.any { c -> c.isLetter() } && !isServiceWord(it)
        }
        val choosingRoute = lines.any { it.equals("Поехали", true) || it.contains("Укажите маршрут", true) }
        var km: Double? = null
        var min: Int? = null
        if (choosingRoute) {
            // Первый вариант — выбранный маршрут до Б (водитель уже у А).
            lines.firstNotNullOfOrNull { etaLineRegex.find(it) }?.let { m ->
                val v = m.groupValues[1].replace(',', '.').toDouble()
                km = if (m.groupValues[2] == "км" || m.groupValues[2] == "km") v else v / 1000.0
                min = m.groupValues[3].toInt()
            }
        }
        if (km == null && addrB != null) {
            km = lines.drop(bIdx).firstNotNullOfOrNull { soloKmRegex.find(it) }?.groupValues?.get(1)?.replace(',', '.')?.toDoubleOrNull()
            min = lines.drop(bIdx).firstNotNullOfOrNull { soloMinRegex.find(it) }?.groupValues?.get(1)?.toIntOrNull()
        }
        val yKm = km ?: return
        val yMin = min ?: return
        if (yKm < 0.3) return
        noDest = null
        Log.d("FARE_CALC", "Заказ без Б: маршрут Яндекса $yKm км, $yMin мин, Б=«$addrB»")
        scope.launch {
            // Город/загород — по нашему маршруту А→Б, если Б известна; иначе считаем, что по городу.
            var cityKm = yKm
            var outKm = 0.0
            if (addrB != null && YandexApiKey.ready(this@OrderAccessibilityService)) {
                RouteFareCalculator.calculate(
                    addresses = listOf(order.addrA, addrB.take(60)), tariffName = order.tariff,
                    yandexApiKey = YandexApiKey.get(this@OrderAccessibilityService).orEmpty(),
                    context = applicationContext
                )?.let { r ->
                    val ours = r.cityKm + r.outOfCityKm
                    if (ours > 0) {
                        outKm = yKm * r.outOfCityKm / ours
                        cityKm = yKm - outKm
                    }
                }
            }
            val price = RouteFareCalculator.price(order.tariff, cityKm, outKm, yMin.toDouble(), order.surge)
            Log.d("FARE_CALC", "Заказ без Б: цена $price L (надбавка +${order.surge})")
            withContext(Dispatchers.Main) {
                FloatingWidgetService.showRefinedPrice(price, yKm, yMin, order.pickupKm, bonus = order.surge)
            }
        }
    }

    // ---------- Доставка ----------

    /** Карточка «Доставка» — радар её не считает (только такси). */
    private fun isDeliveryCard(lines: List<String>): Boolean =
        lines.any { it.equals("Доставка", true) || it.equals("Livrare", true) } &&
                lines.any { it.contains("получени", true) || it.contains("вручени", true) || it.equals("Откуда", true) || it.equals("De unde", true) }

    private val pickupRegex = Regex("""^(\d+(?:[.,]\d+)?)\s*(км|м|km|m)\s*·\s*\d+\s*(мин|min)""")

    /** Подача с карточки заказа: «600 м · 3 мин», «1,3 км · 4 мин», «1,3 km · 6 min.» → км. */
    private fun parsePickupKm(lines: List<String>): Double {
        for (line in lines) {
            val m = pickupRegex.find(line) ?: continue
            val value = m.groupValues[1].replace(',', '.').toDoubleOrNull() ?: continue
            return if (m.groupValues[2] == "км" || m.groupValues[2] == "km") value else value / 1000.0
        }
        return 0.0
    }

    // Яндекс подписывает км и минуты на карточке только у поездок от 40 минут.
    private val MAX_UNLABELED_MIN = 39

    /** Маршрут, который Яндекс сам подписал на мини-карте карточки: «17 км», «50 мин». */
    private data class CardRoute(val km: Double, val min: Int)

    private val cardKmRegex = Regex("""^(\d+(?:[.,]\d+)?)\s*(км|km)$""", RegexOption.IGNORE_CASE)
    private val cardMinRegex = Regex("""^(?:(\d+)\s*(ч|h)\s*)?(\d+)\s*(мин|min)\.?$""", RegexOption.IGNORE_CASE)

    /**
     * Отдельные подписи на карте, а не строка подачи «200 м · 1 мин» (в ней
     * «·», и она в одной ноде с «Ближняя подача»). Есть не на каждой карточке
     * и не сразу — мини-карта дорисовывается через секунду.
     */
    private fun parseCardRoute(texts: List<String>): CardRoute? {
        val km = texts.firstNotNullOfOrNull { cardKmRegex.find(it.trim()) }
            ?.groupValues?.get(1)?.replace(',', '.')?.toDoubleOrNull() ?: return null
        val m = texts.firstNotNullOfOrNull { cardMinRegex.find(it.trim()) } ?: return null
        val min = (m.groupValues[1].toIntOrNull() ?: 0) * 60 + m.groupValues[3].toInt()
        return if (km > 0 && min > 0) CardRoute(km, min) else null
    }

    // Подписи маршрута, которые уже видели на карточке заказа (ключ — адреса заказа).
    private val cardRoutes = LinkedHashMap<String, Pair<CardRoute, Long>>()

    // Заказы, по карточке которых уже поучились, — чтобы не записать одно и то же много раз.
    private val learnedCards = LinkedHashSet<String>()

    // Номер последнего запущенного расчёта: расчёты идут параллельно, и
    // более ранний (например, ещё без подписей маршрута) может закончиться
    // позже — на виджет попадает только самый свежий.
    @Volatile
    private var calcSeq = 0L

    private suspend fun calculateAndShow(
        route: List<String>, tariff: String, surgeBonus: Int, apiKey: String, pickupKm: Double,
        cardRoute: CardRoute?, calcId: Long
    ) {
        Log.d("FARE_CALC", "calculateAndShow: маршрут=$route tariff=$tariff маршрут_Яндекса=$cardRoute")
        val traffic = TrafficModel.factor(this)
        var result = RouteFareCalculator.calculate(
            addresses = route,
            tariffName = tariff,
            yandexApiKey = apiKey,
            surgeBonus = surgeBonus,
            timeFactor = traffic.value,
            context = applicationContext
        )

        // Час пик, а свои поездки в этот час ещё не выучены — +10 минут
        // (на короткой поездке — не больше её собственного времени). Если Яндекс
        // подписал маршрут на карточке, ниже берём его минуты, там пробки уже есть.
        if (result != null && cardRoute == null && !traffic.fromDrivers) {
            val extra = minOf(TrafficModel.rushHourExtraMin(), result.durationMin)
            if (extra > 0) {
                val minutes = result.durationMin + extra
                val price = RouteFareCalculator.price(tariff, result.cityKm, result.outOfCityKm, minutes.toDouble(), surgeBonus)
                Log.d("FARE_CALC", "Час пик: +$extra мин → $minutes мин, ${result.price} → $price L")
                result = result.copy(price = price, durationMin = minutes)
            }
        }

        // Яндекс подписал на карточке свой маршрут — это его км и минуты с
        // пробками и с заездами. Считаем цену по ним; наш маршрут нужен только
        // чтобы знать, какая доля пути за городом. Явную нестыковку (другой
        // заказ, ошибка распознавания) не берём.
        if (result != null && cardRoute != null) {
            val ourKm = result.cityKm + result.outOfCityKm
            val plausible = ourKm > 0 && cardRoute.km in (ourKm * 0.6)..(ourKm * 1.8)
            if (plausible) {
                val scale = cardRoute.km / ourKm
                val price = RouteFareCalculator.price(
                    tariff, result.cityKm * scale, result.outOfCityKm * scale, cardRoute.min.toDouble(), surgeBonus
                )
                Log.d("FARE_CALC", "По маршруту Яндекса с карточки: ${cardRoute.km} км, ${cardRoute.min} мин → $price L (наша оценка ${result.price} L)")
                val key = route.joinToString(" -> ")
                // Учимся только без заездов: в минуты Яндекса с заездом входит сам
                // заезд и объезд — это не пробки, поправка для обычных поездок завысилась бы.
                if (route.size == 2 && key !in learnedCards && result.osrmMin > 0) {
                    learnedCards += key
                    while (learnedCards.size > 50) learnedCards.remove(learnedCards.first())
                    val now = java.util.Calendar.getInstance()
                    TrafficModel.add(
                        this,
                        TrafficModel.Sample(
                            at = System.currentTimeMillis(),
                            hour = now.get(java.util.Calendar.HOUR_OF_DAY),
                            weekend = TrafficModel.isWeekend(now),
                            osrmMin = result.osrmMin,
                            yandexMin = cardRoute.min,
                            osrmKm = ourKm,
                            yandexKm = cardRoute.km
                        )
                    )
                }
                result = result.copy(price = price, distanceKm = cardRoute.km, durationMin = cardRoute.min)
            } else {
                Log.d("FARE_CALC", "Маршрут с карточки ${cardRoute.km} км не похож на наш ${"%.1f".format(ourKm)} км — не используем")
            }
        } else if (result != null && result.durationMin > MAX_UNLABELED_MIN) {
            // Подписей на карточке нет — значит, по расчёту Яндекса поездка короче
            // 40 минут (длиннее он подписывает км и минуты). Наша оценка вышла
            // больше — мы ошиблись в большую сторону, обрезаем.
            val price = RouteFareCalculator.price(
                tariff, result.cityKm, result.outOfCityKm, MAX_UNLABELED_MIN.toDouble(), surgeBonus
            )
            Log.d("FARE_CALC", "Подписей нет — поездка < 40 мин: ${result.durationMin} → $MAX_UNLABELED_MIN мин, ${result.price} → $price L")
            result = result.copy(price = price, durationMin = MAX_UNLABELED_MIN)
        }

        withContext(Dispatchers.Main) {
            if (calcId != calcSeq) {
                Log.d("FARE_CALC", "Расчёт #$calcId устарел (уже идёт #$calcSeq) — на виджет не выводим")
                return@withContext
            }
            if (result != null && result.price > 0) {
                // Учимся только на поездках А→Б без заездов: с заездом навигатор
                // после «Поехали» ведёт до заезда, а не до Б, и время не сравнить.
                // Цена с заездами считается как обычно, с той же поправкой.
                if (route.size == 2 && route.joinToString(" -> ") !in learnedCards) {
                    rememberTrip(
                        PendingTrip(
                            at = System.currentTimeMillis(),
                            pickupKm = pickupKm,
                            osrmKm = result.distanceKm,
                            osrmMin = result.osrmMin,
                            tariff = tariff,
                            surgeBonus = surgeBonus,
                            cityKm = result.cityKm,
                            outOfCityKm = result.outOfCityKm,
                            route = route,
                            estPrice = result.price,
                            estMin = result.durationMin.toDouble()
                        )
                    )
                }
                FloatingWidgetService.showOrderData(
                    price = result.price,
                    km = result.distanceKm,
                    min = result.durationMin,
                    pickupKm = pickupKm,
                    stops = result.stops,
                    bonus = surgeBonus
                )
            } else {
                // Не угадываем цену: если посчитать не вышло — на виджете её просто нет.
                Log.e("FARE_CALC", "Цену посчитать не удалось — на виджет ничего не выводим")
                FloatingWidgetService.clearOrder()
            }
        }
    }

    // «от 45 L» / «from L 45» — вилка цены тарифа.
    private val priceRangeRegex = Regex("""(?i)^\s*(от|from)\s*(l\s*)?\d+""")

    // Число с единицей: «45 L», «L 45», «3 мин», «1,3 km», «600 м», «35 lei».
    // Раньше хватало буквы: « l» или «мин» внутри строки — и под нож шли адреса
    // вроде «strada Liviu Deleanu», «strada Ismail» или «улица Минская».
    private val unitRegex = Regex(
        """(?i)(\d\s*(км|м|мин|km|m|min|l|lei|лей)(?![\p{L}]))|((?<![\p{L}])(l|lei)\s*\d)"""
    )

    private fun isServiceWord(t: String): Boolean {
        val lower = t.lowercase()
        if (priceRangeRegex.containsMatchIn(t) || unitRegex.containsMatchIn(t)) return true
        return lower.contains("принять") || lower.contains("пропустить") ||
                lower.contains("подача") ||
                lower.contains("·") || lower.startsWith("+") ||
                lower.contains("вы находитесь") || lower.contains("я здесь") ||
                lower.contains("уточнить") || lower.contains("приоритет") ||
                // Яндекс Про на румынском (местами — на английском).
                lower.contains("accept") || lower.contains("omite") ||
                lower.contains("preluare") || lower.contains("accesul la comenzi") ||
                lower.contains("prioritate") || lower.contains("are you here") ||
                // Название тарифа само по себе (например, отдельная строка
                // "Комфорт" в карточке без адреса назначения) — не адрес,
                // но раньше проходило фильтр и попадало в addrB по ошибке.
                lower == "эконом" || lower == "комфорт" || lower == "комфорт+" ||
                lower == "econom" || lower == "comfort" || lower == "comfort+" ||
                lower == "confort" || lower == "confort+" ||
                t == "А" || t == "Б" || t == "A" || t == "B"
    }

    /**
     * Проверяет, является ли текст маркером точки А или Б — либо сам по себе
     * ("А", "Б"), либо в конце строки через запятую (как в реальном интерфейсе
     * Yandex Pro: "Комфорт, А").
     */
    private fun isMarkerFor(t: String, marker: Char): Boolean {
        val cyr = marker
        val lat = when (marker) { 'А' -> 'A'; 'Б' -> 'B'; else -> marker }
        val trimmed = t.trim()
        return trimmed == cyr.toString() || trimmed == lat.toString() ||
                trimmed.endsWith(", $cyr") || trimmed.endsWith(",$cyr") ||
                trimmed.endsWith(", $lat") || trimmed.endsWith(",$lat")
    }

    // Слова, по которым строка похожа на адрес, а не на город или пометку.
    private val streetWordRegex = Regex(
        """(?i)(^|[\s,.])(str|strada|stradela|bd|bul|bulevardul|șos|şos|sos|soseaua|șoseaua|aleea|piața|piata|calea|ул|улица|пр|проспект|бул|бульвар|шоссе|пер|переулок|село|satul|sat|com)[\s.,]"""
    )
    private val entranceRegex = Regex("""(?i)^(entrance|подъезд|scara|scară|poarta|ворота|этаж|etaj|кв|ap)\b""")

    /**
     * Строка между А и Б — настоящий заезд, а не скрытый дубль адреса, город
     * («Chișinău»), подъезд или комментарий. Раньше такая строка считалась
     * заездом: на виджете «1 остановка», а маршрут шёл через центр города.
     */
    private fun looksLikeStop(line: String, addrA: String, addrB: String): Boolean {
        val l = line.trim().lowercase()
        val a = stripHiddenLinesSuffix(addrA).lowercase()
        val b = stripHiddenLinesSuffix(addrB).lowercase()
        if (a.isNotEmpty() && (l.contains(a) || a.contains(l))) return false
        if (b.isNotEmpty() && (l.contains(b) || b.contains(l))) return false
        if (entranceRegex.containsMatchIn(l)) return false
        return l.any { it.isDigit() } || streetWordRegex.containsMatchIn(" $l ")
    }

    /**
     * Убирает хвост вида ", +1" / "+2" и т.п. — так приложение помечает,
     * что часть адреса скрыта и не показана текстом на экране.
     */
    private fun stripHiddenLinesSuffix(address: String): String {
        return address.replace(Regex("""[,\s]*\+\d+\s*$"""), "").trim()
    }

    data class NodeData(val text: String, val rect: Rect)

    private fun collectNodes(node: AccessibilityNodeInfo?, list: MutableList<NodeData>) {
        if (node == null) return
        val t = (node.text ?: node.contentDescription)?.toString()?.trim()
        if (!t.isNullOrBlank()) {
            val rect = Rect()
            node.getBoundsInScreen(rect)
            list.add(NodeData(t, rect))
        }
        for (i in 0 until node.childCount) {
            collectNodes(node.getChild(i), list)
        }
    }

    private fun playTickSound() {
        try {
            ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100).startTone(ToneGenerator.TONE_PROP_BEEP, 200)
        } catch (e: Exception) {}
    }

    override fun onInterrupt() {}

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        isConnected = false
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        isConnected = false
        super.onDestroy()
    }
}