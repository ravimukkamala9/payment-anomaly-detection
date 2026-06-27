from fastapi import FastAPI, UploadFile, File, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel, Field
from typing import Optional, List
import numpy as np
import sys
import os

sys.path.insert(0, os.path.dirname(__file__))

from utils.data_processor import DATASETS, parse_uploaded_csv, preprocess
from utils.payment_data_generator import generate_payment_data, get_anomaly_info, CURRENT_DAY, CURRENT_HOUR
from detectors import zscore, isolation_forest, lof
from stages import stage1_rollup_wow, stage2_contribution, stage3_wow_granular
from fastapi.responses import StreamingResponse

app = FastAPI(title="Anomaly Detection API", version="1.0.0")

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)


class ZScoreParams(BaseModel):
    threshold: float = Field(default=3.0, ge=0.5, le=10.0)


class IFParams(BaseModel):
    contamination: float = Field(default=0.05, ge=0.01, le=0.5)
    n_estimators: int = Field(default=100, ge=10, le=500)


class LOFParams(BaseModel):
    n_neighbors: int = Field(default=20, ge=2, le=100)
    contamination: float = Field(default=0.05, ge=0.01, le=0.5)
    metric: str = Field(default="euclidean")


class AnalysisRequest(BaseModel):
    dataset: str = "synthetic_2d"
    n_samples: int = Field(default=500, ge=50, le=5000)
    zscore_params: ZScoreParams = ZScoreParams()
    if_params: IFParams = IFParams()
    lof_params: LOFParams = LOFParams()
    methods: List[str] = ["zscore", "isolation_forest", "lof"]


# In-memory dataset cache keyed by session
_dataset_cache: dict = {}


@app.get("/datasets")
def list_datasets():
    return {
        "datasets": [
            {
                "id": "synthetic_2d",
                "name": "Synthetic 2D",
                "description": "Two Gaussian clusters with scattered outliers. Great for visualizing detection boundaries.",
                "features": ["feature_1", "feature_2"],
                "has_labels": True,
            },
            {
                "id": "credit_card",
                "name": "Credit Card Fraud",
                "description": "Simulated transaction data with rare fraudulent transactions (3% contamination).",
                "features": ["amount", "hour_of_day", "merchant_category", "distance_from_home", "transaction_velocity"],
                "has_labels": True,
            },
            {
                "id": "network_traffic",
                "name": "Network Traffic",
                "description": "Simulated network flow data with intrusion attempts (4% contamination).",
                "features": ["bytes_sent", "bytes_received", "duration_sec", "packet_count", "port_entropy"],
                "has_labels": True,
            },
        ]
    }


@app.post("/analyze")
def analyze(req: AnalysisRequest):
    if req.dataset not in DATASETS:
        raise HTTPException(status_code=404, detail=f"Dataset '{req.dataset}' not found")

    df = DATASETS[req.dataset](n_samples=req.n_samples)
    X_scaled, y_true, feature_cols, mean_vals, std_vals = preprocess(df)

    raw_data = df.drop(columns=["true_label"], errors="ignore").values.tolist()
    raw_cols = [c for c in df.columns if c != "true_label"]

    results = {}
    if "zscore" in req.methods:
        results["zscore"] = zscore.detect(X_scaled, y_true, threshold=req.zscore_params.threshold)
    if "isolation_forest" in req.methods:
        results["isolation_forest"] = isolation_forest.detect(
            X_scaled, y_true,
            contamination=req.if_params.contamination,
            n_estimators=req.if_params.n_estimators,
        )
    if "lof" in req.methods:
        results["lof"] = lof.detect(
            X_scaled, y_true,
            n_neighbors=req.lof_params.n_neighbors,
            contamination=req.lof_params.contamination,
            metric=req.lof_params.metric,
        )

    return {
        "dataset": req.dataset,
        "n_samples": len(df),
        "feature_names": feature_cols,
        "raw_columns": raw_cols,
        "data": raw_data,
        "true_labels": y_true.tolist() if y_true is not None else None,
        "results": results,
        "scalability": _scalability_notes(),
    }


