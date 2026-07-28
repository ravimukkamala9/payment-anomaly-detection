package com.anomalydetection.payment;

public class PaymentRow {
    public int week;
    public int dayOfWeek;
    public int hour;
    public String network, geography, entryMode, purchaseType, authType, channel, declineCode;
    public int totalCount;
    public int declineCount;
    public double declineRate;

    public PaymentRow(int week, int dayOfWeek, int hour, String network, String geography, String entryMode,
                       String purchaseType, String authType, String channel, String declineCode,
                       int totalCount, int declineCount, double declineRate) {
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
    }

    public String cellKey() {
        return network + "|" + geography + "|" + entryMode + "|" + purchaseType + "|" + authType + "|" + channel + "|" + declineCode;
    }
}
