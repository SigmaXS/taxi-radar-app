package com.example.taxiradar.throttle.net;

/* JADX INFO: compiled from: TcpHeader.kt */
/* JADX INFO: loaded from: classes2.dex */
@kotlin.Metadata(d1 = {"\u0000\"\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010\b\n\u0002\b\u0002\n\u0002\u0010\t\n\u0002\b\u0010\n\u0002\u0010\u000b\n\u0002\b\u0003\b\u0001\u0018\u0000 \u00192\u00020\u0001:\u0001\u0019BA\b\u0002\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0003\u0012\u0006\u0010\u0005\u001a\u00020\u0006\u0012\u0006\u0010\u0007\u001a\u00020\u0006\u0012\u0006\u0010\b\u001a\u00020\u0003\u0012\u0006\u0010\t\u001a\u00020\u0003\u0012\u0006\u0010\n\u001a\u00020\u0003Вў\u0006\u0004\b\u000b\u0010\fJ\u000e\u0010\u0016\u001a\u00020\u00172\u0006\u0010\u0018\u001a\u00020\u0003R\u0011\u0010\u0002\u001a\u00020\u0003Вў\u0006\b\n\u0000\u001a\u0004\b\r\u0010\u000eR\u0011\u0010\u0004\u001a\u00020\u0003Вў\u0006\b\n\u0000\u001a\u0004\b\u000f\u0010\u000eR\u0011\u0010\u0005\u001a\u00020\u0006Вў\u0006\b\n\u0000\u001a\u0004\b\u0010\u0010\u0011R\u0011\u0010\u0007\u001a\u00020\u0006Вў\u0006\b\n\u0000\u001a\u0004\b\u0012\u0010\u0011R\u0011\u0010\b\u001a\u00020\u0003Вў\u0006\b\n\u0000\u001a\u0004\b\u0013\u0010\u000eR\u0011\u0010\t\u001a\u00020\u0003Вў\u0006\b\n\u0000\u001a\u0004\b\u0014\u0010\u000eR\u0011\u0010\n\u001a\u00020\u0003Вў\u0006\b\n\u0000\u001a\u0004\b\u0015\u0010\u000eВЁ\u0006\u001a"}, d2 = {"Lotmena/v5/net/TcpHeader;", "", "sourcePort", "", "destinationPort", "sequenceNumber", "", "ackNumber", "headerLength", "flags", "windowSize", "<init>", "(IIJJIII)V", "getSourcePort", "()I", "getDestinationPort", "getSequenceNumber", "()J", "getAckNumber", "getHeaderLength", "getFlags", "getWindowSize", "hasFlag", "", "flag", "Companion", "app_release"}, k = 1, mv = {2, 0, 0}, xi = 48)
public final class TcpHeader {
    public static final int $stable = 0;

    /* JADX INFO: renamed from: Companion, reason: from kotlin metadata */
    public static final com.example.taxiradar.throttle.net.TcpHeader.Companion INSTANCE = new com.example.taxiradar.throttle.net.TcpHeader.Companion(null);
    private final long ackNumber;
    private final int destinationPort;
    private final int flags;
    private final int headerLength;
    private final long sequenceNumber;
    private final int sourcePort;
    private final int windowSize;

    public /* synthetic */ TcpHeader(int i, int i2, long j, long j2, int i3, int i4, int i5, kotlin.jvm.internal.DefaultConstructorMarker defaultConstructorMarker) {
        this(i, i2, j, j2, i3, i4, i5);
    }

    private TcpHeader(int i, int i2, long j, long j2, int i3, int i4, int i5) {
        this.sourcePort = i;
        this.destinationPort = i2;
        this.sequenceNumber = j;
        this.ackNumber = j2;
        this.headerLength = i3;
        this.flags = i4;
        this.windowSize = i5;
    }

    public final int getSourcePort() {
        return this.sourcePort;
    }

    public final int getDestinationPort() {
        return this.destinationPort;
    }

    public final long getSequenceNumber() {
        return this.sequenceNumber;
    }

    public final long getAckNumber() {
        return this.ackNumber;
    }

    public final int getHeaderLength() {
        return this.headerLength;
    }

    public final int getFlags() {
        return this.flags;
    }

    public final int getWindowSize() {
        return this.windowSize;
    }

    public final boolean hasFlag(int flag) {
        return (flag & this.flags) != 0;
    }

