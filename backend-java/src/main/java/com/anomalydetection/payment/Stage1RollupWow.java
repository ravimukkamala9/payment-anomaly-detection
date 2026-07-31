package com.anomalydetection.payment;

import java.util.*;

import static com.anomalydetection.payment.RowAggregation.*;

/** Port of stages/stage1_rollup_wow.py.
 * Two queries -- current window and prior weeks, both grouped by
 * (decline_code, channel) -- then a small downstream z-score per monitor. */
public class Stage1RollupWow {

    record RollupKey(String declineCode, String channel) {
        static RollupKey of(PaymentRow r) { return new RollupKey(r.declineCode, r.channel); }
    }

    public static Map<String, Object> run(List<PaymentRow> df, int currentDay, int currentHour, double threshold) {
        long startNs = System.nanoTime();

        // Query 1 -- current window, rolled up to (decline_code, channel)
        Map<RollupKey, Agg> current = groupByKey(currentWindow(df, currentDay, currentHour), RollupKey::of);

        // Query 2 -- prior 4 weeks, same slot, per (week, decline_code, channel)
        Map<Integer, Map<RollupKey, Agg>> historical = groupByWeekThenKey(historicalWindow(df, currentDay, currentHour), RollupKey::of);

        // Downstream: transpose to per-monitor list of historical rates
        Map<RollupKey, List<Double>> historicalRates = new LinkedHashMap<>();
        historical.forEach((week, byKey) -> byKey.forEach((key, agg) ->
                historicalRates.computeIfAbsent(key, k -> new ArrayList<>()).add(agg.rate())));

        List<Map<String, Object>> merged = new ArrayList<>();
        for (Map.Entry<RollupKey, Agg> e : current.entrySet()) {
            RollupKey key = e.getKey();
            Agg agg = e.getValue();
            double rate = agg.rate();
            List<Double> history = historicalRates.getOrDefault(key, List.of());
            boolean isNew = history.isEmpty();
            double z = WowMath.zScore(rate, history, 0.0005);
            boolean alerted = Math.abs(z) >= threshold || isNew;

            Map<String, Object> m = new LinkedHashMap<>();
            m.put("decline_code", key.declineCode());
            m.put("channel", key.channel());
            m.put("total", agg.total);
            m.put("declines", agg.declines);
            m.put("rate", rate);
            m.put("wow_mean", isNew ? 0 : WowMath.mean(history));
            m.put("wow_std", isNew ? 0 : WowMath.std(history));
            m.put("z_score", z);
            m.put("is_new", isNew);
            m.put("alerted", alerted);
            merged.add(m);
        }

        merged.sort((x, y) -> Double.compare((Double) y.get("z_score"), (Double) x.get("z_score")));
        List<Map<String, Object>> alerts = merged.stream().filter(m -> (Boolean) m.get("alerted")).toList();

        List<Map<String, Object>> chartData = new ArrayList<>();
        for (Map<String, Object> r : merged.subList(0, Math.min(30, merged.size()))) {
            Map<String, Object> c = new LinkedHashMap<>();
            c.put("label", r.get("decline_code") + "×" + r.get("channel"));
            c.put("z_score", WowMath.round((Double) r.get("z_score"), 2));
            c.put("rate", WowMath.round((Double) r.get("rate"), 4));
            c.put("wow_mean", WowMath.round((Double) r.get("wow_mean"), 4));
            c.put("alerted", r.get("alerted"));
            chartData.add(c);
        }

        List<Map<String, Object>> alertRecords = new ArrayList<>();
        for (Map<String, Object> r : alerts) {
            Map<String, Object> d = new LinkedHashMap<>();
            d.put("decline_code", r.get("decline_code"));
            d.put("channel", r.get("channel"));
            d.put("total", ((Long) r.get("total")).intValue());
            d.put("declines", ((Long) r.get("declines")).intValue());
            d.put("rate", WowMath.round((Double) r.get("rate"), 4));
            d.put("wow_mean", WowMath.round((Double) r.get("wow_mean"), 4));
            d.put("wow_std", WowMath.round((Double) r.get("wow_std"), 4));
            d.put("z_score", WowMath.round((Double) r.get("z_score"), 4));
            d.put("alerted", r.get("alerted"));
            alertRecords.add(d);
        }

        double execMs = (System.nanoTime() - startNs) / 1e6;

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("stage", 1);
        result.put("name", "Roll-up WoW Z-Score");
        result.put("description", "Rolled to (decline_code, channel). Current window vs same Mon 14:00 in weeks −1 through −4.");
        result.put("n_monitors", merged.size());
        result.put("n_alerts", alerts.size());
        result.put("threshold", threshold);
        result.put("execution_ms", WowMath.round(execMs, 2));
        result.put("chart_data", chartData);
        result.put("alerts", alertRecords);
        return result;
    }
}
