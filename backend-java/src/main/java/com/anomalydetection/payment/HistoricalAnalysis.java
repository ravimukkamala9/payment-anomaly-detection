package com.anomalydetection.payment;

import org.springframework.jdbc.core.JdbcTemplate;

import java.util.*;

/** Aggregate views across all 5 weeks -- the seasonal shape every WoW
 * comparison in stages 1-3 is implicitly checking the current window
 * against. Four plain GROUP BY queries, no historical comparison and so no
 * z-score step -- this is the one class in the payment package that's just
 * SQL, start to finish. */
public class HistoricalAnalysis {

    public static Map<String, Object> run(JdbcTemplate jdbc) {
        long startNs = System.nanoTime();

        List<Map<String, Object>> weeklyTrend = jdbc.queryForList("""
                SELECT week AS "week", SUM(total_count) AS "total_count", SUM(decline_count) AS "decline_count"
                FROM payment_declines GROUP BY week ORDER BY week
                """).stream().map(HistoricalAnalysis::withRate).toList();

        List<Map<String, Object>> hourPattern = jdbc.queryForList("""
                SELECT hour_of_day AS "hour", SUM(total_count) AS "total_count", SUM(decline_count) AS "decline_count"
                FROM payment_declines GROUP BY hour_of_day ORDER BY hour_of_day
                """).stream().map(HistoricalAnalysis::withRate).toList();

        List<Map<String, Object>> dayPattern = jdbc.queryForList("""
                SELECT day_of_week AS "day_of_week", SUM(total_count) AS "total_count", SUM(decline_count) AS "decline_count"
                FROM payment_declines GROUP BY day_of_week ORDER BY day_of_week
                """).stream().map(HistoricalAnalysis::withRate).toList();

        List<Map<String, Object>> dayHourHeatmap = jdbc.queryForList("""
                SELECT day_of_week AS "day_of_week", hour_of_day AS "hour",
                       SUM(total_count) AS "total_count", SUM(decline_count) AS "decline_count"
                FROM payment_declines GROUP BY day_of_week, hour_of_day ORDER BY day_of_week, hour_of_day
                """).stream().map(HistoricalAnalysis::withRate).toList();

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

    /** Adds decline_rate to a row already carrying total_count/decline_count,
     * as ints (the row-mapper hands back Long/BigDecimal from SUM(), and the
     * frontend expects plain counts here same as everywhere else). */
    private static Map<String, Object> withRate(Map<String, Object> row) {
        long total = ((Number) row.get("total_count")).longValue();
        long declines = ((Number) row.get("decline_count")).longValue();
        Map<String, Object> out = new LinkedHashMap<>(row);
        out.put("total_count", total);
        out.put("decline_count", declines);
        out.put("decline_rate", WowMath.round(declines / (double) Math.max(total, 1), 6));
        return out;
    }
}
