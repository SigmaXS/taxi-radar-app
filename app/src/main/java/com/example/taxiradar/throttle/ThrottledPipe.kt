package com.example.taxiradar.throttle

import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Перекачка байт из сокета в tun (или наоборот) с ограничением скорости.
 *
 * Если [blockUploadWhileBurstActive] и [responseBlockActive] взведены, то после
 * первого куска данных включается [blockedLimiter] — так обрывается отправка
 * ответа на карточку заказа.
 */
class ThrottledPipe(
    private val limiter: BandwidthLimiter,
    private val blockUploadWhileBurstActive: Boolean = false,
    private val responseBlockActive: AtomicBoolean? = null,
    private val blockedLimiter: BandwidthLimiter? = null,
) {

    @Throws(InterruptedException::class, IOException::class)
    fun pump(input: InputStream, output: OutputStream) {
        val buffer = ByteArray(4096)
        var first = true
        while (true) {
            val read = input.read(buffer)
            if (read == -1) return

            val useBlocked = blockUploadWhileBurstActive && !first &&
                responseBlockActive?.get() == true && blockedLimiter != null
            val active = if (useBlocked) blockedLimiter!! else limiter

            var offset = 0
            while (offset < read) {
                val granted = active.take(read - offset)
                output.write(buffer, offset, granted)
                offset += granted
            }
            output.flush()
            first = false
        }
    }
}
