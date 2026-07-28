package com.anomalydetection.payment;

import java.util.*;

/** Port of stages/stage1_rollup_wow.py */
public class Stage1RollupWow {

    static class RollupKey {
        String declineCode, channel;
        RollupKey(String dc, String ch) { declineCode = dc; channel = ch; }
        @Override public boolean equals(Object o) {
            if (!(o instanceof RollupKey)) return false;
            RollupKey k = (RollupKey) o;
            return declineCode.equals(k.declineCode) && channel.equals(k.channel);
        }
        @Override public int hashCode() { return Objects.hash(declineCode, channel); }
    }

    static class Agg { long total; long declines; }

    public static Map<String, Object> run(List<PaymentRow> df, int currentDay, int currentHour, double threshold) {
        long startNs = System.nanoTime();

        List<PaymentRow> curr = new ArrayList<>();
        List<PaymentRow> hist = new ArrayList<>();
        for (PaymentRow r : df) {
            if (r.week == 0 && r.dayOfWeek == currentDay && r.hour == currentHour) curr.add(r);
            else if (r.week > 0 && r.dayOfWeek == currentDay && r.hour == currentHour) hist.add(r);
        }

        // curr rollup: (decline_code, channel) -> total, declines
        Map<RollupKey, Agg> currAgg = new LinkedHashMap<>();
        for (PaymentRow r : curr) {
            RollupKey k = new RollupKey(r.declineCode, r.channel);
            Agg a = currAgg.computeIfAbsent(k, kk -> new Agg());
            a.total += r.totalCount;
            a.declines += r.declineCount;
        }

        // hist rollup per week: (week, decline_code, channel) -> total, declines
        Map<String, Agg> histWeekAgg = new LinkedHashMap<>();
        Map<String, Integer> histWeekOf = new HashMap<>();
        Map<String, RollupKey> histWeekKeyOf = new HashMap<>();
        for (PaymentRow r : hist) {
            String wk = r.week + "|" + r.declineCode + "|" + r.channel;
            Agg a = histWeekAgg.computeIfAbsent(wk, kk -> new Agg());
            a.total += r.totalCount;
            a.declines += r.declineCount;
            histWeekOf.put(wk, r.week);
            histWeekKeyOf.put(wk, new RollupKey(r.declineCode, r.channel));
        }
        // per rollup key: list of week rates
        Map<RollupKey, List<Double>> ratesByKey = new LinkedHashMap<>();
        for (String wk : histWeekAgg.keySet()) {
            Agg a = histWeekAgg.get(wk);
            double rate = a.declines / (double) Math.max(a.total, 1);
            ratesByKey.computeIfAbsent(histWeekKeyOf.get(wk), kk -> new ArrayList<>()).add(rate);
        }

        List<Map<String, Object>> merged = new ArrayList<>();
        for (Map.Entry<RollupKey, Agg> e : currAgg.entrySet()) {
            RollupKey key = e.getKey();
            Agg a = e.getValue();
            double rate = a.declines / (double) Math.max(a.total, 1);

            List<Double> rates = ratesByKey.get(key);
            boolean isNew = rates == null || rates.isEmpty();
            double wowMean = 0, wowStd = 0;
            if (!isNew) {
                wowMean = mean(rates);
                wowStd = std(rates, wowMean);
            }
            double z;
            if (isNew) z = 99.0;
            else z = (rate - wowMean) / Math.max(wowStd, 0.0005);
            boolean alerted = Math.abs(z) >= threshold || isNew;

            Map<String, Object> m = new LinkedHashMap<>();
            m.put("decline_code", key.declineCode);
            m.put("channel", key.channel);
            m.put("total", a.total);
            m.put("declines", a.declines);
            m.put("rate", rate);
            m.put("wow_mean", wowMean);
            m.put("wow_std", wowStd);
            m.put("z_score", z);
            m.put("is_new", isNew);
            m.put("alerted", alerted);
            merged.add(m);
        }

        merged.sort((x, y) -> Double.compare((Double) y.get("z_score"), (Double) x.get("z_score")));

        List<Map<String, Object>> alerts = new ArrayList<>();
        for (Map<String, Object> m : merged) if ((Boolean) m.get("alerted")) alerts.add(m);

        List<Map<String, Object>> chartData = new ArrayList<>();
        for (int i = 0; i < Math.min(30, merged.size()); i++) {
            Map<String, Object> r = merged.get(i);
            Map<String, Object> c = new LinkedHashMap<>();
            c.put("label", r.get("decline_code") + "×" + r.get("channel"));
            c.put("z_score", round((Double) r.get("z_score"), 2));
            c.put("rate", round((Double) r.get("rate"), 4));
            c.put("wow_mean", round((Double) r.get("wow_mean"), 4));
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
            d.put("rate", round((Double) r.get("rate"), 4));
            d.put("wow_mean", round((Double) r.get("wow_mean"), 4));
            d.put("wow_std", round((Double) r.get("wow_std"), 4));
            d.put("z_score", round((Double) r.get("z_score"), 4));
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
        result.put("execution_ms", round(execMs, 2));
        result.put("chart_data", chartData);
        result.put("alerts", alertRecords);
        return result;
    }

    static double mean(List<Double> v) {
        double s = 0; for (double x : v) s += x; return s / v.size();
    }
    static double std(List<Double> v, double mean) {
        if (v.size() <= 1) return 0;
        double s = 0; for (double x : v) s += Math.pow(x - mean, 2);
        return Math.sqrt(s / (v.size() - 1)); // pandas .std() default ddof=1
    }
    static double round(double v, int places) {
        double f = Math.pow(10, places);
        return Math.round(v * f) / f;
    }
}
