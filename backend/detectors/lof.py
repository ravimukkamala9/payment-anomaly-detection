import numpy as np
import time
from typing import Optional
from sklearn.neighbors import LocalOutlierFactor
from utils.metrics import compute_metrics


def detect(
    X: np.ndarray,
    y_true: Optional[np.ndarray],
    n_neighbors: int = 20,
    contamination: float = 0.05,
    metric: str = "euclidean",
) -> dict:
    start = time.perf_counter()

    clf = LocalOutlierFactor(
        n_neighbors=n_neighbors,
        contamination=contamination,
        metric=metric,
        novelty=False,
    )
    raw_pred = clf.fit_predict(X)
    y_pred = (raw_pred == -1).astype(int)
    # negative_outlier_factor_: more negative = more anomalous; negate for intuitive "higher = more anomalous"
    scores = -clf.negative_outlier_factor_

    exec_time = time.perf_counter() - start
    metrics = compute_metrics(y_true, y_pred, exec_time)

    return {
        "method": "Local Outlier Factor",
        "predictions": y_pred.tolist(),
        "scores": scores.tolist(),
        "threshold": contamination,
        "metrics": metrics,
    }
