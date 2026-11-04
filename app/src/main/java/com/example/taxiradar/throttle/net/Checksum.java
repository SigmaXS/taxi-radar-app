package com.example.taxiradar.throttle.net;

/* JADX INFO: compiled from: Checksum.kt */
/* JADX INFO: loaded from: classes2.dex */
@kotlin.Metadata(d1 = {"\u0000\"\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0003\n\u0002\u0010\b\n\u0000\n\u0002\u0010\u0012\n\u0002\b\u0003\n\u0002\u0010\t\n\u0002\b\u0003\bГЃ\u0002\u0018\u00002\u00020\u0001B\t\b\u0002Вў\u0006\u0004\b\u0002\u0010\u0003J(\u0010\u0004\u001a\u00020\u00052\u0006\u0010\u0006\u001a\u00020\u00072\u0006\u0010\b\u001a\u00020\u00052\u0006\u0010\t\u001a\u00020\u00052\b\b\u0002\u0010\n\u001a\u00020\u000bJ\u000e\u0010\f\u001a\u00020\u000b2\u0006\u0010\r\u001a\u00020\u0007ВЁ\u0006\u000e"}, d2 = {"Lotmena/v5/net/Checksum;", "", "<init>", "()V", "compute", "", "data", "", "offset", "length", "initialSum", "", "sumOfAddress", "addr", "app_release"}, k = 1, mv = {2, 0, 0}, xi = 48)
public final class Checksum {
    public static final int $stable = 0;
    public static final com.example.taxiradar.throttle.net.Checksum INSTANCE = new com.example.taxiradar.throttle.net.Checksum();

    private Checksum() {
    }

    public static /* synthetic */ int compute$default(com.example.taxiradar.throttle.net.Checksum checksum, byte[] bArr, int i, int i2, long j, int i3, java.lang.Object obj) {
        if ((i3 & 8) != 0) {
            j = 0;
        }
        return checksum.compute(bArr, i, i2, j);
    }

    public final int compute(byte[] data, int offset, int length, long initialSum) {
        kotlin.jvm.internal.Intrinsics.checkNotNullParameter(data, "data");
        int i = length + offset;
        while (true) {
            int i2 = offset + 1;
            if (i2 >= i) {
                break;
            }
            initialSum += (long) ((data[i2] & 255) | ((data[offset] & 255) << 8));
            offset += 2;
        }
        if (offset < i) {
            initialSum += (long) ((data[offset] & 255) << 8);
        }
        while (true) {
            long j = initialSum >> 16;
            if (j == 0) {
                return (int) ((~initialSum) & okhttp3.internal.ws.WebSocketProtocol.PAYLOAD_SHORT_MAX);
            }
            initialSum = (initialSum & okhttp3.internal.ws.WebSocketProtocol.PAYLOAD_SHORT_MAX) + j;
        }
    }

    public final long sumOfAddress(byte[] addr) {
        kotlin.jvm.internal.Intrinsics.checkNotNullParameter(addr, "addr");
        return ((long) (((addr[0] & 255) << 8) | (addr[1] & 255))) + ((long) ((addr[3] & 255) | ((addr[2] & 255) << 8)));
    }
}

