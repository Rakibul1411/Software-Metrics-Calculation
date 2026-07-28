# Defect Prediction System

A full-stack, microservice-based Software Defect Prediction System.

## System Components

| Component | Technology | Description |
|---|---|---|
| `backend-java/` | Java 8 + Spring Boot 2.7 | Metrics extraction API and prediction orchestrator |
| `ml-service-python/` | Python 3 + FastAPI | CORAL + KNN defect prediction engine |
| `frontend-angular/` | Angular 19 + TypeScript | Metrics extraction UI for ZIP and GitHub Java projects |
| `docs/` | Markdown | Architecture and API documentation |

## Workflow Overview

```
[User Project ZIP / GitHub URL]
        │
        ▼
  [backend-java: Spring Boot]
  ├── Parses Java source code with Eclipse JDT
  ├── Samples AEEEM history every 14 days
  ├── Mines added/deleted lines with Git
  ├── Calculates PROMISE or AEEEM metrics
  ├── Generates target CSV
  └── Forwards to Python ML service
        │
        ▼
  [ml-service-python: FastAPI]
  ├── Normalizes features (StandardScaler)
  ├── Applies CORAL domain adaptation
  ├── Predicts class-level defects with KNN
  └── Returns bug/clean predictions per class
        │
        ▼
  [Results returned to frontend / API consumer]
```

## Quick Start

### 1. Start the Java Backend
```bash
cd backend-java
mvn spring-boot:run
# Runs at http://localhost:8080
```

### 2. Start the Angular Frontend
```bash
cd frontend-angular
npm install
npm start
# Runs at http://localhost:4200
```

The current UI covers metrics extraction and CSV preview/download. Prediction remains an API-only module for now.

### 3. Start the Python ML Service (prediction API only)
```bash
cd ml-service-python
python3 -m venv venv
source venv/bin/activate
python -m pip install --upgrade pip
python -m pip install -r requirements.txt
python -m uvicorn app.main:app --host 0.0.0.0 --port 8000 --reload
# Runs at http://localhost:8000
```

Python 3.11 through 3.14 is supported. Keep the ML service running while using
the prediction UI; `http://localhost:8000/health` can be used as a health check.

### 4. Extract Metrics (example)
```bash
curl -X POST "http://localhost:8080/api/metrics/extract" \
  -F "projectZip=@/path/to/your/java-project.zip" \
  -F "datasetFormat=promise"
```

For a public GitHub repository, replace `projectZip` with `-F "githubUrl=https://github.com/owner/repository"`.

### 5. Run Prediction (example)
```bash
curl -X POST "http://localhost:8080/api/prediction/run" \
  -F "targetDatasetId=YOUR_DATASET_ID" \
  -F "sourceFiles=@ant-1.3.csv" \
  -F "labelColumn=bug" \
  -F "knnValue=5" \
  -F "coralOption=true"
```

## Dataset Formats Supported

| Format | Columns | Label |
|---|---|---|
| PROMISE | 20 CK metrics (wmc, dit, noc, cbo, rfc, lcom, ...) | `bug` (0/1) |
| AEEEM target | 56 predictors: 17 final CK/OO + 17 WCHU + 17 LDHH + 5 Cvs history-of-complexity | none |

Generated target datasets never include a defect label. AEEEM extraction is self-contained:
Eclipse JDT calculates the 17 source metrics and Git supplies raw commit/line-change
history. No CK, CKJM, PyDriller, or other external metric calculator is used.

The five bug-history predictors and the `class` label are intentionally excluded
because they require external defect-tracker data. AEEEM extraction therefore
produces 57 CSV columns: `name` plus 56 non-defect predictors.

For AEEEM, the source must be a Git repository (or an archive retaining `.git`).
The first-parent history is sampled every 14 days. Current-project mode analyzes
the latest 26 snapshots by default so large, long-lived repositories can finish
with bounded memory and runtime. The five benchmark profiles instead use the
published EQ/JDT/LC/ML/PDE start dates and historical releases and ignore the
recent-history cap.

Benchmark periods are fixed in code:

| Profile | Metric interval | Final release | Snapshots |
|---|---|---|---:|
| JDT | 2005-01-01 → 2008-06-17 | JDT Core 3.4 / `R3_4` | 91 |
| EQ | 2005-01-01 → 2008-06-25 | Equinox 3.4 | 91 |
| PDE | 2005-01-01 → 2008-09-11 | PDE UI 3.4.1 / `R3_4_1` | 97 |
| LC | 2005-01-01 → 2008-10-08 | Lucene 2.4.0 | 99 |
| ML | 2005-01-17 → 2009-03-17 | Mylyn 3.1 / `R_3_1_0` | 98 |

Generated CSV/ARFF metrics use at most six digits after the decimal point,
without unnecessary trailing zeros. Metric calculations themselves keep full
precision.

A GitHub folder URL such as
`https://github.com/eclipse-jdt/eclipse.jdt.core/tree/master/org.eclipse.jdt.core`
preserves `master` and `org.eclipse.jdt.core`. Every static snapshot and Git
changed-line query is restricted to that module. Sibling modules are not parsed
or exported. When the selected module has the same Git tree at adjacent sample
dates, its previous metric result is reused.

Set `AEEEM_MAX_SNAPSHOTS=0` to analyze complete history in current-project mode,
or set it to another integer (minimum 2) to choose a different recent window.

The JDT parser processes production sources in bounded batches and excludes
tests, JCL/compiler fixtures, generated sources, examples, and build
infrastructure. `AEEEM_JDT_BATCH_SIZE` may be set from 16 through 512; the
memory-safe default is 96.

### Large repositories

AEEEM extraction is intentionally slower than PROMISE because every selected
revision is parsed. For a large repository, start the backend with an explicit
heap and leave the request running while snapshot progress appears in the
backend terminal:

```bash
cd backend-java
mvn spring-boot:run -Dspring-boot.run.jvmArguments="-Xmx2g"
```

JDT diagnostics from unresolved optional project dependencies are warnings and
are capped per snapshot. Test/JCL conflicts should no longer be analyzed. Only
one AEEEM extraction runs at once; a second simultaneous request returns HTTP
429 with an actionable message. AEEEM GitHub clones download the selected
branch's full history and blobs before analysis; this avoids a late network
fetch failure after source snapshots have already completed.

See [AEEEM implementation details](docs/aeeem-implementation.md) for the
equations, data flow, benchmark periods, configuration, and validation boundary.
