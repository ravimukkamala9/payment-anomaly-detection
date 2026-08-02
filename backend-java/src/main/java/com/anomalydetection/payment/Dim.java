package com.anomalydetection.payment;

import java.util.function.Function;

/** One monitorable dimension: its JSON name and how to read it off a row.
 * Pairing the two in a single object means a head's key and its column
 * labels can never drift apart -- you pick dimensions, and both the
 * grouping and the output naming follow from that one choice. */
public record Dim(String label, Function<PaymentRow, String> get) {

    public static final Dim NETWORK       = new Dim("network",       r -> r.network);
    public static final Dim GEOGRAPHY     = new Dim("geography",     r -> r.geography);
    public static final Dim ENTRY_MODE    = new Dim("entry_mode",    r -> r.entryMode);
    public static final Dim PURCHASE_TYPE = new Dim("purchase_type", r -> r.purchaseType);
    public static final Dim AUTH_TYPE     = new Dim("auth_type",     r -> r.authType);
    public static final Dim CHANNEL       = new Dim("channel",       r -> r.channel);
    public static final Dim DECLINE_CODE  = new Dim("decline_code",  r -> r.declineCode);

    /** Diagnostic dimensions -- see DrillDown. Monitored only by the
     * Acquiring & Risk head in Stage2Heads. */
    public static final Dim BIN      = new Dim("bin",      r -> r.bin);
    public static final Dim ACQUIRER = new Dim("acquirer", r -> r.acquirer);
}
