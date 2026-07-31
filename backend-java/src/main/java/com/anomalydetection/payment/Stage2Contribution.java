package com.anomalydetection.payment;

import java.util.*;

import static com.anomalydetection.payment.RowAggregation.*;

/** Port of stages/stage2_contribution.py.
 * Two queries -- current window and prior weeks, both grouped by the full
 * 7-dimension cell -- then a downstream contribution-share z-score. Share
 * is declines-in-cell over declines-in-window, so unlike Stage 1/3's rate
 * it needs each week's total as a second small step. */
public class Stage2Contribution {

    public static Map<String, Object> run(List<PaymentRow> df, int currentDay, int currentHour, double threshold) {
        long startNs = System.nanoTime();

        // Query 1 -- current window, one row per active cell
        Map<CellKey, Agg> current = groupByKey(currentWindow(df, currentDay, currentHour), CellKey::of);
        long totalCurrentDeclines = current.values().stream().mapToLong(a -> a.declines).sum();
        long totalCurrentDeclinesSafe = Math.max(totalCurrentDeclines, 1);

        // Query 2 -- prior 4 weeks, same slot, per (week, cell)
        Map<Integer, Map<CellKey, Agg>> historical = groupByWeekThenKey(historicalWindow(df, currentDay, currentHour), CellKey::of);

        // Downstream: each week's total declines, then per-cell contribution share history
        Map<CellKey, List<Double>> historicalContribution = new LinkedHashMap<>();
        historical.forEach((week, byKey) -> {
            long weekTotal = Math.max(byKey.values().stream().mapToLong(a -> a.declines).sum(), 1);
            byKey.forEach((key, agg) -> historicalContribution
                    .computeIfAbsent(key, k -> new ArrayList<>())
                    .add(agg.declines / (double) weekTotal));
        });

        List<Map<String, Object>> merged = new ArrayList<>();
        for (Map.Entry<CellKey, Agg> e : current.entrySet()) {
            CellKey key = e.getKey();
            Agg agg = e.getValue();
            double contributionPct = agg.declines / (double) totalCurrentDeclinesSafe;
            List<Double> history = historicalContribution.getOrDefault(key, List.of());
            boolean isNewCell = history.isEmpty();
            double z = WowMath.zScore(contributionPct, history, 1e-6);
            boolean alerted = Math.abs(z) >= threshold || isNewCell;

            Map<String, Object> m = new LinkedHashMap<>();
            m.put("network", key.network()); m.put("geography", key.geography()); m.put("entry_mode", key.entryMode());
            m.put("purchase_type", key.purchaseType()); m.put("auth_type", key.authType());
            m.put("channel", key.channel()); m.put("decline_code", key.declineCode());
            m.put("decline_count", agg.declines);
            m.put("total_count", agg.total);
            m.put("contribution_pct", contributionPct);
            m.put("hist_mean", isNewCell ? 0 : WowMath.mean(history));
            m.put("hist_std", isNewCell ? 0 : WowMath.std(history));
            m.put("z_contribution", z);
            m.put("is_new_cell", isNewCell);
            m.put("alerted", alerted);
            merged.add(m);
        }

        List<Map<String, Object>> alerts = new ArrayList<>(merged.stream().filter(m -> (Boolean) m.get("alerted")).toList());
        alerts.sort((x, y) -> Double.compare((Double) y.get("z_contribution"), (Double) x.get("z_contribution")));

        List<Map<String, Object>> topByContrib = new ArrayList<>(merged);
        topByContrib.sort((x, y) -> Double.compare((Double) y.get("contribution_pct"), (Double) x.get("contribution_pct")));

        List<Map<String, Object>> chartData = new ArrayList<>();
        for (Map<String, Object> r : topByContrib.subList(0, Math.min(20, topByContrib.size()))) {
            Map<String, Object> c = new LinkedHashMap<>();
            c.put("label", r.get("decline_code"));
            c.put("cell_short", r.get("network") + "×" + r.get("geography") + "×" + r.get("decline_code"));
            c.put("contribution_pct", WowMath.round((Double) r.get("contribution_pct") * 100, 3));
            c.put("hist_mean_pct", WowMath.round((Double) r.get("hist_mean") * 100, 3));
            c.put("z_contribution", WowMath.round((Double) r.get("z_contribution"), 2));
            c.put("alerted", r.get("alerted"));
            chartData.add(c);
        }

        long nNewCells = merged.stream().filter(m -> (Boolean) m.get("is_new_cell")).count();

        List<Map<String, Object>> alertRecords = new ArrayList<>();
        for (Map<String, Object> r : alerts.subList(0, Math.min(20, alerts.size()))) {
            Map<String, Object> d = new LinkedHashMap<>();
            d.put("network", r.get("network")); d.put("geography", r.get("geography"));
            d.put("entry_mode", r.get("entry_mode")); d.put("purchase_type", r.get("purchase_type"));
            d.put("auth_type", r.get("auth_type")); d.put("channel", r.get("channel"));
            d.put("decline_code", r.get("decline_code"));
            d.put("decline_count", ((Long) r.get("decline_count")).intValue());
            d.put("contribution_pct", WowMath.round((Double) r.get("contribution_pct"), 6));
            d.put("hist_mean", WowMath.round((Double) r.get("hist_mean"), 6));
            d.put("hist_std", WowMath.round((Double) r.get("hist_std"), 6));
            d.put("z_contribution", WowMath.round((Double) r.get("z_contribution"), 6));
            d.put("is_new_cell", r.get("is_new_cell"));
            alertRecords.add(d);
        }

        double execMs = (System.nanoTime() - startNs) / 1e6;

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("stage", 2);
        result.put("name", "Contribution Shift Monitor");
        result.put("description", "Single computation — no pre-defined monitors. Each active cell's share of total declines vs same window weeks −1..−4. Self-discovers new cell combinations.");
        result.put("n_active_cells", merged.size());
        result.put("n_alerts", alerts.size());
        result.put("n_new_cells", (int) nNewCells);
        result.put("total_declines_current", totalCurrentDeclines);
        result.put("threshold", threshold);
        result.put("execution_ms", WowMath.round(execMs, 2));
        result.put("chart_data", chartData);
        result.put("alerts", alertRecords);
        return result;
    }
}
