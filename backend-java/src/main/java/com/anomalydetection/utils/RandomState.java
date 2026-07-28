package com.anomalydetection.utils;

import java.util.Random;

/**
 * RNG helper approximating numpy.random.RandomState's distributions used by
 * the Python payment/data generators. Exact bit-for-bit reproduction of numpy's
 * Mersenne Twister isn't required — only matching statistical shape (mean/std/etc).
 */
public class RandomState {
    private final Random rnd;
    private Double spareGaussian = null;

    public RandomState(long seed) {
        this.rnd = new Random(seed);
    }

    /** Standard normal via Box-Muller. */
    public double nextGaussian() {
        return rnd.nextGaussian();
    }

    public double normal(double mean, double std) {
        return mean + std * nextGaussian();
    }

    public double uniform(double low, double high) {
        return low + (high - low) * rnd.nextDouble();
    }

    public int randint(int low, int highExclusive) {
        return low + rnd.nextInt(highExclusive - low);
    }

    public double lognormal(double mean, double sigma) {
        return Math.exp(normal(mean, sigma));
    }

    public double exponential(double scale) {
        double u = rnd.nextDouble();
        while (u <= 0) u = rnd.nextDouble();
        return -Math.log(u) * scale;
    }

    /** Knuth's algorithm for Poisson. */
    public int poisson(double lambda) {
        double L = Math.exp(-lambda);
        int k = 0;
        double p = 1.0;
        do {
            k++;
            p *= rnd.nextDouble();
        } while (p > L);
        return k - 1;
    }

    public <T> T choice(T[] arr) {
        return arr[rnd.nextInt(arr.length)];
    }

    public double nextDouble() {
        return rnd.nextDouble();
    }

    /** Fisher-Yates permutation of indices 0..n-1. */
    public int[] permutation(int n) {
        int[] idx = new int[n];
        for (int i = 0; i < n; i++) idx[i] = i;
        for (int i = n - 1; i > 0; i--) {
            int j = rnd.nextInt(i + 1);
            int tmp = idx[i]; idx[i] = idx[j]; idx[j] = tmp;
        }
        return idx;
    }
}
