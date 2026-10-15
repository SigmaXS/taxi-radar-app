package com.example.taxiradar

import android.content.Context

/**
 * Связка «экран Bolt → разбор → расчёт → карточка». Вызывается из OrderAccessibilityService
 * в главном потоке. Данные Яндекса сюда не попадают: на вход — только строки окна Bolt,
 * на выход — только отдельная карточка Bolt (кружок Яндекса и OrderPreview не трогаем).
 */
object BoltRadar {
    private var tracker = BoltOfferTracker()
    private var trackerShowMs = -1L
    private var lastDiag = ""

    val isShowing get() = tracker.current != null
    fun isCurrent(gen: Long) = tracker.isCurrent(gen)

    private fun trackerFor(c: Context): BoltOfferTracker {
        val ms = TaxiServices.boltShowMs(c)
        if (ms != trackerShowMs) {
            tracker.current?.let { FloatingWidgetService.hideBoltCard() }
            tracker = BoltOfferTracker(maxShowMs = ms)
            trackerShowMs = ms
        }
        return tracker
    }

    fun onBoltScreen(c: Context, lines: List<String>, now: Long = System.currentTimeMillis()) {
        if (!TaxiServices.boltEnabled(c)) { reset(); return }
        val result = BoltOfferParser.parse(lines)
        diagnostics(c, lines, result)
        val offer = (result as? BoltOfferParser.Result.Ok)?.offer
        apply(c, trackerFor(c).onBoltScreen(offer, now))
    }

    /** На экране не Bolt (Яндекс Про с карточкой, лаунчер, другое приложение). */
    fun onOtherApp(c: Context, now: Long = System.currentTimeMillis()) = apply(c, trackerFor(c).onOtherApp(now))

    fun tick(c: Context, now: Long = System.currentTimeMillis()) = apply(c, trackerFor(c).tick(now))

    fun dismissedByUser() { tracker.dismissedByUser(System.currentTimeMillis()) }

    fun reset() {
        tracker.reset()
        FloatingWidgetService.hideBoltCard()
    }

    private fun apply(c: Context, d: BoltOfferTracker.Decision) {
        when (d) {
            is BoltOfferTracker.Decision.Show -> {
                val s = TaxiServices.boltSettings(c)
                val m = BoltEconomics.calculate(d.offer, s)
                val ru = DriverUi.t(c, "ru", "ro") == "ru"
                TripLog.d("Bolt: карточка ${d.offer.price} ${d.offer.currency}, подача ${d.offer.pickup}, поездка ${d.offer.trip}, " +
                    "★${d.offer.rating ?: "-"} → выручка ${"%.0f".format(m.revenue)}, за км ${m.perKm?.let { "%.1f".format(it) } ?: "нет"}, " +
                    "в час ${m.perHour?.let { "%.0f".format(it) } ?: "нет"}")
                // Новая карточка Bolt: результат Яндекса, если он ещё на кружке, уже не про этот экран.
                FloatingWidgetService.clearOrderIfShown()
                FloatingWidgetService.showBoltCard(BoltCardFormat.rows(d.offer, m, s, ru), d.generation)
            }
            is BoltOfferTracker.Decision.Hide -> {
                TripLog.d("Bolt: карточка скрыта — ${d.reason}")
                FloatingWidgetService.hideBoltCard()
            }
            BoltOfferTracker.Decision.Keep -> Unit
        }
    }

    /** По желанию водителя — строки нераспознанных/новых карточек в журнал (там адреса, поэтому выключено по умолчанию). */
    private fun diagnostics(c: Context, lines: List<String>, r: BoltOfferParser.Result) {
        if (!TaxiServices.boltDiagnostics(c)) return
        val sig = when (r) { is BoltOfferParser.Result.Ok -> "ok:" + r.offer.key; is BoltOfferParser.Result.NotOffer -> "no:" + r.reason }
        if (sig == lastDiag) return
        lastDiag = sig
        // Экран без единой цифры — не карточка заказа, его в журнал не пишем.
        if (r is BoltOfferParser.Result.NotOffer && lines.none { l -> l.any { it.isDigit() } }) return
        TripLog.d("Bolt диагностика [$sig]: " + lines.joinToString(" | ") { it.replace('\n', ' ').take(80) }.take(1500))
    }
}
