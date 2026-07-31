package com.anomalydetection.payment;

import java.util.*;

import static com.anomalydetection.payment.RowAggregation.*;

/** Port of stages/stage3_wow_granular.py.
 * Two queries -- current window and prior weeks, both at full cell
 * granularity, no roll-up -- then a downstream per-cell rate z-score. */
public class Stage3WowGranular {

    public static Map<String, Object> run(List<PaymentRow> df, int currentDay, int currentHour, double threshold) {
        long startNs = System.nanoTime();

        // Query 1 -- current window, one row per active cell, no aggregation needed
        Map<CellKey, Agg> current = groupByKey(currentWindow(df, currentDay, currentHour), CellKey::of);

        // Query 2 -- prior 4 weeks, same slot, per (week, cell)
        Map<Integer, Map<CellKey, Agg>> historical = groupByWeekThenKey(historicalWindow(df, currentDay, currentHour), CellKey::of);

        // Downstream: per-cell week -> rate, kept indexed by week for the W-4..W-1 chart columns
        Map<CellKey, Map<Integer, Double>> rateByCellAndWeek = new LinkedHashMap<>();
        historical.forEach((week, byKey) -> byKey.forEach((key, agg) -> rateByCellAndWeek
                .computeIfAbsent(key, k -> new LinkedHashMap<>())
                .put(week, agg.rate())));

        List<Map<String, Object>> merged = new ArrayList<>();
        for (Map.Entry<CellKey, Agg> e : current.entrySet()) {
            CellKey key = e.getKey();
            Agg agg = e.getValue();
            double declineRate = agg.rate();

            Map<Integer, Double> weekRates = rateByCellAndWeek.getOrDefault(key, Map.of());
            List<Double> history = new ArrayList<>(weekRates.values());
            boolean isNewCell = history.isEmpty();
            double z = WowMath.zScore(declineRate, history, 0.0008);
            boolean alerted = Math.abs(z) >= threshold || isNewCell;

            Map<String, Object> m = new LinkedHashMap<>();
            m.put("network", key.network()); m.put("geography", key.geography()); m.put("entry_mode", key.entryMode());
            m.put("purchase_type", key.purchaseType()); m.put("auth_type", key.authType());
            m.put("channel", key.channel()); m.put("decline_code", key.declineCode());
            m.put("total_count", agg.total); m.put("decline_count", agg.declines);
            m.put("decline_rate", declineRate);
            m.put("wow_mean", isNewCell ? 0 : WowMath.mean(history));
            m.put("wow_std", isNewCell ? 0 : WowMath.std(history));
            m.put("z_wow", z);
            m.put("n_weeks", weekRates.size());
            m.put("w1_rate", weekRates.get(1)); m.put("w2_rate", weekRates.get(2));
            m.put("w3_rate", weekRates.get(3)); m.put("w4_rate", weekRates.get(4));
            m.put("is_new_cell", isNewCell);
            m.put("alerted", alerted);
            merged.add(m);
        }

        List<Map<String, Object>> alerts = new ArrayList<>(merged.stream().filter(m -> (Boolean) m.get("alerted")).toList());
        alerts.sort((x, y) -> Double.compare((Double) y.get("z_wow"), (Double) x.get("z_wow")));

        long nNewCells = merged.stream().filter(m -> (Boolean) m.get("is_new_cell")).count();

        List<Map<String, Object>> wowChartData = new ArrayList<>();
        for (Map<String, Object> r : alerts.subList(0, Math.min(10, alerts.size()))) {
            Map<String, Object> c = new LinkedHashMap<>();
            c.put("cell", r.get("network") + "×" + r.get("geography") + "×" + r.get("entry_mode") + "×" + r.get("decline_code"));
            c.put("short", r.get("network") + "×" + r.get("decline_code"));
            Map<String, Object> weekRates = new LinkedHashMap<>();
            weekRates.put("W-4", safe((Double) r.get("w4_rate")));
            weekRates.put("W-3", safe((Double) r.get("w3_rate")));
            weekRates.put("W-2", safe((Double) r.get("w2_rate")));
            weekRates.put("W-1", safe((Double) r.get("w1_rate")));
            weekRates.put("Current", WowMath.round((Double) r.get("decline_rate"), 4));
            c.put("week_rates", weekRates);
            c.put("wow_mean", WowMath.round((Double) r.get("wow_mean"), 4));
            c.put("current_rate", WowMath.round((Double) r.get("decline_rate"), 4));
            c.put("z_wow", WowMath.round((Double) r.get("z_wow"), 2));
            c.put("is_new_cell", r.get("is_new_cell"));
            wowChartData.add(c);
        }

        List<Map<String, Object>> alertRecords = new ArrayList<>();
        for (Map<String, Object> r : alerts.subList(0, Math.min(20, alerts.size()))) {
            Map<String, Object> d = new LinkedHashMap<>();
            d.put("network", r.get("network")); d.put("geography", r.get("geography"));
            d.put("entry_mode", r.get("entry_mode")); d.put("purchase_type", r.get("purchase_type"));
            d.put("auth_type", r.get("auth_type")); d.put("channel", r.get("channel"));
            d.put("decline_code", r.get("decline_code"));
            d.put("decline_rate", WowMath.round((Double) r.get("decline_rate"), 6));
            d.put("wow_mean", WowMath.round((Double) r.get("wow_mean"), 6));
            d.put("wow_std", WowMath.round((Double) r.get("wow_std"), 6));
            d.put("z_wow", WowMath.round((Double) r.get("z_wow"), 6));
            d.put("n_weeks", r.get("n_weeks"));
            d.put("is_new_cell", r.get("is_new_cell"));
            alertRecords.add(d);
        }

        double execMs = (System.nanoTime() - startNs) / 1e6;

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("stage", 3);
        result.put("name", "WoW Z-Score — Full Granularity");
        result.put("description", "No roll-up. Per-cell decline rate vs same Mon 14:00 in weeks −1..−4. Catches low-volume cells invisible to Stage 1, and rate shifts invisible to Stage 2.");
        result.put("n_active_cells", merged.size());
        result.put("n_alerts", alerts.size());
        result.put("n_new_cells", (int) nNewCells);
        result.put("threshold", threshold);
        result.put("execution_ms", WowMath.round(execMs, 2));
        result.put("wow_chart_data", wowChartData);
        result.put("alerts", alertRecords);
        return result;
    }

    static Double safe(Double v) {
        if (v == null || v.isNaN()) return null;
        return WowMath.round(v, 4);
    }
}
