# Defect Prediction System

A full-stack, microservice-based Software Defect Prediction System.

## System Components

| Component | Technology | Description |
|---|---|---|
| `backend-java/` | Java 8 + Spring Boot 2.7 | Metrics extraction API and prediction orchestrator |
| `ml-service-python/` | Python 3 + FastAPI | CORAL + KNN defect prediction engine |
| `frontend-angular/` | Angular + TypeScript | (Coming soon) Web UI for the full prediction workflow |
| `docs/` | Markdown | Architecture and API documentation |

## Workflow Overview

```
[User Project ZIP / GitHub URL]
        │
        ▼
  [backend-java: Spring Boot]
  ├── Parses Java source code with Eclipse JDT
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

### 2. Start the Python ML Service
```bash
cd ml-service-python
pip install -r requirements.txt
uvicorn app.main:app --host 0.0.0.0 --port 8000 --reload
# Runs at http://localhost:8000
```

### 3. Extract Metrics (example)
```bash
curl -X POST "http://localhost:8080/api/metrics/extract" \
  -d "sourceDirectory=/path/to/your/java/project" \
  -d "datasetFormat=promise"
```

### 4. Run Prediction (example)
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
| AEEEM | 61 multi-tool metrics (ck_oo_*, LDHH_*, WCHU_*, Cvs*) | `class` (buggy/clean) |
