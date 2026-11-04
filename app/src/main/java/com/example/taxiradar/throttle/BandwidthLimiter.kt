package com.example.taxiradar.throttle

import java.util.concurrent.atomic.AtomicReference

/**
 * Простейший token bucket. Поток, который качает данные, спит ровно столько,
 * сколько нужно, чтобы уложиться в [ThrottleProfile.kbps].
 */
class BandwidthLimiter(private val profileRef: AtomicReference<ThrottleProfile>) {

    private val lock = Any()
    private var bucketBytes = 0.0
    private var lastRefillNanos = System.nanoTime()

    fun reset() {
        synchronized(lock) {
            bucketBytes = 0.0
            lastRefillNanos = System.nanoTime()
        }
    }

    /** Сколько байт можно отдать прямо сейчас; спит, пока не появится хотя бы байт. */
    @Throws(InterruptedException::class)
    fun take(requested: Int): Int {
        while (true) {
            val sleepMs: Long
            synchronized(lock) {
                val kbps = profileRef.get().kbps
                if (kbps <= 0.0) return requested
                val bytesPerSecond = kbps * 1000.0 / 8.0
                val now = System.nanoTime()
                val elapsedSeconds = (now - lastRefillNanos) / 1_000_000_000.0
                lastRefillNanos = now
                // Небольшой burst, чтобы мелкие пакеты проходили без задержки.
                val available = minOf(0.15 * bytesPerSecond, bucketBytes + elapsedSeconds * bytesPerSecond)
                bucketBytes = available
                if (available >= 1.0) {
                    val granted = minOf(available.toInt(), requested)
                    bucketBytes -= granted.toDouble()
                    return granted
                }
                sleepMs = ((1.0 - available) / bytesPerSecond * 1000.0).toLong().coerceAtLeast(1L)
            }
            Thread.sleep(sleepMs)
        }
    }

    fun acquireAll(bytes: Int) {
        var left = bytes
        while (left > 0) {
            left -= take(left)
        }
    }
}
