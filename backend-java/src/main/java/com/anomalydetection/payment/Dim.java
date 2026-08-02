package com.anomalydetection.payment;

/** One monitorable dimension. The label doubles as the payment_declines
 * column name, so choosing dimensions is choosing both a GROUP BY clause and
 * the JSON field names in the response -- the two can't drift apart. */
public record Dim(String label) {

    public static final Dim NETWORK       = new Dim("network");
    public static final Dim GEOGRAPHY     = new Dim("geography");
    public static final Dim ENTRY_MODE    = new Dim("entry_mode");
    public static final Dim PURCHASE_TYPE = new Dim("purchase_type");
    public static final Dim AUTH_TYPE     = new Dim("auth_type");
    public static final Dim CHANNEL       = new Dim("channel");
    public static final Dim DECLINE_CODE  = new Dim("decline_code");

    /** Diagnostic dimensions -- see DrillDown. Monitored only by the
     * Acquiring & Risk head in Stage2Heads. */
    public static final Dim BIN      = new Dim("bin");
    public static final Dim ACQUIRER = new Dim("acquirer");
}
