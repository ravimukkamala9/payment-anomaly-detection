package com.anomalydetection.detectors;

import com.anomalydetection.utils.Metrics;

import java.util.LinkedHashMap;
import java.util.Map;

public class ZScoreDetector {

    /** Port of detectors/zscore.py detect(). */
    public static Map<String, Object> detect(double[][] X, int[] yTrue, double threshold) {
        long start = System.nanoTime();

        int n = X.length;
        int d = X[0].length;
        double[] mean = new double[d];
        double[] std = new double[d];
        for (int j = 0; j < d; j++) {
            double sum = 0;
            for (int i = 0; i < n; i++) sum += X[i][j];
            mean[j] = sum / n;
        }
        for (int j = 0; j < d; j++) {
            double sq = 0;
            for (int i = 0; i < n; i++) sq += Math.pow(X[i][j] - mean[j], 2);
            std[j] = Math.sqrt(sq / n); // population std, matches numpy default ddof=0
        }

        double[] maxZ = new double[n];
        int[] yPred = new int[n];
        for (int i = 0; i < n; i++) {
            double m = 0;
            for (int j = 0; j < d; j++) {
                double z = Math.abs((X[i][j] - mean[j]) / (std[j] + 1e-8));
                if (z > m) m = z;
            }
            maxZ[i] = m;
            yPred[i] = m > threshold ? 1 : 0;
        }

        double execTime = (System.nanoTime() - start) / 1e9;
        Map<String, Object> metrics = Metrics.computeMetrics(yTrue, yPred, execTime);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("method", "Z-Score");
        result.put("predictions", yPred);
        result.put("scores", maxZ);
        result.put("threshold", threshold);
        result.put("metrics", metrics);
        return result;
    }
}
