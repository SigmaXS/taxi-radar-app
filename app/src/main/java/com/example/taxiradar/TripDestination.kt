package com.example.taxiradar

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Куда едем по принятому заказу (точка Б) — чтобы на кружке показывать две
 * надбавки: «здесь» и «Б». Б берём с экранов Яндекс Про после «Принять»
 * («А … / Б <адрес>», «Я на месте», «в пути»), пока он виден. Пропал с экранов
 * на 15 минут или заказ завершён — забываем.
 *
 * Доступно на телефонах, планшетах и магнитолах; отображение включается в настройках радара.
 */
object TripDestination {

    private const val TAG = "TRIP_DEST"
    private const val TTL_MS = 15 * 60_000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile private var address: String? = null
    @Volatile private var point: Pair<Double, Double>? = null
    @Volatile private var seenAt = 0L
    @Volatile private var geocoding: String? = null
    @Volatile private var failedAt = 0L
    @Volatile private var failed: String? = null

    /** Б виден на экране, но ещё не найден на карте — на кружке «Б ?». */
    @Volatile var pending: String? = null
        private set

    // Улица внутри длинной строки: «Radisson Blu …, Chisinau, strada Mitropolit Varlaam, 77» → «strada …, 77».
    private val streetPart = Regex(
        """(?iu)(strada|str\.|bulevardul|bd\.|șoseaua|soseaua|calea|aleea|piața|ул\.|улица|бульвар|проспект|шоссе)\s.*$"""
    )

    private val endWords = listOf(
        "заказ завершён", "заказ завершен", "поездка завершена",
        "comanda finalizată", "comandă finalizată", "comanda a fost finalizată", "cursa s-a încheiat",
        "order completed", "trip completed", "ride completed"
    )
    private val notAddress = Regex("""(?iu)^(я здесь|уточнить|завершить|звонок|ожидание|поехали|б|b|\d+|[\d:]+|.*\d\s*(км|м|мин|km|min)\.?)$""")

    fun enabled() = true

    /** Где Б сейчас (lat to lon), если едем по заказу. */
    fun current(): Pair<Double, Double>? =
        point?.takeIf { System.currentTimeMillis() - seenAt < TTL_MS }

    fun clear() {
        address = null
        point = null
        seenAt = 0L
        pending = null
    }

    /** Любой экран Яндекс Про, кроме карточки нового заказа. */
    fun onScreen(context: Context, lines: List<String>) {
        if (!enabled()) return
        val lower = lines.map { it.lowercase() }
        if (lower.any { l -> endWords.any { l.contains(it) } }) {
            if (address != null) Log.d(TAG, "Заказ завершён — Б забыли")
            clear()
            return
        }
        for (i in 0 until lines.size - 1) {
            val m = lines[i].trim()
            if (m != "Б" && m != "B") continue
            val next = lines[i + 1].trim()
            if (next.length <= 5 || next.none { it.isLetter() } || notAddress.matches(next)) continue
            remember(context.applicationContext, next)
            return
        }
    }

    private fun remember(context: Context, b: String) {
        val now = System.currentTimeMillis()
        if (b.equals(address, true) && point != null) {
            seenAt = now
            return
        }
        // Другой заказ (или пассажир сменил Б) — старую точку не показываем.
        if (address != null && !b.equals(address, true)) {
            address = null
            point = null
        }
        if (b == geocoding) return
        // Не нашёлся недавно — не дёргаем поиск на каждом кадре, раз в минуту.
        if (b == failed && now - failedAt < 60_000) return
        geocoding = b
        if (pending != b) {
            pending = b
            FloatingWidgetService.refreshSurge()
        }
        scope.launch {
            fun find(q: String) = try {
                RouteFareCalculator.locate(context, q)
            } catch (e: Exception) {
                null
            }
            // Название места («Radisson Blu …») геокодеру мешает — тогда ищем по улице.
            val p = find(b) ?: streetPart.find(b)?.value?.takeIf { it != b }?.let { find(it) }
            geocoding = null
            if (p != null) {
                address = b
                point = p
                pending = null
                seenAt = System.currentTimeMillis()
                Log.d(TAG, "Едем в Б: «$b» → $p")
            } else {
                failed = b
                failedAt = System.currentTimeMillis()
                Log.d(TAG, "Б не нашёлся на карте: «$b»")
            }
            FloatingWidgetService.refreshSurge()
        }
    }
}
