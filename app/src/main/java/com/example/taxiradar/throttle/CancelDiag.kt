package com.example.taxiradar.throttle

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.example.taxiradar.R

/**
 * Проверка «Отмены»: когда радар последний раз видел карточку заказа и живой тест «вижу ли я Яндекс».
 * Постоянной работы нет: события учитываются одним числом, а тест включается кнопкой на 25 секунд.
 */
object CancelDiag {
    private const val PREFS = "cancel_diag"
    private const val TEST_MS = 25_000L

    @Volatile private var lastCardAt = 0L
    @Volatile private var loaded = false
    @Volatile private var testUntil = 0L
    @Volatile private var sawYandex = false
    private var lastSavedAt = 0L
    private val main = Handler(Looper.getMainLooper())

    private fun load(c: Context) {
        if (loaded) return
        loaded = true
        lastCardAt = c.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong("last_card", 0L)
    }

    /** Время (мс, как System.currentTimeMillis) последней карточки заказа; 0 — ещё не видел. */
    fun lastCard(c: Context): Long { load(c); return lastCardAt }

    /** На экране Яндекса пришло событие. Дёшево: без теста — один просмотр флага. */
    fun yandexSeen(c: Context) {
        if (testUntil == 0L || sawYandex) return
        sawYandex = true
        CancelNotices.test(c, R.string.cancel_test_yandex_ok, R.string.cancel_test_wait_card)
    }

    /** Радар увидел карточку заказа («Принять» на экране). */
    fun cardSeen(c: Context) {
        val now = System.currentTimeMillis()
        lastCardAt = now
        loaded = true
        // В настройки пишем не чаще раза в 10 секунд.
        if (now - lastSavedAt > 10_000L) {
            lastSavedAt = now
            c.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putLong("last_card", now).apply()
        }
        if (testUntil != 0L) {
            testUntil = 0L
            CancelNotices.test(c, R.string.cancel_test_card_ok, R.string.cancel_test_card_ok_text)
        }
    }

    /** Кнопка «Проверить»: 25 секунд слушаем, видит ли радар Яндекс и карточку; итог приходит уведомлением. */
    fun startTest(c: Context) {
        val app = c.applicationContext
        sawYandex = false
        testUntil = SystemClock.elapsedRealtime() + TEST_MS
        CancelNotices.test(app, R.string.cancel_test_started, R.string.cancel_test_started_text)
        main.postDelayed({
            if (testUntil != 0L && SystemClock.elapsedRealtime() >= testUntil) {
                testUntil = 0L
                if (sawYandex) CancelNotices.test(app, R.string.cancel_test_yandex_ok, R.string.cancel_test_no_card)
                else CancelNotices.test(app, R.string.cancel_test_no_yandex, R.string.cancel_test_no_yandex_text)
            }
        }, TEST_MS + 500)
    }
}
