package com.anomalydetection.payment;

import java.util.*;

/** Three independently-owned contribution-shift heads, each over a smaller
 * dimension subset than the full 7-dim Stage 2 CellKey. decline_code is
 * shared across every head -- it's the "why," and contribution share is
 * meaningless without it. This is the fix for cell-count growth as more
 * features get requested: instead of one monitor whose cardinality is the
 * cross-product of every dimension (250k+ cells at 12-15 real features),
 * each head stays small and dense, owned by the team that can act on it. */
public class Stage2Heads {

    public static Map<String, Object> run(List<PaymentRow> df, int currentDay, int currentHour, double threshold) {
        Map<String, Object> heads = new LinkedHashMap<>();

        heads.put("network_channel", head(df, currentDay, currentHour, threshold,
                "Network & Channel", "Network Ops",
                r -> List.of(r.network, r.channel, r.geography, r.declineCode),
                List.of("network", "channel", "geography", "decline_code")));

        heads.put("payment_method", head(df, currentDay, currentHour, threshold,
                "Payment Method", "Payment Product",
                r -> List.of(r.entryMode, r.purchaseType, r.authType, r.declineCode),
                List.of("entry_mode", "purchase_type", "auth_type", "decline_code")));

        heads.put("acquiring_risk", head(df, currentDay, currentHour, threshold,
                "Acquiring & Risk", "Risk / Fraud",
                r -> List.of(r.bin, r.acquirer, r.declineCode),
                List.of("bin", "acquirer", "decline_code")));

        return Map.of("heads", heads);
    }

    private static Map<String, Object> head(List<PaymentRow> df, int currentDay, int currentHour, double threshold,
                                             String name, String owner,
                                             java.util.function.Function<PaymentRow, List<String>> keyFn,
                                             List<String> keyLabels) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("name", name);
        result.put("owner", owner);
        result.put("dims", keyLabels);
        result.putAll(ContributionHead.run(df, currentDay, currentHour, threshold, keyFn, keyLabels));
        return result;
    }
}
