# Anomaly Detection — Java Spring Boot (frontend + backend, single project)

A Java Spring Boot port of the Python FastAPI backend in `../backend`, packaged as
**one deployable project** with the React frontend. `mvn clean package` builds the
React app (`../frontend`) and embeds the compiled static files into this module's
jar, so `java -jar target/backend-java-1.0.0.jar` starts a single process on port
8000 that serves both the UI (`/`) and the API (`/api/**`).

This is a from-scratch reimplementation: there is no scikit-learn in Java, so
Isolation Forest and Local Outlier Factor are implemented natively (see
"Implementation notes" below).

## One project, one jar

- The frontend already calls same-origin `/api/*` in its fetch code
  (`frontend/src/utils/api.ts`, `frontend/src/pages/PaymentPipelinePage.tsx`), and
  Vite's dev server proxies `/api` → `http://localhost:8000` during `npm run dev`
  (`frontend/vite.config.ts`).
- In production there's no proxy needed: Spring Boot serves the built React files
  from `src/main/resources/static` (populated at build time) at `/`, and the
  `@RequestMapping("/api")` prefix on both controllers answers API calls on the same
  origin/port. No CORS hop, no separate frontend server, no base-URL config.
- The `frontend-maven-plugin` in `pom.xml` downloads a local Node/npm, runs
  `npm install && npm run build` under `../frontend`, and a `maven-resources-plugin`
  execution copies `../frontend/dist/**` into
  `target/classes/static` before the jar is assembled.

## Tech stack

| Layer | Technology |
|-------|-----------|
| Language | Java 21 (LTS) |
| Framework | Spring Boot 3.3 (spring-boot-starter-web, spring-boot-starter-jdbc) |
| Storage | H2, embedded and in-memory (`jdbc:h2:mem:paymentdb`) |
| Build | Maven |
| JSON | Jackson (bundled with Spring Boot web starter) |
| CSV parsing | Apache Commons CSV |

## Storage

The generated payment dataset lives in an H2 table (`payment_declines`,
schema in `src/main/resources/schema.sql`), not a Java `List` field. Every
stage/head's grouping and summing is a SQL `GROUP BY` query
(`PaymentDb.currentWindow` / `historicalWindow` / `singleWindow`) --
that's the actual computation over up to 150k+ rows. The mean/std/z-score
step that follows is a handful of numbers per already-aggregated cell, and
stays in Java (`WowMath`) rather than being pushed into SQL too.

`PaymentDataGenerator`'s RNG-driven synthetic generation (weighted sampling,
anomaly injection) stays 100% Java -- that's procedural business logic, not
something SQL should do. Only the *storage* of its output moved to the
database; `POST /payment/generate` calls the generator, then bulk-inserts
the result (`PaymentDb.regenerate`), replacing whatever was there before.

## Prerequisites