@app.post("/analyze/upload")
async def analyze_upload(
    file: UploadFile = File(...),
    zscore_threshold: float = 3.0,
    if_contamination: float = 0.05,
    if_n_estimators: int = 100,
    lof_n_neighbors: int = 20,
    lof_contamination: float = 0.05,
):
    content = await file.read()
    try:
        df = parse_uploaded_csv(content)
    except Exception as e:
        raise HTTPException(status_code=400, detail=f"Failed to parse CSV: {e}")

    X_scaled, y_true, feature_cols, mean_vals, std_vals = preprocess(df)
    raw_data = df.drop(columns=["true_label", "label", "anomaly", "is_fraud", "class"], errors="ignore").values.tolist()
    raw_cols = [c for c in df.columns if c not in ["true_label", "label", "anomaly", "is_fraud", "class"]]

    results = {
        "zscore": zscore.detect(X_scaled, y_true, threshold=zscore_threshold),
        "isolation_forest": isolation_forest.detect(X_scaled, y_true, contamination=if_contamination, n_estimators=if_n_estimators),
        "lof": lof.detect(X_scaled, y_true, n_neighbors=lof_n_neighbors, contamination=lof_contamination),
    }

    return {
        "dataset": file.filename,
        "n_samples": len(df),
        "feature_names": feature_cols,
        "raw_columns": raw_cols,
        "data": raw_data,
        "true_labels": y_true.tolist() if y_true is not None else None,
        "results": results,
        "scalability": _scalability_notes(),
    }


def _scalability_notes():
    return {
        "zscore": {
            "time_complexity": "O(n·d)",
            "space_complexity": "O(n·d)",
            "scales_to": "Millions of records",
            "parallelizable": True,
        },
        "isolation_forest": {
            "time_complexity": "O(n·t·log(ψ))",
            "space_complexity": "O(t·ψ)",
            "scales_to": "Hundreds of thousands",
            "parallelizable": True,
        },
        "lof": {
            "time_complexity": "O(n²·d) naive",
            "space_complexity": "O(n²)",
            "scales_to": "Tens of thousands",
            "parallelizable": False,
        },
    }


@app.get("/health")
def health():
    return {"status": "ok"}


# ── Payment Pipeline ───────────────────────────────────────────────────────────

_payment_df = None  # in-memory cache


@app.post("/payment/generate")
def payment_generate():
    global _payment_df
    _payment_df = generate_payment_data()
    n_cells = len(_payment_df[['network','geography','entry_mode','purchase_type',
                                'auth_type','channel','decline_code']].drop_duplicates())
    return {
        "n_rows": len(_payment_df),
        "n_cells": n_cells,
        "weeks": 5,
        "current_window": {
            "week": 0,
            "day_of_week": CURRENT_DAY,
            "hour": CURRENT_HOUR,
            "label": "Monday 14:00",
        },
        "anomalies_injected": get_anomaly_info(),
        "decline_codes_total": 40,
    }


@app.get("/payment/download")
def payment_download():
    if _payment_df is None:
        raise HTTPException(status_code=404, detail="Generate data first via POST /payment/generate")
    csv_bytes = _payment_df.to_csv(index=False).encode()
    return StreamingResponse(
        iter([csv_bytes]),
        media_type="text/csv",
        headers={"Content-Disposition": "attachment; filename=payment_decline_data.csv"},
    )


@app.post("/payment/stage/{stage_num}")
def run_payment_stage(stage_num: int, threshold: float = 3.0):
    if _payment_df is None:
        raise HTTPException(status_code=400, detail="Generate data first")
    if stage_num == 1:
        return stage1_rollup_wow.run(_payment_df, CURRENT_DAY, CURRENT_HOUR, threshold)
    elif stage_num == 2:
        return stage2_contribution.run(_payment_df, CURRENT_DAY, CURRENT_HOUR, threshold)
    elif stage_num == 3:
        return stage3_wow_granular.run(_payment_df, CURRENT_DAY, CURRENT_HOUR, threshold)
    raise HTTPException(status_code=400, detail="Stage must be 1, 2 or 3")
