package com.example.taxiradar.throttle.net;

/* JADX INFO: compiled from: TcpConnection.kt */
/* JADX INFO: loaded from: classes2.dex */
@kotlin.Metadata(d1 = {"\u0000r\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u0012\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0005\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000b\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\u0010\u0002\n\u0002\b\b\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\t\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0005\n\u0002\u0010\u000e\n\u0002\b\u0004\n\u0002\u0018\u0002\n\u0002\b\u0006\n\u0002\u0010\b\n\u0002\b\u0013\b\u0001\u0018\u0000 I2\u00020\u0001:\u0001IBs\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0005\u0012\u0006\u0010\u0006\u001a\u00020\u0007\u0012\u0006\u0010\b\u001a\u00020\t\u0012\u0006\u0010\n\u001a\u00020\t\u0012\u0006\u0010\u000b\u001a\u00020\t\u0012\u0006\u0010\f\u001a\u00020\t\u0012\u0006\u0010\r\u001a\u00020\t\u0012\u0006\u0010\u000e\u001a\u00020\u000f\u0012\u0006\u0010\u0010\u001a\u00020\u0011\u0012\u0006\u0010\u0012\u001a\u00020\u0011\u0012\u0012\u0010\u0013\u001a\u000e\u0012\u0004\u0012\u00020\u0003\u0012\u0004\u0012\u00020\u00150\u0014Вў\u0006\u0004\b\u0016\u0010\u0017J\u000e\u0010.\u001a\u00020\u00152\u0006\u0010/\u001a\u000200J\b\u00101\u001a\u00020\u0015H\u0002J\u0010\u00102\u001a\u00020\u00152\u0006\u00103\u001a\u00020#H\u0002J&\u00104\u001a\u00020\u00152\u0006\u0010/\u001a\u0002002\u0006\u00105\u001a\u00020\u00052\u0006\u00106\u001a\u0002072\u0006\u00108\u001a\u000207J\u0006\u00109\u001a\u00020\u0015J\b\u0010:\u001a\u00020\u0015H\u0002J\b\u0010;\u001a\u00020\u0015H\u0002J\b\u0010<\u001a\u00020\u0015H\u0002J\b\u0010=\u001a\u00020\u0015H\u0002J\u0018\u0010>\u001a\u00020\u00152\u0006\u0010?\u001a\u0002072\u0006\u0010@\u001a\u00020\u0011H\u0002J%\u0010A\u001a\u00020\u00152\u0006\u0010B\u001a\u00020\u00052\u0006\u0010C\u001a\u0002072\u0006\u0010D\u001a\u000207H\u0000Вў\u0006\u0002\bEJ2\u0010F\u001a\u00020\u00152\u0006\u0010?\u001a\u0002072\u0006\u0010G\u001a\u00020\u00052\u0006\u00106\u001a\u0002072\u0006\u00108\u001a\u0002072\b\b\u0002\u0010H\u001a\u00020\u0005H\u0002R\u0011\u0010\u0002\u001a\u00020\u0003Вў\u0006\b\n\u0000\u001a\u0004\b\u0018\u0010\u0019R\u000e\u0010\u0004\u001a\u00020\u0005X\u0082\u0004Вў\u0006\u0002\n\u0000R\u000e\u0010\u0006\u001a\u00020\u0007X\u0082\u0004Вў\u0006\u0002\n\u0000R\u000e\u0010\b\u001a\u00020\tX\u0082\u0004Вў\u0006\u0002\n\u0000R\u000e\u0010\n\u001a\u00020\tX\u0082\u0004Вў\u0006\u0002\n\u0000R\u000e\u0010\u000b\u001a\u00020\tX\u0082\u0004Вў\u0006\u0002\n\u0000R\u000e\u0010\f\u001a\u00020\tX\u0082\u0004Вў\u0006\u0002\n\u0000R\u000e\u0010\r\u001a\u00020\tX\u0082\u0004Вў\u0006\u0002\n\u0000R\u000e\u0010\u000e\u001a\u00020\u000fX\u0082\u0004Вў\u0006\u0002\n\u0000R\u000e\u0010\u0010\u001a\u00020\u0011X\u0082\u0004Вў\u0006\u0002\n\u0000R\u000e\u0010\u0012\u001a\u00020\u0011X\u0082\u0004Вў\u0006\u0002\n\u0000R\u001a\u0010\u0013\u001a\u000e\u0012\u0004\u0012\u00020\u0003\u0012\u0004\u0012\u00020\u00150\u0014X\u0082\u0004Вў\u0006\u0002\n\u0000R\u0018\u0010\u001a\u001a\n \u001b*\u0004\u0018\u00010\u00050\u0005X\u0082\u0004Вў\u0006\u0004\n\u0002\u0010\u001cR\u000e\u0010\u001d\u001a\u00020\u001eX\u0082\u0004Вў\u0006\u0002\n\u0000R\u000e\u0010\u001f\u001a\u00020 X\u0082\u000eВў\u0006\u0002\n\u0000R\u000e\u0010!\u001a\u00020\u0011X\u0082\u000eВў\u0006\u0002\n\u0000R\u0010\u0010\"\u001a\u0004\u0018\u00010#X\u0082\u000eВў\u0006\u0002\n\u0000R\u0014\u0010$\u001a\b\u0012\u0004\u0012\u00020\u00050%X\u0082\u0004Вў\u0006\u0002\n\u0000R\u000e\u0010&\u001a\u00020\u001eX\u0082\u0004Вў\u0006\u0002\n\u0000R\u000e\u0010'\u001a\u00020\u001eX\u0082\u0004Вў\u0006\u0002\n\u0000R\u000e\u0010(\u001a\u00020 X\u0082\u000eВў\u0006\u0002\n\u0000R\u000e\u0010)\u001a\u00020\u0005X\u0082\u0004Вў\u0006\u0002\n\u0000R\u0014\u0010*\u001a\u00020+8BX\u0082\u0004Вў\u0006\u0006\u001a\u0004\b,\u0010-ВЁ\u0006J"}, d2 = {"Lotmena/v5/net/TcpConnection;", "", "key", "Lotmena/v5/net/ConnectionKey;", "vpnAddress", "", "sink", "Lotmena/v5/net/VpnPacketSink;", "downloadLimiter", "Lotmena/v5/BandwidthLimiter;", "uploadLimiter", "alwaysThrottleUploadLimiter", "burstBlockUploadLimiter", "alwaysUnlimitedDownloadLimiter", "responseBlockActive", "Ljava/util/concurrent/atomic/AtomicBoolean;", "logTraffic", "", "unlimitedDownload", "onClosed", "Lkotlin/Function1;", "", "<init>", "(Lotmena/v5/net/ConnectionKey;[BLotmena/v5/net/VpnPacketSink;Lotmena/v5/BandwidthLimiter;Lotmena/v5/BandwidthLimiter;Lotmena/v5/BandwidthLimiter;Lotmena/v5/BandwidthLimiter;Lotmena/v5/BandwidthLimiter;Ljava/util/concurrent/atomic/AtomicBoolean;ZZLkotlin/jvm/functions/Function1;)V", "getKey", "()Lotmena/v5/net/ConnectionKey;", "remoteAddressBytes", "kotlin.jvm.PlatformType", "[B", "mySeq", "Ljava/util/concurrent/atomic/AtomicLong;", "clientNextSeq", "", "closed", "remoteSocket", "Ljava/net/Socket;", "uploadQueue", "Ljava/util/concurrent/ArrayBlockingQueue;", "rxBytes", "txBytes", "connectedAtMs", "downloadSegmentBuf", "remoteHost", "", "getRemoteHost", "()Ljava/lang/String;", "handleSyn", "header", "Lotmena/v5/net/TcpHeader;", "connectAndRelay", "relay", "socket", "handleSegment", "packet", "payloadOffset", "", "payloadLength", "close", "sendSynAck", "sendAck", "sendFin", "sendRst", "sendControlPacket", "flags", "consumesSeq", "sendData", "data", "offset", "length", "sendData", "writeSegment", "payload", "into", "Companion", "app_release"}, k = 1, mv = {2, 0, 0}, xi = 48)
public final class TcpConnection {
    private static final int CONNECT_TIMEOUT_MS = 10000;
    private static final int MAX_CONCURRENT_CONNECTS = 64;
    private static final int MAX_QUEUED_CONNECTS = 128;
    public static final int MSS = 1400;
    private static final long THREAD_STACK_BYTES = 262144;
    private static final int UPLOAD_QUEUE_CAPACITY = 64;
    private static final int WINDOW_SIZE = 65535;
    private final com.example.taxiradar.throttle.BandwidthLimiter alwaysThrottleUploadLimiter;
    private final com.example.taxiradar.throttle.BandwidthLimiter alwaysUnlimitedDownloadLimiter;
    private final com.example.taxiradar.throttle.BandwidthLimiter burstBlockUploadLimiter;
    private volatile long clientNextSeq;
    private volatile boolean closed;
    private long connectedAtMs;
    private final com.example.taxiradar.throttle.BandwidthLimiter downloadLimiter;
    private final byte[] downloadSegmentBuf;
    private final com.example.taxiradar.throttle.net.ConnectionKey key;
    private final boolean logTraffic;
    private final java.util.concurrent.atomic.AtomicLong mySeq;
    private final kotlin.jvm.functions.Function1<? super com.example.taxiradar.throttle.net.ConnectionKey, kotlin.Unit> onClosed;
    private final byte[] remoteAddressBytes;
    private java.net.Socket remoteSocket;
    private final java.util.concurrent.atomic.AtomicBoolean responseBlockActive;
    private final java.util.concurrent.atomic.AtomicLong rxBytes;
    private final com.example.taxiradar.throttle.net.VpnPacketSink sink;
    private final java.util.concurrent.atomic.AtomicLong txBytes;
    private final boolean unlimitedDownload;
    private final com.example.taxiradar.throttle.BandwidthLimiter uploadLimiter;
    private final java.util.concurrent.ArrayBlockingQueue<byte[]> uploadQueue;
    private final byte[] vpnAddress;
    public static final int $stable = 8;
    private static final java.util.concurrent.ThreadPoolExecutor connectExecutor = new java.util.concurrent.ThreadPoolExecutor(64, 64, 0, java.util.concurrent.TimeUnit.MILLISECONDS, new java.util.concurrent.LinkedBlockingQueue(128), new java.util.concurrent.ThreadPoolExecutor.AbortPolicy());

