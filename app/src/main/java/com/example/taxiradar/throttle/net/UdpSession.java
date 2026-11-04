package com.example.taxiradar.throttle.net;

/* JADX INFO: compiled from: UdpSession.kt */
/* JADX INFO: loaded from: classes2.dex */
@kotlin.Metadata(d1 = {"\u0000\\\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u0012\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000b\n\u0000\n\u0002\u0018\u0002\n\u0002\u0010\u0002\n\u0002\b\b\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\t\n\u0002\b\u0002\n\u0002\u0010\u000e\n\u0002\b\u0007\n\u0002\u0010\b\n\u0002\b\t\b\u0001\u0018\u0000 02\u00020\u0001:\u00010BC\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0005\u0012\u0006\u0010\u0006\u001a\u00020\u0007\u0012\u0006\u0010\b\u001a\u00020\t\u0012\u0006\u0010\n\u001a\u00020\u000b\u0012\u0012\u0010\f\u001a\u000e\u0012\u0004\u0012\u00020\u0003\u0012\u0004\u0012\u00020\u000e0\rВў\u0006\u0004\b\u000f\u0010\u0010J\u0006\u0010#\u001a\u00020\u000bJ\u0006\u0010$\u001a\u00020\u001dJ\u001e\u0010%\u001a\u00020\u000e2\u0006\u0010&\u001a\u00020\u00052\u0006\u0010'\u001a\u00020(2\u0006\u0010)\u001a\u00020(J\u0010\u0010*\u001a\u00020\u000e2\u0006\u0010+\u001a\u00020\u0017H\u0002J \u0010,\u001a\u00020\u000e2\u0006\u0010-\u001a\u00020\u00052\u0006\u0010.\u001a\u00020\u00052\u0006\u0010)\u001a\u00020(H\u0002J\u0006\u0010/\u001a\u00020\u000eR\u0011\u0010\u0002\u001a\u00020\u0003Вў\u0006\b\n\u0000\u001a\u0004\b\u0011\u0010\u0012R\u000e\u0010\u0004\u001a\u00020\u0005X\u0082\u0004Вў\u0006\u0002\n\u0000R\u000e\u0010\u0006\u001a\u00020\u0007X\u0082\u0004Вў\u0006\u0002\n\u0000R\u000e\u0010\b\u001a\u00020\tX\u0082\u0004Вў\u0006\u0002\n\u0000R\u000e\u0010\n\u001a\u00020\u000bX\u0082\u0004Вў\u0006\u0002\n\u0000R\u001a\u0010\f\u001a\u000e\u0012\u0004\u0012\u00020\u0003\u0012\u0004\u0012\u00020\u000e0\rX\u0082\u0004Вў\u0006\u0002\n\u0000R\u0018\u0010\u0013\u001a\n \u0014*\u0004\u0018\u00010\u00050\u0005X\u0082\u0004Вў\u0006\u0004\n\u0002\u0010\u0015R\u0010\u0010\u0016\u001a\u0004\u0018\u00010\u0017X\u0082\u000eВў\u0006\u0002\n\u0000R\u000e\u0010\u0018\u001a\u00020\u000bX\u0082\u000eВў\u0006\u0002\n\u0000R\u000e\u0010\u0019\u001a\u00020\u001aX\u0082\u0004Вў\u0006\u0002\n\u0000R\u000e\u0010\u001b\u001a\u00020\u001aX\u0082\u0004Вў\u0006\u0002\n\u0000R\u000e\u0010\u001c\u001a\u00020\u001dX\u0082\u0004Вў\u0006\u0002\n\u0000R\u000e\u0010\u001e\u001a\u00020\u001dX\u0082\u000eВў\u0006\u0002\n\u0000R\u0014\u0010\u001f\u001a\u00020 8BX\u0082\u0004Вў\u0006\u0006\u001a\u0004\b!\u0010\"ВЁ\u00061"}, d2 = {"Lotmena/v5/net/UdpSession;", "", "key", "Lotmena/v5/net/ConnectionKey;", "vpnAddress", "", "sink", "Lotmena/v5/net/VpnPacketSink;", "downloadLimiter", "Lotmena/v5/BandwidthLimiter;", "logTraffic", "", "onClosed", "Lkotlin/Function1;", "", "<init>", "(Lotmena/v5/net/ConnectionKey;[BLotmena/v5/net/VpnPacketSink;Lotmena/v5/BandwidthLimiter;ZLkotlin/jvm/functions/Function1;)V", "getKey", "()Lotmena/v5/net/ConnectionKey;", "remoteAddressBytes", "kotlin.jvm.PlatformType", "[B", "socket", "Ljava/net/DatagramSocket;", "closed", "rxBytes", "Ljava/util/concurrent/atomic/AtomicLong;", "txBytes", "startedAtMs", "", "lastActivityMs", "remoteHost", "", "getRemoteHost", "()Ljava/lang/String;", "start", "lastActivityAt", "send", "payload", "offset", "", "length", "relay", "s", "writeBack", "packet", "data", "close", "Companion", "app_release"}, k = 1, mv = {2, 0, 0}, xi = 48)
public final class UdpSession {
    private static final long IDLE_TIMEOUT_MS = 60000;
    private static final int SOCKET_POLL_MS = 10000;
    private static final int UDP_BUFFER_SIZE = 8192;
    private volatile boolean closed;
    private final com.example.taxiradar.throttle.BandwidthLimiter downloadLimiter;
    private final com.example.taxiradar.throttle.net.ConnectionKey key;
    private volatile long lastActivityMs;
    private final boolean logTraffic;
    private final kotlin.jvm.functions.Function1<? super com.example.taxiradar.throttle.net.ConnectionKey, kotlin.Unit> onClosed;
    private final byte[] remoteAddressBytes;
    private final java.util.concurrent.atomic.AtomicLong rxBytes;
    private final com.example.taxiradar.throttle.net.VpnPacketSink sink;
    private volatile java.net.DatagramSocket socket;
    private final long startedAtMs;
    private final java.util.concurrent.atomic.AtomicLong txBytes;
    private final byte[] vpnAddress;
    public static final int $stable = 8;

