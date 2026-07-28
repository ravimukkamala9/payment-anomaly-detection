package com.anomalydetection.model;

import java.util.List;

public class AnalysisRequest {
    public String dataset = "synthetic_2d";
    public int n_samples = 500;
    public ZScoreParams zscore_params = new ZScoreParams();
    public IFParams if_params = new IFParams();
    public LOFParams lof_params = new LOFParams();
    public List<String> methods = List.of("zscore", "isolation_forest", "lof");

    public static class ZScoreParams {
        public double threshold = 3.0;
    }

    public static class IFParams {
        public double contamination = 0.05;
        public int n_estimators = 100;
    }

    public static class LOFParams {
        public int n_neighbors = 20;
        public double contamination = 0.05;
        public String metric = "euclidean";
    }
}
