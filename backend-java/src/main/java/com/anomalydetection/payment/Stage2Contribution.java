package com.anomalydetection.payment;

import java.util.*;

/** Port of stages/stage2_contribution.py. Thin wrapper around ContributionHead
 * keyed on the full 7-dim CellKey -- the original, always-on Stage 2 monitor.
 * See Stage2Heads.java for the same math split across smaller, independently
 * owned dimension subsets. */
public class Stage2Contribution {

    private static final List<String> KEY_LABELS = List.of(
            "network", "geography", "entry_mode", "purchase_type", "auth_type", "channel", "decline_code");

    public static Map<String, Object> run(List<PaymentRow> df, int currentDay, int currentHour, double threshold) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("stage", 2);
        result.put("name", "Contribution Shift Monitor");
        result.put("description", "Single computation — no pre-defined monitors. Each active cell's share of total declines vs same window weeks −1..−4. Self-discovers new cell combinations.");
        result.putAll(ContributionHead.run(df, currentDay, currentHour, threshold,
                r -> List.of(r.network, r.geography, r.entryMode, r.purchaseType, r.authType, r.channel, r.declineCode),
                KEY_LABELS));
        return result;
    }
}
