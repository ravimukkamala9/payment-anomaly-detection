package com.anomalydetection.controller;

import com.anomalydetection.detectors.IsolationForestDetector;
import com.anomalydetection.detectors.ZScoreDetector;
import com.anomalydetection.payment.*;
import com.anomalydetection.utils.DataProcessor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.*;

@RestController
@RequestMapping("/api/payment")
public class PaymentController {

    private volatile List<PaymentRow> paymentDf = null;

    /** Every analysis endpoint needs generated data, so they all call this
     * instead of repeating the same null check and error body. */
    private List<PaymentRow> data() {
        if (paymentDf == null) throw new NoDataException();
        return paymentDf;
    }

    private static class NoDataException extends RuntimeException {}

    @ExceptionHandler(NoDataException.class)
    public ResponseEntity<?> handleNoData() {
        return ResponseEntity.badRequest().body(Map.of("detail", "Generate data first"));
    }

    @PostMapping("/generate")
    public Map<String, Object> generate() {
        paymentDf = PaymentDataGenerator.generate(42);

        Set<String> distinctCells = new HashSet<>();
        for (PaymentRow r : paymentDf) distinctCells.add(r.cellKey());

        Map<String, Object> currentWindow = new LinkedHashMap<>();
        currentWindow.put("week", 0);
        currentWindow.put("day_of_week", PaymentDataGenerator.CURRENT_DAY);
        currentWindow.put("hour", PaymentDataGenerator.CURRENT_HOUR);
        currentWindow.put("label", "Monday 14:00");

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("n_rows", paymentDf.size());
        resp.put("n_cells", distinctCells.size());
        resp.put("weeks", 5);
        resp.put("current_window", currentWindow);
        resp.put("anomalies_injected", PaymentDataGenerator.getAnomalyInfo());
        resp.put("decline_codes_total", 40);
        return resp;
    }

    @GetMapping("/download")
    public ResponseEntity<byte[]> download() {
        if (paymentDf == null) {
            return ResponseEntity.status(404).body("Generate data first via POST /payment/generate".getBytes(StandardCharsets.UTF_8));
        }
        StringBuilder sb = new StringBuilder();
        sb.append("week,day_of_week,hour,network,geography,entry_mode,purchase_type,auth_type,channel,decline_code,total_count,decline_count,decline_rate\n");
        for (PaymentRow r : paymentDf) {
            sb.append(r.week).append(',').append(r.dayOfWeek).append(',').append(r.hour).append(',')
                    .append(r.network).append(',').append(r.geography).append(',').append(r.entryMode).append(',')
                    .append(r.purchaseType).append(',').append(r.authType).append(',').append(r.channel).append(',')
                    .append(r.declineCode).append(',').append(r.totalCount).append(',').append(r.declineCount).append(',')
                    .append(r.declineRate).append('\n');
        }
        byte[] csv = sb.toString().getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=payment_decline_data.csv")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(csv);
    }

    @PostMapping("/stage/{stageNum}")
    public ResponseEntity<?> stage(@PathVariable int stageNum, @RequestParam(defaultValue = "3.0") double threshold) {
        List<PaymentRow> df = data();
        int cd = PaymentDataGenerator.CURRENT_DAY;
        int ch = PaymentDataGenerator.CURRENT_HOUR;
        return switch (stageNum) {
            case 1 -> ResponseEntity.ok(Stage1RollupWow.run(df, cd, ch, threshold));
            case 2 -> ResponseEntity.ok(Stage2Contribution.run(df, cd, ch, threshold));
            case 3 -> ResponseEntity.ok(Stage3WowGranular.run(df, cd, ch, threshold));
            default -> ResponseEntity.badRequest().body(Map.of("detail", "Stage must be 1, 2 or 3"));
        };
    }

    /** Full raw dataset -- every hourly row, all 5 weeks, for client-side filtering in the Raw Data tab. */
    @GetMapping("/raw")
    public List<PaymentRow> raw() {
        return data();
    }

