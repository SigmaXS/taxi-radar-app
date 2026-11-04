package com.example.taxiradar.throttle.net

import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.atomic.AtomicLong

/** Считает принятые байты — нужно для статистики в журнале. */
internal class CountingInputStream(
    private val delegate: InputStream,
    private val counter: AtomicLong,
) : InputStream() {

    override fun read(): Int {
        val b = delegate.read()
        if (b >= 0) counter.incrementAndGet()
        return b
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        val n = delegate.read(b, off, len)
        if (n > 0) counter.addAndGet(n.toLong())
        return n
    }
}

/** Считает отправленные байты. */
internal class CountingOutputStream(
    private val delegate: OutputStream,
    private val counter: AtomicLong,
) : OutputStream() {

    override fun write(b: Int) {
        delegate.write(b)
        counter.incrementAndGet()
    }

    override fun write(b: ByteArray, off: Int, len: Int) {
        delegate.write(b, off, len)
        counter.addAndGet(len.toLong())
    }

    override fun flush() {
        delegate.flush()
    }
}

/** Пишет данные из сокета обратно в tun-интерфейс. */
internal class TunOutputStream(private val connection: TcpConnection) : OutputStream() {

    override fun write(b: Int) {
        write(byteArrayOf(b.toByte()), 0, 1)
    }

    override fun write(b: ByteArray, off: Int, len: Int) {
        connection.sendData(b, off, len)
    }
}
