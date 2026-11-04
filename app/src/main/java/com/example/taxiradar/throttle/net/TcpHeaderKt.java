package com.example.taxiradar.throttle.net;

/* JADX INFO: compiled from: TcpHeader.kt */
/* JADX INFO: loaded from: classes2.dex */
@kotlin.Metadata(d1 = {"\u0000\u0012\n\u0000\n\u0002\u0010\b\n\u0002\b\u0006\n\u0002\u0010\u0012\n\u0002\b\u0006\u001a8\u0010\u0006\u001a\u00020\u00012\u0006\u0010\u0007\u001a\u00020\b2\u0006\u0010\t\u001a\u00020\u00012\u0006\u0010\n\u001a\u00020\u00012\u0006\u0010\u000b\u001a\u00020\b2\u0006\u0010\f\u001a\u00020\b2\u0006\u0010\r\u001a\u00020\u0001H\u0000\"\u000e\u0010\u0000\u001a\u00020\u0001X\u0080TВў\u0006\u0002\n\u0000\"\u000e\u0010\u0002\u001a\u00020\u0001X\u0080TВў\u0006\u0002\n\u0000\"\u000e\u0010\u0003\u001a\u00020\u0001X\u0080TВў\u0006\u0002\n\u0000\"\u000e\u0010\u0004\u001a\u00020\u0001X\u0080TВў\u0006\u0002\n\u0000\"\u000e\u0010\u0005\u001a\u00020\u0001X\u0080TВў\u0006\u0002\n\u0000ВЁ\u0006\u000e"}, d2 = {"TCP_FLAG_FIN", "", "TCP_FLAG_SYN", "TCP_FLAG_RST", "TCP_FLAG_PSH", "TCP_FLAG_ACK", "pseudoHeaderChecksum", "segment", "", "segmentOffset", "segmentLength", "sourceAddress", "destinationAddress", "protocol", "app_release"}, k = 2, mv = {2, 0, 0}, xi = 48)
public final class TcpHeaderKt {
    public static final int TCP_FLAG_ACK = 16;
    public static final int TCP_FLAG_FIN = 1;
    public static final int TCP_FLAG_PSH = 8;
    public static final int TCP_FLAG_RST = 4;
    public static final int TCP_FLAG_SYN = 2;

    public static final int pseudoHeaderChecksum(byte[] segment, int i, int i2, byte[] sourceAddress, byte[] destinationAddress, int i3) {
        kotlin.jvm.internal.Intrinsics.checkNotNullParameter(segment, "segment");
        kotlin.jvm.internal.Intrinsics.checkNotNullParameter(sourceAddress, "sourceAddress");
        kotlin.jvm.internal.Intrinsics.checkNotNullParameter(destinationAddress, "destinationAddress");
        return com.example.taxiradar.throttle.net.Checksum.INSTANCE.compute(segment, i, i2, com.example.taxiradar.throttle.net.Checksum.INSTANCE.sumOfAddress(sourceAddress) + com.example.taxiradar.throttle.net.Checksum.INSTANCE.sumOfAddress(destinationAddress) + ((long) i3) + ((long) i2));
    }
}

