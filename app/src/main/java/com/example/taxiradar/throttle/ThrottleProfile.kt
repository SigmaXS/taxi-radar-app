package com.example.taxiradar.throttle

/**
 * Скоростной профиль туннеля. [kbps] <= 0 — без ограничения.
 *
 * Значения подобраны как в исходном приложении: 1.8 кбит/с полностью «затыкает»
 * отправку запросов, 60 кбит/с — медленный, но живой канал, 400 кбит/с — «очень
 * медленно» на время отмены.
 */
data class ThrottleProfile(val kbps: Double) {

    companion object {
        val UNLIMITED = ThrottleProfile(0.0)

        /** Очень медленно: на время отмены заказа. */
        const val VERY_LOW_KBPS = 400.0

        /** Постоянное медленное соединение в режиме «Старт». */
        const val ALWAYS_KBPS = 60.0

        /** Полная блокировка отправки. */
        const val BURST_BLOCK_KBPS = 1.8

        /** Разгон после ручного стопа, чтобы приложение успело «отдышаться». */
        const val STOP_BOOST_KBPS = 50_000.0
    }
}
