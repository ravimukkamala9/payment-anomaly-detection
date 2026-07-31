package com.anomalydetection.payment;

import java.util.*;

/** Aggregate views across all 5 weeks -- the seasonal shape every WoW
 * comparison in stages 1-3 is implicitly checking the current window
 * against. Three cuts: per-week trend, hour-of-day pattern, day-of-week
 * pattern. */
public class HistoricalAnalysis {

    static class Totals { long total; long declines; }

    public static Map<String, Object> run(List<PaymentRow> df) {
        long startNs = System.nanoTime();

        Map<Integer, Totals> byWeek = new TreeMap<>();
        Map<Integer, Totals> byHour = new TreeMap<>();
        Map<Integer, Totals> byDay = new TreeMap<>();
        Map<String, Totals> byDayHour = new LinkedHashMap<>();

        for (PaymentRow r : df) {
            add(byWeek, r.week, r);
            add(byHour, r.hour, r);
            add(byDay, r.dayOfWeek, r);
            add(byDayHour, r.dayOfWeek + "|" + r.hour, r);
        }

        List<Map<String, Object>> weeklyTrend = new ArrayList<>();
        byWeek.forEach((week, t) -> weeklyTrend.add(rowOf(Map.of("week", week), t)));

        List<Map<String, Object>> hourPattern = new ArrayList<>();
        byHour.forEach((hour, t) -> hourPattern.add(rowOf(Map.of("hour", hour), t)));

        List<Map<String, Object>> dayPattern = new ArrayList<>();
        byDay.forEach((day, t) -> dayPattern.add(rowOf(Map.of("day_of_week", day), t)));

        List<Map<String, Object>> dayHourHeatmap = new ArrayList<>();
        byDayHour.forEach((key, t) -> {
            String[] parts = key.split("\\|");
            dayHourHeatmap.add(rowOf(Map.of("day_of_week", Integer.parseInt(parts[0]), "hour", Integer.parseInt(parts[1])), t));
        });

        double execMs = (System.nanoTime() - startNs) / 1e6;

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("name", "Historical Data Analysis");
        result.put("description", "Aggregate decline patterns across all 5 weeks -- the seasonal baseline every WoW z-score is measured against.");
        result.put("execution_ms", WowMath.round(execMs, 2));
        result.put("weekly_trend", weeklyTrend);
        result.put("hour_pattern", hourPattern);
        result.put("day_pattern", dayPattern);
        result.put("day_hour_heatmap", dayHourHeatmap);
        return result;
    }

    private static <K> void add(Map<K, Totals> map, K key, PaymentRow r) {
        Totals t = map.computeIfAbsent(key, k -> new Totals());
        t.total += r.totalCount;
        t.declines += r.declineCount;
    }

    private static Map<String, Object> rowOf(Map<String, Object> keyFields, Totals t) {
        Map<String, Object> m = new LinkedHashMap<>(keyFields);
        m.put("total_count", t.total);
        m.put("decline_count", t.declines);
        m.put("decline_rate", WowMath.round(t.declines / (double) Math.max(t.total, 1), 6));
        return m;
    }
}
