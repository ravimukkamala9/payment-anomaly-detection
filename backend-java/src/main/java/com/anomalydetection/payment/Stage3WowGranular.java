package com.anomalydetection.payment;

import java.util.*;

/** Port of stages/stage3_wow_granular.py */
public class Stage3WowGranular {

    static class Agg { long total; long decline; }

    public static Map<String, Object> run(List<PaymentRow> df, int currentDay, int currentHour, double threshold) {
        long startNs = System.nanoTime();

        List<PaymentRow> curr = new ArrayList<>();
        List<PaymentRow> hist = new ArrayList<>();
        for (PaymentRow r : df) {
            if (r.week == 0 && r.dayOfWeek == currentDay && r.hour == currentHour) curr.add(r);
            else if (r.week > 0 && r.dayOfWeek == currentDay && r.hour == currentHour) hist.add(r);
        }

        Map<CellKey, Agg> currCell = new LinkedHashMap<>();
        for (PaymentRow r : curr) {
            Agg a = currCell.computeIfAbsent(CellKey.of(r), k -> new Agg());
            a.total += r.totalCount;
            a.decline += r.declineCount;
        }

        // hist per (week, cell)
        Map<String, Agg> histWeekAgg = new LinkedHashMap<>();
        Map<String, CellKey> keyOf = new HashMap<>();
        Map<String, Integer> weekOf = new HashMap<>();
        for (PaymentRow r : hist) {
            CellKey ck = CellKey.of(r);
            String wk = r.week + "|" + ck.network + "|" + ck.geography + "|" + ck.entryMode + "|" + ck.purchaseType + "|" + ck.authType + "|" + ck.channel + "|" + ck.declineCode;
            Agg a = histWeekAgg.computeIfAbsent(wk, kk -> new Agg());
            a.total += r.totalCount;
            a.decline += r.declineCount;
            keyOf.put(wk, ck);
            weekOf.put(wk, r.week);
        }

        // per cell: list of (week, rate)
        Map<CellKey, List<double[]>> weekRatesByCell = new LinkedHashMap<>();
        for (String wk : histWeekAgg.keySet()) {
            Agg a = histWeekAgg.get(wk);
            double rate = a.decline / (double) Math.max(a.total, 1);
            weekRatesByCell.computeIfAbsent(keyOf.get(wk), k -> new ArrayList<>()).add(new double[]{weekOf.get(wk), rate});
        }

        List<Map<String, Object>> merged = new ArrayList<>();
        for (Map.Entry<CellKey, Agg> e : currCell.entrySet()) {
            CellKey key = e.getKey();
            Agg a = e.getValue();
            double declineRate = a.decline / (double) Math.max(a.total, 1);

            List<double[]> weekRates = weekRatesByCell.get(key);
            boolean isNewCell = weekRates == null || weekRates.isEmpty();
            double wowMean = 0, wowStd = 0;
            Double w1 = null, w2 = null, w3 = null, w4 = null;
            int nWeeks = 0;
            if (!isNewCell) {
                List<Double> rates = new ArrayList<>();
                for (double[] wr : weekRates) rates.add(wr[1]);
                wowMean = Stage1RollupWow.mean(rates);
                wowStd = Stage1RollupWow.std(rates, wowMean);
                nWeeks = weekRates.size();
                for (double[] wr : weekRates) {
                    int wk = (int) wr[0];
                    if (wk == 1) w1 = wr[1];
                    else if (wk == 2) w2 = wr[1];
                    else if (wk == 3) w3 = wr[1];
                    else if (wk == 4) w4 = wr[1];
                }
            }
            double z = isNewCell ? 99.0 : (declineRate - wowMean) / Math.max(wowStd, 0.0008);
            boolean alerted = Math.abs(z) >= threshold || isNewCell;

            Map<String, Object> m = new LinkedHashMap<>();
            m.put("network", key.network); m.put("geography", key.geography); m.put("entry_mode", key.entryMode);
            m.put("purchase_type", key.purchaseType); m.put("auth_type", key.authType);
            m.put("channel", key.channel); m.put("decline_code", key.declineCode);
            m.put("total_count", a.total); m.put("decline_count", a.decline);
            m.put("decline_rate", declineRate);
            m.put("wow_mean", wowMean); m.put("wow_std", wowStd);
            m.put("z_wow", z); m.put("n_weeks", nWeeks);
            m.put("w1_rate", w1); m.put("w2_rate", w2); m.put("w3_rate", w3); m.put("w4_rate", w4);
            m.put("is_new_cell", isNewCell);
            m.put("alerted", alerted);
            merged.add(m);
        }

        List<Map<String, Object>> alerts = new ArrayList<>();
        for (Map<String, Object> m : merged) if ((Boolean) m.get("alerted")) alerts.add(m);
        alerts.sort((x, y) -> Double.compare((Double) y.get("z_wow"), (Double) x.get("z_wow")));

        int nNewCells = 0;
        for (Map<String, Object> m : merged) if ((Boolean) m.get("is_new_cell")) nNewCells++;

        List<Map<String, Object>> wowChartData = new ArrayList<>();
        for (int i = 0; i < Math.min(10, alerts.size()); i++) {
            Map<String, Object> r = alerts.get(i);
            Map<String, Object> c = new LinkedHashMap<>();
            String label = r.get("network") + "×" + r.get("geography") + "×" + r.get("entry_mode") + "×" + r.get("decline_code");
            c.put("cell", label);
            c.put("short", r.get("network") + "×" + r.get("decline_code"));
            Map<String, Object> weekRates = new LinkedHashMap<>();
            weekRates.put("W-4", safe((Double) r.get("w4_rate")));
            weekRates.put("W-3", safe((Double) r.get("w3_rate")));
            weekRates.put("W-2", safe((Double) r.get("w2_rate")));
            weekRates.put("W-1", safe((Double) r.get("w1_rate")));
            weekRates.put("Current", round((Double) r.get("decline_rate"), 4));
            c.put("week_rates", weekRates);
            c.put("wow_mean", round((Double) r.get("wow_mean"), 4));
            c.put("current_rate", round((Double) r.get("decline_rate"), 4));
            c.put("z_wow", round((Double) r.get("z_wow"), 2));
            c.put("is_new_cell", r.get("is_new_cell"));
            wowChartData.add(c);
        }

        List<Map<String, Object>> alertRecords = new ArrayList<>();
        for (int i = 0; i < Math.min(20, alerts.size()); i++) {
            Map<String, Object> r = alerts.get(i);
            Map<String, Object> d = new LinkedHashMap<>();
            d.put("network", r.get("network")); d.put("geography", r.get("geography"));
            d.put("entry_mode", r.get("entry_mode")); d.put("purchase_type", r.get("purchase_type"));
            d.put("auth_type", r.get("auth_type")); d.put("channel", r.get("channel"));
            d.put("decline_code", r.get("decline_code"));
            d.put("decline_rate", round6((Double) r.get("decline_rate")));
            d.put("wow_mean", round6((Double) r.get("wow_mean")));
            d.put("wow_std", round6((Double) r.get("wow_std")));
            d.put("z_wow", round6((Double) r.get("z_wow")));
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
        result.put("n_new_cells", nNewCells);
        result.put("threshold", threshold);
        result.put("execution_ms", round(execMs, 2));
        result.put("wow_chart_data", wowChartData);
        result.put("alerts", alertRecords);
        return result;
    }

    static Double safe(Double v) {
        if (v == null || v.isNaN()) return null;
        return round(v, 4);
    }
    static double round(double v, int places) {
        double f = Math.pow(10, places);
        return Math.round(v * f) / f;
    }
    static double round6(double v) {
        return round(v, 6);
    }
}
