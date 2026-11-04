package com.example.taxiradar.throttle.net;

/* JADX INFO: compiled from: Ipv4Header.kt */
/* JADX INFO: loaded from: classes2.dex */
@kotlin.Metadata(d1 = {"\u0000\u001a\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010\b\n\u0002\b\u0004\n\u0002\u0010\u0012\n\u0002\b\r\b\u0001\u0018\u0000 \u00142\u00020\u0001:\u0001\u0014B9\b\u0002\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0003\u0012\u0006\u0010\u0005\u001a\u00020\u0003\u0012\u0006\u0010\u0006\u001a\u00020\u0003\u0012\u0006\u0010\u0007\u001a\u00020\b\u0012\u0006\u0010\t\u001a\u00020\bВў\u0006\u0004\b\n\u0010\u000bR\u0011\u0010\u0002\u001a\u00020\u0003Вў\u0006\b\n\u0000\u001a\u0004\b\f\u0010\rR\u0011\u0010\u0004\u001a\u00020\u0003Вў\u0006\b\n\u0000\u001a\u0004\b\u000e\u0010\rR\u0011\u0010\u0005\u001a\u00020\u0003Вў\u0006\b\n\u0000\u001a\u0004\b\u000f\u0010\rR\u0011\u0010\u0006\u001a\u00020\u0003Вў\u0006\b\n\u0000\u001a\u0004\b\u0010\u0010\rR\u0011\u0010\u0007\u001a\u00020\bВў\u0006\b\n\u0000\u001a\u0004\b\u0011\u0010\u0012R\u0011\u0010\t\u001a\u00020\bВў\u0006\b\n\u0000\u001a\u0004\b\u0013\u0010\u0012ВЁ\u0006\u0015"}, d2 = {"Lotmena/v5/net/Ipv4Header;", "", "version", "", "headerLength", "totalLength", "protocol", "sourceAddress", "", "destinationAddress", "<init>", "(IIII[B[B)V", "getVersion", "()I", "getHeaderLength", "getTotalLength", "getProtocol", "getSourceAddress", "()[B", "getDestinationAddress", "Companion", "app_release"}, k = 1, mv = {2, 0, 0}, xi = 48)
public final class Ipv4Header {
    private final byte[] destinationAddress;
    private final int headerLength;
    private final int protocol;
    private final byte[] sourceAddress;
    private final int totalLength;
    private final int version;

    /* JADX INFO: renamed from: Companion, reason: from kotlin metadata */
    public static final com.example.taxiradar.throttle.net.Ipv4Header.Companion INSTANCE = new com.example.taxiradar.throttle.net.Ipv4Header.Companion(null);
    public static final int $stable = 8;

    public /* synthetic */ Ipv4Header(int i, int i2, int i3, int i4, byte[] bArr, byte[] bArr2, kotlin.jvm.internal.DefaultConstructorMarker defaultConstructorMarker) {
        this(i, i2, i3, i4, bArr, bArr2);
    }

    private Ipv4Header(int i, int i2, int i3, int i4, byte[] bArr, byte[] bArr2) {
        this.version = i;
        this.headerLength = i2;
        this.totalLength = i3;
        this.protocol = i4;
        this.sourceAddress = bArr;
        this.destinationAddress = bArr2;
    }

    public final int getVersion() {
        return this.version;
    }

    public final int getHeaderLength() {
        return this.headerLength;
    }

    public final int getTotalLength() {
        return this.totalLength;
    }

    public final int getProtocol() {
        return this.protocol;
    }

    public final byte[] getSourceAddress() {
        return this.sourceAddress;
    }

    public final byte[] getDestinationAddress() {
        return this.destinationAddress;
    }

    /* JADX INFO: compiled from: Ipv4Header.kt */
    @kotlin.Metadata(d1 = {"\u0000&\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u0012\n\u0000\n\u0002\u0010\b\n\u0000\n\u0002\u0010\u0002\n\u0002\b\u0007\b\u0086\u0003\u0018\u00002\u00020\u0001B\t\b\u0002Вў\u0006\u0004\b\u0002\u0010\u0003J\u0018\u0010\u0004\u001a\u0004\u0018\u00010\u00052\u0006\u0010\u0006\u001a\u00020\u00072\u0006\u0010\b\u001a\u00020\tJ6\u0010\n\u001a\u00020\u000b2\u0006\u0010\f\u001a\u00020\u00072\u0006\u0010\r\u001a\u00020\t2\u0006\u0010\u000e\u001a\u00020\t2\u0006\u0010\u000f\u001a\u00020\u00072\u0006\u0010\u0010\u001a\u00020\u00072\u0006\u0010\u0011\u001a\u00020\tВЁ\u0006\u0012"}, d2 = {"Lotmena/v5/net/Ipv4Header$Companion;", "", "<init>", "()V", "parse", "Lotmena/v5/net/Ipv4Header;", "data", "", "length", "", "write", "", "out", "offset", "protocol", "sourceAddress", "destinationAddress", "payloadLength", "app_release"}, k = 1, mv = {2, 0, 0}, xi = 48)
    public static final class Companion {
        public /* synthetic */ Companion(kotlin.jvm.internal.DefaultConstructorMarker defaultConstructorMarker) {
            this();
        }

        private Companion() {
        }

        public final com.example.taxiradar.throttle.net.Ipv4Header parse(byte[] data, int length) {
            int i;
            kotlin.jvm.internal.Intrinsics.checkNotNullParameter(data, "data");
            if (length < 20) {
                return null;
            }
            int i2 = data[0];
            int i3 = (i2 & 255) >> 4;
            if (i3 == 4 && (i = (i2 & 15) * 4) >= 20 && i <= length) {
                return new com.example.taxiradar.throttle.net.Ipv4Header(i3, i, ((data[2] & 255) << 8) | (data[3] & 255), data[9] & 255, kotlin.collections.ArraysKt.copyOfRange(data, 12, 16), kotlin.collections.ArraysKt.copyOfRange(data, 16, 20), null);
            }
            return null;
        }

        public final void write(byte[] out, int offset, int protocol, byte[] sourceAddress, byte[] destinationAddress, int payloadLength) {
            kotlin.jvm.internal.Intrinsics.checkNotNullParameter(out, "out");
            kotlin.jvm.internal.Intrinsics.checkNotNullParameter(sourceAddress, "sourceAddress");
            kotlin.jvm.internal.Intrinsics.checkNotNullParameter(destinationAddress, "destinationAddress");
            int i = payloadLength + 20;
            out[offset] = 69;
            out[offset + 1] = 0;
            out[offset + 2] = (byte) (i >> 8);
            out[offset + 3] = (byte) i;
            out[offset + 4] = 0;
            out[offset + 5] = 0;
            out[offset + 6] = 64;
            out[offset + 7] = 0;
            out[offset + 8] = 64;
            out[offset + 9] = (byte) protocol;
            int i2 = offset + 10;
            out[i2] = 0;
            int i3 = offset + 11;
            out[i3] = 0;
            java.lang.System.arraycopy(sourceAddress, 0, out, offset + 12, 4);
            java.lang.System.arraycopy(destinationAddress, 0, out, offset + 16, 4);
            int iCompute$default = com.example.taxiradar.throttle.net.Checksum.compute$default(com.example.taxiradar.throttle.net.Checksum.INSTANCE, out, offset, 20, 0L, 8, null);
            out[i2] = (byte) (iCompute$default >> 8);
            out[i3] = (byte) iCompute$default;
        }
    }
}

