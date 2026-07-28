package com.anomalydetection.utils;

import com.anomalydetection.model.Dataset;

import java.util.ArrayList;
import java.util.List;

/** Port of utils/data_processor.py generator + preprocess functions. */
public class DataProcessor {

    public static Dataset generateSynthetic2D(int nSamples, long seed) {
        RandomState rng = new RandomState(seed);
        double contamination = 0.05;
        int nAnomalies = (int) (nSamples * contamination);
        int nNormal = nSamples - nAnomalies;

        List<double[]> rows = new ArrayList<>();
        List<Integer> labels = new ArrayList<>();

        for (int i = 0; i < nNormal; i++) {
            double x = rng.nextGaussian() * 1.5 + 2.0;
            double y = rng.nextGaussian() * 1.0 + 3.0;
            rows.add(new double[]{x, y});
            labels.add(0);
        }
        int nNormal2 = nNormal / 3;
        for (int i = 0; i < nNormal2; i++) {
            double x = rng.nextGaussian() * 0.5 - 2.0;
            double y = rng.nextGaussian() * 0.8 - 1.0;
            rows.add(new double[]{x, y});
            labels.add(0);
        }
        for (int i = 0; i < nAnomalies; i++) {
            double x = rng.uniform(-8, 8);
            double y = rng.uniform(-8, 8);
            rows.add(new double[]{x, y});
            labels.add(1);
        }

        int n = rows.size();
        int[] perm = rng.permutation(n);
        double[][] X = new double[n][2];
        int[] y = new int[n];
        for (int i = 0; i < n; i++) {
            X[i] = rows.get(perm[i]);
            y[i] = labels.get(perm[i]);
        }
        return new Dataset(List.of("feature_1", "feature_2"), X, y);
    }

    public static Dataset generateCreditCard(int nSamples, long seed) {
        RandomState rng = new RandomState(seed);
        int nAnomalies = (int) (nSamples * 0.03);
        int nNormal = nSamples - nAnomalies;

        double[] amount = new double[nSamples];
        double[] hour = new double[nSamples];
        double[] cat = new double[nSamples];
        double[] dist = new double[nSamples];
        double[] vel = new double[nSamples];
        int[] label = new int[nSamples];

        for (int i = 0; i < nNormal; i++) {
            amount[i] = Math.abs(rng.lognormal(3.5, 1.2));
            hour[i] = rng.randint(6, 22);
            cat[i] = rng.randint(1, 10);
            dist[i] = Math.abs(rng.exponential(15));
            vel[i] = rng.poisson(2);
            label[i] = 0;
        }
        Integer[] hourChoices = {1, 2, 3, 4, 5};
        for (int i = 0; i < nAnomalies; i++) {
            int idx = nNormal + i;
            amount[idx] = Math.abs(rng.lognormal(6.5, 1.5));
            hour[idx] = rng.choice(hourChoices);
            cat[idx] = rng.randint(1, 10);
            dist[idx] = Math.abs(rng.exponential(200));
            vel[idx] = rng.poisson(15);
            label[idx] = 1;
        }

        int[] perm = rng.permutation(nSamples);
        double[][] X = new double[nSamples][5];
        int[] y = new int[nSamples];
        for (int i = 0; i < nSamples; i++) {
            int p = perm[i];
            X[i] = new double[]{amount[p], hour[p], cat[p], dist[p], vel[p]};
            y[i] = label[p];
        }
        return new Dataset(List.of("amount", "hour_of_day", "merchant_category", "distance_from_home", "transaction_velocity"), X, y);
    }

    public static Dataset generateNetworkTraffic(int nSamples, long seed) {
        RandomState rng = new RandomState(seed);
        int nAnomalies = (int) (nSamples * 0.04);
        int nNormal = nSamples - nAnomalies;

        double[] bs = new double[nSamples];
        double[] br = new double[nSamples];
        double[] dur = new double[nSamples];
        double[] pkt = new double[nSamples];
        double[] pe = new double[nSamples];
        int[] label = new int[nSamples];

        for (int i = 0; i < nNormal; i++) {
            bs[i] = Math.abs(rng.lognormal(4.0, 1.0));
            br[i] = Math.abs(rng.lognormal(5.0, 1.2));
            dur[i] = Math.abs(rng.exponential(30));
            pkt[i] = Math.abs(rng.poisson(50));
            pe[i] = rng.uniform(0, 2);
            label[i] = 0;
        }
        for (int i = 0; i < nAnomalies; i++) {
            int idx = nNormal + i;
            bs[idx] = Math.abs(rng.lognormal(8.0, 1.5));
            br[idx] = Math.abs(rng.lognormal(2.0, 2.0));
            dur[idx] = Math.abs(rng.exponential(200));
            pkt[idx] = Math.abs(rng.poisson(500));
            pe[idx] = rng.uniform(3, 5);
            label[idx] = 1;
        }

        int[] perm = rng.permutation(nSamples);
        double[][] X = new double[nSamples][5];
        int[] y = new int[nSamples];
        for (int i = 0; i < nSamples; i++) {
            int p = perm[i];
            X[i] = new double[]{bs[p], br[p], dur[p], pkt[p], pe[p]};
            y[i] = label[p];
        }
        return new Dataset(List.of("bytes_sent", "bytes_received", "duration_sec", "packet_count", "port_entropy"), X, y);
    }

    /** Standardize: mean/std per column, std==0 -> 1. Returns scaled matrix. */
    public static double[][] preprocessScale(double[][] X) {
        int n = X.length, d = X[0].length;
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
            std[j] = Math.sqrt(sq / n);
            if (std[j] == 0) std[j] = 1;
        }
        double[][] scaled = new double[n][d];
        for (int i = 0; i < n; i++)
            for (int j = 0; j < d; j++)
                scaled[i][j] = (X[i][j] - mean[j]) / std[j];
        return scaled;
    }
}
