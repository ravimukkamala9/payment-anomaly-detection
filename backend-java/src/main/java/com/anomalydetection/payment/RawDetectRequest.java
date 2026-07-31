package com.anomalydetection.payment;

/** Filters narrow the raw dataset down to a slice worth scoring; method picks
 * which detector runs on that slice's [total_count, decline_count, decline_rate]
 * feature vector. All filter fields are optional -- null means "any". */
public class RawDetectRequest {
    public Integer week;
    public Integer dayOfWeek;
    public Integer hour;
    public String network;
    public String geography;
    public String entryMode;
    public String purchaseType;
    public String authType;
    public String channel;
    public String declineCode;

    public String method = "zscore"; // "zscore" | "isolation_forest"
    public double threshold = 3.0;
    public double contamination = 0.1;
}
