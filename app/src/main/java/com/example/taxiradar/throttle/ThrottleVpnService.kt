package com.example.taxiradar.throttle

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.VpnService
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.util.Log
import com.example.taxiradar.MainActivity
import com.example.taxiradar.R
import com.example.taxiradar.throttle.net.ConnectionKey
import com.example.taxiradar.throttle.net.Ipv4Header
import com.example.taxiradar.throttle.net.TcpConnection
import com.example.taxiradar.throttle.net.TcpHeader
import com.example.taxiradar.throttle.net.UdpHeader
import com.example.taxiradar.throttle.net.UdpSession
import com.example.taxiradar.throttle.net.VpnPacketSink
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * VPN-туннель, который ограничивает скорость приложения Яндекс Про.
 *
 * Работает как обычный tun2socks: читает IP-пакеты из tun, поднимает на каждое
 * TCP-соединение настоящий сокет и перекачивает байты через [BandwidthLimiter].
 *
 * Два режима:
 *  - «Старт» — канал всё время медленный (60 кбит/с на отправку);
 *  - «Отмена» — на 40 секунд включается почти полная блокировка, чтобы карточка
 *    заказа не успела приняться.
 */
class ThrottleVpnService : VpnService(), VpnPacketSink {

    companion object {
        private const val TAG = "ThrottleVpn"

        const val ACTION_START = "com.example.taxiradar.throttle.START"
        const val ACTION_STOP = "com.example.taxiradar.throttle.STOP"
        const val ACTION_END_BURST = "com.example.taxiradar.throttle.END_BURST"
        const val EXTRA_DURATION_MS = "duration_ms"
        const val EXTRA_KEEP_ALIVE = "keep_alive"
        const val EXTRA_CONNECT_ONLY = "connect_only"

        const val CHANNEL_ID = "throttle_proxy"
        private const val NOTIF_ID = 1
        private const val MTU = 1500
        private const val TARGET_PACKAGE = "ru.yandex.taximeter"
        private const val MAX_TCP_CONNECTIONS = 128
        private const val MAX_UDP_SESSIONS = 128

        private val RETRY_DELAYS_MS = longArrayOf(0, 300, 600, 1200)

        @Volatile
        var isRunning: Boolean = false
            private set

        /** Запустить «отмену» на [durationMs] миллисекунд. */
        fun start(context: Context, durationMs: Long, keepAlive: Boolean = false) {
            if (!CancelLicense.allowed(context)) return
            startCompat(
                context,
                Intent(context, ThrottleVpnService::class.java).apply {
                    action = ACTION_START
                    putExtra(EXTRA_DURATION_MS, durationMs)
                    putExtra(EXTRA_KEEP_ALIVE, keepAlive)
                },
            )
        }

        /** Просто поднять туннель, не трогая скорость (нужно для «Старт»). */
        fun connect(context: Context) {
            if (!CancelLicense.allowed(context)) return
            startCompat(
                context,
                Intent(context, ThrottleVpnService::class.java).apply {
                    action = ACTION_START
                    putExtra(EXTRA_CONNECT_ONLY, true)
                },
            )
        }

        fun stop(context: Context) {
            startCompat(context, Intent(context, ThrottleVpnService::class.java).apply { action = ACTION_STOP })
        }

        /** Надёжное выключение: команда «стоп» + остановка службы. */
        fun shutdown(context: Context) {
            runCatching {
                startCompat(context, Intent(context, ThrottleVpnService::class.java).apply { action = ACTION_STOP })
            }
            runCatching { context.stopService(Intent(context, ThrottleVpnService::class.java)) }
        }

        fun endBurst(context: Context) {
            startCompat(context, Intent(context, ThrottleVpnService::class.java).apply { action = ACTION_END_BURST })
        }

        private fun startCompat(context: Context, intent: Intent) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }

    private val tcpConnections = ConcurrentHashMap<ConnectionKey, TcpConnection>()
    private val udpSessions = ConcurrentHashMap<ConnectionKey, UdpSession>()

    private val vpnAddress = byteArrayOf(10, 0, 0, 2)
    private val writeLock = Any()
    private val handler = Handler(Looper.getMainLooper())

    @Volatile
    private var tunInterface: ParcelFileDescriptor? = null

    @Volatile
    private var tunOutput: FileOutputStream? = null

    @Volatile
    private var running = false

    @Volatile
    private var stopped = false

    private var keepAliveOnEnd = false
    private var pendingEnd: Runnable? = null

