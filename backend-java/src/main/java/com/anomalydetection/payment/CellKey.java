package com.anomalydetection.payment;

import java.util.Objects;

public class CellKey {
    public final String network, geography, entryMode, purchaseType, authType, channel, declineCode;

    public CellKey(String network, String geography, String entryMode, String purchaseType,
                   String authType, String channel, String declineCode) {
        this.network = network; this.geography = geography; this.entryMode = entryMode;
        this.purchaseType = purchaseType; this.authType = authType; this.channel = channel;
        this.declineCode = declineCode;
    }

    public static CellKey of(PaymentRow r) {
        return new CellKey(r.network, r.geography, r.entryMode, r.purchaseType, r.authType, r.channel, r.declineCode);
    }

    @Override public boolean equals(Object o) {
        if (!(o instanceof CellKey)) return false;
        CellKey k = (CellKey) o;
        return network.equals(k.network) && geography.equals(k.geography) && entryMode.equals(k.entryMode)
                && purchaseType.equals(k.purchaseType) && authType.equals(k.authType)
                && channel.equals(k.channel) && declineCode.equals(k.declineCode);
    }

    @Override public int hashCode() {
        return Objects.hash(network, geography, entryMode, purchaseType, authType, channel, declineCode);
    }
}
