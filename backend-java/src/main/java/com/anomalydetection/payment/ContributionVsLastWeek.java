package com.anomalydetection.payment;

import org.springframework.jdbc.core.JdbcTemplate;

import java.util.*;

import static com.anomalydetection.payment.Dim.*;

/** All 63 cells, current window contribution share vs strictly last week (W-1)
 * at the same slot -- not the 4-week average Stage 2 uses. Two SQL queries
 * (current window, week=1), each grouped by the full 7-dim cell -- a
 * deliberately simpler baseline than Stage 2's. */
public class ContributionVsLastWeek {

    private static final List<Dim> DIMS =
            List.of(NETWORK, GEOGRAPHY, ENTRY_MODE, PURCHASE_TYPE, AUTH_TYPE, CHANNEL, DECLINE_CODE);

    public static Map<String, Object> run(JdbcTemplate jdbc, int currentDay, int currentHour) {
        long startNs = System.nanoTime();
        List<String> cols = DIMS.stream().map(Dim::label).toList();

        List<Map<String, Object>> currentRows = PaymentDb.currentWindow(jdbc, cols, currentDay, currentHour);
        Map<List<String>, PaymentDb.Agg> current = new LinkedHashMap<>();
        for (Map<String, Object> row : currentRows) current.put(PaymentDb.keyOf(row, cols), PaymentDb.aggOf(row));
        long totalCurrentDeclines = Math.max(current.values().stream().mapToLong(PaymentDb.Agg::declines).sum(), 1);

        List<Map<String, Object>> lastWeekRows = PaymentDb.singleWindow(jdbc, cols, 1, currentDay, currentHour);
        Map<List<String>, PaymentDb.Agg> lastWeek = new LinkedHashMap<>();
        for (Map<String, Object> row : lastWeekRows) lastWeek.put(PaymentDb.keyOf(row, cols), PaymentDb.aggOf(row));
        long totalLastWeekDeclines = Math.max(lastWeek.values().stream().mapToLong(PaymentDb.Agg::declines).sum(), 1);

        List<Map<String, Object>> cells = new ArrayList<>();
        for (Map.Entry<List<String>, PaymentDb.Agg> e : current.entrySet()) {
            List<String> key = e.getKey();
            PaymentDb.Agg curAgg = e.getValue();
            PaymentDb.Agg lwAgg = lastWeek.get(key);

            double currentPct = curAgg.declines() / (double) totalCurrentDeclines;
            Double lastWeekPct = lwAgg == null ? null : lwAgg.declines() / (double) totalLastWeekDeclines;
            Double deltaPct = lastWeekPct == null ? null : currentPct - lastWeekPct;

            Map<String, Object> m = new LinkedHashMap<>();
            m.put("network", key.get(0)); m.put("geography", key.get(1)); m.put("entry_mode", key.get(2));
            m.put("purchase_type", key.get(3)); m.put("auth_type", key.get(4));
            m.put("channel", key.get(5)); m.put("decline_code", key.get(6));
            m.put("cell_short", key.get(0) + "×" + key.get(1) + "×" + key.get(6));
            m.put("decline_count", curAgg.declines());
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
