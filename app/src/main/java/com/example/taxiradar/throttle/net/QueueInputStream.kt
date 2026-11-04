package com.example.taxiradar.throttle.net

import java.io.InputStream
import java.util.concurrent.BlockingQueue

/** Поток чтения из очереди кусков, которую наполняет TCP-соединение. */
class QueueInputStream(private val queue: BlockingQueue<ByteArray>) : InputStream() {

    companion object {
        val EOF_MARKER = ByteArray(0)
    }

    private var current: ByteArray? = null
    private var pos = 0
    private var eof = false

    override fun read(): Int {
        if (eof) return -1
        while (true) {
            val chunk = current
            if (chunk != null && pos < chunk.size) {
                return chunk[pos++].toInt() and 0xFF
            }
            val next = queue.take()
            if (next === EOF_MARKER) {
                eof = true
                return -1
            }
            current = next
            pos = 0
        }
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (eof) return -1
        if (len == 0) return 0
        while (true) {
            val chunk = current
            if (chunk != null && pos < chunk.size) {
                val n = minOf(chunk.size - pos, len)
                System.arraycopy(chunk, pos, b, off, n)
                pos += n
                return n
            }
            val next = queue.take()
            if (next === EOF_MARKER) {
                eof = true
                return -1
            }
            current = next
            pos = 0
        }
    }
}