    /* JADX WARN: Multi-variable type inference failed */
    public TcpConnection(com.example.taxiradar.throttle.net.ConnectionKey key, byte[] vpnAddress, com.example.taxiradar.throttle.net.VpnPacketSink sink, com.example.taxiradar.throttle.BandwidthLimiter downloadLimiter, com.example.taxiradar.throttle.BandwidthLimiter uploadLimiter, com.example.taxiradar.throttle.BandwidthLimiter alwaysThrottleUploadLimiter, com.example.taxiradar.throttle.BandwidthLimiter burstBlockUploadLimiter, com.example.taxiradar.throttle.BandwidthLimiter alwaysUnlimitedDownloadLimiter, java.util.concurrent.atomic.AtomicBoolean responseBlockActive, boolean z, boolean z2, kotlin.jvm.functions.Function1<? super com.example.taxiradar.throttle.net.ConnectionKey, kotlin.Unit> onClosed) {
        kotlin.jvm.internal.Intrinsics.checkNotNullParameter(key, "key");
        kotlin.jvm.internal.Intrinsics.checkNotNullParameter(vpnAddress, "vpnAddress");
        kotlin.jvm.internal.Intrinsics.checkNotNullParameter(sink, "sink");
        kotlin.jvm.internal.Intrinsics.checkNotNullParameter(downloadLimiter, "downloadLimiter");
        kotlin.jvm.internal.Intrinsics.checkNotNullParameter(uploadLimiter, "uploadLimiter");
        kotlin.jvm.internal.Intrinsics.checkNotNullParameter(alwaysThrottleUploadLimiter, "alwaysThrottleUploadLimiter");
        kotlin.jvm.internal.Intrinsics.checkNotNullParameter(burstBlockUploadLimiter, "burstBlockUploadLimiter");
        kotlin.jvm.internal.Intrinsics.checkNotNullParameter(alwaysUnlimitedDownloadLimiter, "alwaysUnlimitedDownloadLimiter");
        kotlin.jvm.internal.Intrinsics.checkNotNullParameter(responseBlockActive, "responseBlockActive");
        kotlin.jvm.internal.Intrinsics.checkNotNullParameter(onClosed, "onClosed");
        this.key = key;
        this.vpnAddress = vpnAddress;
        this.sink = sink;
        this.downloadLimiter = downloadLimiter;
        this.uploadLimiter = uploadLimiter;
        this.alwaysThrottleUploadLimiter = alwaysThrottleUploadLimiter;
        this.burstBlockUploadLimiter = burstBlockUploadLimiter;
        this.alwaysUnlimitedDownloadLimiter = alwaysUnlimitedDownloadLimiter;
        this.responseBlockActive = responseBlockActive;
        this.logTraffic = z;
        this.unlimitedDownload = z2;
        this.onClosed = onClosed;
        this.remoteAddressBytes = key.getRemoteAddress().getAddress();
        this.mySeq = new java.util.concurrent.atomic.AtomicLong(((long) kotlin.random.Random.Default.nextInt()) & 4294967295L);
        this.uploadQueue = new java.util.concurrent.ArrayBlockingQueue<>(64);
        this.rxBytes = new java.util.concurrent.atomic.AtomicLong(0L);
        this.txBytes = new java.util.concurrent.atomic.AtomicLong(0L);
        this.downloadSegmentBuf = new byte[1440];
    }