- JDK 21 or newer
- Maven 3.8+
- Internet access on first build (frontend-maven-plugin downloads a local Node/npm
  into `../frontend`; npm then installs the frontend's own dependencies)

## Build (frontend + backend, one command)

```bash
cd backend-java
mvn clean package
```

This runs `npm install && npm run build` in `../frontend`, copies the compiled
static assets into this module, and produces one self-contained jar:
`target/backend-java-1.0.0.jar`.

## Run

```bash
java -jar target/backend-java-1.0.0.jar
```

Then open **http://localhost:8000** — the React UI and the API are served from the
same process/port, configured in `src/main/resources/application.properties`.

(`mvn spring-boot:run` also works, but re-runs the full frontend build every time
via the `generate-resources` phase — prefer `mvn package` once, then re-run the jar,
during iterative frontend-only development.)

## API

All endpoints mirror `backend/main.py`, mounted under `/api`:

- `GET /api/health` — `{"status": "ok"}`
- `GET /api/datasets` — list of the 3 built-in synthetic datasets
- `POST /api/analyze` — run zscore / isolation_forest / lof on a generated dataset
- `POST /api/analyze/upload` — same, but on an uploaded CSV (multipart `file` + query params)
- `POST /api/payment/generate` — generate the payment-decline dataset and store it in H2
- `GET /api/payment/download` — download the generated dataset as CSV
- `GET /api/payment/raw` — every row, for the Raw Data tab's client-side filters
- `POST /api/payment/raw/detect` — filter rows server-side, then score them with zscore/isolation_forest
- `POST /api/payment/stage/{1,2,3}?threshold=3.0` — run WoW anomaly-detection stage 1/2/3
- `POST /api/payment/stage2-heads?threshold=3.0` — Stage 2 + Stage 3's math, split across 3 smaller per-team dimension subsets
- `GET /api/payment/drill-down?...` — on-demand bin×acquirer breakdown of one already-flagged cell
- `GET /api/payment/contribution-vs-last-week` — all cells' share vs. strictly last week
- `GET /api/payment/historical-analysis` — weekly/hourly/day-of-week aggregate patterns

CORS is open to all origins/methods/headers, matching the Python `allow_origins=["*"]`
(kept for convenience if you ever run the frontend's own dev server against this
backend directly instead of through the bundled static files).

## Project layout

```
src/main/resources/
  schema.sql                         payment_declines table + index, run on startup
src/main/java/com/anomalydetection/
  AnomalyDetectionApplication.java   Spring Boot entry point
  config/CorsConfig.java             CORS (allow all)
  controller/
    AnalysisController.java          /health, /datasets, /analyze, /analyze/upload
    PaymentController.java           /payment/*
  model/
    Dataset.java                     in-memory feature matrix + labels
    AnalysisRequest.java             /analyze request DTO
  detectors/
    ZScoreDetector.java              per-feature z-score, max-abs-z per row
    IsolationForestDetector.java     from-scratch isolation forest
    LofDetector.java                 from-scratch local outlier factor
  payment/
    PaymentRow.java, PaymentDataGenerator.java   RNG-driven synthetic generator
    PaymentDb.java                   all SQL: insert, and the two-query pattern
                                      (current window / historical window) every
                                      stage and head is built on
    Dim.java                         a monitorable dimension = its column name
    ContributionHead.java            Stage 2's share-vs-window math, any dimension subset
    RateHead.java                    Stage 3's own-rate-vs-history math, any dimension subset
    Stage1RollupWow.java             port of stages/stage1_rollup_wow.py
    Stage2Contribution.java          ContributionHead over all 7 dimensions
    Stage3WowGranular.java           RateHead over all 7 dimensions
    Stage2Heads.java                 ContributionHead + RateHead over 3 smaller per-team subsets
    DrillDown.java                   on-demand bin×acquirer breakdown, one SQL query
    ContributionVsLastWeek.java, HistoricalAnalysis.java
  utils/
    DataProcessor.java               port of utils/data_processor.py generators + preprocess
    Metrics.java                     port of utils/metrics.py compute_metrics
    RandomState.java                 RNG helper (Box-Muller gaussian, Knuth Poisson, etc.)
```

## Implementation notes

- **Isolation Forest** and **Local Outlier Factor** have no Java/sklearn equivalent, so
  both are implemented from scratch:
  - Isolation Forest builds `n_estimators` random isolation trees over
    `min(256, n)`-sample subsets (sklearn's `max_samples="auto"` default), scores via
    `2^(-avgPathLength / c(n))` with the standard `c(n)` average-path-length
    normalization constant, and flags the top-`contamination` fraction of points by
    score as anomalies (sign convention matches Python's `-clf.score_samples(X)`, i.e.
    higher score = more anomalous).
  - LOF is a naive O(n²) implementation: pairwise Euclidean distances, k-distance,
    reachability distance, local reachability density, and the LOF ratio, with the
    top-`contamination` fraction by LOF flagged as outliers.
  - Random-number generation does not bit-for-bit match numpy's Mersenne Twister;
    only the statistical distributions/shapes are matched (Gaussian via Box-Muller,
    exponential via inverse-CDF, log-normal via `exp(gaussian)`, Poisson via Knuth's
    algorithm). Exact per-run point clouds and metric values will therefore differ
    slightly from the Python backend, but algorithm behavior and formulas are faithful
    ports.
- **Z-Score, metrics, and payment/WoW math** are direct, formula-exact ports
  (including the `.clip(lower=X)` std floors, `is_new`/`is_new_cell` → `z=99.0`
  sentinel, and rounding precision used in each stage's `_to_records` helper).
