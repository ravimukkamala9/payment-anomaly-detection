package com.anomalydetection.payment;

public class PaymentRow {
    public int week;
    public int dayOfWeek;
    public int hour;
    public String network, geography, entryMode, purchaseType, authType, channel, declineCode;
    public int totalCount;
    public int declineCount;
    public double declineRate;

    /** Diagnostic-only dimensions -- never part of CellKey, never grouped on by
     * Stage 1/2/3. They exist only to be queried by /api/payment/drill-down
     * once a core-key cell has already flagged, so that a business team asking
     * for a new field becomes a query-column addition, not a new standing
     * monitor. */
    public String bin;
    public String acquirer;

    public PaymentRow(int week, int dayOfWeek, int hour, String network, String geography, String entryMode,
                       String purchaseType, String authType, String channel, String declineCode,
                       int totalCount, int declineCount, double declineRate, String bin, String acquirer) {
        this.week = week;
        this.dayOfWeek = dayOfWeek;
        this.hour = hour;
        this.network = network;
        this.geography = geography;
        this.entryMode = entryMode;
        this.purchaseType = purchaseType;
        this.authType = authType;
        this.channel = channel;
        this.declineCode = declineCode;
        this.totalCount = totalCount;
        this.declineCount = declineCount;
        this.declineRate = declineRate;
        this.bin = bin;
        this.acquirer = acquirer;
    }

    public String cellKey() {
        return network + "|" + geography + "|" + entryMode + "|" + purchaseType + "|" + authType + "|" + channel + "|" + declineCode;
    }
}
