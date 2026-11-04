package com.example.taxiradar.throttle

import android.content.Context
import android.net.VpnService
import android.os.SystemClock
import android.util.Log

/**
 * Автоматический режим: как только на экране/в уведомлении появляется новый заказ,
 * включаем «тормоз». Как только карточка пропала — снимаем сразу, не ждём таймер.
 *
 * Повторные события по той же карточке игнорируются, поэтому после отмены скрипт
 * не запускается заново.
 */
object ThrottleAuto {

    private const val TAG = "ThrottleAuto"
    private const val PREFS = "throttle_auto"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_AUTO_ON_ORDER = "auto_on_order"

    /** Максимум, сколько держим тормоз. Обычно снимается раньше — когда карточка ушла. */
    const val BURST_MS = 33_000L

    /** Пауза между разными заказами. */
    private const val COOLDOWN_MS = 6_000L

    /** Минимальный интервал между двумя разными заказами. */
    private const val MIN_INTERVAL_MS = 10_000L

    /** После окончания тормоза не реагируем, пока карточка не исчезнет. */
    private const val BURST_END_GUARD_MS = 5_000L

    @Volatile
    var enabled: Boolean = false
        private set

    /** Срабатывать ли на новый заказ автоматически (галочка на экране). */
    @Volatile
    var autoOnOrder: Boolean = true
        private set

    private var lastTriggerAt = 0L
    private var burstStartedAt = 0L
    private var burstEndedAt = 0L

    /** Подпись карточки, по которой уже сработали: второй раз её не трогаем. */
    @Volatile
    private var handledSignature: String? = null

    /** Взводимся только после того, как карточка ушла с экрана. */
    @Volatile
    private var armed = true

    /** Пауза с плавающей кнопки: временно вернуть интернет, чтобы взять заказ. */
    @Volatile
    var pausedUntil: Long = 0L
        private set

    /** Уведомления по одному заказу могут приходить пачкой — глушим их на время. */
    private var notificationGuardUntil = 0L

    @Volatile
    private var loaded = false

    fun load(context: Context) {
        loaded = true
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        enabled = p.getBoolean(KEY_ENABLED, false)
        autoOnOrder = p.getBoolean(KEY_AUTO_ON_ORDER, true)
    }

    /** Загружает настройки один раз — сервисы и виджет могут стартовать раньше Activity. */
    fun ensureLoaded(context: Context) {
        if (!loaded) load(context)
    }

    fun setEnabled(context: Context, on: Boolean) {
        ensureLoaded(context)
        // Включить можно только с действующей подпиской «Отмены» (виджет и плитка идут этим же путём).
        val allowed = !on || com.example.taxiradar.throttle.CancelLicense.allowed(context)
        if (!allowed) Log.w(TAG, "enable refused: no active Cancel subscription")
        val on = on && allowed
        enabled = on
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_ENABLED, on).apply()
        if (on) {
            armed = true
            if (VpnService.prepare(context) == null) {
                ThrottleVpnService.connect(context)
            }
            ThrottleState.ensureLoaded(context)
        } else {
            handledSignature = null
            armed = true
            pausedUntil = 0L
            ThrottleVpnService.shutdown(context)
        }
        ThrottleWidget.update(context)
    }

    fun setAutoOnOrder(context: Context, on: Boolean) {
        autoOnOrder = on
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_AUTO_ON_ORDER, on).apply()
    }

    /** Переключатель для виджета и плавающей кнопки. */
    fun toggle(context: Context) {
        ensureLoaded(context)
        setEnabled(context, !enabled)
    }

    /**
     * Появилась карточка заказа (экран или уведомление).
     * Запускаем тормоз только один раз на карточку.
     */
    fun isPaused(): Boolean = pausedUntil > SystemClock.elapsedRealtime()

    /** Временная пауза: VPN снимается, авто остаётся включённым и вернётся само. */
    fun pause(context: Context, millis: Long = 60_000L) {
        ensureLoaded(context)
        pausedUntil = SystemClock.elapsedRealtime() + millis
        ThrottleVpnService.shutdown(context)
        ThrottleWidget.update(context)
    }

    fun resume(context: Context) {
        pausedUntil = 0L
        if (enabled && !ThrottleState.isRunning.value && VpnService.prepare(context) == null) {
            ThrottleVpnService.connect(context)
        }
        ThrottleWidget.update(context)
    }

    fun onOrderDetected(context: Context, source: String, signature: String? = null) {
        ensureLoaded(context)
        if (!enabled || !autoOnOrder) return
        if (isPaused()) {
            Log.d(TAG, "order via $source ignored: paused")
            return
        }

        val now = SystemClock.elapsedRealtime()

        // Та же карточка, по которой уже срабатывали — игнорируем до её исчезновения.
        if (signature != null && signature == handledSignature && now - lastTriggerAt < 300_000L) {
            Log.d(TAG, "order via $source ignored: same card")
            return
        }
        if (!armed) {
            // Новый заказ с другими адресами — можно работать, даже если сигнал
            // «карточка ушла» не пришёл. Тот же заказ так не разблокировать.
            val newOrder = signature != null && signature != handledSignature && now - lastTriggerAt >= 8_000L
            if (newOrder || now - lastTriggerAt >= 45_000L) {
                Log.d(TAG, "re-armed (newOrder=$newOrder)")
                armed = true
            } else {
                Log.d(TAG, "order via $source ignored: waiting for card to disappear")
                return
            }
        }
        if (source == "notification" && now < notificationGuardUntil) {
            Log.d(TAG, "order via $source ignored: notification guard")
            return
        }
        // Если служба умерла, не сняв флаг, он застрянет навсегда — чиним.
        if (ThrottleState.isRunning.value) {
            val ends = ThrottleState.endsAtElapsedRealtime.value
            if (ends > 0L && now > ends + 4_000L) {
                Log.w(TAG, "stale burst state, reset")
                ThrottleState.setRunning(context, false)
                ThrottleState.responseBlockActive.set(false)
            }
        }
        if (ThrottleState.isRunning.value) {
            if (signature != null) handledSignature = signature
            Log.d(TAG, "order via $source ignored: burst running")
            return
        }
        if (now - lastTriggerAt < MIN_INTERVAL_MS) {
            Log.d(TAG, "order via $source ignored: min interval")
            return
        }
        if (now - burstEndedAt < BURST_END_GUARD_MS) {
            Log.d(TAG, "order via $source ignored: guard after burst")
            return
        }

        lastTriggerAt = now
        burstStartedAt = now
        handledSignature = signature
        armed = false
        if (source == "notification") notificationGuardUntil = now + 20_000L
        Log.i(TAG, "order via $source -> burst ${BURST_MS}ms sig=${signature?.take(80)}")
        ThrottleVpnService.start(context, BURST_MS, keepAlive = true)
    }

    /**
     * Карточка ушла с экрана — снимаем тормоз, не дожидаясь конца таймера,
     * и разрешаем следующий заказ.
     */
    fun onOrderGone(context: Context) {
        // Карточка ушла: разрешаем следующий заказ, но подпись этой карточки
        // не сбрасываем — иначе при мигании экрана она запустится повторно.
        armed = true
        Log.d(TAG, "order gone")
    }

    /** Сервис сообщает, что тормоз закончился. */
    fun onBurstFinished() {
        burstEndedAt = SystemClock.elapsedRealtime()
    }
}
