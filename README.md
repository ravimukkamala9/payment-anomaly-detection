# Payment Anomaly Detection Framework

A full-stack ML anomaly detection system with a real-world **Payment Decline Pipeline** demonstrating hierarchical, week-over-week anomaly detection across thousands of payment cell combinations.

## Features

### Core ML Framework
- **Z-Score**, **Isolation Forest**, and **Local Outlier Factor (LOF)** detectors
- Side-by-side comparison with precision, recall, F1, and confusion matrix metrics
- Upload your own CSV dataset for analysis
- 7-step interactive learning/tutorial mode explaining each algorithm

### Payment Decline Pipeline
Three-stage hierarchical detection system that solves the **combinatorial explosion** problem (40 business rules × 3 networks × 2 geo × 5 entry modes × ... = 9,600+ potential monitors):

| Stage | Method | Purpose |
|-------|--------|---------|
| Stage 1 | Roll-up WoW Z-Score | Catches aggregate spikes across 18 roll-up monitors |
| Stage 2 | Contribution Shift Monitor | Catches masked anomalies via each cell's share of total declines |
| Stage 3 | Full-granularity WoW Z-Score | Catches low-volume cells invisible to Stage 1 & 2 |

**Week-over-Week (WoW) Z-Score** compares the current window to the same day-of-week + same hour in weeks −1, −2, −3, −4 — capturing seasonal patterns without CUSUM state machines.

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Backend | FastAPI, scikit-learn, pandas, numpy |
| Frontend | React, Vite, TypeScript, Plotly.js |

## Getting Started

### Prerequisites
- Python 3.9+
- Node.js 18+

### Backend
```bash
cd backend
pip install fastapi uvicorn pandas numpy scikit-learn scipy python-multipart
uvicorn main:app --reload
```

### Frontend
```bash
cd frontend
npm install
npm run dev
```

Open [http://localhost:5173](http://localhost:5173)

## Payment Pipeline Demo

1. Navigate to **Payment Pipeline** in the sidebar
2. Click **Generate Test Data** — creates 52,920 rows (5 weeks × 7 days × 24 hours × 63 cells)
3. Optionally **Download CSV** to inspect the synthetic dataset
4. Adjust the **WoW Threshold** slider (default 3σ)
5. Click **Run Pipeline** — watch all 3 stages execute sequentially

### Injected Anomalies
| Stage | Cell | Multiplier | Real-world Scenario |
|-------|------|-----------|---------------------|
| 1 | VISA × Intl × TAP × NSF | ×14 | Network routing misconfiguration sending TAP auth to wrong endpoint |
| 2 | MC × Domestic × PIN × Cashback × WRONG_PIN | ×18 | ATM firmware bug specific to cashback transactions — invisible at roll-up |
| 3 | AMEX × Intl × Wallet × CVV_MISMATCH | ×22 | Token provisioning defect in international ecom wallet — low volume cell |

## Architecture

```
anomaly-detection/
├── backend/
│   ├── main.py                          # FastAPI app
│   ├── detectors/
│   │   ├── zscore.py
│   │   ├── isolation_forest.py
│   │   └── lof.py
│   ├── stages/
│   │   ├── stage1_rollup_wow.py         # Roll-up WoW Z-Score
│   │   ├── stage2_contribution.py       # Contribution Shift Monitor
│   │   └── stage3_wow_granular.py       # Full-granularity WoW Z-Score
│   └── utils/
│       ├── data_processor.py
│       ├── metrics.py
│       └── payment_data_generator.py    # 52,920-row synthetic dataset
└── frontend/
    └── src/
        ├── pages/
        │   ├── PaymentPipelinePage.tsx
        │   ├── AnalyzePage.tsx
        │   ├── ComparePage.tsx
        │   └── TutorialPage.tsx
        └── components/
            └── charts/
                └── WoWLineChart.tsx
```