    public final com.example.taxiradar.throttle.net.ConnectionKey getKey() {
        return this.key;
    }

    private final java.lang.String getRemoteHost() {
        java.lang.String hostAddress = this.key.getRemoteAddress().getHostAddress();
        if (hostAddress != null) {
            return hostAddress;
        }
        java.lang.String string = this.key.getRemoteAddress().toString();
        kotlin.jvm.internal.Intrinsics.checkNotNullExpressionValue(string, "toString(...)");
        return string;
    }

    public final void handleSyn(com.example.taxiradar.throttle.net.TcpHeader header) {
        kotlin.jvm.internal.Intrinsics.checkNotNullParameter(header, "header");
        this.clientNextSeq = (header.getSequenceNumber() + 1) & 4294967295L;
        try {
            try {
                connectExecutor.execute(new java.lang.Runnable() { // from class: com.example.taxiradar.throttle.net.TcpConnection$$ExternalSyntheticLambda0
                    @Override // java.lang.Runnable
                    public final void run() {
                        TcpConnection.this.connectAndRelay();
                    }
                });
            } catch (java.util.concurrent.RejectedExecutionException unused) {
                sendRst();
                this.onClosed.invoke(this.key);
            }
        } catch (java.lang.Throwable th) {
            this.onClosed.invoke(this.key);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public final void connectAndRelay() {
        final java.net.Socket socket = new java.net.Socket();
        try {
            socket.bind(new java.net.InetSocketAddress(0));
            if (!this.sink.protectSocket(socket)) {
                throw new java.io.IOException("protect() failed for " + this.key);
            }
            socket.connect(new java.net.InetSocketAddress(this.key.getRemoteAddress(), this.key.getRemotePort()), CONNECT_TIMEOUT_MS);
            this.remoteSocket = socket;
            this.connectedAtMs = java.lang.System.currentTimeMillis();
            if (this.logTraffic) {
                com.example.taxiradar.throttle.RequestLog.INSTANCE.logConnect("TCP", getRemoteHost(), this.key.getRemotePort());
            }
            sendSynAck();
            try {
                try {
                    new java.lang.Thread(null, new java.lang.Runnable() { // from class: com.example.taxiradar.throttle.net.TcpConnection$$ExternalSyntheticLambda1
                        @Override // java.lang.Runnable
                        public final void run() {
                            TcpConnection.this.relay(socket);
                        }
                    }, "tcp-relay-" + this.key, THREAD_STACK_BYTES).start();
                } catch (java.lang.Throwable th) {
                    try {
                        sendRst();
                    } catch (java.lang.Throwable th2) {
                    }
                    this.onClosed.invoke(this.key);
                }
            } catch (java.lang.OutOfMemoryError unused) {
                socket.close();
                sendRst();
                this.onClosed.invoke(this.key);
            }
        } catch (java.lang.Exception unused2) {
            sendRst();
            this.onClosed.invoke(this.key);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    /* JADX WARN: Code duplicated, block: B:43:0x0137 A[DONT_GENERATE] */
    public final void relay(final java.net.Socket socket) {
        java.io.InputStream inputStream;
        try {
            try {
                if (this.logTraffic) {
                    com.example.taxiradar.throttle.RequestLog.INSTANCE.registerLive(this.key.toString(), "TCP", getRemoteHost(), this.key.getRemotePort(), this.rxBytes, this.txBytes);
                }
                java.lang.Thread thread = new java.lang.Thread(null, new java.lang.Runnable() { // from class: com.example.taxiradar.throttle.net.TcpConnection$$ExternalSyntheticLambda2
                    @Override // java.lang.Runnable
                    public final void run() {
                        com.example.taxiradar.throttle.net.TcpConnection.relay$lambda$7(TcpConnection.this, socket);
                    }
                }, "tcp-up-" + this.key, THREAD_STACK_BYTES);
                thread.start();
                try {
                    com.example.taxiradar.throttle.net.TcpConnection tcpConnection = this;
                    if (this.logTraffic) {
                        java.io.InputStream inputStream2 = socket.getInputStream();
                        kotlin.jvm.internal.Intrinsics.checkNotNullExpressionValue(inputStream2, "getInputStream(...)");
                        inputStream = new com.example.taxiradar.throttle.net.CountingInputStream(inputStream2, this.rxBytes);
                    } else {
                        inputStream = socket.getInputStream();
                    }
                    com.example.taxiradar.throttle.ThrottledPipe throttledPipe = new com.example.taxiradar.throttle.ThrottledPipe(this.unlimitedDownload ? this.alwaysUnlimitedDownloadLimiter : this.downloadLimiter, false, null, null);
                    kotlin.jvm.internal.Intrinsics.checkNotNull(inputStream);
                    throttledPipe.pump(inputStream, new com.example.taxiradar.throttle.net.TunOutputStream(this));
                } catch (java.lang.Throwable th) {
                }
                try {
                    com.example.taxiradar.throttle.net.TcpConnection tcpConnection2 = this;
                    sendFin();
                } catch (java.lang.Throwable th2) {
                }
                thread.join();
                try {
                    com.example.taxiradar.throttle.net.TcpConnection tcpConnection3 = this;
                    socket.close();
                } catch (java.lang.Throwable th3) {
                }
                if (this.logTraffic) {
                    com.example.taxiradar.throttle.RequestLog.INSTANCE.logClosed("TCP", getRemoteHost(), this.key.getRemotePort(), this.rxBytes.get(), this.txBytes.get(), java.lang.System.currentTimeMillis() - this.connectedAtMs);
                }
            } catch (java.lang.Exception unused) {
                socket.close();
            }
        } catch (java.lang.Throwable th4) {
        } finally {
            if (this.logTraffic) {
                com.example.taxiradar.throttle.RequestLog.INSTANCE.unregisterLive(this.key.toString());
            }
            this.onClosed.invoke(this.key);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static final void relay$lambda$7(com.example.taxiradar.throttle.net.TcpConnection tcpConnection, java.net.Socket socket) {
        java.io.OutputStream outputStream;
        try {
            if (tcpConnection.logTraffic) {
                java.io.OutputStream outputStream2 = socket.getOutputStream();
                kotlin.jvm.internal.Intrinsics.checkNotNullExpressionValue(outputStream2, "getOutputStream(...)");
                outputStream = new com.example.taxiradar.throttle.net.CountingOutputStream(outputStream2, tcpConnection.txBytes);
            } else {
                outputStream = socket.getOutputStream();
            }
            com.example.taxiradar.throttle.BandwidthLimiter bandwidthLimiter = tcpConnection.logTraffic ? tcpConnection.alwaysThrottleUploadLimiter : tcpConnection.uploadLimiter;
            java.lang.String str = tcpConnection.getRemoteHost() + ":" + tcpConnection.key.getRemotePort() + " up";
            boolean z = tcpConnection.logTraffic;
            com.example.taxiradar.throttle.ThrottledPipe throttledPipe = new com.example.taxiradar.throttle.ThrottledPipe(bandwidthLimiter, z, tcpConnection.responseBlockActive, z ? tcpConnection.burstBlockUploadLimiter : null);
            com.example.taxiradar.throttle.net.QueueInputStream queueInputStream = new com.example.taxiradar.throttle.net.QueueInputStream(tcpConnection.uploadQueue);
            kotlin.jvm.internal.Intrinsics.checkNotNull(outputStream);
            throttledPipe.pump(queueInputStream, outputStream);
        } catch (java.lang.Throwable th) {
        }
        try {
            socket.shutdownOutput();
        } catch (java.lang.Throwable th2) {
        }
    }

    public final void handleSegment(com.example.taxiradar.throttle.net.TcpHeader header, byte[] packet, int payloadOffset, int payloadLength) {
        kotlin.jvm.internal.Intrinsics.checkNotNullParameter(header, "header");
        kotlin.jvm.internal.Intrinsics.checkNotNullParameter(packet, "packet");
        if (header.hasFlag(4)) {
            close();
            return;
        }
        if (payloadLength > 0 && header.getSequenceNumber() == this.clientNextSeq) {
            if (this.uploadQueue.offer(kotlin.collections.ArraysKt.copyOfRange(packet, payloadOffset, payloadOffset + payloadLength))) {
                this.clientNextSeq = (this.clientNextSeq + ((long) payloadLength)) & 4294967295L;
                sendAck();
            }
        }
        if (header.hasFlag(1)) {
            this.clientNextSeq = (this.clientNextSeq + 1) & 4294967295L;
            this.uploadQueue.offer(com.example.taxiradar.throttle.net.QueueInputStream.Companion.getEOF_MARKER());
            sendAck();
        }
    }

    public final void close() {
        kotlin.Unit unit;
        if (this.closed) {
            return;
        }
        this.closed = true;
        try {
            com.example.taxiradar.throttle.net.TcpConnection tcpConnection = this;
            java.net.Socket socket = this.remoteSocket;
            if (socket != null) {
                socket.close();
                unit = kotlin.Unit.INSTANCE;
            } else {
                unit = null;
            }
        } catch (java.lang.Throwable th) {
        }
        this.uploadQueue.offer(com.example.taxiradar.throttle.net.QueueInputStream.Companion.getEOF_MARKER());
    }

    private final void sendSynAck() {
        sendControlPacket(18, true);
    }

    private final void sendAck() {
        sendControlPacket(16, false);
    }

    private final void sendFin() {
        sendControlPacket(17, true);
    }

    private final void sendRst() {
        sendControlPacket(20, false);
    }

    private final void sendControlPacket(int flags, boolean consumesSeq) {
        writeSegment(flags, new byte[0], 0, 0, new byte[40]);
        if (consumesSeq) {
            this.mySeq.incrementAndGet();
        }
    }

    public final void sendData(byte[] data, int offset, int length) {
        kotlin.jvm.internal.Intrinsics.checkNotNullParameter(data, "data");
        int i = length + offset;
        int i2 = offset;
        while (i2 < i) {
            int iMin = java.lang.Math.min(MSS, i - i2);
            byte[] bArr = data;
            writeSegment(24, bArr, i2, iMin, this.downloadSegmentBuf);
            this.mySeq.addAndGet(iMin);
            i2 += iMin;
            data = bArr;
        }
    }

    static /* synthetic */ void writeSegment$default(com.example.taxiradar.throttle.net.TcpConnection tcpConnection, int i, byte[] bArr, int i2, int i3, byte[] bArr2, int i4, java.lang.Object obj) {
        if ((i4 & 16) != 0) {
            bArr2 = new byte[i3 + 40];
        }
        tcpConnection.writeSegment(i, bArr, i2, i3, bArr2);
    }

    private final void writeSegment(int flags, byte[] payload, int payloadOffset, int payloadLength, byte[] into) {
        com.example.taxiradar.throttle.net.TcpHeader.Companion companion = com.example.taxiradar.throttle.net.TcpHeader.INSTANCE;
        byte[] remoteAddressBytes = this.remoteAddressBytes;
        kotlin.jvm.internal.Intrinsics.checkNotNullExpressionValue(remoteAddressBytes, "remoteAddressBytes");
        int iWrite = companion.write(into, 20, remoteAddressBytes, this.vpnAddress, this.key.getRemotePort(), this.key.getClientPort(), this.mySeq.get(), this.clientNextSeq, flags, 65535, payload, payloadOffset, payloadLength);
        com.example.taxiradar.throttle.net.Ipv4Header.Companion companion2 = com.example.taxiradar.throttle.net.Ipv4Header.INSTANCE;
        byte[] remoteAddressBytes2 = this.remoteAddressBytes;
        kotlin.jvm.internal.Intrinsics.checkNotNullExpressionValue(remoteAddressBytes2, "remoteAddressBytes");
        companion2.write(into, 0, 6, remoteAddressBytes2, this.vpnAddress, iWrite);
        this.sink.writePacket(into, iWrite + 20);
    }
}

