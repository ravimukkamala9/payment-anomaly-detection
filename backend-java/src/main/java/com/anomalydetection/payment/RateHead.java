package com.anomalydetection.payment;

import org.springframework.jdbc.core.JdbcTemplate;

import java.util.*;

/** WoW rate monitoring over any chosen set of dimensions -- the Stage 3
 * counterpart to ContributionHead. No roll-up, no shared pool: each cell's
 * own decline rate this window vs. its own rate in the same slot across the
 * prior 4 weeks. SQL groups and sums (PaymentDb.currentWindow /
 * historicalWindow); the z-score is the only Java-side step.
 *
 * Pairing this with ContributionHead per head is what makes a head a
 * complete monitor rather than half of one -- share catches masking
 * (Stage 2's job), rate catches a cell's own genuine drift regardless of
 * what share of the pie it holds (Stage 3's job). Same two jobs, now
 * available at whatever dimension subset a team owns. */
public class RateHead {

    public static Map<String, Object> run(JdbcTemplate jdbc, int currentDay, int currentHour,
                                           double threshold, List<Dim> dims) {
        long startNs = System.nanoTime();
        List<String> cols = dims.stream().map(Dim::label).toList();

        List<Map<String, Object>> currentRows = PaymentDb.currentWindow(jdbc, cols, currentDay, currentHour);
        Map<List<String>, PaymentDb.Agg> current = new LinkedHashMap<>();
        for (Map<String, Object> row : currentRows) current.put(PaymentDb.keyOf(row, cols), PaymentDb.aggOf(row));

        // rate history per cell, kept indexed by week for the W-4..W-1 chart columns
        List<Map<String, Object>> histRows = PaymentDb.historicalWindow(jdbc, cols, currentDay, currentHour);
        Map<List<String>, Map<Integer, Double>> rateByCellAndWeek = new LinkedHashMap<>();
        for (Map<String, Object> row : histRows) {
            PaymentDb.Agg agg = PaymentDb.aggOf(row);
            rateByCellAndWeek
                    .computeIfAbsent(PaymentDb.keyOf(row, cols), k -> new LinkedHashMap<>())
                    .put(PaymentDb.weekOf(row), agg.rate());
        }

        List<Map<String, Object>> cells = new ArrayList<>();
        for (Map.Entry<List<String>, PaymentDb.Agg> e : current.entrySet()) {
            PaymentDb.Agg agg = e.getValue();
            Map<Integer, Double> weekRates = rateByCellAndWeek.getOrDefault(e.getKey(), Map.of());
            List<Double> history = new ArrayList<>(weekRates.values());
            boolean isNew = history.isEmpty();
            double rate = agg.rate();
            double z = WowMath.zScore(rate, history, 0.0008);

            Map<String, Object> cell = new LinkedHashMap<>();
            for (int i = 0; i < dims.size(); i++) cell.put(dims.get(i).label(), e.getKey().get(i));
            cell.put("total_count", (int) agg.total());
            cell.put("decline_count", (int) agg.declines());
            cell.put("decline_rate", rate);
            cell.put("wow_mean", isNew ? 0.0 : WowMath.mean(history));
            cell.put("wow_std", isNew ? 0.0 : WowMath.std(history));
            cell.put("z_wow", z);
            cell.put("n_weeks", weekRates.size());
            cell.put("w1_rate", weekRates.get(1)); cell.put("w2_rate", weekRates.get(2));
            cell.put("w3_rate", weekRates.get(3)); cell.put("w4_rate", weekRates.get(4));
            cell.put("is_new_cell", isNew);
            cell.put("alerted", Math.abs(z) >= threshold || isNew);
            cells.add(cell);
        }

        List<Map<String, Object>> alerts = cells.stream()
                .filter(c -> (Boolean) c.get("alerted"))
                .sorted((a, b) -> Double.compare((Double) b.get("z_wow"), (Double) a.get("z_wow")))
                .limit(20)
                .toList();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("n_active_cells", cells.size());
        result.put("n_alerts", (int) cells.stream().filter(c -> (Boolean) c.get("alerted")).count());
        result.put("n_new_cells", (int) cells.stream().filter(c -> (Boolean) c.get("is_new_cell")).count());
        result.put("threshold", threshold);
        result.put("execution_ms", WowMath.round((System.nanoTime() - startNs) / 1e6, 2));
        result.put("wow_chart_data", alerts.stream().limit(6).map(c -> chartPoint(c, dims)).toList());
        result.put("alerts", alerts.stream().map(c -> alertRecord(c, dims)).toList());
        return result;
    }

    private static Map<String, Object> chartPoint(Map<String, Object> cell, List<Dim> dims) {
        List<String> values = dims.stream().map(d -> String.valueOf(cell.get(d.label()))).toList();
        Map<String, Object> weekRates = new LinkedHashMap<>();
        weekRates.put("W-4", safe((Double) cell.get("w4_rate")));
        weekRates.put("W-3", safe((Double) cell.get("w3_rate")));
        weekRates.put("W-2", safe((Double) cell.get("w2_rate")));
        weekRates.put("W-1", safe((Double) cell.get("w1_rate")));
        weekRates.put("Current", WowMath.round((Double) cell.get("decline_rate"), 4));

        Map<String, Object> point = new LinkedHashMap<>();
        point.put("cell", String.join("×", values));
        point.put("short", values.get(0) + "×" + values.get(values.size() - 1));
        point.put("week_rates", weekRates);
        point.put("wow_mean", WowMath.round((Double) cell.get("wow_mean"), 4));
        point.put("current_rate", WowMath.round((Double) cell.get("decline_rate"), 4));
        point.put("z_wow", WowMath.round((Double) cell.get("z_wow"), 2));
        point.put("is_new_cell", cell.get("is_new_cell"));
        return point;
    }

    private static Map<String, Object> alertRecord(Map<String, Object> cell, List<Dim> dims) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (Dim d : dims) out.put(d.label(), cell.get(d.label()));
        out.put("total_count", cell.get("total_count"));
        out.put("decline_count", cell.get("decline_count"));
        for (String field : List.of("decline_rate", "wow_mean", "wow_std", "z_wow")) {
            out.put(field, WowMath.round((Double) cell.get(field), 6));
        }
        out.put("n_weeks", cell.get("n_weeks"));
        out.put("is_new_cell", cell.get("is_new_cell"));
        return out;
    }

    private static Double safe(Double v) {
        return (v == null || v.isNaN()) ? null : WowMath.round(v, 4);
    }
}
