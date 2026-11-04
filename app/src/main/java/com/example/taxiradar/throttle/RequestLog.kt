package com.example.taxiradar.throttle

import android.util.Log
import java.util.concurrent.atomic.AtomicLong

/**
 * Небольшой журнал подключений туннеля. Раньше здесь была телеметрия на сервер,
 * теперь пишем только в logcat — этого достаточно для отладки.
 */
object RequestLog {

    private const val TAG = "Throttle"

    fun logConnect(protocol: String, host: String, port: Int) {
        Log.d(TAG, "connect $protocol $host:$port")
    }

    fun registerLive(id: String, protocol: String, host: String, port: Int, rx: AtomicLong, tx: AtomicLong) {
        Log.d(TAG, "open $protocol $host:$port")
    }

    fun unregisterLive(id: String) {
        Log.d(TAG, "close $id")
    }

    fun logClosed(protocol: String, host: String, port: Int, rx: Long, tx: Long, durationMs: Long) {
        Log.d(TAG, "done $protocol $host:$port rx=$rx tx=$tx ${durationMs}ms")
    }

    fun observeDnsResponse(data: ByteArray, length: Int) {
        // DNS-ответы не разбираем: журнал нужен только для отладки.
    }
}
