package com.example.taxiradar.throttle

import android.content.Context
import android.content.SharedPreferences
import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**
 * Общее состояние туннеля: какие профили сейчас активны, сколько осталось,
 * и настройки, которые пользователь меняет на экране.
 *
 * Живёт в процессе приложения; сервис и UI читают одни и те же объекты.
 */
object ThrottleState {

    private const val PREFS_NAME = "throttle_state"
    private const val KEY_RUNNING = "is_running"
    private const val KEY_ENDS_AT = "ends_at_elapsed_realtime"
    private const val KEY_VERY_LOW_KBPS = "very_low_kbps"
    private const val KEY_ALWAYS_KBPS = "always_throttle_kbps"
    private const val KEY_BLOCK_KBPS = "burst_block_kbps"
    private const val KEY_CLAIM_GRACE_MS = "claim_grace_ms"

    const val DEFAULT_CLAIM_GRACE_MS = 3_000L
    private const val MANUAL_STOP_HOLD_MS = 15_000L
    private const val STOP_BOOST_MS = 10_000L

    private val boostExecutor = Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "throttle-boost").apply { isDaemon = true }
    }
    private var pendingBoost: ScheduledFuture<*>? = null

    val activeProfile = AtomicReference(ThrottleProfile.UNLIMITED)
    val responseBlockActive = AtomicBoolean(false)

    val downloadLimiter = BandwidthLimiter(activeProfile)
    val uploadLimiter = BandwidthLimiter(activeProfile)

    val veryLowProfile = AtomicReference(ThrottleProfile(ThrottleProfile.VERY_LOW_KBPS))
    val alwaysProfile = AtomicReference(ThrottleProfile(ThrottleProfile.ALWAYS_KBPS))
    val blockProfile = AtomicReference(ThrottleProfile(ThrottleProfile.BURST_BLOCK_KBPS))
    private val unlimitedProfile = AtomicReference(ThrottleProfile.UNLIMITED)

    val alwaysUploadLimiter = BandwidthLimiter(alwaysProfile)
    val blockUploadLimiter = BandwidthLimiter(blockProfile)
    val alwaysUnlimitedDownloadLimiter = BandwidthLimiter(unlimitedProfile)

    private val unthrottleHoldUntil = AtomicLong(0L)

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private val _endsAt = MutableStateFlow(0L)
    val endsAtElapsedRealtime: StateFlow<Long> = _endsAt.asStateFlow()

    private val _veryLowKbps = MutableStateFlow(ThrottleProfile.VERY_LOW_KBPS)
    val veryLowKbps: StateFlow<Double> = _veryLowKbps.asStateFlow()

    private val _alwaysKbps = MutableStateFlow(ThrottleProfile.ALWAYS_KBPS)
    val alwaysKbps: StateFlow<Double> = _alwaysKbps.asStateFlow()

    private val _blockKbps = MutableStateFlow(ThrottleProfile.BURST_BLOCK_KBPS)
    val blockKbps: StateFlow<Double> = _blockKbps.asStateFlow()

    private val _claimGraceMs = MutableStateFlow(DEFAULT_CLAIM_GRACE_MS)
    val claimGraceMs: StateFlow<Long> = _claimGraceMs.asStateFlow()

    @Volatile
    private var prefs: SharedPreferences? = null

    fun ensureLoaded(context: Context) {
        attach(context)
    }

    @Synchronized
    private fun attach(context: Context): SharedPreferences {
        prefs?.let { return it }
        val p = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        _isRunning.value = p.getBoolean(KEY_RUNNING, false)
        _endsAt.value = p.getLong(KEY_ENDS_AT, 0L)

        val veryLow = p.getFloat(KEY_VERY_LOW_KBPS, ThrottleProfile.VERY_LOW_KBPS.toFloat()).toDouble()
        val always = p.getFloat(KEY_ALWAYS_KBPS, ThrottleProfile.ALWAYS_KBPS.toFloat()).toDouble()
        val block = p.getFloat(KEY_BLOCK_KBPS, ThrottleProfile.BURST_BLOCK_KBPS.toFloat()).toDouble()

        veryLowProfile.set(ThrottleProfile(veryLow))
        alwaysProfile.set(ThrottleProfile(always))
        blockProfile.set(ThrottleProfile(block))

        _veryLowKbps.value = veryLow
        _alwaysKbps.value = always
        _blockKbps.value = block
        _claimGraceMs.value = p.getLong(KEY_CLAIM_GRACE_MS, DEFAULT_CLAIM_GRACE_MS)

        prefs = p
        return p
    }

    fun setRunning(context: Context, running: Boolean, endsAt: Long = 0L) {
        _isRunning.value = running
        _endsAt.value = endsAt
        attach(context).edit().putBoolean(KEY_RUNNING, running).putLong(KEY_ENDS_AT, endsAt).apply()
    }

    fun isRunningNow(context: Context): Boolean {
        attach(context)
        return _isRunning.value
    }

    fun isUnthrottleHoldActive(): Boolean =
        SystemClock.elapsedRealtime() < unthrottleHoldUntil.get()

    /** Мгновенно снять ограничение — например, когда водитель нажал «Стоп». */
    fun forceUnthrottleNow(context: Context) {
        activeProfile.set(ThrottleProfile.UNLIMITED)
        responseBlockActive.set(false)
        setRunning(context, false)
        unthrottleHoldUntil.set(SystemClock.elapsedRealtime() + MANUAL_STOP_HOLD_MS)
    }

    /**
     * После ручного стопа даём каналу 10 секунд полной скорости, чтобы Яндекс Про
     * успел закрыть свои запросы, а потом возвращаемся к обычному медленному режиму.
     */
    @Synchronized
    fun boostAlwaysThrottleAfterManualStop() {
        pendingBoost?.cancel(false)
        alwaysProfile.set(ThrottleProfile(ThrottleProfile.STOP_BOOST_KBPS))
        pendingBoost = boostExecutor.schedule({
            alwaysProfile.set(ThrottleProfile(_alwaysKbps.value))
        }, STOP_BOOST_MS, TimeUnit.MILLISECONDS)
    }

    fun setVeryLowKbps(context: Context, kbps: Double) {
        veryLowProfile.set(ThrottleProfile(kbps))
        _veryLowKbps.value = kbps
        attach(context).edit().putFloat(KEY_VERY_LOW_KBPS, kbps.toFloat()).apply()
    }

    fun setAlwaysKbps(context: Context, kbps: Double) {
        alwaysProfile.set(ThrottleProfile(kbps))
        _alwaysKbps.value = kbps
        attach(context).edit().putFloat(KEY_ALWAYS_KBPS, kbps.toFloat()).apply()
    }

    fun setBlockKbps(context: Context, kbps: Double) {
        blockProfile.set(ThrottleProfile(kbps))
        _blockKbps.value = kbps
        attach(context).edit().putFloat(KEY_BLOCK_KBPS, kbps.toFloat()).apply()
    }

    fun setClaimGraceMs(context: Context, ms: Long) {
        _claimGraceMs.value = ms
        attach(context).edit().putLong(KEY_CLAIM_GRACE_MS, ms).apply()
    }
}
