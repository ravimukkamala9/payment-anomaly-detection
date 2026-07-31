package com.anomalydetection.payment;

import java.util.*;

import static com.anomalydetection.payment.RowAggregation.*;

/** All 63 cells, current window contribution share vs strictly last week (W-1)
 * at the same slot -- not the 4-week average Stage 2 uses. A one-point
 * comparison, deliberately simpler than Stage 2's baseline. */
public class ContributionVsLastWeek {

    public static Map<String, Object> run(List<PaymentRow> df, int currentDay, int currentHour) {
        long startNs = System.nanoTime();

        Map<CellKey, Agg> current = groupByKey(currentWindow(df, currentDay, currentHour), CellKey::of);
        long totalCurrentDeclines = Math.max(current.values().stream().mapToLong(a -> a.declines).sum(), 1);

        Map<CellKey, Agg> lastWeek = groupByKey(
                df.stream().filter(r -> r.week == 1 && r.dayOfWeek == currentDay && r.hour == currentHour),
                CellKey::of);
        long totalLastWeekDeclines = Math.max(lastWeek.values().stream().mapToLong(a -> a.declines).sum(), 1);

        List<Map<String, Object>> cells = new ArrayList<>();
        for (Map.Entry<CellKey, Agg> e : current.entrySet()) {
            CellKey key = e.getKey();
            Agg curAgg = e.getValue();
            Agg lwAgg = lastWeek.get(key);

            double currentPct = curAgg.declines / (double) totalCurrentDeclines;
            Double lastWeekPct = lwAgg == null ? null : lwAgg.declines / (double) totalLastWeekDeclines;
            Double deltaPct = lastWeekPct == null ? null : currentPct - lastWeekPct;

            Map<String, Object> m = new LinkedHashMap<>();
            m.put("network", key.network()); m.put("geography", key.geography()); m.put("entry_mode", key.entryMode());
            m.put("purchase_type", key.purchaseType()); m.put("auth_type", key.authType());
            m.put("channel", key.channel()); m.put("decline_code", key.declineCode());
            m.put("cell_short", key.network() + "×" + key.geography() + "×" + key.declineCode());
            m.put("decline_count", curAgg.declines);
            m.put("contribution_pct", WowMath.round(currentPct * 100, 3));
            m.put("last_week_contribution_pct", lastWeekPct == null ? null : WowMath.round(lastWeekPct * 100, 3));
            m.put("delta_pct", deltaPct == null ? null : WowMath.round(deltaPct * 100, 3));
            m.put("is_new_vs_last_week", lwAgg == null);
            cells.add(m);
        }

        cells.sort((x, y) -> Double.compare((Double) y.get("contribution_pct"), (Double) x.get("contribution_pct")));

        double execMs = (System.nanoTime() - startNs) / 1e6;

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("name", "Contribution Shift — All Cells vs Last Week");
        result.put("description", "Every active cell's share of total declines this window vs strictly last week (W-1), same Mon 14:00 slot.");
        result.put("n_cells", cells.size());
        result.put("total_declines_current", totalCurrentDeclines);
        result.put("total_declines_last_week", totalLastWeekDeclines);
        result.put("execution_ms", WowMath.round(execMs, 2));
        result.put("cells", cells);
        return result;
    }
}
