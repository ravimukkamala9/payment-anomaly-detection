package com.anomalydetection.detectors;

import com.anomalydetection.utils.Metrics;

import java.util.*;

/**
 * From-scratch Local Outlier Factor, port-equivalent of sklearn.neighbors.LocalOutlierFactor
 * as used in detectors/lof.py. Naive O(n^2) euclidean-distance implementation
 * (matches the Python scalability notes' own claim of O(n^2 * d) naive).
 *
 * Algorithm (standard LOF, Breunig et al.):
 *  - For each point, find k nearest neighbors (by euclidean distance) and the
 *    k-distance (distance to the k-th nearest neighbor).
 *  - reachability_distance(a, b) = max(k_distance(b), dist(a, b))
 *  - local_reachability_density(a) = 1 / (mean of reachability_distance(a, o) over o in N_k(a))
 *  - LOF(a) = mean over o in N_k(a) of ( lrd(o) / lrd(a) )
 *  - Points with the highest LOF ratios are anomalies. contamination fraction
 *    with highest LOF selected as outliers, matching sklearn's contamination-based cutoff.
 */
public class LofDetector {

    public static Map<String, Object> detect(double[][] X, int[] yTrue, int nNeighbors,
                                               double contamination, String metric) {
        long start = System.nanoTime();

        int n = X.length;
        int k = Math.min(nNeighbors, n - 1);
        if (k < 1) k = 1;

        // pairwise distance matrix (euclidean)
        double[][] dist = new double[n][n];
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                double dd = euclidean(X[i], X[j]);
                dist[i][j] = dd;
                dist[j][i] = dd;
            }
        }

        // k nearest neighbor indices + k-distance per point
        int[][] knn = new int[n][k];
        double[] kDist = new double[n];
        for (int i = 0; i < n; i++) {
            Integer[] order = new Integer[n];
            for (int j = 0; j < n; j++) order[j] = j;
            final int fi = i;
            Arrays.sort(order, Comparator.comparingDouble(j -> j == fi ? Double.POSITIVE_INFINITY : dist[fi][j]));
            for (int m = 0; m < k; m++) knn[i][m] = order[m];
            kDist[i] = dist[i][knn[i][k - 1]];
        }

        // local reachability density
        double[] lrd = new double[n];
        for (int i = 0; i < n; i++) {
            double sum = 0;
            for (int j : knn[i]) {
                double reach = Math.max(kDist[j], dist[i][j]);
                sum += reach;
            }
            double meanReach = sum / k;
            lrd[i] = meanReach > 0 ? 1.0 / meanReach : Double.POSITIVE_INFINITY;
        }

        // LOF ratio
        double[] lof = new double[n];
        for (int i = 0; i < n; i++) {
            double sum = 0;
            for (int j : knn[i]) {
                double ratio = Double.isInfinite(lrd[j]) && Double.isInfinite(lrd[i]) ? 1.0
                        : lrd[j] / (lrd[i] == 0 ? 1e-12 : lrd[i]);
                sum += ratio;
            }
            lof[i] = sum / k; // higher = more anomalous
        }

        double[] sorted = lof.clone();
        Arrays.sort(sorted);
        int nAnomExpected = (int) Math.round(contamination * n);
        if (nAnomExpected < 1) nAnomExpected = 1;
        if (nAnomExpected > n) nAnomExpected = n;
        double thresholdScore = sorted[n - nAnomExpected];

        int[] yPred = new int[n];
        for (int i = 0; i < n; i++) {
            yPred[i] = lof[i] >= thresholdScore ? 1 : 0;
        }

        double execTime = (System.nanoTime() - start) / 1e9;
        Map<String, Object> metrics = Metrics.computeMetrics(yTrue, yPred, execTime);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("method", "Local Outlier Factor");
        result.put("predictions", yPred);
        result.put("scores", lof);
        result.put("threshold", contamination);
        result.put("metrics", metrics);
        return result;
    }

    private static double euclidean(double[] a, double[] b) {
        double sum = 0;
        for (int i = 0; i < a.length; i++) {
            double diff = a[i] - b[i];
            sum += diff * diff;
        }
        return Math.sqrt(sum);
    }
}
