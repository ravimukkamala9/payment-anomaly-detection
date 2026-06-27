import numpy as np
import time
from typing import Optional
from utils.metrics import compute_metrics


def detect(X: np.ndarray, y_true: Optional[np.ndarray], threshold: float = 3.0) -> dict:
    start = time.perf_counter()

    # Compute per-feature z-scores, then use max absolute z-score per sample
    z_scores = np.abs((X - X.mean(axis=0)) / (X.std(axis=0) + 1e-8))
    max_z = z_scores.max(axis=1)
    y_pred = (max_z > threshold).astype(int)

    exec_time = time.perf_counter() - start
    metrics = compute_metrics(y_true, y_pred, exec_time)

    return {
        "method": "Z-Score",
        "predictions": y_pred.tolist(),
        "scores": max_z.tolist(),
        "threshold": threshold,
        "metrics": metrics,
    }
