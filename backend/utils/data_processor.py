import pandas as pd
import numpy as np
from typing import Optional, Tuple
import io


def generate_synthetic_dataset(n_samples: int = 500, contamination: float = 0.05, seed: int = 42) -> pd.DataFrame:
    rng = np.random.RandomState(seed)
    n_anomalies = int(n_samples * contamination)
    n_normal = n_samples - n_anomalies

    # Normal cluster
    X_normal = rng.randn(n_normal, 2) * np.array([1.5, 1.0]) + np.array([2.0, 3.0])
    # Second normal cluster
    X_normal2 = rng.randn(n_normal // 3, 2) * np.array([0.5, 0.8]) + np.array([-2.0, -1.0])
    X_all_normal = np.vstack([X_normal, X_normal2])

    # Anomalies scattered far from clusters
    X_anomaly = rng.uniform(low=-8, high=8, size=(n_anomalies, 2))

    X = np.vstack([X_all_normal, X_anomaly])
    labels = np.hstack([
        np.zeros(len(X_all_normal), dtype=int),
        np.ones(n_anomalies, dtype=int)
    ])

    idx = rng.permutation(len(X))
    X, labels = X[idx], labels[idx]

    df = pd.DataFrame(X, columns=["feature_1", "feature_2"])
    df["true_label"] = labels
    return df


def generate_credit_card_dataset(n_samples: int = 1000, seed: int = 42) -> pd.DataFrame:
    """Synthetic credit card-like dataset with multiple features."""
    rng = np.random.RandomState(seed)
    n_anomalies = int(n_samples * 0.03)
    n_normal = n_samples - n_anomalies

    # Normal transactions
    amount_normal = np.abs(rng.lognormal(mean=3.5, sigma=1.2, size=n_normal))
    hour_normal = rng.randint(6, 22, size=n_normal).astype(float)
    merchant_cat = rng.randint(1, 10, size=n_normal).astype(float)
    dist_from_home = np.abs(rng.exponential(scale=15, size=n_normal))
    velocity = rng.poisson(lam=2, size=n_normal).astype(float)

    # Anomalous transactions
    amount_anom = np.abs(rng.lognormal(mean=6.5, sigma=1.5, size=n_anomalies))
    hour_anom = rng.choice([1, 2, 3, 4, 5], size=n_anomalies).astype(float)
    merchant_cat_anom = rng.randint(1, 10, size=n_anomalies).astype(float)
    dist_anom = np.abs(rng.exponential(scale=200, size=n_anomalies))
    velocity_anom = rng.poisson(lam=15, size=n_anomalies).astype(float)

    amounts = np.hstack([amount_normal, amount_anom])
    hours = np.hstack([hour_normal, hour_anom])
    cats = np.hstack([merchant_cat, merchant_cat_anom])
    dists = np.hstack([dist_from_home, dist_anom])
    vels = np.hstack([velocity, velocity_anom])
    labels = np.hstack([np.zeros(n_normal, dtype=int), np.ones(n_anomalies, dtype=int)])

    idx = rng.permutation(n_samples)
    df = pd.DataFrame({
        "amount": amounts[idx],
        "hour_of_day": hours[idx],
        "merchant_category": cats[idx],
        "distance_from_home": dists[idx],
        "transaction_velocity": vels[idx],
        "true_label": labels[idx],
    })
    return df


def generate_network_dataset(n_samples: int = 800, seed: int = 42) -> pd.DataFrame:
    """Synthetic network traffic dataset."""
    rng = np.random.RandomState(seed)
    n_anomalies = int(n_samples * 0.04)
    n_normal = n_samples - n_anomalies

    bytes_sent_n = np.abs(rng.lognormal(4.0, 1.0, n_normal))
    bytes_recv_n = np.abs(rng.lognormal(5.0, 1.2, n_normal))
    duration_n = np.abs(rng.exponential(30, n_normal))
    packets_n = np.abs(rng.poisson(50, n_normal)).astype(float)
    port_entropy_n = rng.uniform(0, 2, n_normal)

    bytes_sent_a = np.abs(rng.lognormal(8.0, 1.5, n_anomalies))
    bytes_recv_a = np.abs(rng.lognormal(2.0, 2.0, n_anomalies))
    duration_a = np.abs(rng.exponential(200, n_anomalies))
    packets_a = np.abs(rng.poisson(500, n_anomalies)).astype(float)
    port_entropy_a = rng.uniform(3, 5, n_anomalies)

    bs = np.hstack([bytes_sent_n, bytes_sent_a])
    br = np.hstack([bytes_recv_n, bytes_recv_a])
    dur = np.hstack([duration_n, duration_a])
    pkt = np.hstack([packets_n, packets_a])
    pe = np.hstack([port_entropy_n, port_entropy_a])
    labels = np.hstack([np.zeros(n_normal, dtype=int), np.ones(n_anomalies, dtype=int)])

    idx = rng.permutation(n_samples)
    df = pd.DataFrame({
        "bytes_sent": bs[idx],
        "bytes_received": br[idx],
        "duration_sec": dur[idx],
        "packet_count": pkt[idx],
        "port_entropy": pe[idx],
        "true_label": labels[idx],
    })
    return df


DATASETS = {
    "synthetic_2d": generate_synthetic_dataset,
    "credit_card": generate_credit_card_dataset,
    "network_traffic": generate_network_dataset,
}


def parse_uploaded_csv(content: bytes) -> pd.DataFrame:
    df = pd.read_csv(io.BytesIO(content))
    df.columns = [c.strip().lower().replace(" ", "_") for c in df.columns]
    return df


def preprocess(df: pd.DataFrame) -> Tuple[np.ndarray, Optional[np.ndarray], list]:
    """Return feature matrix, optional true labels, and feature names."""
    label_col = None
    for candidate in ["true_label", "label", "anomaly", "is_fraud", "class"]:
        if candidate in df.columns:
            label_col = candidate
            break

    feature_cols = [c for c in df.select_dtypes(include=[np.number]).columns if c != label_col]
    X = df[feature_cols].fillna(df[feature_cols].median()).values

    # Standardize
    mean = X.mean(axis=0)
    std = X.std(axis=0)
    std[std == 0] = 1
    X_scaled = (X - mean) / std

    y = df[label_col].values.astype(int) if label_col else None
    return X_scaled, y, feature_cols, mean.tolist(), std.tolist()
