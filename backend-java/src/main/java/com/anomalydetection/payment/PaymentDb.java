package com.anomalydetection.payment;

import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

/** All SQL for the payment_declines table lives here: generation/insert, and
 * the two-query pattern every stage and head is built on --
 *
 *   1. current window  -> each cell's totals this window, grouped by
 *      whichever dimensions the caller is monitoring
 *   2. prior 4 weeks   -> the same, one more GROUP BY level for week
 *
 * SQL does the filtering, grouping, and summing -- the actual "computation"
 * over up to 150k+ rows. What's left for Java (WowMath) is a handful of
 * numbers per already-aggregated cell: mean, std, and a z-score. That split
 * is deliberate, not partial -- see docs/payment_anomaly_brief.html for why
 * the boundary sits there. */
public final class PaymentDb {
    private PaymentDb() {}

    public record Agg(long total, long declines) {
        public double rate() { return declines / (double) Math.max(total, 1); }
    }

    private static final String INSERT_SQL = """
            INSERT INTO payment_declines
                (week, day_of_week, hour_of_day, network, geography, entry_mode, purchase_type,
                 auth_type, channel, decline_code, bin, acquirer, total_count, decline_count, decline_rate)
            VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
            """;

    /** Regenerates the dataset: run the RNG-driven Java generator, then
     * replace whatever's in the table with its output. Generation itself
     * stays Java -- weighted synthetic sampling with anomaly injection
     * isn't something SQL should do -- only storage moves to the DB. */
    public static void regenerate(JdbcTemplate jdbc, long seed) {
        List<PaymentRow> rows = PaymentDataGenerator.generate(seed);
        jdbc.update("DELETE FROM payment_declines");
        jdbc.batchUpdate(INSERT_SQL, rows, 1000, (ps, r) -> {
            ps.setInt(1, r.week);
            ps.setInt(2, r.dayOfWeek);
            ps.setInt(3, r.hour);
            ps.setString(4, r.network);
            ps.setString(5, r.geography);
            ps.setString(6, r.entryMode);
            ps.setString(7, r.purchaseType);
            ps.setString(8, r.authType);
            ps.setString(9, r.channel);
            ps.setString(10, r.declineCode);
            ps.setString(11, r.bin);
            ps.setString(12, r.acquirer);
            ps.setInt(13, r.totalCount);
            ps.setInt(14, r.declineCount);
            ps.setDouble(15, r.declineRate);
        });
    }

    public static boolean hasData(JdbcTemplate jdbc) {
        return count(jdbc, "SELECT COUNT(*) FROM payment_declines") > 0;
    }

    public static int rowCount(JdbcTemplate jdbc) {
        return count(jdbc, "SELECT COUNT(*) FROM payment_declines");
    }

    public static int distinctCellCount(JdbcTemplate jdbc) {
        return count(jdbc, """
                SELECT COUNT(*) FROM (
                    SELECT DISTINCT network, geography, entry_mode, purchase_type, auth_type, channel, decline_code
                    FROM payment_declines
                )""");
    }

    private static int count(JdbcTemplate jdbc, String sql) {
        Integer n = jdbc.queryForObject(sql, Integer.class);
        return n == null ? 0 : n;
    }

    /** Query 1 -- current window (week 0), grouped by `dims`. Every column is
     * explicitly aliased in lowercase quotes so the returned map keys are
     * predictable regardless of how H2 happens to case unquoted identifiers
     * internally -- callers can always do row.get("network"), row.get("total"). */
    public static List<Map<String, Object>> currentWindow(JdbcTemplate jdbc, List<String> dims, int day, int hour) {
        String cols = selectList(dims);
        String sql = "SELECT " + cols + ", SUM(total_count) AS \"total\", SUM(decline_count) AS \"declines\" " +
                "FROM payment_declines WHERE week = 0 AND day_of_week = ? AND hour_of_day = ? " +
                "GROUP BY " + groupList(dims);
        return jdbc.queryForList(sql, day, hour);
    }

    /** Query 2 -- the same slot in weeks -1..-4, grouped by (week, dims). */
    public static List<Map<String, Object>> historicalWindow(JdbcTemplate jdbc, List<String> dims, int day, int hour) {
        String cols = selectList(dims);
        String sql = "SELECT week AS \"week\", " + cols + ", SUM(total_count) AS \"total\", SUM(decline_count) AS \"declines\" " +
                "FROM payment_declines WHERE week > 0 AND day_of_week = ? AND hour_of_day = ? " +
                "GROUP BY week, " + groupList(dims);
        return jdbc.queryForList(sql, day, hour);
    }

    /** A specific (week, day, hour), unaggregated, for the raw rows behind
     * one exact week -- DrillDown and ContributionVsLastWeek's "last week"
     * comparison both need this shape rather than the multi-week roll-up above. */
    public static List<Map<String, Object>> singleWindow(JdbcTemplate jdbc, List<String> dims, int week, int day, int hour) {
        String cols = selectList(dims);
        String sql = "SELECT " + cols + ", SUM(total_count) AS \"total\", SUM(decline_count) AS \"declines\" " +
                "FROM payment_declines WHERE week = ? AND day_of_week = ? AND hour_of_day = ? " +
                "GROUP BY " + groupList(dims);
        return jdbc.queryForList(sql, week, day, hour);
    }

    public static List<String> keyOf(Map<String, Object> row, List<String> dims) {
        return dims.stream().map(d -> String.valueOf(row.get(d))).toList();
    }

    public static Agg aggOf(Map<String, Object> row) {
        return new Agg(((Number) row.get("total")).longValue(), ((Number) row.get("declines")).longValue());
    }

    public static int weekOf(Map<String, Object> row) {
        return ((Number) row.get("week")).intValue();
    }

    private static String selectList(List<String> dims) {
        return dims.stream().map(d -> d + " AS \"" + d + "\"").reduce((a, b) -> a + ", " + b).orElse("");
    }

    private static String groupList(List<String> dims) {
        return String.join(", ", dims);
    }
}
