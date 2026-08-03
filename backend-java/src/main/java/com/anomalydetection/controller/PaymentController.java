package com.anomalydetection.controller;

import com.anomalydetection.detectors.IsolationForestDetector;
import com.anomalydetection.detectors.ZScoreDetector;
import com.anomalydetection.payment.*;
import com.anomalydetection.utils.DataProcessor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.*;

@RestController
@RequestMapping("/api/payment")
public class PaymentController {

    private final JdbcTemplate jdbc;

    public PaymentController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Every analysis endpoint needs generated data, so they all call this
     * instead of repeating the same check and error body. */
    private void requireData() {
        if (!PaymentDb.hasData(jdbc)) throw new NoDataException();
    }

    private static class NoDataException extends RuntimeException {}

    @ExceptionHandler(NoDataException.class)
    public ResponseEntity<?> handleNoData() {
        return ResponseEntity.badRequest().body(Map.of("detail", "Generate data first"));
    }

    @PostMapping("/generate")
    public Map<String, Object> generate() {
        PaymentDb.regenerate(jdbc, 42);

        Map<String, Object> currentWindow = new LinkedHashMap<>();
        currentWindow.put("week", 0);
        currentWindow.put("day_of_week", PaymentDataGenerator.CURRENT_DAY);
        currentWindow.put("hour", PaymentDataGenerator.CURRENT_HOUR);
        currentWindow.put("label", "Monday 14:00");

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("n_rows", PaymentDb.rowCount(jdbc));
        resp.put("n_cells", PaymentDb.distinctCellCount(jdbc));
        resp.put("weeks", 5);
        resp.put("current_window", currentWindow);
        resp.put("anomalies_injected", PaymentDataGenerator.getAnomalyInfo());
        resp.put("decline_codes_total", 40);
        return resp;
    }

    @GetMapping("/download")
    public ResponseEntity<byte[]> download() {
        if (!PaymentDb.hasData(jdbc)) {
            return ResponseEntity.status(404).body("Generate data first via POST /payment/generate".getBytes(StandardCharsets.UTF_8));
        }
        StringBuilder sb = new StringBuilder();
        sb.append("week,day_of_week,hour,network,geography,entry_mode,purchase_type,auth_type,channel,decline_code,total_count,decline_count,decline_rate\n");
        jdbc.query("""
                SELECT week, day_of_week, hour_of_day AS "hour", network, geography, entry_mode, purchase_type,
                       auth_type, channel, decline_code, total_count, decline_count, decline_rate
                FROM payment_declines
                ORDER BY week, day_of_week, hour_of_day
                """, rs -> {
            sb.append(rs.getInt("week")).append(',').append(rs.getInt("day_of_week")).append(',').append(rs.getInt("hour")).append(',')
                    .append(rs.getString("network")).append(',').append(rs.getString("geography")).append(',').append(rs.getString("entry_mode")).append(',')
                    .append(rs.getString("purchase_type")).append(',').append(rs.getString("auth_type")).append(',').append(rs.getString("channel")).append(',')
                    .append(rs.getString("decline_code")).append(',').append(rs.getInt("total_count")).append(',').append(rs.getInt("decline_count")).append(',')
                    .append(rs.getDouble("decline_rate")).append('\n');
        });
        byte[] csv = sb.toString().getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=payment_decline_data.csv")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(csv);
    }

    @PostMapping("/stage/{stageNum}")
    public ResponseEntity<?> stage(@PathVariable int stageNum, @RequestParam(defaultValue = "3.0") double threshold) {
        requireData();
        int cd = PaymentDataGenerator.CURRENT_DAY;
        int ch = PaymentDataGenerator.CURRENT_HOUR;
        return switch (stageNum) {
            case 1 -> ResponseEntity.ok(Stage1RollupWow.run(jdbc, cd, ch, threshold));
            case 2 -> ResponseEntity.ok(Stage2Contribution.run(jdbc, cd, ch, threshold));
            case 3 -> ResponseEntity.ok(Stage3WowGranular.run(jdbc, cd, ch, threshold));
            default -> ResponseEntity.badRequest().body(Map.of("detail", "Stage must be 1, 2 or 3"));
        };
    }

    /** Full raw dataset -- every hourly row, all 5 weeks, for client-side
     * filtering in the Raw Data tab. Columns aliased to match the JSON shape
     * the frontend already expects (camelCase, same as PaymentRow's fields). */
    @GetMapping("/raw")
    public List<Map<String, Object>> raw() {
        requireData();
        return jdbc.queryForList("""
                SELECT week AS "week", day_of_week AS "dayOfWeek", hour_of_day AS "hour",
                       network AS "network", geography AS "geography", entry_mode AS "entryMode",
                       purchase_type AS "purchaseType", auth_type AS "authType", channel AS "channel",
                       decline_code AS "declineCode", bin AS "bin", acquirer AS "acquirer",
                       total_count AS "totalCount", decline_count AS "declineCount", decline_rate AS "declineRate"
                FROM payment_declines
                """);
    }

