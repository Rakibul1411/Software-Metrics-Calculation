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
| AEEEM target | 51 AST-derived static metrics (ck_oo_*, LDHH_*, WCHU_*) | none |

Generated target datasets never include a defect label. AEEEM repository-history fields (`Cvs*`, historical bug counts) cannot be derived from source ASTs and are deliberately excluded.
