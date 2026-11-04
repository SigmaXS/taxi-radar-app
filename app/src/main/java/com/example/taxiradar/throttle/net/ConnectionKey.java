package com.example.taxiradar.throttle.net;

/* JADX INFO: compiled from: ConnectionKey.kt */
/* JADX INFO: loaded from: classes2.dex */
@kotlin.Metadata(d1 = {"\u0000(\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010\b\n\u0000\n\u0002\u0018\u0002\n\u0002\b\t\n\u0002\u0010\u000e\n\u0002\b\u0005\n\u0002\u0010\u000b\n\u0002\b\u0003\b\u0081\b\u0018\u00002\u00020\u0001B\u001f\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0005\u0012\u0006\u0010\u0006\u001a\u00020\u0003Вў\u0006\u0004\b\u0007\u0010\bJ\b\u0010\u000e\u001a\u00020\u000fH\u0016J\t\u0010\u0010\u001a\u00020\u0003HГ†\u0003J\t\u0010\u0011\u001a\u00020\u0005HГ†\u0003J\t\u0010\u0012\u001a\u00020\u0003HГ†\u0003J'\u0010\u0013\u001a\u00020\u00002\b\b\u0002\u0010\u0002\u001a\u00020\u00032\b\b\u0002\u0010\u0004\u001a\u00020\u00052\b\b\u0002\u0010\u0006\u001a\u00020\u0003HГ†\u0001J\u0013\u0010\u0014\u001a\u00020\u00152\b\u0010\u0016\u001a\u0004\u0018\u00010\u0001HГ–\u0003J\t\u0010\u0017\u001a\u00020\u0003HГ–\u0001R\u0011\u0010\u0002\u001a\u00020\u0003Вў\u0006\b\n\u0000\u001a\u0004\b\t\u0010\nR\u0011\u0010\u0004\u001a\u00020\u0005Вў\u0006\b\n\u0000\u001a\u0004\b\u000b\u0010\fR\u0011\u0010\u0006\u001a\u00020\u0003Вў\u0006\b\n\u0000\u001a\u0004\b\r\u0010\nВЁ\u0006\u0018"}, d2 = {"Lotmena/v5/net/ConnectionKey;", "", "clientPort", "", "remoteAddress", "Ljava/net/InetAddress;", "remotePort", "<init>", "(ILjava/net/InetAddress;I)V", "getClientPort", "()I", "getRemoteAddress", "()Ljava/net/InetAddress;", "getRemotePort", "toString", "", "component1", "component2", "component3", "copy", "equals", "", "other", "hashCode", "app_release"}, k = 1, mv = {2, 0, 0}, xi = 48)
public final /* data */ class ConnectionKey {
    public static final int $stable = 8;
    private final int clientPort;
    private final java.net.InetAddress remoteAddress;
    private final int remotePort;

    public static /* synthetic */ com.example.taxiradar.throttle.net.ConnectionKey copy$default(com.example.taxiradar.throttle.net.ConnectionKey connectionKey, int i, java.net.InetAddress inetAddress, int i2, int i3, java.lang.Object obj) {
        if ((i3 & 1) != 0) {
            i = connectionKey.clientPort;
        }
        if ((i3 & 2) != 0) {
            inetAddress = connectionKey.remoteAddress;
        }
        if ((i3 & 4) != 0) {
            i2 = connectionKey.remotePort;
        }
        return connectionKey.copy(i, inetAddress, i2);
    }

    /* JADX INFO: renamed from: component1, reason: from getter */
    public final int getClientPort() {
        return this.clientPort;
    }

    /* JADX INFO: renamed from: component2, reason: from getter */
    public final java.net.InetAddress getRemoteAddress() {
        return this.remoteAddress;
    }

    /* JADX INFO: renamed from: component3, reason: from getter */
    public final int getRemotePort() {
        return this.remotePort;
    }

    public final com.example.taxiradar.throttle.net.ConnectionKey copy(int clientPort, java.net.InetAddress remoteAddress, int remotePort) {
        kotlin.jvm.internal.Intrinsics.checkNotNullParameter(remoteAddress, "remoteAddress");
        return new com.example.taxiradar.throttle.net.ConnectionKey(clientPort, remoteAddress, remotePort);
    }

    public boolean equals(java.lang.Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof com.example.taxiradar.throttle.net.ConnectionKey)) {
            return false;
        }
        com.example.taxiradar.throttle.net.ConnectionKey connectionKey = (com.example.taxiradar.throttle.net.ConnectionKey) other;
        return this.clientPort == connectionKey.clientPort && kotlin.jvm.internal.Intrinsics.areEqual(this.remoteAddress, connectionKey.remoteAddress) && this.remotePort == connectionKey.remotePort;
    }

    public int hashCode() {
        return (((java.lang.Integer.hashCode(this.clientPort) * 31) + this.remoteAddress.hashCode()) * 31) + java.lang.Integer.hashCode(this.remotePort);
    }

    public ConnectionKey(int i, java.net.InetAddress remoteAddress, int i2) {
        kotlin.jvm.internal.Intrinsics.checkNotNullParameter(remoteAddress, "remoteAddress");
        this.clientPort = i;
        this.remoteAddress = remoteAddress;
        this.remotePort = i2;
    }

    public java.lang.String toString() {
        return this.remoteAddress.getHostAddress() + ":" + this.remotePort + "<-" + this.clientPort;
    }
}

