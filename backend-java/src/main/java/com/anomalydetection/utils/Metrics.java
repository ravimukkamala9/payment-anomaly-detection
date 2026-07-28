package com.anomalydetection.utils;

import java.util.LinkedHashMap;
import java.util.Map;

public class Metrics {

    /**
     * Port of utils/metrics.py compute_metrics.
     * yTrue may be null (unlabeled data) -> precision/recall/etc are null.
     */
    public static Map<String, Object> computeMetrics(int[] yTrue, int[] yPred, double execTimeSeconds) {
        Map<String, Object> base = new LinkedHashMap<>();
        base.put("execution_time_ms", round(execTimeSeconds * 1000.0, 2));
        int nAnomalies = 0;
        for (int v : yPred) nAnomalies += v;
        base.put("n_anomalies", nAnomalies);
        base.put("anomaly_rate", round((double) nAnomalies / yPred.length * 100.0, 2));

        if (yTrue == null) {
            base.put("precision", null);
            base.put("recall", null);
            base.put("f1_score", null);
            base.put("false_positive_rate", null);
            base.put("accuracy", null);
            base.put("confusion_matrix", null);
            return base;
        }

        int tp = 0, fp = 0, fn = 0, tn = 0;
        for (int i = 0; i < yTrue.length; i++) {
            int t = yTrue[i], p = yPred[i];
            if (t == 1 && p == 1) tp++;
            else if (t == 0 && p == 1) fp++;
            else if (t == 1 && p == 0) fn++;
            else tn++;
        }

        double precision = (tp + fp) > 0 ? (double) tp / (tp + fp) : 0.0;
        double recall = (tp + fn) > 0 ? (double) tp / (tp + fn) : 0.0;
        double f1 = (precision + recall) > 0 ? 2 * precision * recall / (precision + recall) : 0.0;
        double fpr = (fp + tn) > 0 ? (double) fp / (fp + tn) : 0.0;
        double accuracy = (double) (tp + tn) / yTrue.length;

        // confusion_matrix as sklearn.confusion_matrix(y_true, y_pred): [[tn, fp], [fn, tp]]
        int[][] cm = new int[][]{{tn, fp}, {fn, tp}};

        base.put("precision", round(precision, 4));
        base.put("recall", round(recall, 4));
        base.put("f1_score", round(f1, 4));
        base.put("false_positive_rate", round(fpr, 4));
        base.put("accuracy", round(accuracy, 4));
        base.put("confusion_matrix", cm);
        return base;
    }

    public static double round(double v, int places) {
        double factor = Math.pow(10, places);
        return Math.round(v * factor) / factor;
    }
}
