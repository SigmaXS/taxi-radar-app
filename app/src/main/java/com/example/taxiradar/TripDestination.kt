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
 * Пока включено только на мультимедиа машин (BYD): там большой экран и кружок
 * не мешает; на телефоне — позже, если понравится.
 */
object TripDestination {

    private const val TAG = "TRIP_DEST"
    private const val TTL_MS = 15 * 60_000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile private var address: String? = null
    @Volatile private var point: Pair<Double, Double>? = null
    @Volatile private var seenAt = 0L
    @Volatile private var geocoding: String? = null

    private val endWords = listOf(
        "заказ завершён", "заказ завершен", "поездка завершена",
        "comanda finalizată", "comandă finalizată", "comanda a fost finalizată", "cursa s-a încheiat",
        "order completed", "trip completed", "ride completed"
    )
    private val notAddress = Regex("""(?i)^(я здесь|уточнить|завершить|звонок|ожидание|поехали|б|b|\d+|[\d:]+|.*\d\s*(км|м|мин|km|min)\.?)$""")

    fun enabled() = AccessibilityAccess.isCarHeadUnit()

    /** Где Б сейчас (lat to lon), если едем по заказу. */
    fun current(): Pair<Double, Double>? =
        point?.takeIf { System.currentTimeMillis() - seenAt < TTL_MS }

    fun clear() {
        address = null
        point = null
        seenAt = 0L
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
        if (b == geocoding) return
        geocoding = b
        scope.launch {
            val p = try {
                RouteFareCalculator.locate(context, b)
            } catch (e: Exception) {
                null
            }
            geocoding = null
            if (p != null) {
                address = b
                point = p
                seenAt = System.currentTimeMillis()
                Log.d(TAG, "Едем в Б: «$b» → $p")
                FloatingWidgetService.refreshSurge()
            }
        }
    }
}
