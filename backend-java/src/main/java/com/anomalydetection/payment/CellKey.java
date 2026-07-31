package com.anomalydetection.payment;

public record CellKey(String network, String geography, String entryMode, String purchaseType,
                       String authType, String channel, String declineCode) {
    public static CellKey of(PaymentRow r) {
        return new CellKey(r.network, r.geography, r.entryMode, r.purchaseType, r.authType, r.channel, r.declineCode);
    }
}
