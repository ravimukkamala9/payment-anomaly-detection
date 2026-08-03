package com.anomalydetection.payment;

import org.springframework.jdbc.core.JdbcTemplate;

import java.util.*;

/** Contribution-shift monitoring over any chosen set of dimensions.
 *
 * The math is always the same three steps:
 *   1. current window  -> each cell's share of this window's declines
 *   2. prior 4 weeks   -> each cell's share in each of those windows
 *   3. z-score of (1) against (2)
 *
 * Steps 1 and 2 are SQL (PaymentDb.currentWindow / historicalWindow); step 3
 * is the only part left in Java. Only the choice of dimensions varies, which
 * is what lets one monitor over all 7 dimensions (Stage2Contribution) and
 * several smaller per-team monitors (Stage2Heads) share this implementation. */
public class ContributionHead {

    public static Map<String, Object> run(JdbcTemplate jdbc, int currentDay, int currentHour,
                                           double threshold, List<Dim> dims) {
        long startNs = System.nanoTime();
        List<String> cols = dims.stream().map(Dim::label).toList();

        // Step 1 -- current window, grouped into cells
        List<Map<String, Object>> currentRows = PaymentDb.currentWindow(jdbc, cols, currentDay, currentHour);
        Map<List<String>, PaymentDb.Agg> current = new LinkedHashMap<>();
        for (Map<String, Object> row : currentRows) current.put(PaymentDb.keyOf(row, cols), PaymentDb.aggOf(row));
        long declinesNow = current.values().stream().mapToLong(PaymentDb.Agg::declines).sum();

        // Step 2 -- same slot in each of the prior 4 weeks, as share-of-window history per cell
        List<Map<String, Object>> histRows = PaymentDb.historicalWindow(jdbc, cols, currentDay, currentHour);
        Map<Integer, List<Map<String, Object>>> byWeek = new TreeMap<>();
        for (Map<String, Object> row : histRows) byWeek.computeIfAbsent(PaymentDb.weekOf(row), w -> new ArrayList<>()).add(row);

        Map<List<String>, List<Double>> history = new LinkedHashMap<>();
        byWeek.forEach((week, rows) -> {
            long declinesThatWeek = Math.max(rows.stream().mapToLong(r -> PaymentDb.aggOf(r).declines()).sum(), 1);
            rows.forEach(r -> history
                    .computeIfAbsent(PaymentDb.keyOf(r, cols), k -> new ArrayList<>())
                    .add(PaymentDb.aggOf(r).declines() / (double) declinesThatWeek));
        });

        // Step 3 -- score every currently-active cell against its own history
        List<Map<String, Object>> cells = new ArrayList<>();
        for (Map.Entry<List<String>, PaymentDb.Agg> e : current.entrySet()) {
            PaymentDb.Agg agg = e.getValue();
            List<Double> past = history.getOrDefault(e.getKey(), List.of());
            double share = agg.declines() / (double) Math.max(declinesNow, 1);
            double z = WowMath.zScore(share, past, 1e-6);
            boolean isNew = past.isEmpty();

            Map<String, Object> cell = new LinkedHashMap<>();
            for (int i = 0; i < dims.size(); i++) cell.put(dims.get(i).label(), e.getKey().get(i));
            cell.put("decline_count", (int) agg.declines());
            cell.put("total_count", (int) agg.total());
            cell.put("contribution_pct", share);
            cell.put("hist_mean", isNew ? 0.0 : WowMath.mean(past));
            cell.put("hist_std", isNew ? 0.0 : WowMath.std(past));
            cell.put("z_contribution", z);
            cell.put("is_new_cell", isNew);
            cell.put("alerted", Math.abs(z) >= threshold || isNew);
            cells.add(cell);
        }

        List<Map<String, Object>> alerts = sortedBy(cells, "z_contribution").stream()
                .filter(c -> (Boolean) c.get("alerted")).limit(20).toList();
        List<Map<String, Object>> topCells = sortedBy(cells, "contribution_pct").stream().limit(20).toList();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("n_active_cells", cells.size());
        result.put("n_alerts", (int) cells.stream().filter(c -> (Boolean) c.get("alerted")).count());
        result.put("n_new_cells", (int) cells.stream().filter(c -> (Boolean) c.get("is_new_cell")).count());
        result.put("total_declines_current", declinesNow);
        result.put("threshold", threshold);
        result.put("execution_ms", WowMath.round((System.nanoTime() - startNs) / 1e6, 2));
        result.put("chart_data", topCells.stream().map(c -> chartPoint(c, dims)).toList());
        result.put("alerts", alerts.stream().map(c -> alertRecord(c, dims)).toList());
        return result;
    }

    /** Descending by the given numeric field. */
    private static List<Map<String, Object>> sortedBy(List<Map<String, Object>> cells, String field) {
        return cells.stream()
                .sorted((a, b) -> Double.compare((Double) b.get(field), (Double) a.get(field)))
                .toList();
    }

    private static Map<String, Object> chartPoint(Map<String, Object> cell, List<Dim> dims) {
        List<String> values = dims.stream().map(d -> String.valueOf(cell.get(d.label()))).toList();
        Map<String, Object> point = new LinkedHashMap<>();
        point.put("label", values.get(values.size() - 1));
        point.put("cell_short", String.join("×", values));
        point.put("contribution_pct", WowMath.round((Double) cell.get("contribution_pct") * 100, 3));
        point.put("hist_mean_pct", WowMath.round((Double) cell.get("hist_mean") * 100, 3));
        point.put("z_contribution", WowMath.round((Double) cell.get("z_contribution"), 2));
        point.put("alerted", cell.get("alerted"));
        return point;
    }

    private static Map<String, Object> alertRecord(Map<String, Object> cell, List<Dim> dims) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (Dim d : dims) out.put(d.label(), cell.get(d.label()));
        out.put("decline_count", cell.get("decline_count"));
        for (String field : List.of("contribution_pct", "hist_mean", "hist_std", "z_contribution")) {
            out.put(field, WowMath.round((Double) cell.get(field), 6));
        }
        out.put("is_new_cell", cell.get("is_new_cell"));
        return out;
    }
}
