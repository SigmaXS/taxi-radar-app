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
    const val BURST_MS = 30_000L

    /** Пауза между разными заказами. */
    private const val COOLDOWN_MS = 6_000L

    /** Сколько карточка должна отсутствовать, чтобы вернувшийся такой же заказ считался новым. */
    private const val CARD_GONE_MS = 2_500L

    /**
     * Карточка всё ещё на экране — тот же заказ, повторно не включаем. Ждём, пока она исчезнет.
     * Только если сигнал «карточка ушла» не приходит совсем, через это время страхуемся и взводимся сами.
     */
    private const val STUCK_REARM_MS = 180_000L

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

    /** Когда карточка пропала с экрана (0 — она на экране). Если её нет несколько секунд, тот же заказ — уже новый. */
    private var goneSince = 0L

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

    /**
     * Первый вызов в новом процессе: «Отмена» всегда выключена. Закрыли приложение («очистить всё»),
     * систему убила процесс или телефон перезагрузился — водитель сам заходит на вкладку и жмёт «Старт».
     * Повторные вызовы (экран пересоздался) состояние не трогают.
     */
    fun load(context: Context, notify: Boolean = false) {
        if (loaded) return
        loaded = true
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val wasOn = p.getBoolean(KEY_ENABLED, false)
        enabled = false
        p.edit().putBoolean(KEY_ENABLED, false).apply()
        autoOnOrder = p.getBoolean(KEY_AUTO_ON_ORDER, true)
        // Была включена и пропала не по кнопке «Стоп» — приложение закрыла система: скажем водителю.
        if (wasOn && notify) CancelNotices.stoppedBySystem(context)
    }

    /** Загружает настройки один раз — сервисы и виджет могут стартовать раньше Activity. */
    fun ensureLoaded(context: Context) {
        if (!loaded) load(context, notify = true)
    }

    private fun today() = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())

    /** Сколько раз «Отмена» срабатывала: всего и сегодня (для вкладки и админки). */
    fun runsTotal(context: Context): Int = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt("runs_total", 0)

    fun runsToday(context: Context): Int {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return if (p.getString("runs_day", "") == today()) p.getInt("runs_today", 0) else 0
    }

    private fun bumpRuns(context: Context) {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val sameDay = p.getString("runs_day", "") == today()
        p.edit().putInt("runs_total", p.getInt("runs_total", 0) + 1)
            .putString("runs_day", today()).putInt("runs_today", (if (sameDay) p.getInt("runs_today", 0) else 0) + 1).apply()
    }

    /** Время включения и последнего срабатывания — по ним служба выключает «Отмену», если долго нет заказов. */
    @Volatile
    private var enabledAt = 0L

    fun idleMs(): Long = SystemClock.elapsedRealtime() - maxOf(enabledAt, lastTriggerAt)

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
            enabledAt = SystemClock.elapsedRealtime()
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

    fun onOrderDetected(context: Context, source: String, signature: String? = null, trustSignature: Boolean = true) {
        ensureLoaded(context)
        if (!enabled || !autoOnOrder) return
        if (isPaused()) {
            Log.d(TAG, "order via $source ignored: paused")
            return
        }

        val now = SystemClock.elapsedRealtime()

        // Карточка пропадала с экрана дольше пары секунд (не взяли, она вернулась тем же заказом) —
        // это новое предложение: прошлую подпись забываем. Мигание в доли секунды подпись сохраняет.
        if (goneSince > 0L && now - goneSince >= CARD_GONE_MS) {
            Log.d(TAG, "same order came again after ${now - goneSince}ms -> treated as new")
            handledSignature = null
            armed = true
        }
        goneSince = 0L

        // Та же карточка, по которой уже срабатывали — игнорируем до её исчезновения.
        if (signature != null && signature == handledSignature && now - lastTriggerAt < 300_000L) {
            Log.d(TAG, "order via $source ignored: same card")
            return
        }
        if (!armed) {
            // Новый заказ с другими адресами — можно работать, даже если сигнал
            // «карточка ушла» не пришёл. Тот же заказ так не разблокировать.
            // trustSignature=false («Только Отмена»): подпись приблизительная, на ней «новый заказ» не решаем — ждём ухода карточки.
            val newOrder = trustSignature && signature != null && signature != handledSignature && now - lastTriggerAt >= 8_000L
            if (newOrder || now - lastTriggerAt >= STUCK_REARM_MS) {
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
        bumpRuns(context)
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
        if (goneSince == 0L) goneSince = SystemClock.elapsedRealtime()
        Log.d(TAG, "order gone")
    }

    /** Сервис сообщает, что тормоз закончился. */
    fun onBurstFinished() {
        burstEndedAt = SystemClock.elapsedRealtime()
    }
}
