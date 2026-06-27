export interface Metrics {
  execution_time_ms: number;
  n_anomalies: number;
  anomaly_rate: number;
  precision: number | null;
  recall: number | null;
  f1_score: number | null;
  false_positive_rate: number | null;
  accuracy: number | null;
  confusion_matrix: number[][] | null;
}

export interface DetectionResult {
  method: string;
  predictions: number[];
  scores: number[];
  threshold: number;
  metrics: Metrics;
}

export interface ScalabilityInfo {
  time_complexity: string;
  space_complexity: string;
  scales_to: string;
  parallelizable: boolean;
}

export interface AnalysisResponse {
  dataset: string;
  n_samples: number;
  feature_names: string[];
  raw_columns: string[];
  data: number[][];
  true_labels: number[] | null;
  results: {
    zscore?: DetectionResult;
    isolation_forest?: DetectionResult;
    lof?: DetectionResult;
  };
  scalability: {
    zscore: ScalabilityInfo;
    isolation_forest: ScalabilityInfo;
    lof: ScalabilityInfo;
  };
}

export interface DatasetInfo {
  id: string;
  name: string;
  description: string;
  features: string[];
  has_labels: boolean;
}

export type MethodKey = 'zscore' | 'isolation_forest' | 'lof';

export interface AnalysisParams {
  dataset: string;
  n_samples: number;
  methods: MethodKey[];
  zscore_params: { threshold: number };
  if_params: { contamination: number; n_estimators: number };
  lof_params: { n_neighbors: number; contamination: number; metric: string };
}
