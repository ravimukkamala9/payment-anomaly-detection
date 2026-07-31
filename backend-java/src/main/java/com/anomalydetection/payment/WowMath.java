package com.anomalydetection.payment;

import java.util.List;

/** The downstream computation every stage performs on its two query results:
 * mean and standard deviation across a cell's weeks of history, and the
 * z-score of the current value against them. Kept separate from aggregation
 * on purpose -- the queries only group and sum, this is where "how far off
 * is this?" gets decided. */
final class WowMath {
    private WowMath() {}

    static double mean(List<Double> values) {
        return values.stream().mapToDouble(Double::doubleValue).average().orElse(0);
    }

    /** pandas .std() default: sample standard deviation, ddof=1 */
    static double std(List<Double> values) {
        if (values.size() <= 1) return 0;
        double m = mean(values);
        double sumSq = values.stream().mapToDouble(v -> (v - m) * (v - m)).sum();
        return Math.sqrt(sumSq / (values.size() - 1));
    }

    /** No history at all -> flagged outright with the z=99 sentinel, matching the Python stages. */
    static double zScore(double current, List<Double> history, double stdFloor) {
        if (history.isEmpty()) return 99.0;
        return (current - mean(history)) / Math.max(std(history), stdFloor);
    }

    static double round(double v, int places) {
        double f = Math.pow(10, places);
        return Math.round(v * f) / f;
    }
}