    /* JADX WARN: Multi-variable type inference failed */
    public UdpSession(com.example.taxiradar.throttle.net.ConnectionKey key, byte[] vpnAddress, com.example.taxiradar.throttle.net.VpnPacketSink sink, com.example.taxiradar.throttle.BandwidthLimiter downloadLimiter, boolean z, kotlin.jvm.functions.Function1<? super com.example.taxiradar.throttle.net.ConnectionKey, kotlin.Unit> onClosed) {
        kotlin.jvm.internal.Intrinsics.checkNotNullParameter(key, "key");
        kotlin.jvm.internal.Intrinsics.checkNotNullParameter(vpnAddress, "vpnAddress");
        kotlin.jvm.internal.Intrinsics.checkNotNullParameter(sink, "sink");
        kotlin.jvm.internal.Intrinsics.checkNotNullParameter(downloadLimiter, "downloadLimiter");
        kotlin.jvm.internal.Intrinsics.checkNotNullParameter(onClosed, "onClosed");
        this.key = key;
        this.vpnAddress = vpnAddress;
        this.sink = sink;
        this.downloadLimiter = downloadLimiter;
        this.logTraffic = z;
        this.onClosed = onClosed;
        this.remoteAddressBytes = key.getRemoteAddress().getAddress();
        this.rxBytes = new java.util.concurrent.atomic.AtomicLong(0L);
        this.txBytes = new java.util.concurrent.atomic.AtomicLong(0L);
        this.startedAtMs = java.lang.System.currentTimeMillis();
        this.lastActivityMs = java.lang.System.currentTimeMillis();
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

    public final boolean start() {
        try {
            final java.net.DatagramSocket datagramSocket = new java.net.DatagramSocket();
            this.sink.protectSocket(datagramSocket);
            this.socket = datagramSocket;
            if (this.logTraffic) {
                com.example.taxiradar.throttle.RequestLog.INSTANCE.logConnect("UDP", getRemoteHost(), this.key.getRemotePort());
                com.example.taxiradar.throttle.RequestLog.INSTANCE.registerLive(this.key.toString(), "UDP", getRemoteHost(), this.key.getRemotePort(), this.rxBytes, this.txBytes);
            }
            try {
                try {
                    new java.lang.Thread(null, new java.lang.Runnable() { // from class: com.example.taxiradar.throttle.net.UdpSession$$ExternalSyntheticLambda0
                        @Override // java.lang.Runnable
                        public final void run() {
                            UdpSession.this.relay(datagramSocket);
                        }
                    }, "udp-" + this.key, 262144L).start();
                    return true;
                } catch (java.lang.Throwable th) {
                    this.onClosed.invoke(this.key);
                    return false;
                }
            } catch (java.lang.OutOfMemoryError unused) {
                datagramSocket.close();
                this.onClosed.invoke(this.key);
                return false;
            }
        } catch (java.lang.Exception unused2) {
            this.onClosed.invoke(this.key);
            return false;
        }
    }

    /* JADX INFO: renamed from: lastActivityAt, reason: from getter */
    public final long getLastActivityMs() {
        return this.lastActivityMs;
    }

    public final void send(byte[] payload, int offset, int length) {
        kotlin.jvm.internal.Intrinsics.checkNotNullParameter(payload, "payload");
        java.net.DatagramSocket datagramSocket = this.socket;
        if (datagramSocket == null) {
            return;
        }
        try {
            com.example.taxiradar.throttle.net.UdpSession udpSession = this;
            datagramSocket.send(new java.net.DatagramPacket(kotlin.collections.ArraysKt.copyOfRange(payload, offset, offset + length), length, this.key.getRemoteAddress(), this.key.getRemotePort()));
            this.lastActivityMs = java.lang.System.currentTimeMillis();
            if (this.logTraffic) {
                this.txBytes.addAndGet(length);
            }
        } catch (java.lang.Throwable th) {
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    /* JADX WARN: Code duplicated, block: B:46:0x0100  */
    public final void relay(java.net.DatagramSocket s) {
        try {
            com.example.taxiradar.throttle.net.UdpSession udpSession = this;
            s.setSoTimeout(SOCKET_POLL_MS);
        } catch (java.lang.Throwable th) {
        }
        byte[] bArr = new byte[8192];
        byte[] bArr2 = new byte[8220];
        while (!this.closed) {
            try {
                java.net.DatagramPacket datagramPacket = new java.net.DatagramPacket(bArr, 8192);
                try {
                    try {
                        s.receive(datagramPacket);
                        this.lastActivityMs = java.lang.System.currentTimeMillis();
                        if (this.logTraffic) {
                            this.rxBytes.addAndGet(datagramPacket.getLength());
                        }
                        if (this.key.getRemotePort() == 53) {
                            com.example.taxiradar.throttle.RequestLog.INSTANCE.observeDnsResponse(bArr, datagramPacket.getLength());
                        }
                        this.downloadLimiter.acquireAll(datagramPacket.getLength());
                        writeBack(bArr2, bArr, datagramPacket.getLength());
                    } catch (java.lang.Throwable th2) {
                        if (this.logTraffic) {
                            com.example.taxiradar.throttle.RequestLog.INSTANCE.unregisterLive(this.key.toString());
                            com.example.taxiradar.throttle.RequestLog.INSTANCE.logClosed("UDP", getRemoteHost(), this.key.getRemotePort(), this.rxBytes.get(), this.txBytes.get(), java.lang.System.currentTimeMillis() - this.startedAtMs);
                        }
                        this.onClosed.invoke(this.key);
                    }
                } catch (java.lang.Throwable unused) {
                    if (java.lang.System.currentTimeMillis() - this.lastActivityMs > IDLE_TIMEOUT_MS) {
                        break;
                    }
                    com.example.taxiradar.throttle.net.UdpSession udpSession2 = this;
                    s.close();
                    if (this.logTraffic) {
                        com.example.taxiradar.throttle.RequestLog.INSTANCE.unregisterLive(this.key.toString());
                        com.example.taxiradar.throttle.RequestLog.INSTANCE.logClosed("UDP", getRemoteHost(), this.key.getRemotePort(), this.rxBytes.get(), this.txBytes.get(), java.lang.System.currentTimeMillis() - this.startedAtMs);
                    }
                    this.onClosed.invoke(this.key);
                }
            } catch (java.lang.Exception unused2) {
                s.close();
                if (this.logTraffic) {
                    com.example.taxiradar.throttle.RequestLog.INSTANCE.unregisterLive(this.key.toString());
                    com.example.taxiradar.throttle.RequestLog.INSTANCE.logClosed("UDP", getRemoteHost(), this.key.getRemotePort(), this.rxBytes.get(), this.txBytes.get(), java.lang.System.currentTimeMillis() - this.startedAtMs);
                }
                this.onClosed.invoke(this.key);
            } catch (java.lang.Throwable th3) {
                try {
                    s.close();
                } catch (java.lang.Throwable th4) {
                }
                if (this.logTraffic) {
                    com.example.taxiradar.throttle.RequestLog.INSTANCE.unregisterLive(this.key.toString());
                    com.example.taxiradar.throttle.RequestLog.INSTANCE.logClosed("UDP", getRemoteHost(), this.key.getRemotePort(), this.rxBytes.get(), this.txBytes.get(), java.lang.System.currentTimeMillis() - this.startedAtMs);
                }
                this.onClosed.invoke(this.key);
                throw th3;
            }
        }
        try {
            com.example.taxiradar.throttle.net.UdpSession udpSession3 = this;
            s.close();
        } catch (java.lang.Throwable th5) {
        }
        if (this.logTraffic) {
            com.example.taxiradar.throttle.RequestLog.INSTANCE.unregisterLive(this.key.toString());
            com.example.taxiradar.throttle.RequestLog.INSTANCE.logClosed("UDP", getRemoteHost(), this.key.getRemotePort(), this.rxBytes.get(), this.txBytes.get(), java.lang.System.currentTimeMillis() - this.startedAtMs);
        }
        this.onClosed.invoke(this.key);
    }

    private final void writeBack(byte[] packet, byte[] data, int length) {
        com.example.taxiradar.throttle.net.UdpHeader.Companion companion = com.example.taxiradar.throttle.net.UdpHeader.INSTANCE;
        byte[] remoteAddressBytes = this.remoteAddressBytes;
        kotlin.jvm.internal.Intrinsics.checkNotNullExpressionValue(remoteAddressBytes, "remoteAddressBytes");
        int iWrite = companion.write(packet, 20, remoteAddressBytes, this.vpnAddress, this.key.getRemotePort(), this.key.getClientPort(), data, 0, length);
        com.example.taxiradar.throttle.net.Ipv4Header.Companion companion2 = com.example.taxiradar.throttle.net.Ipv4Header.INSTANCE;
        byte[] remoteAddressBytes2 = this.remoteAddressBytes;
        kotlin.jvm.internal.Intrinsics.checkNotNullExpressionValue(remoteAddressBytes2, "remoteAddressBytes");
        companion2.write(packet, 0, 17, remoteAddressBytes2, this.vpnAddress, iWrite);
        this.sink.writePacket(packet, iWrite + 20);
    }

    public final void close() {
        kotlin.Unit unit;
        this.closed = true;
        try {
            com.example.taxiradar.throttle.net.UdpSession udpSession = this;
            java.net.DatagramSocket datagramSocket = this.socket;
            if (datagramSocket != null) {
                datagramSocket.close();
                unit = kotlin.Unit.INSTANCE;
            } else {
                unit = null;
            }
        } catch (java.lang.Throwable th) {
        }
    }
}

