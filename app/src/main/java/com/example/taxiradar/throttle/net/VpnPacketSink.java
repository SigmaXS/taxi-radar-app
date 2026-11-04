package com.example.taxiradar.throttle.net;

/* JADX INFO: compiled from: VpnPacketSink.kt */
/* JADX INFO: loaded from: classes2.dex */
@kotlin.Metadata(d1 = {"\u0000,\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010\u000b\n\u0000\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u0002\n\u0000\n\u0002\u0010\u0012\n\u0000\n\u0002\u0010\b\n\u0000\b`\u0018\u00002\u00020\u0001J\u0010\u0010\u0002\u001a\u00020\u00032\u0006\u0010\u0004\u001a\u00020\u0005H&J\u0010\u0010\u0002\u001a\u00020\u00032\u0006\u0010\u0004\u001a\u00020\u0006H&J\u0018\u0010\u0007\u001a\u00020\b2\u0006\u0010\t\u001a\u00020\n2\u0006\u0010\u000b\u001a\u00020\fH&ВЁ\u0006\r"}, d2 = {"Lotmena/v5/net/VpnPacketSink;", "", "protectSocket", "", "socket", "Ljava/net/Socket;", "Ljava/net/DatagramSocket;", "writePacket", "", "buffer", "", "length", "", "app_release"}, k = 1, mv = {2, 0, 0}, xi = 48)
public interface VpnPacketSink {
    boolean protectSocket(java.net.DatagramSocket socket);

    boolean protectSocket(java.net.Socket socket);

    void writePacket(byte[] buffer, int length);
}