    /** Filter the raw dataset down to a slice, then run Z-Score or Isolation Forest
     * on that slice's [total_count, decline_count, decline_rate] feature vector. */
    @PostMapping("/raw/detect")
    public ResponseEntity<?> rawDetect(@RequestBody RawDetectRequest req) {
        List<PaymentRow> rows = data().stream().filter(r -> matchesFilters(r, req)).toList();

        if (rows.size() < 2) {
            return ResponseEntity.status(400).body(Map.of("detail", "Need at least 2 matching rows to score — widen the filters"));
        }

        double[][] x = new double[rows.size()][3];
        for (int i = 0; i < rows.size(); i++) {
            PaymentRow r = rows.get(i);
            x[i][0] = r.totalCount;
            x[i][1] = r.declineCount;
            x[i][2] = r.declineRate;
        }
        double[][] scaled = DataProcessor.preprocessScale(x);

        Map<String, Object> detection = "isolation_forest".equals(req.method)
                ? IsolationForestDetector.detect(scaled, null, req.contamination, 100, 42)
                : ZScoreDetector.detect(scaled, null, req.threshold);

        int[] predictions = (int[]) detection.get("predictions");
        double[] scores = (double[]) detection.get("scores");

        List<Map<String, Object>> scoredRows = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            PaymentRow r = rows.get(i);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("week", r.week); m.put("day_of_week", r.dayOfWeek); m.put("hour", r.hour);
            m.put("network", r.network); m.put("geography", r.geography); m.put("entry_mode", r.entryMode);
            m.put("purchase_type", r.purchaseType); m.put("auth_type", r.authType); m.put("channel", r.channel);
            m.put("decline_code", r.declineCode);
            m.put("total_count", r.totalCount); m.put("decline_count", r.declineCount); m.put("decline_rate", r.declineRate);
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

    /** A null filter field means "any", so each clause only constrains when set. */
    private static boolean matchesFilters(PaymentRow r, RawDetectRequest q) {
        return matches(q.week, r.week)
                && matches(q.dayOfWeek, r.dayOfWeek)
                && matches(q.hour, r.hour)
                && matches(q.network, r.network)
                && matches(q.geography, r.geography)
                && matches(q.entryMode, r.entryMode)
                && matches(q.purchaseType, r.purchaseType)
                && matches(q.authType, r.authType)
                && matches(q.channel, r.channel)
                && matches(q.declineCode, r.declineCode);
    }

    private static boolean matches(Integer filter, int value) {
        return filter == null || filter == value;
    }

    private static boolean matches(String filter, String value) {
        return filter == null || filter.equals(value);
    }

    /** All 63 cells' contribution share vs strictly last week (W-1), not the 4-week average. */
    @GetMapping("/contribution-vs-last-week")
    public Map<String, Object> contributionVsLastWeek() {
        return ContributionVsLastWeek.run(data(), PaymentDataGenerator.CURRENT_DAY, PaymentDataGenerator.CURRENT_HOUR);
    }

    /** Aggregate decline patterns across all 5 weeks: weekly trend, hour-of-day, day-of-week, day×hour heatmap. */
    @GetMapping("/historical-analysis")
    public Map<String, Object> historicalAnalysis() {
        return HistoricalAnalysis.run(data());
    }

    /** Three independently-owned contribution-shift heads over smaller
     * dimension subsets than Stage 2's full 7-dim key -- see Stage2Heads.java. */
    @PostMapping("/stage2-heads")
    public Map<String, Object> stage2Heads(@RequestParam(defaultValue = "3.0") double threshold) {
        return Stage2Heads.run(data(), PaymentDataGenerator.CURRENT_DAY, PaymentDataGenerator.CURRENT_HOUR, threshold);
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
        return DrillDown.run(data(), week, dayOfWeek, hour,
                network, geography, entryMode, purchaseType, authType, channel, declineCode);
    }
}
