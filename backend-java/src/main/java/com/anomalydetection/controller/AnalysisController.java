package com.anomalydetection.controller;

import com.anomalydetection.detectors.IsolationForestDetector;
import com.anomalydetection.detectors.LofDetector;
import com.anomalydetection.detectors.ZScoreDetector;
import com.anomalydetection.model.AnalysisRequest;
import com.anomalydetection.model.Dataset;
import com.anomalydetection.utils.DataProcessor;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.*;

@RestController
@RequestMapping("/api")
public class AnalysisController {

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of("status", "ok");
    }

    @GetMapping("/datasets")
    public Map<String, Object> listDatasets() {
        List<Map<String, Object>> datasets = new ArrayList<>();

        Map<String, Object> d1 = new LinkedHashMap<>();
        d1.put("id", "synthetic_2d");
        d1.put("name", "Synthetic 2D");
        d1.put("description", "Two Gaussian clusters with scattered outliers. Great for visualizing detection boundaries.");
        d1.put("features", List.of("feature_1", "feature_2"));
        d1.put("has_labels", true);
        datasets.add(d1);

        Map<String, Object> d2 = new LinkedHashMap<>();
        d2.put("id", "credit_card");
        d2.put("name", "Credit Card Fraud");
        d2.put("description", "Simulated transaction data with rare fraudulent transactions (3% contamination).");
        d2.put("features", List.of("amount", "hour_of_day", "merchant_category", "distance_from_home", "transaction_velocity"));
        d2.put("has_labels", true);
        datasets.add(d2);

        Map<String, Object> d3 = new LinkedHashMap<>();
        d3.put("id", "network_traffic");
        d3.put("name", "Network Traffic");
        d3.put("description", "Simulated network flow data with intrusion attempts (4% contamination).");
        d3.put("features", List.of("bytes_sent", "bytes_received", "duration_sec", "packet_count", "port_entropy"));
        d3.put("has_labels", true);
        datasets.add(d3);

        return Map.of("datasets", datasets);
    }

    @PostMapping("/analyze")
    public ResponseEntity<?> analyze(@RequestBody AnalysisRequest req) {
        Dataset ds;
        switch (req.dataset) {
            case "synthetic_2d": ds = DataProcessor.generateSynthetic2D(req.n_samples, 42); break;
            case "credit_card": ds = DataProcessor.generateCreditCard(req.n_samples, 42); break;
            case "network_traffic": ds = DataProcessor.generateNetworkTraffic(req.n_samples, 42); break;
            default:
                return ResponseEntity.status(404).body(Map.of("detail", "Dataset '" + req.dataset + "' not found"));
        }

        double[][] scaled = DataProcessor.preprocessScale(ds.X);

        Map<String, Object> results = new LinkedHashMap<>();
        if (req.methods.contains("zscore")) {
            results.put("zscore", ZScoreDetector.detect(scaled, ds.labels, req.zscore_params.threshold));
        }
        if (req.methods.contains("isolation_forest")) {
            results.put("isolation_forest", IsolationForestDetector.detect(scaled, ds.labels,
                    req.if_params.contamination, req.if_params.n_estimators, 42));
        }
        if (req.methods.contains("lof")) {
            results.put("lof", LofDetector.detect(scaled, ds.labels, req.lof_params.n_neighbors,
                    req.lof_params.contamination, req.lof_params.metric));
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("dataset", req.dataset);
        response.put("n_samples", ds.nSamples());
        response.put("feature_names", ds.featureCols);
        response.put("raw_columns", ds.featureCols);
        response.put("data", ds.X);
        response.put("true_labels", ds.labels);
        response.put("results", results);
        response.put("scalability", scalabilityNotes());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/analyze/upload")
    public ResponseEntity<?> analyzeUpload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(defaultValue = "3.0") double zscore_threshold,
            @RequestParam(defaultValue = "0.05") double if_contamination,
            @RequestParam(defaultValue = "100") int if_n_estimators,
            @RequestParam(defaultValue = "20") int lof_n_neighbors,
            @RequestParam(defaultValue = "0.05") double lof_contamination
    ) {
        List<String> labelCandidates = List.of("true_label", "label", "anomaly", "is_fraud", "class");
        List<String> allCols;
        List<Map<String, String>> rawRows = new ArrayList<>();
        try (Reader reader = new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8)) {
            CSVFormat format = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).build();
            CSVParser parser = format.parse(reader);
            allCols = new ArrayList<>();
            for (String h : parser.getHeaderNames()) {
                allCols.add(h.strip().toLowerCase().replace(" ", "_"));
            }
            for (CSVRecord rec : parser) {
                Map<String, String> row = new LinkedHashMap<>();
                for (int i = 0; i < allCols.size(); i++) {
                    row.put(allCols.get(i), rec.get(i));
                }
                rawRows.add(row);
            }
        } catch (Exception e) {
            return ResponseEntity.status(400).body(Map.of("detail", "Failed to parse CSV: " + e.getMessage()));
        }

        String labelCol = null;
        for (String c : labelCandidates) {
            if (allCols.contains(c)) { labelCol = c; break; }
        }

        // detect numeric columns (excluding label col)
        List<String> featureCols = new ArrayList<>();
        for (String c : allCols) {
            if (c.equals(labelCol)) continue;
            boolean numeric = true;
            for (Map<String, String> row : rawRows) {
                String v = row.get(c);
                if (v == null || v.isBlank()) continue;
                try { Double.parseDouble(v); } catch (NumberFormatException e) { numeric = false; break; }
            }
            if (numeric) featureCols.add(c);
        }

        int n = rawRows.size();
        double[][] X = new double[n][featureCols.size()];
        // compute median for NaN fill
        double[] medians = new double[featureCols.size()];
        for (int j = 0; j < featureCols.size(); j++) {
            List<Double> vals = new ArrayList<>();
            for (Map<String, String> row : rawRows) {
                String v = row.get(featureCols.get(j));
                if (v != null && !v.isBlank()) {
                    try { vals.add(Double.parseDouble(v)); } catch (NumberFormatException ignored) {}
                }
            }
            Collections.sort(vals);
            medians[j] = vals.isEmpty() ? 0 : vals.get(vals.size() / 2);
        }
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < featureCols.size(); j++) {
                String v = rawRows.get(i).get(featureCols.get(j));
                double val = medians[j];
                if (v != null && !v.isBlank()) {
                    try { val = Double.parseDouble(v); } catch (NumberFormatException ignored) {}
                }
                X[i][j] = val;
            }
        }

        int[] labels = null;
        if (labelCol != null) {
            labels = new int[n];
            for (int i = 0; i < n; i++) {
                String v = rawRows.get(i).get(labelCol);
                try { labels[i] = (int) Double.parseDouble(v); } catch (Exception e) { labels[i] = 0; }
            }
        }

        List<String> rawCols = new ArrayList<>();
        for (String c : allCols) if (!labelCandidates.contains(c)) rawCols.add(c);

        Object[][] rawData = new Object[n][rawCols.size()];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < rawCols.size(); j++) {
                String v = rawRows.get(i).get(rawCols.get(j));
                Object val = v;
                try { val = Double.parseDouble(v); } catch (Exception ignored) {}
                rawData[i][j] = val;
            }
        }

        double[][] scaled = DataProcessor.preprocessScale(X);

        Map<String, Object> results = new LinkedHashMap<>();
        results.put("zscore", ZScoreDetector.detect(scaled, labels, zscore_threshold));
        results.put("isolation_forest", IsolationForestDetector.detect(scaled, labels, if_contamination, if_n_estimators, 42));
        results.put("lof", LofDetector.detect(scaled, labels, lof_n_neighbors, lof_contamination, "euclidean"));

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("dataset", file.getOriginalFilename());
        response.put("n_samples", n);
        response.put("feature_names", featureCols);
        response.put("raw_columns", rawCols);
        response.put("data", rawData);
        response.put("true_labels", labels);
        response.put("results", results);
        response.put("scalability", scalabilityNotes());
        return ResponseEntity.ok(response);
    }

    private Map<String, Object> scalabilityNotes() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("zscore", Map.of(
                "time_complexity", "O(n·d)",
                "space_complexity", "O(n·d)",
                "scales_to", "Millions of records",
                "parallelizable", true));
        m.put("isolation_forest", Map.of(
                "time_complexity", "O(n·t·log(ψ))",
                "space_complexity", "O(t·ψ)",
                "scales_to", "Hundreds of thousands",
                "parallelizable", true));
        m.put("lof", Map.of(
                "time_complexity", "O(n²·d) naive",
                "space_complexity", "O(n²)",
                "scales_to", "Tens of thousands",
                "parallelizable", false));
        return m;
    }
}
