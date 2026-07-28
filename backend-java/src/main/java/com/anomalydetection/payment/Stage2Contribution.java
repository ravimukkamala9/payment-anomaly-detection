package com.anomalydetection.payment;

import java.util.*;

/** Port of stages/stage2_contribution.py */
public class Stage2Contribution {

    static class CellAgg { long decline; long total; }

    public static Map<String, Object> run(List<PaymentRow> df, int currentDay, int currentHour, double threshold) {
        long startNs = System.nanoTime();

        List<PaymentRow> curr = new ArrayList<>();
        List<PaymentRow> hist = new ArrayList<>();
        for (PaymentRow r : df) {
            if (r.week == 0 && r.dayOfWeek == currentDay && r.hour == currentHour) curr.add(r);
            else if (r.week > 0 && r.dayOfWeek == currentDay && r.hour == currentHour) hist.add(r);
        }

        long totalCurrDeclines = 0;
        for (PaymentRow r : curr) totalCurrDeclines += r.declineCount;
        long totalCurrDeclinesSafe = Math.max(totalCurrDeclines, 1);

        Map<CellKey, CellAgg> currCell = new LinkedHashMap<>();
        for (PaymentRow r : curr) {
            CellAgg a = currCell.computeIfAbsent(CellKey.of(r), k -> new CellAgg());
            a.decline += r.declineCount;
            a.total += r.totalCount;
        }

        // week totals (decline_count sum per week) for historical
        Map<Integer, Long> weekTotal = new HashMap<>();
        for (PaymentRow r : hist) weekTotal.merge(r.week, (long) r.declineCount, Long::sum);

        // per cell per week contribution_pct
        Map<CellKey, List<Double>> contribByCell = new LinkedHashMap<>();
        Map<String, Long> cellWeekAgg = new LinkedHashMap<>();
        Map<String, CellKey> cellWeekKeyOf = new HashMap<>();
        Map<String, Integer> cellWeekOf = new HashMap<>();
        for (PaymentRow r : hist) {
            String wk = r.week + "|" + r.network + "|" + r.geography + "|" + r.entryMode + "|" + r.purchaseType + "|" + r.authType + "|" + r.channel + "|" + r.declineCode;
            cellWeekAgg.merge(wk, (long) r.declineCount, Long::sum);
            cellWeekKeyOf.put(wk, CellKey.of(r));
            cellWeekOf.put(wk, r.week);
        }
        for (String wk : cellWeekAgg.keySet()) {
            long declineSum = cellWeekAgg.get(wk);
            int week = cellWeekOf.get(wk);
            long wt = Math.max(weekTotal.getOrDefault(week, 0L), 1L);
            double contribPct = declineSum / (double) wt;
            contribByCell.computeIfAbsent(cellWeekKeyOf.get(wk), k -> new ArrayList<>()).add(contribPct);
        }

        List<Map<String, Object>> merged = new ArrayList<>();
        for (Map.Entry<CellKey, CellAgg> e : currCell.entrySet()) {
            CellKey key = e.getKey();
            CellAgg a = e.getValue();
            double contributionPct = a.decline / (double) totalCurrDeclinesSafe;

            List<Double> hist_ = contribByCell.get(key);
            boolean isNewCell = hist_ == null || hist_.isEmpty();
            double histMean = 0, histStd = 0;
            if (!isNewCell) {
                histMean = Stage1RollupWow.mean(hist_);
                histStd = Stage1RollupWow.std(hist_, histMean);
            }
            double z = isNewCell ? 99.0 : (contributionPct - histMean) / Math.max(histStd, 1e-6);
            boolean alerted = Math.abs(z) >= threshold || isNewCell;

            Map<String, Object> m = new LinkedHashMap<>();
            m.put("network", key.network); m.put("geography", key.geography); m.put("entry_mode", key.entryMode);
            m.put("purchase_type", key.purchaseType); m.put("auth_type", key.authType);
            m.put("channel", key.channel); m.put("decline_code", key.declineCode);
            m.put("decline_count", a.decline);
            m.put("total_count", a.total);
            m.put("contribution_pct", contributionPct);
            m.put("hist_mean", histMean);
            m.put("hist_std", histStd);
            m.put("z_contribution", z);
            m.put("is_new_cell", isNewCell);
            m.put("alerted", alerted);
            merged.add(m);
        }

        List<Map<String, Object>> alerts = new ArrayList<>();
        for (Map<String, Object> m : merged) if ((Boolean) m.get("alerted")) alerts.add(m);
        alerts.sort((x, y) -> Double.compare((Double) y.get("z_contribution"), (Double) x.get("z_contribution")));

        List<Map<String, Object>> topByContrib = new ArrayList<>(merged);
        topByContrib.sort((x, y) -> Double.compare((Double) y.get("contribution_pct"), (Double) x.get("contribution_pct")));

        List<Map<String, Object>> chartData = new ArrayList<>();
        for (int i = 0; i < Math.min(20, topByContrib.size()); i++) {
            Map<String, Object> r = topByContrib.get(i);
            Map<String, Object> c = new LinkedHashMap<>();
            c.put("label", r.get("decline_code"));
            c.put("cell_short", r.get("network") + "×" + r.get("geography") + "×" + r.get("decline_code"));
            c.put("contribution_pct", round((Double) r.get("contribution_pct") * 100, 3));
            c.put("hist_mean_pct", round((Double) r.get("hist_mean") * 100, 3));
            c.put("z_contribution", round((Double) r.get("z_contribution"), 2));
            c.put("alerted", r.get("alerted"));
            chartData.add(c);
        }

        int nNewCells = 0;
        for (Map<String, Object> m : merged) if ((Boolean) m.get("is_new_cell")) nNewCells++;

        List<Map<String, Object>> alertRecords = new ArrayList<>();
        for (int i = 0; i < Math.min(20, alerts.size()); i++) {
            Map<String, Object> r = alerts.get(i);
            Map<String, Object> d = new LinkedHashMap<>();
            d.put("network", r.get("network")); d.put("geography", r.get("geography"));
            d.put("entry_mode", r.get("entry_mode")); d.put("purchase_type", r.get("purchase_type"));
            d.put("auth_type", r.get("auth_type")); d.put("channel", r.get("channel"));
            d.put("decline_code", r.get("decline_code"));
            d.put("decline_count", ((Long) r.get("decline_count")).intValue());
            d.put("contribution_pct", round6((Double) r.get("contribution_pct")));
            d.put("hist_mean", round6((Double) r.get("hist_mean")));
            d.put("hist_std", round6((Double) r.get("hist_std")));
            d.put("z_contribution", round6((Double) r.get("z_contribution")));
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
        result.put("n_new_cells", nNewCells);
        result.put("total_declines_current", totalCurrDeclines);
        result.put("threshold", threshold);
        result.put("execution_ms", round(execMs, 2));
        result.put("chart_data", chartData);
        result.put("alerts", alertRecords);
        return result;
    }

    static double round(double v, int places) {
        double f = Math.pow(10, places);
        return Math.round(v * f) / f;
    }
    static double round6(double v) {
        return round(v, 6);
    }
}
