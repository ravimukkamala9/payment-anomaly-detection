package com.anomalydetection.payment;

import java.util.*;

/** On-demand only -- never runs continuously, unlike Stage 1/2/3. Given the
 * exact 7-dimension CellKey of a cell that a stage has already flagged,
 * groups that exact slice's raw rows by diagnostic dimensions (bin,
 * acquirer) that were never part of the monitored CellKey. This is how a
 * new business-requested field (a new diagnostic column here) avoids ever
 * becoming a new standing monitor: it only costs a query, run only against
 * an already-flagged cell, not every cell every window. */
public class DrillDown {

    public static Map<String, Object> run(List<PaymentRow> df, int week, int dayOfWeek, int hour,
                                           String network, String geography, String entryMode,
                                           String purchaseType, String authType, String channel, String declineCode) {
        long startNs = System.nanoTime();

        List<PaymentRow> slice = df.stream().filter(r ->
                r.week == week && r.dayOfWeek == dayOfWeek && r.hour == hour &&
                r.network.equals(network) && r.geography.equals(geography) && r.entryMode.equals(entryMode) &&
                r.purchaseType.equals(purchaseType) && r.authType.equals(authType) &&
                r.channel.equals(channel) && r.declineCode.equals(declineCode)
        ).toList();

        record Key(String bin, String acquirer) {}
        Map<Key, long[]> agg = new LinkedHashMap<>(); // [total, declines]
        long totalDeclines = 0;
        for (PaymentRow r : slice) {
            Key k = new Key(r.bin, r.acquirer);
            long[] a = agg.computeIfAbsent(k, kk -> new long[2]);
            a[0] += r.totalCount;
            a[1] += r.declineCount;
            totalDeclines += r.declineCount;
        }
        long totalDeclinesSafe = Math.max(totalDeclines, 1);

        List<Map<String, Object>> breakdown = new ArrayList<>();
        for (Map.Entry<Key, long[]> e : agg.entrySet()) {
            long[] a = e.getValue();
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("bin", e.getKey().bin());
            m.put("acquirer", e.getKey().acquirer());
            m.put("total_count", a[0]);
            m.put("decline_count", a[1]);
            m.put("decline_rate", WowMath.round(a[1] / (double) Math.max(a[0], 1), 6));
            m.put("share_of_cell_declines", WowMath.round(a[1] / (double) totalDeclinesSafe * 100, 2));
            breakdown.add(m);
        }
        breakdown.sort((a, b) -> Long.compare((Long) b.get("decline_count"), (Long) a.get("decline_count")));

        double execMs = (System.nanoTime() - startNs) / 1e6;

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("network", network);
        result.put("geography", geography);
        result.put("entry_mode", entryMode);
        result.put("purchase_type", purchaseType);
        result.put("auth_type", authType);
        result.put("channel", channel);
        result.put("decline_code", declineCode);
        result.put("n_combinations", breakdown.size());
        result.put("total_declines", totalDeclines);
        result.put("execution_ms", WowMath.round(execMs, 2));
        result.put("breakdown", breakdown);
        return result;
    }
}