    /* JADX INFO: compiled from: TcpHeader.kt */
    @kotlin.Metadata(d1 = {"\u00000\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u0012\n\u0000\n\u0002\u0010\b\n\u0002\b\u0002\n\u0002\u0010\t\n\u0002\b\u000e\n\u0002\u0010\u0002\n\u0002\b\u0002\b\u0086\u0003\u0018\u00002\u00020\u0001B\t\b\u0002Вў\u0006\u0004\b\u0002\u0010\u0003J \u0010\u0004\u001a\u0004\u0018\u00010\u00052\u0006\u0010\u0006\u001a\u00020\u00072\u0006\u0010\b\u001a\u00020\t2\u0006\u0010\n\u001a\u00020\tJ\u0018\u0010\u000b\u001a\u00020\f2\u0006\u0010\u0006\u001a\u00020\u00072\u0006\u0010\b\u001a\u00020\tH\u0002Jn\u0010\r\u001a\u00020\t2\u0006\u0010\u000e\u001a\u00020\u00072\u0006\u0010\b\u001a\u00020\t2\u0006\u0010\u000f\u001a\u00020\u00072\u0006\u0010\u0010\u001a\u00020\u00072\u0006\u0010\u0011\u001a\u00020\t2\u0006\u0010\u0012\u001a\u00020\t2\u0006\u0010\u0013\u001a\u00020\f2\u0006\u0010\u0014\u001a\u00020\f2\u0006\u0010\u0015\u001a\u00020\t2\u0006\u0010\u0016\u001a\u00020\t2\u0006\u0010\u0017\u001a\u00020\u00072\u0006\u0010\u0018\u001a\u00020\t2\u0006\u0010\u0019\u001a\u00020\tJ \u0010\u001a\u001a\u00020\u001b2\u0006\u0010\u000e\u001a\u00020\u00072\u0006\u0010\b\u001a\u00020\t2\u0006\u0010\u001c\u001a\u00020\fH\u0002ВЁ\u0006\u001d"}, d2 = {"Lotmena/v5/net/TcpHeader$Companion;", "", "<init>", "()V", "parse", "Lotmena/v5/net/TcpHeader;", "data", "", "offset", "", "length", "readUInt32", "", "write", "out", "sourceAddress", "destinationAddress", "sourcePort", "destinationPort", "sequenceNumber", "ackNumber", "flags", "windowSize", "payload", "payloadOffset", "payloadLength", "writeUInt32", "", "value", "app_release"}, k = 1, mv = {2, 0, 0}, xi = 48)
    public static final class Companion {
        public /* synthetic */ Companion(kotlin.jvm.internal.DefaultConstructorMarker defaultConstructorMarker) {
            this();
        }

        private Companion() {
        }

        public final com.example.taxiradar.throttle.net.TcpHeader parse(byte[] data, int offset, int length) {
            kotlin.jvm.internal.Intrinsics.checkNotNullParameter(data, "data");
            if (length < 20) {
                return null;
            }
            int i = ((data[offset] & 255) << 8) | (data[offset + 1] & 255);
            int i2 = ((data[offset + 2] & 255) << 8) | (data[offset + 3] & 255);
            long uInt32 = readUInt32(data, offset + 4);
            long uInt33 = readUInt32(data, offset + 8);
            int i3 = ((data[offset + 12] & 255) >> 4) * 4;
            if (i3 < 20 || i3 > length) {
                return null;
            }
            return new com.example.taxiradar.throttle.net.TcpHeader(i, i2, uInt32, uInt33, i3, data[offset + 13] & 255, ((data[offset + 14] & 255) << 8) | (data[offset + 15] & 255), null);
        }

        private final long readUInt32(byte[] data, int offset) {
            return ((((long) data[offset + 3]) & 255) | ((((long) data[offset]) & 255) << 24) | ((((long) data[offset + 1]) & 255) << 16) | ((((long) data[offset + 2]) & 255) << 8)) & 4294967295L;
        }

        public final int write(byte[] out, int offset, byte[] sourceAddress, byte[] destinationAddress, int sourcePort, int destinationPort, long sequenceNumber, long ackNumber, int flags, int windowSize, byte[] payload, int payloadOffset, int payloadLength) {
            kotlin.jvm.internal.Intrinsics.checkNotNullParameter(out, "out");
            kotlin.jvm.internal.Intrinsics.checkNotNullParameter(sourceAddress, "sourceAddress");
            kotlin.jvm.internal.Intrinsics.checkNotNullParameter(destinationAddress, "destinationAddress");
            kotlin.jvm.internal.Intrinsics.checkNotNullParameter(payload, "payload");
            out[offset] = (byte) (sourcePort >> 8);
            out[offset + 1] = (byte) sourcePort;
            out[offset + 2] = (byte) (destinationPort >> 8);
            out[offset + 3] = (byte) destinationPort;
            writeUInt32(out, offset + 4, sequenceNumber);
            writeUInt32(out, offset + 8, ackNumber);
            out[offset + 12] = 80;
            out[offset + 13] = (byte) flags;
            out[offset + 14] = (byte) (windowSize >> 8);
            out[offset + 15] = (byte) windowSize;
            int i = offset + 16;
            out[i] = 0;
            int i2 = offset + 17;
            out[i2] = 0;
            out[offset + 18] = 0;
            out[offset + 19] = 0;
            java.lang.System.arraycopy(payload, payloadOffset, out, offset + 20, payloadLength);
            int i3 = payloadLength + 20;
            int iPseudoHeaderChecksum = com.example.taxiradar.throttle.net.TcpHeaderKt.pseudoHeaderChecksum(out, offset, i3, sourceAddress, destinationAddress, 6);
            out[i] = (byte) (iPseudoHeaderChecksum >> 8);
            out[i2] = (byte) iPseudoHeaderChecksum;
            return i3;
        }

        private final void writeUInt32(byte[] out, int offset, long value) {
            out[offset] = (byte) (value >> 24);
            out[offset + 1] = (byte) (value >> 16);
            out[offset + 2] = (byte) (value >> 8);
            out[offset + 3] = (byte) value;
        }
    }
}

