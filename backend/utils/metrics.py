import numpy as np
from typing import Optional
from sklearn.metrics import precision_score, recall_score, f1_score, confusion_matrix


def compute_metrics(y_true: Optional[np.ndarray], y_pred: np.ndarray, exec_time: float) -> dict:
    base = {
        "execution_time_ms": round(exec_time * 1000, 2),
        "n_anomalies": int(y_pred.sum()),
        "anomaly_rate": round(float(y_pred.mean()) * 100, 2),
    }

    if y_true is None:
        base.update({
            "precision": None,
            "recall": None,
            "f1_score": None,
            "false_positive_rate": None,
            "accuracy": None,
            "confusion_matrix": None,
        })
        return base

    precision = precision_score(y_true, y_pred, zero_division=0)
    recall = recall_score(y_true, y_pred, zero_division=0)
    f1 = f1_score(y_true, y_pred, zero_division=0)
    cm = confusion_matrix(y_true, y_pred).tolist()
    tn, fp, fn, tp = confusion_matrix(y_true, y_pred).ravel() if len(np.unique(y_true)) == 2 else (0, 0, 0, 0)
    fpr = fp / (fp + tn) if (fp + tn) > 0 else 0.0
    accuracy = (tp + tn) / len(y_true)

    base.update({
        "precision": round(float(precision), 4),
        "recall": round(float(recall), 4),
        "f1_score": round(float(f1), 4),
        "false_positive_rate": round(float(fpr), 4),
        "accuracy": round(float(accuracy), 4),
        "confusion_matrix": cm,
    })
    return base