    /** Filter the raw dataset down to a slice, then run Z-Score or Isolation Forest
     * on that slice's [total_count, decline_count, decline_rate] feature vector. */
    @PostMapping("/raw/detect")
    public ResponseEntity<?> rawDetect(@RequestBody RawDetectRequest req) {
        requireData();

        StringBuilder sql = new StringBuilder("""
                SELECT week AS "week", day_of_week AS "day_of_week", hour_of_day AS "hour",
                       network AS "network", geography AS "geography", entry_mode AS "entry_mode",
                       purchase_type AS "purchase_type", auth_type AS "auth_type", channel AS "channel",
                       decline_code AS "decline_code", total_count AS "total_count",
                       decline_count AS "decline_count", decline_rate AS "decline_rate"
                FROM payment_declines WHERE 1=1
                """);
        List<Object> params = new ArrayList<>();
        addFilter(sql, params, "week", req.week);
        addFilter(sql, params, "day_of_week", req.dayOfWeek);
        addFilter(sql, params, "hour_of_day", req.hour);
        addFilter(sql, params, "network", req.network);
        addFilter(sql, params, "geography", req.geography);
        addFilter(sql, params, "entry_mode", req.entryMode);
        addFilter(sql, params, "purchase_type", req.purchaseType);
        addFilter(sql, params, "auth_type", req.authType);
        addFilter(sql, params, "channel", req.channel);
        addFilter(sql, params, "decline_code", req.declineCode);

        List<Map<String, Object>> rows = jdbc.queryForList(sql.toString(), params.toArray());

        if (rows.size() < 2) {
            return ResponseEntity.status(400).body(Map.of("detail", "Need at least 2 matching rows to score — widen the filters"));
        }

        double[][] x = new double[rows.size()][3];
        for (int i = 0; i < rows.size(); i++) {
            Map<String, Object> r = rows.get(i);
            x[i][0] = ((Number) r.get("total_count")).doubleValue();
            x[i][1] = ((Number) r.get("decline_count")).doubleValue();
            x[i][2] = ((Number) r.get("decline_rate")).doubleValue();
        }
        double[][] scaled = DataProcessor.preprocessScale(x);

        Map<String, Object> detection = "isolation_forest".equals(req.method)
                ? IsolationForestDetector.detect(scaled, null, req.contamination, 100, 42)
                : ZScoreDetector.detect(scaled, null, req.threshold);

        int[] predictions = (int[]) detection.get("predictions");
        double[] scores = (double[]) detection.get("scores");

        List<Map<String, Object>> scoredRows = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            Map<String, Object> r = rows.get(i);
            Map<String, Object> m = new LinkedHashMap<>(r);
            m.put("anomaly_score", WowMath.round(scores[i], 4));
            m.put("is_anomaly", predictions[i] == 1);
            scoredRows.add(m);
        }
        scoredRows.sort((a, b) -> Double.compare((Double) b.get("anomaly_score"), (Double) a.get("anomaly_score")));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("method", req.method);
        result.put("n_rows", rows.size());
        result.put("n_anomalies", (int) scoredRows.stream().filter(m -> (Boolean) m.get("is_anomaly")).count());
        result.put("rows", scoredRows);
        return ResponseEntity.ok(result);
    }

    /** A null filter field means "any", so a clause is only added when set. */
    private static void addFilter(StringBuilder sql, List<Object> params, String column, Object value) {
        if (value == null) return;
        sql.append(" AND ").append(column).append(" = ?");
        params.add(value);
    }

    /** All 63 cells' contribution share vs strictly last week (W-1), not the 4-week average. */
    @GetMapping("/contribution-vs-last-week")
    public Map<String, Object> contributionVsLastWeek() {
        requireData();
        return ContributionVsLastWeek.run(jdbc, PaymentDataGenerator.CURRENT_DAY, PaymentDataGenerator.CURRENT_HOUR);
    }

    /** Aggregate decline patterns across all 5 weeks: weekly trend, hour-of-day, day-of-week, day×hour heatmap. */
    @GetMapping("/historical-analysis")
    public Map<String, Object> historicalAnalysis() {
        requireData();
        return HistoricalAnalysis.run(jdbc);
    }

    /** Three independently-owned contribution-shift heads over smaller
     * dimension subsets than Stage 2's full 7-dim key -- see Stage2Heads.java. */
    @PostMapping("/stage2-heads")
    public Map<String, Object> stage2Heads(@RequestParam(defaultValue = "3.0") double threshold) {
        requireData();
        return Stage2Heads.run(jdbc, PaymentDataGenerator.CURRENT_DAY, PaymentDataGenerator.CURRENT_HOUR, threshold);
    }

    /** On-demand only. Given the exact 7-dimension cell a stage has already
     * flagged, breaks it down by diagnostic dimensions (bin, acquirer) that
     * are never part of the continuously-monitored CellKey. */
    @GetMapping("/drill-down")
    public Map<String, Object> drillDown(
            @RequestParam int week, @RequestParam int dayOfWeek, @RequestParam int hour,
            @RequestParam String network, @RequestParam String geography, @RequestParam String entryMode,
            @RequestParam String purchaseType, @RequestParam String authType,
            @RequestParam String channel, @RequestParam String declineCode) {
        requireData();
        return DrillDown.run(jdbc, week, dayOfWeek, hour,
                network, geography, entryMode, purchaseType, authType, channel, declineCode);
    }
}
