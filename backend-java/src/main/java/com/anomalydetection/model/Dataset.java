package com.anomalydetection.model;

import java.util.List;

/** Simple in-memory tabular dataset: numeric feature columns + optional integer labels. */
public class Dataset {
    public List<String> featureCols;
    public double[][] X; // raw (unscaled) numeric matrix, rows x featureCols.size()
    public int[] labels; // nullable

    public Dataset(List<String> featureCols, double[][] X, int[] labels) {
        this.featureCols = featureCols;
        this.X = X;
        this.labels = labels;
    }

    public int nSamples() {
        return X.length;
    }
}
