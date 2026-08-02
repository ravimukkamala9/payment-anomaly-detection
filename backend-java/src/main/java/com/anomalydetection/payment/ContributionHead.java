package com.anomalydetection.payment;

import java.util.*;
import java.util.function.Function;

import static com.anomalydetection.payment.RowAggregation.*;

/** Generalized Stage 2 -- the exact same contribution-shift math (two queries,
 * then a downstream contribution-share z-score), but parameterized by an
 * arbitrary key subset instead of the fixed 7-dim CellKey. This is what lets
 * "Stage 2" become multiple independently-owned heads, each over its own
 * smaller dimension subset, without duplicating the math. */
public class ContributionHead {

    public static Map<String, Object> run(List<PaymentRow> df, int currentDay, int currentHour, double threshold,
                                           Function<PaymentRow, List<String>> keyFn, List<String> keyLabels) {
        long startNs = System.nanoTime();

        // Query 1 -- current window, one row per active cell in this head's key
        Map<List<String>, Agg> current = groupByKey(currentWindow(df, currentDay, currentHour), keyFn);
        long totalCurrentDeclines = current.values().stream().mapToLong(a -> a.declines).sum();
        long totalCurrentDeclinesSafe = Math.max(totalCurrentDeclines, 1);

        // Query 2 -- prior 4 weeks, same slot, per (week, cell)
        Map<Integer, Map<List<String>, Agg>> historical = groupByWeekThenKey(historicalWindow(df, currentDay, currentHour), keyFn);

        // Downstream: each week's total declines, then per-cell contribution share history
        Map<List<String>, List<Double>> historicalContribution = new LinkedHashMap<>();
        historical.forEach((week, byKey) -> {
            long weekTotal = Math.max(byKey.values().stream().mapToLong(a -> a.declines).sum(), 1);
            byKey.forEach((key, agg) -> historicalContribution
                    .computeIfAbsent(key, k -> new ArrayList<>())
                    .add(agg.declines / (double) weekTotal));
        });

        List<Map<String, Object>> merged = new ArrayList<>();
        for (Map.Entry<List<String>, Agg> e : current.entrySet()) {
            List<String> key = e.getKey();
            Agg agg = e.getValue();
            double contributionPct = agg.declines / (double) totalCurrentDeclinesSafe;
            List<Double> history = historicalContribution.getOrDefault(key, List.of());
            boolean isNewCell = history.isEmpty();
            double z = WowMath.zScore(contributionPct, history, 1e-6);
            boolean alerted = Math.abs(z) >= threshold || isNewCell;

            Map<String, Object> m = new LinkedHashMap<>();
            for (int i = 0; i < keyLabels.size(); i++) m.put(keyLabels.get(i), key.get(i));
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
            List<String> parts = new ArrayList<>();
            for (String label : keyLabels) parts.add(String.valueOf(r.get(label)));
            Map<String, Object> c = new LinkedHashMap<>();
            c.put("label", parts.get(parts.size() - 1));
            c.put("cell_short", String.join("×", parts));
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
            for (String label : keyLabels) d.put(label, r.get(label));
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
