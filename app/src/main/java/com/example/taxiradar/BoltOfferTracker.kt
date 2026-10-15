package com.example.taxiradar

/**
 * Что сейчас должно быть на карточке Bolt. Чистая логика без Android — её проверяют тесты.
 *
 *  - повторные события той же карточки (та же цена, км, минуты) не перерисовывают её и не продлевают показ
 *    бесконечно (как в 1H Profit: тот же заказ в течение 12 с — без новой анимации);
 *  - карточка пропала с экрана Bolt дольше goneGraceMs — скрываем (1H Profit — 8 с, у нас Accessibility
 *    сообщает об изменениях сразу, поэтому короче);
 *  - на экране другое приложение (Яндекс Про, лаунчер) — скрываем сразу;
 *  - показ не дольше maxShowMs;
 *  - каждая новая карточка получает новый номер поколения: отрисовка с устаревшим номером отбрасывается.
 */
class BoltOfferTracker(
    private val goneGraceMs: Long = 1_500,
    private val maxShowMs: Long = 20_000,
    private val sameOfferHoldMs: Long = 12_000
) {
    sealed class Decision {
        data class Show(val offer: BoltOfferParser.Offer, val generation: Long) : Decision()
        data class Hide(val reason: String) : Decision()
        object Keep : Decision()
    }

    var generation: Long = 0
        private set
    var current: BoltOfferParser.Offer? = null
        private set
    private var shownAt = 0L
    private var lastSeenAt = 0L
    private var missingSince = 0L
    // Закрытая (по таймеру или пальцем) карточка не возвращается, пока на экране та же самая.
    private var dismissedKey: String? = null
    private var dismissedAt = 0L

    fun isCurrent(gen: Long) = gen == generation && current != null

    /** Разобран экран Bolt: offer — карточка или null, если карточки на экране нет. */
    fun onBoltScreen(offer: BoltOfferParser.Offer?, now: Long): Decision {
        if (offer == null) {
            if (current == null) return Decision.Keep
            if (missingSince == 0L) missingSince = now
            return if (now - missingSince >= goneGraceMs) hide("карточка исчезла с экрана Bolt") else Decision.Keep
        }
        missingSince = 0L
        lastSeenAt = now
        val cur = current
        if (cur != null && cur.key == offer.key) {
            if (now - shownAt >= maxShowMs) return hide("показ дольше ${maxShowMs / 1000} с").also { dismiss(offer.key, now) }
            return Decision.Keep
        }
        if (cur == null && offer.key == dismissedKey && now - dismissedAt < sameOfferHoldMs * 5) return Decision.Keep
        dismissedKey = null
        generation++
        current = offer
        shownAt = now
        return Decision.Show(offer, generation)
    }

    /** На экране другое приложение — данные Bolt больше не про то, что видит водитель. */
    fun onOtherApp(now: Long): Decision = if (current != null) hide("на экране другое приложение") else Decision.Keep

    /** Периодическая проверка, пока карточка видна. */
    fun tick(now: Long): Decision {
        val cur = current ?: return Decision.Keep
        if (now - shownAt >= maxShowMs) { dismiss(cur.key, now); return hide("показ дольше ${maxShowMs / 1000} с") }
        if (missingSince != 0L && now - missingSince >= goneGraceMs) return hide("карточка исчезла с экрана Bolt")
        return Decision.Keep
    }

    /** Водитель закрыл карточку сам. */
    fun dismissedByUser(now: Long) { current?.let { dismiss(it.key, now) }; current = null; generation++; missingSince = 0L }

    /** Bolt выключили в настройках — забыть всё. */
    fun reset() { current = null; generation++; missingSince = 0L; dismissedKey = null }

    private fun dismiss(key: String, now: Long) { dismissedKey = key; dismissedAt = now }

    private fun hide(reason: String): Decision {
        current = null
        generation++
        missingSince = 0L
        return Decision.Hide(reason)
    }
}
