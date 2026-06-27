import numpy as np
import time
from typing import Optional
from sklearn.ensemble import IsolationForest
from utils.metrics import compute_metrics


def detect(
    X: np.ndarray,
    y_true: Optional[np.ndarray],
    contamination: float = 0.05,
    n_estimators: int = 100,
    max_samples: str = "auto",
    random_state: int = 42,
) -> dict:
    start = time.perf_counter()

    clf = IsolationForest(
        contamination=contamination,
        n_estimators=n_estimators,
        max_samples=max_samples,
        random_state=random_state,
    )
    raw_pred = clf.fit_predict(X)
    # sklearn returns -1 for anomalies, 1 for normal
    y_pred = (raw_pred == -1).astype(int)
    # Anomaly scores: negate so higher = more anomalous
    scores = -clf.score_samples(X)

    exec_time = time.perf_counter() - start
    metrics = compute_metrics(y_true, y_pred, exec_time)

    return {
        "method": "Isolation Forest",
        "predictions": y_pred.tolist(),
        "scores": scores.tolist(),
        "threshold": contamination,
        "metrics": metrics,
    }