    private val receivedBytes = AtomicLong(0)
    private val sentBytes = AtomicLong(0)

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        // Сразу выходим на передний план, до onStartCommand: shutdown() шлёт «стоп» и тут же останавливает службу,
        // и без этого Android убивал приложение (ForegroundServiceDidNotStartInTimeException).
        runCatching { startForeground(NOTIF_ID, buildNotification()) }
        ThrottleState.ensureLoaded(applicationContext)
        ThrottleAuto.ensureLoaded(applicationContext)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return try {
            handleStart(intent)
        } catch (t: Throwable) {
            Log.e(TAG, "start failed", t)
            stopSelf()
            START_NOT_STICKY
        }
    }

    private fun handleStart(intent: Intent?): Int {
        if (intent == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        // Контракт startForegroundService: показать уведомление надо в любом случае,
        // даже если команда — «стоп».
        startForeground(NOTIF_ID, buildNotification())

        if (intent.action == ACTION_STOP) {
            stopBurst()
            return START_NOT_STICKY
        }
        // Единая проверка подписки «Отмены»: плитка, виджет, авто-режим и всё остальное идут через эту службу.
        if (!CancelLicense.allowed(this)) {
            Log.w(TAG, "no active Cancel subscription — refusing to start")
            stopBurst()
            return START_NOT_STICKY
        }
        if (intent.action == ACTION_END_BURST) {
            endBurst()
            return START_NOT_STICKY
        }

        val connectOnly = intent.getBooleanExtra(EXTRA_CONNECT_ONLY, false)
        val durationMs = intent.getLongExtra(EXTRA_DURATION_MS, -1L)
        if (!connectOnly && durationMs <= 0L) {
            if (running) return START_STICKY
            stopSelf()
            return START_NOT_STICKY
        }

        if (VpnService.prepare(applicationContext) != null) {
            // Пользователь ещё не разрешил VPN — молча выходим, экран сам покажет кнопку.
            stopSelf()
            return START_NOT_STICKY
        }

        if (!running) {
            Thread({
                if (establishVpn()) {
                    if (stopped) {
                        release()
                        stopSelf()
                    } else {
                        stopped = false
                        onVpnReady(intent)
                    }
                } else {
                    stopSelf()
                }
            }, "throttle-connect").start()
            return START_STICKY
        }

        stopped = false
        onVpnReady(intent)
        return START_STICKY
    }

    private fun onVpnReady(intent: Intent) {
        if (intent.getBooleanExtra(EXTRA_CONNECT_ONLY, false)) {
            // Режим «просто держать туннель»: скорость не трогаем.
            ThrottleState.activeProfile.set(ThrottleProfile.UNLIMITED)
            cancelPendingEnd()
            ThrottleWidget.update(applicationContext)
            return
        }

        if (ThrottleState.isUnthrottleHoldActive()) {
            Log.i(TAG, "burst suppressed: post-stop hold")
            return
        }

        keepAliveOnEnd = intent.getBooleanExtra(EXTRA_KEEP_ALIVE, false)
        ThrottleState.activeProfile.set(ThrottleState.veryLowProfile.get())
        ThrottleState.responseBlockActive.set(true)
        ThrottleState.downloadLimiter.reset()
        ThrottleState.uploadLimiter.reset()

        val durationMs = intent.getLongExtra(EXTRA_DURATION_MS, -1L)
        val endsAt = if (durationMs > 0) SystemClock.elapsedRealtime() + durationMs else 0L
        ThrottleState.setRunning(applicationContext, true, endsAt)
        ThrottleWidget.update(applicationContext)

        cancelPendingEnd()
        if (durationMs > 0) {
            val end = Runnable { endBurst() }
            pendingEnd = end
            handler.postDelayed(end, durationMs)
        }
    }

    private fun cancelPendingEnd() {
        pendingEnd?.let { handler.removeCallbacks(it) }
        pendingEnd = null
    }

    private fun stopBurst() {
        Log.i(TAG, "burst stop")
        if (stopped) return
        stopped = true
        release()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun endBurst() {
        Log.i(TAG, "burst end, keepAlive=$keepAliveOnEnd")
        if (!keepAliveOnEnd) {
            stopBurst()
            return
        }
        cancelPendingEnd()
        ThrottleState.activeProfile.set(ThrottleProfile.UNLIMITED)
        ThrottleState.setRunning(applicationContext, false)
        ThrottleState.responseBlockActive.set(false)
        ThrottleAuto.onBurstFinished()
        ThrottleWidget.update(applicationContext)
    }

    private fun release() {
        Log.i(TAG, "release")
        running = false
        isRunning = false
        cancelPendingEnd()

        tcpConnections.values.forEach { runCatching { it.close() } }
        tcpConnections.clear()
        udpSessions.values.forEach { runCatching { it.close() } }
        udpSessions.clear()

        runCatching { tunOutput?.close() }
        runCatching { tunInterface?.close() }
        tunOutput = null
        tunInterface = null

        ThrottleState.setRunning(applicationContext, false)
        ThrottleState.responseBlockActive.set(false)
        ThrottleAuto.onBurstFinished()
        ThrottleWidget.update(applicationContext)
    }

    private fun establishVpn(): Boolean {
        var fd: ParcelFileDescriptor? = null
        for ((index, delay) in RETRY_DELAYS_MS.withIndex()) {
            if (index > 0) Thread.sleep(delay)
            fd = tryEstablish()
            if (fd != null) break
        }
        val established = fd ?: return false

        tunInterface = established
        tunOutput = FileOutputStream(established.fileDescriptor)
        ThrottleState.downloadLimiter.reset()
        ThrottleState.uploadLimiter.reset()
        running = true
        isRunning = true
        Log.i(TAG, "tunnel established, fd=${established.fd}")

        Thread({ readLoop(FileInputStream(established.fileDescriptor)) }, "throttle-tun").start()
        return true
    }

    private fun tryEstablish(): ParcelFileDescriptor? = try {
        Builder()
            .setSession("Taxi Radar")
            .setMtu(MTU)
            .addAddress("10.0.0.2", 32)
            .addRoute("0.0.0.0", 0)
            .addDnsServer("8.8.8.8")
            .addDnsServer("1.1.1.1")
            .establish()
    } catch (e: Exception) {
        Log.w(TAG, "establish failed: ${e.message}")
        null
    }

    private fun readLoop(input: FileInputStream) {
        val buffer = ByteArray(MTU)
        // С Android 10 дескриптор туннеля неблокирующий: прямой read() сразу возвращал «нет данных», и поток
        // крутился вхолостую на 100% ядра, пока «Отмена» включена. Ждём пакет через poll — процессор спит.
        val pfd = android.system.StructPollfd().also {
            it.fd = input.fd
            it.events = android.system.OsConstants.POLLIN.toShort()
        }
        val bad = android.system.OsConstants.POLLERR or android.system.OsConstants.POLLHUP or android.system.OsConstants.POLLNVAL
        while (running) {
            try {
                pfd.revents = 0
                val ready = android.system.Os.poll(arrayOf(pfd), 500)
                if (ready <= 0) continue // пакетов нет: просто ждём дальше, заодно проверяем running
                if (pfd.revents.toInt() and bad != 0) {
                    if (!running || stopped) return
                    recover()
                    return
                }
                val length = input.read(buffer)
                if (length < 0) {
                    if (!running || stopped) return
                    // Туннель отвалился — пробуем поднять заново.
                    recover()
                    return
                }
                if (length > 0) {

                    try {
                        dispatch(buffer, length)
                    } catch (t: Throwable) {
                        Log.w(TAG, "dispatch failed", t)
                    }
                }
            } catch (e: Exception) {
                if (!running) return
                // Не крутимся вхолостую, если дескриптор вдруг недоступен.
                try { Thread.sleep(20) } catch (_: InterruptedException) { return }
            }
        }
    }

    private fun recover() {
        Log.i(TAG, "tunnel lost, reconnecting")
        release()
        if (stopped) return
        if (establishVpn()) {
            ThrottleState.activeProfile.set(ThrottleState.veryLowProfile.get())
            ThrottleState.responseBlockActive.set(true)
            running = true
        } else {
            handler.post { stopBurst() }
        }
    }

    private fun dispatch(packet: ByteArray, length: Int) {
        val ip = Ipv4Header.INSTANCE.parse(packet, length) ?: return
        val headerLength = ip.headerLength
        val payloadLength = minOf(ip.totalLength, length) - headerLength
        if (payloadLength < 0) return

        val remote = InetAddress.getByAddress(ip.destinationAddress)
        when (ip.protocol) {
            6 -> dispatchTcp(packet, headerLength, payloadLength, remote)
            17 -> dispatchUdp(packet, headerLength, payloadLength, remote)
        }
    }

    private fun dispatchTcp(packet: ByteArray, headerLength: Int, payloadLength: Int, remote: InetAddress) {
        val tcp = TcpHeader.INSTANCE.parse(packet, headerLength, payloadLength) ?: return
        val key = ConnectionKey(tcp.sourcePort, remote, tcp.destinationPort)

        if (tcp.hasFlag(2) && !tcp.hasFlag(16)) { // SYN без ACK

            if (tcpConnections.containsKey(key)) return
            if (tcpConnections.size >= MAX_TCP_CONNECTIONS) {
                sendReset(key, remote, tcp)
                return
            }
            val target = isTargetApp(6, tcp.sourcePort, remote, tcp.destinationPort)
            val connection = TcpConnection(
                key,
                vpnAddress,
                this,
                ThrottleState.downloadLimiter,
                ThrottleState.uploadLimiter,
                ThrottleState.alwaysUploadLimiter,
                ThrottleState.blockUploadLimiter,
                ThrottleState.alwaysUnlimitedDownloadLimiter,
                ThrottleState.responseBlockActive,
                target,
                target || Build.VERSION.SDK_INT < 29,
            ) { closedKey ->
                tcpConnections.remove(closedKey)
                Unit
            }
            tcpConnections[key] = connection
            connection.handleSyn(tcp)
            return
        }

        val dataOffset = headerLength + tcp.headerLength
        val dataLength = payloadLength - tcp.headerLength
        val connection = tcpConnections[key]
        if (connection != null) {
            connection.handleSegment(tcp, packet, dataOffset, dataLength)
        } else if (!tcp.hasFlag(4)) {
            sendReset(key, remote, tcp)
        }
    }

    private fun dispatchUdp(packet: ByteArray, headerLength: Int, payloadLength: Int, remote: InetAddress) {
        val udp = UdpHeader.INSTANCE.parse(packet, headerLength, payloadLength) ?: return
        val key = ConnectionKey(udp.sourcePort, remote, udp.destinationPort)
        val offset = headerLength + 8
        val length = payloadLength - 8
        if (length < 0) return

        var session = udpSessions[key]
        if (session == null) {
            if (udpSessions.size >= MAX_UDP_SESSIONS) return
            session = UdpSession(
                key,
                vpnAddress,
                this,
                ThrottleState.downloadLimiter,
                isTargetApp(17, udp.sourcePort, remote, udp.destinationPort),
            ) { closedKey ->
                udpSessions.remove(closedKey)
                Unit
            }
            if (!session.start()) return
            udpSessions[key] = session
        }
        session.send(packet, offset, length)
    }

    private fun sendReset(key: ConnectionKey, remote: InetAddress, tcp: TcpHeader) {
        val remoteBytes = remote.address
        val buffer = ByteArray(40)
        val tcpLength = TcpHeader.INSTANCE.write(
            buffer, 20, remoteBytes, vpnAddress,
            key.remotePort, key.clientPort, tcp.ackNumber, 0L, 4, 0, ByteArray(0), 0, 0,
        )
        Ipv4Header.INSTANCE.write(buffer, 0, 6, remoteBytes, vpnAddress, tcpLength)
        writePacket(buffer, tcpLength + 20)
    }

    private fun isTargetApp(protocol: Int, clientPort: Int, remote: InetAddress, remotePort: Int): Boolean {
        if (Build.VERSION.SDK_INT < 29) return false
        return try {
            val cm = getSystemService(ConnectivityManager::class.java) ?: return false
            val uid = cm.getConnectionOwnerUid(
                protocol,
                InetSocketAddress(InetAddress.getByAddress(vpnAddress), clientPort),
                InetSocketAddress(remote, remotePort),
            )
            uid >= 0 && packageManager.getPackagesForUid(uid)?.contains(TARGET_PACKAGE) == true
        } catch (e: Exception) {
            false
        }
    }

    override fun protectSocket(socket: Socket): Boolean = protect(socket)

    override fun protectSocket(socket: DatagramSocket): Boolean = protect(socket)

    override fun writePacket(buffer: ByteArray, length: Int) {
        synchronized(writeLock) {
            try {
                tunOutput?.write(buffer, 0, length)
                sentBytes.addAndGet(length.toLong())
            } catch (e: Exception) {
                // Туннель уже закрыт — нечего делать.
            }
        }
    }

    override fun onRevoke() {
        runCatching { stopBurst() }
        super.onRevoke()
    }

    override fun onDestroy() {
        release()
        super.onDestroy()
    }

    private fun buildNotification(): Notification {
        val openApp = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stop = PendingIntent.getService(
            this, 1,
            Intent(this, ThrottleVpnService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return androidx.core.app.NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_nav_radar)
            .setContentTitle(getString(R.string.throttle_notification_title))
            .setContentText(getString(R.string.throttle_notification_text))
            .setContentIntent(openApp)
            .addAction(R.drawable.ic_nav_throttle, getString(R.string.throttle_stop), stop)
            .setOngoing(true)
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.throttle_channel), NotificationManager.IMPORTANCE_LOW),
        )
    }
}
