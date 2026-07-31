# Payment Anomaly Detection Framework

A full-stack ML anomaly detection system with a real-world **Payment Decline Pipeline** demonstrating hierarchical, week-over-week anomaly detection across thousands of payment cell combinations.

Two interchangeable backend implementations ship side by side:

| | Recommended | Alternative |
|---|---|---|
| Stack | **Java 17 / Spring Boot** (`backend-java/`) | Python / FastAPI (`backend/`) |
| Deploy | One jar serves the API **and** the built React UI on a single port (8000) | Two separate processes — FastAPI on 8000, Vite dev server on 5173 |
| ML detectors | Z-Score, Isolation Forest and LOF implemented from scratch in Java | Z-Score, Isolation Forest and LOF via scikit-learn |
| Payment pipeline | Full port — identical formulas, identical JSON | Original implementation |

Both expose the same REST contract, so the React frontend works unmodified against either one.

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

See [docs/payment_anomaly_brief.html](docs/payment_anomaly_brief.html) for an executive walkthrough with real numbers from a generated run — 6.89M raw transactions, 52,920 aggregated rows, 63 discovered cells, SQL for each stage, and a worked example contrasting Stage 2 and Stage 3.

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Backend (recommended) | Java 17, Spring Boot 3.3, Maven |
| Backend (alternative) | FastAPI, scikit-learn, pandas, numpy |
| Frontend | React, Vite, TypeScript, Plotly.js |

## Getting Started — Java (single jar, recommended)

### Prerequisites
- JDK 17+
- Maven 3.8+
- Internet access on first build (pulls a local Node/npm to build the frontend)

```bash
cd backend-java
mvn clean package
java -jar target/backend-java-1.0.0.jar
```

Open [http://localhost:8000](http://localhost:8000) — UI and API served from the same process and port. See [backend-java/README.md](backend-java/README.md) for build internals and API routes (mounted under `/api`).

## Getting Started — Python + Vite (alternative, two processes)

### Prerequisites
- Python 3.9+
- Node.js 18+

```bash
# Terminal 1 — backend
cd backend
pip install fastapi uvicorn pandas numpy scikit-learn scipy python-multipart
uvicorn main:app --reload

# Terminal 2 — frontend
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
├── docs/
│   └── payment_anomaly_brief.html       # Executive brief with worked examples
├── backend-java/                        # Recommended: Java Spring Boot, single jar
│   ├── pom.xml                          # Also builds & embeds the React frontend
│   └── src/main/java/com/anomalydetection/
│       ├── controller/                  # /api/health, /analyze, /payment/*
│       ├── detectors/                   # Z-Score, Isolation Forest, LOF (from scratch)
│       └── payment/                     # PaymentDataGenerator, Stage1/2/3
├── backend/                             # Alternative: Python FastAPI
│   ├── main.py
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
└── frontend/                            # React UI — shared by both backends
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
