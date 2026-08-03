package com.anomalydetection.payment;

import org.springframework.jdbc.core.JdbcTemplate;

import java.util.*;

/** On-demand only -- never runs continuously, unlike Stage 1/2/3. Given the
 * exact 7-dimension cell a stage has already flagged, groups that exact
 * slice by diagnostic dimensions (bin, acquirer) that were never part of the
 * monitored CellKey. One SQL query, scoped to a single already-flagged cell
 * -- this is how a new business-requested field (a new SELECT column here)
 * avoids ever becoming a new standing monitor: it only costs a query, run
 * only against an already-flagged cell, not every cell every window. */
public class DrillDown {

    public static Map<String, Object> run(JdbcTemplate jdbc, int week, int dayOfWeek, int hour,
                                           String network, String geography, String entryMode,
                                           String purchaseType, String authType, String channel, String declineCode) {
        long startNs = System.nanoTime();

        String sql = """
                SELECT bin AS "bin", acquirer AS "acquirer",
                       SUM(total_count) AS "total", SUM(decline_count) AS "declines"
                FROM payment_declines
                WHERE week = ? AND day_of_week = ? AND hour_of_day = ?
                  AND network = ? AND geography = ? AND entry_mode = ?
                  AND purchase_type = ? AND auth_type = ? AND channel = ? AND decline_code = ?
                GROUP BY bin, acquirer
                """;
        List<Map<String, Object>> rows = jdbc.queryForList(sql,
                week, dayOfWeek, hour, network, geography, entryMode, purchaseType, authType, channel, declineCode);

        long totalDeclines = rows.stream().mapToLong(r -> ((Number) r.get("declines")).longValue()).sum();
        long totalDeclinesSafe = Math.max(totalDeclines, 1);

        List<Map<String, Object>> breakdown = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            long total = ((Number) row.get("total")).longValue();
            long declines = ((Number) row.get("declines")).longValue();
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("bin", row.get("bin"));
            m.put("acquirer", row.get("acquirer"));
            m.put("total_count", total);
            m.put("decline_count", declines);
            m.put("decline_rate", WowMath.round(declines / (double) Math.max(total, 1), 6));
            m.put("share_of_cell_declines", WowMath.round(declines / (double) totalDeclinesSafe * 100, 2));
            breakdown.add(m);
        }
        breakdown.sort((a, b) -> Long.compare((Long) b.get("decline_count"), (Long) a.get("decline_count")));

        double execMs = (System.nanoTime() - startNs) / 1e6;

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("network", network);
        result.put("geography", geography);
        result.put("entry_mode", entryMode);
        result.put("purchase_type", purchaseType);
        result.put("auth_type", authType);
        result.put("channel", channel);
        result.put("decline_code", declineCode);
        result.put("n_combinations", breakdown.size());
        result.put("total_declines", totalDeclines);
        result.put("execution_ms", WowMath.round(execMs, 2));
        result.put("breakdown", breakdown);
        return result;
    }
}
