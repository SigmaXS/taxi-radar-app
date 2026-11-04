package com.example.taxiradar.throttle.net;

/* JADX INFO: compiled from: UdpHeader.kt */
/* JADX INFO: loaded from: classes2.dex */
@kotlin.Metadata(d1 = {"\u0000\u0012\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010\b\n\u0002\b\n\b\u0001\u0018\u0000 \f2\u00020\u0001:\u0001\fB!\b\u0002\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0003\u0012\u0006\u0010\u0005\u001a\u00020\u0003Вў\u0006\u0004\b\u0006\u0010\u0007R\u0011\u0010\u0002\u001a\u00020\u0003Вў\u0006\b\n\u0000\u001a\u0004\b\b\u0010\tR\u0011\u0010\u0004\u001a\u00020\u0003Вў\u0006\b\n\u0000\u001a\u0004\b\n\u0010\tR\u0011\u0010\u0005\u001a\u00020\u0003Вў\u0006\b\n\u0000\u001a\u0004\b\u000b\u0010\tВЁ\u0006\r"}, d2 = {"Lotmena/v5/net/UdpHeader;", "", "sourcePort", "", "destinationPort", "length", "<init>", "(III)V", "getSourcePort", "()I", "getDestinationPort", "getLength", "Companion", "app_release"}, k = 1, mv = {2, 0, 0}, xi = 48)
public final class UdpHeader {
    public static final int $stable = 0;

    /* JADX INFO: renamed from: Companion, reason: from kotlin metadata */
    public static final com.example.taxiradar.throttle.net.UdpHeader.Companion INSTANCE = new com.example.taxiradar.throttle.net.UdpHeader.Companion(null);
    private final int destinationPort;
    private final int length;
    private final int sourcePort;

    public /* synthetic */ UdpHeader(int i, int i2, int i3, kotlin.jvm.internal.DefaultConstructorMarker defaultConstructorMarker) {
        this(i, i2, i3);
    }

    private UdpHeader(int i, int i2, int i3) {
        this.sourcePort = i;
        this.destinationPort = i2;
        this.length = i3;
    }

    public final int getSourcePort() {
        return this.sourcePort;
    }

    public final int getDestinationPort() {
        return this.destinationPort;
    }

    public final int getLength() {
        return this.length;
    }

    /* JADX INFO: compiled from: UdpHeader.kt */
    @kotlin.Metadata(d1 = {"\u0000 \n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u0012\n\u0000\n\u0002\u0010\b\n\u0002\b\u000b\b\u0086\u0003\u0018\u00002\u00020\u0001B\t\b\u0002Вў\u0006\u0004\b\u0002\u0010\u0003J \u0010\u0004\u001a\u0004\u0018\u00010\u00052\u0006\u0010\u0006\u001a\u00020\u00072\u0006\u0010\b\u001a\u00020\t2\u0006\u0010\n\u001a\u00020\tJN\u0010\u000b\u001a\u00020\t2\u0006\u0010\f\u001a\u00020\u00072\u0006\u0010\b\u001a\u00020\t2\u0006\u0010\r\u001a\u00020\u00072\u0006\u0010\u000e\u001a\u00020\u00072\u0006\u0010\u000f\u001a\u00020\t2\u0006\u0010\u0010\u001a\u00020\t2\u0006\u0010\u0011\u001a\u00020\u00072\u0006\u0010\u0012\u001a\u00020\t2\u0006\u0010\u0013\u001a\u00020\tВЁ\u0006\u0014"}, d2 = {"Lotmena/v5/net/UdpHeader$Companion;", "", "<init>", "()V", "parse", "Lotmena/v5/net/UdpHeader;", "data", "", "offset", "", "length", "write", "out", "sourceAddress", "destinationAddress", "sourcePort", "destinationPort", "payload", "payloadOffset", "payloadLength", "app_release"}, k = 1, mv = {2, 0, 0}, xi = 48)
    public static final class Companion {
        public /* synthetic */ Companion(kotlin.jvm.internal.DefaultConstructorMarker defaultConstructorMarker) {
            this();
        }

        private Companion() {
        }

        public final com.example.taxiradar.throttle.net.UdpHeader parse(byte[] data, int offset, int length) {
            kotlin.jvm.internal.Intrinsics.checkNotNullParameter(data, "data");
            kotlin.jvm.internal.DefaultConstructorMarker defaultConstructorMarker = null;
            if (length < 8) {
                return null;
            }
            return new com.example.taxiradar.throttle.net.UdpHeader(((data[offset] & 255) << 8) | (data[offset + 1] & 255), ((data[offset + 2] & 255) << 8) | (data[offset + 3] & 255), (data[offset + 5] & 255) | ((data[offset + 4] & 255) << 8), defaultConstructorMarker);
        }

        public final int write(byte[] out, int offset, byte[] sourceAddress, byte[] destinationAddress, int sourcePort, int destinationPort, byte[] payload, int payloadOffset, int payloadLength) {
            kotlin.jvm.internal.Intrinsics.checkNotNullParameter(out, "out");
            kotlin.jvm.internal.Intrinsics.checkNotNullParameter(sourceAddress, "sourceAddress");
            kotlin.jvm.internal.Intrinsics.checkNotNullParameter(destinationAddress, "destinationAddress");
            kotlin.jvm.internal.Intrinsics.checkNotNullParameter(payload, "payload");
            int i = payloadLength + 8;
            out[offset] = (byte) (sourcePort >> 8);
            out[offset + 1] = (byte) sourcePort;
            out[offset + 2] = (byte) (destinationPort >> 8);
            out[offset + 3] = (byte) destinationPort;
            out[offset + 4] = (byte) (i >> 8);
            out[offset + 5] = (byte) i;
            out[offset + 6] = 0;
            out[offset + 7] = 0;
            java.lang.System.arraycopy(payload, payloadOffset, out, offset + 8, payloadLength);
            return i;
        }
    }
}

