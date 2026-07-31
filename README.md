# Payment Anomaly Detection Framework

A full-stack ML anomaly detection system with a real-world **Payment Decline Pipeline** demonstrating hierarchical, week-over-week anomaly detection across thousands of payment cell combinations.

Backend is Java 17 / Spring Boot. `mvn clean package` builds the React frontend and embeds it into a single deployable jar that serves both the UI and the REST API on one port.

## Features

### Core ML Framework
- **Z-Score**, **Isolation Forest**, and **Local Outlier Factor (LOF)** detectors — implemented from scratch in Java
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
| Backend | Java 17, Spring Boot 3.3, Maven |
| Frontend | React, Vite, TypeScript, Plotly.js |

## Getting Started

### Prerequisites
- JDK 17+
- Maven 3.8+
- Internet access on first build (Maven pulls a local Node/npm to build the frontend)

### Build and run

```bash
cd backend-java
mvn clean package
java -jar target/backend-java-1.0.0.jar
```

Open [http://localhost:8000](http://localhost:8000) — UI and API served from the same process and port.

See [backend-java/README.md](backend-java/README.md) for build internals and the full list of API routes (mounted under `/api`).

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
├── backend-java/                        # Java Spring Boot, single jar
│   ├── pom.xml                          # Also builds & embeds the React frontend
│   └── src/main/java/com/anomalydetection/
│       ├── controller/                  # /api/health, /analyze, /payment/*
│       ├── detectors/                   # Z-Score, Isolation Forest, LOF (from scratch)
│       └── payment/                     # PaymentDataGenerator, Stage1/2/3
└── frontend/                            # React UI, built and embedded by backend-java
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
