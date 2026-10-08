# DefectLab: Cross-Project Software Defect Prediction System

[![Java](https://img.shields.io/badge/Java-17-007396?logo=openjdk&logoColor=white)](https://www.oracle.com/java/)
[![Spring Boot](https://img.shields.io/badge/Spring_Boot-2.7.18-6DB33F?logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)
[![Angular](https://img.shields.io/badge/Angular-19-DD0031?logo=angular&logoColor=white)](https://angular.dev/)
[![FastAPI](https://img.shields.io/badge/FastAPI-Python_3.12-009688?logo=fastapi&logoColor=white)](https://fastapi.tiangolo.com/)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-4169E1?logo=postgresql&logoColor=white)](https://www.postgresql.org/)
[![Docker Hub](https://img.shields.io/badge/Docker_Hub-rakibalnatiq%2Fdefectlab-2496ED?logo=docker&logoColor=white)](https://hub.docker.com/u/rakibalnatiq)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

---

## Overview

**DefectLab** is a cross-project software defect prediction (CPDP) and software metric analysis workbench developed for Java systems. It provides automated Abstract Syntax Tree (AST) static code analysis, Git repository change history mining, unsupervised domain adaptation via Shallow CORAL (Correlation Alignment), and supervised defect prediction using K-Nearest Neighbors (KNN).

In cross-project defect prediction, models are trained on historical, labeled defect data from one project (or release) and evaluated on a different target project that lacks historical defect labels. DefectLab addresses the primary challenge of CPDP—**domain shift** (the discrepancy between feature distributions and covariance structures across disparate software codebases)—by aligning second-order feature statistics between the source and target domains while strictly preventing target-label leakage.

DefectLab was designed and developed as a final software engineering degree defense project (**SPL-3 / SE801**) at the **Institute of Information Technology (IIT), University of Dhaka**.

---

## Key Capabilities

- **Automated AST Metric Extraction**: Parses Java source archives (`.zip`) or clones public GitHub repositories to extract 20 PROMISE object-oriented metrics using Eclipse JDT 3.37 and Apache BCEL.
- **Git History & Change Mining**: Mines commit logs, author dynamics, and code churn across 14-day development snapshots to calculate 56 AEEEM static, change, entropy, and historical metrics.
- **Domain Adaptation via Shallow CORAL**: Regularizes source and target covariance matrices and applies whitening/recoloring transformations to align feature spaces without target label supervision.
- **Supervised KNN Defect Prediction**: Configurable K-Nearest Neighbors ($K \in [1, 5]$) classifier with calibrated defect probability scoring and descending risk prioritization.
- **Scientific Evaluation Suite**: Evaluates predictions against ground truth labels, computing ROC-AUC, PR-AUC, Matthews Correlation Coefficient (MCC), Recall@20% LOC, Precision, Recall, Specificity, and Confusion Matrix.
- **Interactive Defect Hotspot Visualization**: Squarified Treemap visualization (Bruls et al. algorithm) mapping software classes sized by Lines of Code (LOC) and color-coded by defect risk probability.
- **Benchmark Metric Verification**: Class-wise and distribution-level verification comparing user-extracted metrics against canonical benchmarks under configurable tolerance thresholds ($\pm 5\%$, exact).
- **Cryptographic Artifact Generation**: Produces reproducible PDF evaluation reports via Apache PDFBox and labeled CSV datasets containing predictions for CI/CD integration.

---

## System Architecture

DefectLab is built following **Clean Architecture** principles and a decoupled 4-tier archetype:

### 1. Architectural Context & 4-Tier Archetype

```mermaid
flowchart TD
    subgraph Presentation_Tier["Presentation Tier (Port 4200)"]
        UI["Angular 19 SPA<br/>(Vite / Vanilla CSS Design System)"]
        DESKTOP["Electron Desktop Client<br/>(.dmg for macOS / .exe for Windows)"]
    end

    subgraph Application_Tier["Application & Business Tier (Port 8080)"]
        API["Spring Boot 2.7.18 REST API"]
        AUTH["Authentication & Session Manager"]
        ANALYSIS["AST Analyzer (Eclipse JDT + BCEL + Git Miner)"]
        DATASET["Dataset Catalog & Quality Validator"]
        PREDICT["CPDP Prediction Orchestrator"]
        COMPARE["Metric Comparison Interactor"]
        REPORT["Apache PDFBox Report Generator"]
    end

    subgraph ML_Tier["Machine Learning Microservice (Port 8000 - Internal Bridge)"]
        ML["FastAPI Python 3.12 Engine"]
        PREP["Data Sanitizer & Median Imputer"]
        CORAL["Shallow CORAL Covariance Alignment"]
        KNN["Scikit-Learn KNN Classifier (K=1..5)"]
        EVAL["Evaluation Engine (ROC-AUC, PR-AUC, MCC)"]
    end

    subgraph Persistence_Tier["Persistence Tier"]
        DB[("PostgreSQL 16 / Neon DB<br/>(users, datasets, runs, comparisons)")]
        STORAGE[("Durable Filesystem Storage<br/>(Metrics, Labeled CSVs, PDF Reports)")]
    end

    DESKTOP --> UI
    UI -->|HTTP / JSON + Secure Cookie| API
    API --> AUTH
    API --> ANALYSIS
    API --> DATASET
    API --> PREDICT
    API --> COMPARE
    API --> REPORT

    PREDICT -->|"REST (X-DefectLab-Service-Token)"| ML
    COMPARE -->|REST| ML

    ML --> PREP --> CORAL --> KNN --> EVAL

    AUTH --> DB
    DATASET --> DB
    PREDICT --> DB
    COMPARE --> DB

    ANALYSIS --> STORAGE
    DATASET --> STORAGE
    PREDICT --> STORAGE
    COMPARE --> STORAGE
```

---

### 2. Component-Level Decomposition (Hexagonal Architecture)

The backend is structured into five domain-driven bounded contexts under `org.metrics.defectlab`. Domain models and business invariants remain free from framework dependencies:

```mermaid
flowchart TD
    subgraph Infrastructure_Adapters["Infrastructure & External Adapters"]
        A_REST["Spring MVC Controllers (/api/* REST API)"]
        A_DB["Spring Data JPA (PostgreSQL 16)"]
        A_ML["HTTP Client (FastAPI ML Bridge Port 8000)"]
        A_FILE["Durable File Storage (ArtifactStorage)"]
    end

    subgraph UseCases["Application Use Cases (Inbound Ports)"]
        UC1["AnalysisInteractor"]
        UC2["DatasetInteractor"]
        UC3["PredictionInteractor"]
        UC4["ComparisonInteractor"]
    end

    subgraph Core_Domain["Core Business Domain"]
        D1["Analysis Domain (20 CK/PROMISE & 56 AEEEM Metrics)"]
        D2["Dataset Domain (Dataset Catalog & Quality Guard)"]
        D3["Prediction Domain (PredictionRun, Confusion Matrix, Risk)"]
        D4["Comparison Domain (Metric Tolerances & Distribution Bounds)"]
        D5["Auth Domain (User Entity & BCrypt Password Policy)"]
    end

    A_REST --> UseCases
    UseCases --> Core_Domain
    UseCases --> A_DB
    UseCases --> A_ML
    UseCases --> A_FILE
```

---

### 3. End-to-End Prediction Workflow (Sequence Diagram)

The execution pipeline decouples client requests from prediction computation, ensuring that target-domain labels are shielded during model training:

```mermaid
sequenceDiagram
    autonumber
    actor User as Researcher
    participant UI as Angular SPA (Port 4200)
    participant Backend as Spring Boot API (Port 8080)
    participant ML as FastAPI ML Engine (Port 8000)
    participant Storage as PostgreSQL & Artifact Storage

    User->>UI: Configure Run (Source, Target, K=3, CORAL)
    UI->>Backend: POST /api/predictions
    activate Backend
    Backend->>ML: POST /ml/predict (Source & Target Features)
    activate ML
    Note over ML: 1. Imputation & Scaling<br/>2. Shallow CORAL Alignment<br/>3. KNN Classification
    ML-->>Backend: Probabilities, Labels & Risk Bands
    deactivate ML
    Backend->>Storage: Persist PredictionRun & Generate PDF/CSV Reports
    Backend-->>UI: 200 OK (Ranked Classes & Hotspot Treemap)
    deactivate Backend
    UI-->>User: Render Interactive Visualizations & Download Report
```

---

### 4. Prediction Execution State Lifecycle

```mermaid
flowchart TD
    S0(["1. Configure Run<br/>(Source, Target, K, Threshold)"]) --> S1{"Input & Schema<br/>Validation"}
    S1 -->|Invalid / Missing Labels| S_ERR["Show Validation Error Toast"]
    S_ERR --> S0
    S1 -->|Valid| S2["Data Sanitization & Median Imputation"]
    S2 --> S3{"CORAL Alignment<br/>Enabled?"}
    S3 -->|Yes| S4["Shallow CORAL Covariance Alignment"]
    S3 -->|No| S5["Direct Scaled Feature Space"]
    S4 --> S6["Train KNN Classifier (K=1..5, Euclidean)"]
    S5 --> S6
    S6 --> S7["Risk Calibration & Defect Thresholding"]
    S7 --> S8["Generate Annotated CSV & PDFBox Report"]
    S8 --> S9["Persist Run Record to PostgreSQL"]
    S9 --> S10(["Completed: Explore Results & Treemap Hotspots"])
```

---

## Machine Learning & Domain Adaptation

### Data Preparation Pipeline

```mermaid
flowchart TD
    S["Source Dataset<br/>(Labeled Instances)"] --> P1["1. Schema Normalization<br/>(Alias resolution & column mapping)"]
    T["Target Dataset<br/>(Unlabeled Instances)"] --> P1

    P1 --> P2["2. Source-Median Imputation<br/>(Missing values imputed strictly using source medians)"]
    P2 --> P3["3. Zero-Variance Feature Removal<br/>(Constant columns removed across both domains)"]
    P3 --> P4["4. Independent Domain Standardization<br/>(Source and Target fit independent StandardScalers)"]
    
    P4 --> CORAL_CHECK{"CORAL Alignment Enabled?"}
    
    CORAL_CHECK -- Yes --> P5["5. Shallow CORAL Alignment<br/>• Compute CS and CT covariance matrices<br/>• Whitening: CS^(-1/2)<br/>• Recoloring: CT^(1/2)<br/>• Transform Source: X_hat = XS · CS^(-1/2) · CT^(1/2)"]
    CORAL_CHECK -- No --> P6["Direct Scaled Feature Space"]

    P5 --> P7["6. Supervised KNN Classification<br/>• Neighbors: K ∈ [1, 5] (default K=3)<br/>• Distance: Euclidean Metric<br/>• Weights: Uniform Weighting"]
    P6 --> P7

    P7 --> P8["7. Risk Probability Calibration<br/>P(buggy) = (1/K) · Σ yk"]
    P8 --> P9["8. Defect Thresholding<br/>Predicted Label = 1 if P(buggy) ≥ Threshold else 0"]
    P9 --> P10["9. Descending Risk Ranking & Hotspot Stratification<br/>• HIGH: P ≥ 0.70<br/>• MEDIUM: 0.40 ≤ P < 0.70<br/>• LOW: P < 0.40"]
```

### Mathematical Formulation of Shallow CORAL

Cross-project defect prediction suffers from domain shift: the joint distribution of features differs between source and target software repositories ($P(X_S) \neq P(X_T)$). To minimize domain discrepancy without target label supervision, DefectLab implements the **CORAL** algorithm (Sun et al., 2016):

Let $X_S \in \mathbb{R}^{n_S \times d}$ and $X_T \in \mathbb{R}^{n_T \times d}$ denote zero-centered source and target feature matrices:

1. **Covariance Matrix Estimation**:
   $$C_S = \frac{1}{n_S - 1} X_S^T X_S + \epsilon I_d$$
   $$C_T = \frac{1}{n_T - 1} X_T^T X_T + \epsilon I_d$$
   where $\epsilon = 10^{-5}$ is a regularization constant guaranteeing positive semi-definiteness.

2. **Covariance Whitening & Recoloring**:
   The source domain is whitened using its inverse covariance square root and recolored using the target covariance square root:
   $$\hat{X}_S = X_S \cdot C_S^{-1/2} \cdot C_T^{1/2}$$

3. **Classification & Target Label Shielding**:
   The KNN classifier is trained on aligned source representations $\hat{X}_S$ with source labels $y_S$. Inference is performed directly on standardized target representations $X_T$. Target labels $y_T$ are never exposed during preprocessing, imputation, or covariance transformation.

### Scientific Evaluation Metrics

When evaluating against a labeled benchmark target dataset, the evaluation engine computes:

- **ROC-AUC**: Area Under the Receiver Operating Characteristic Curve.
- **PR-AUC**: Area Under the Precision-Recall Curve (vital for defect datasets exhibiting high class imbalance).
- **Matthews Correlation Coefficient (MCC)**:
  $$MCC = \frac{TP \times TN - FP \times FN}{\sqrt{(TP+FP)(TP+FN)(TN+FP)(TN+FN)}}$$
- **Recall@20% LOC**: The percentage of defects caught when inspecting the top 20% most lines of code, validating effort-aware defect prediction.
- **Confusion Matrix**: True Positives (TP), False Positives (FP), True Negatives (TN), and False Negatives (FN).

---

## Metric Families Specification

DefectLab strictly categorizes metrics into two standardized empirical families. Features from different families cannot be combined within a single run.

### 1. PROMISE Metric Family (20 Predictors)

Static object-oriented code metrics extracted from Java source code using Eclipse JDT 3.37:

| Metric | Name | Scope & Measurement | Software Quality Indication |
|---|---|---|---|
| `WMC` | Weighted Methods per Class | Sum of McCabe cyclomatic complexities across all class methods | High WMC indicates complex classes with large test surfaces. |
| `DIT` | Depth of Inheritance Tree | Maximum length of path from class to root object | Deep inheritance increases subtle unintended side effects. |
| `NOC` | Number of Children | Count of immediate subclasses inheriting from the class | Modifications in parent break multiple derived classes. |
| `CBO` | Coupling Between Object Classes | Number of other classes to which this class is coupled | High coupling hampers testability, modularity, and isolation. |
| `RFC` | Response for a Class | Methods in class plus methods invoked across external classes | Large response sets increase runtime execution error risk. |
| `LCOM` | Lack of Cohesion in Methods | Difference between disjoint and shared method-attribute pairs | Poor cohesion indicates violations of Single Responsibility. |
| `Ca` | Afferent Couplings | Number of external classes depending on this class | Reflects architectural responsibility and incoming dependencies. |
| `Ce` | Efferent Couplings | Number of external classes this class depends on | Reflects external vulnerability to changes in other modules. |
| `NPM` | Number of Public Methods | Total methods declared with public visibility | Larger public APIs expand misuse potential and maintenance overhead. |
| `LCOM3` | Normalized Lack of Cohesion | Henderson-Sellers normalized cohesion index $[0, 2]$ | Values $> 1.0$ indicate fragmented, uncohesive responsibilities. |
| `LOC` | Lines of Code | Total non-blank, non-comment source lines | Size directly correlates with human cognitive defect introduction. |
| `DAM` | Data Access Metric | Ratio of private/protected attributes to total attributes | Encapsulation quality metric. |
| `MOA` | Measure of Aggregation | Count of complex user-defined object types as member fields | Measures structural composition density. |
| `MFA` | Measure of Functional Abstraction | Ratio of inherited methods to total methods accessible | Measures inheritance utilization vs local overriding. |
| `CAM` | Cohesion Among Methods | Parameter-type similarity across member methods $[0, 1]$ | Lower CAM indicates arbitrary method grouping. |
| `IC` | Inheritance Coupling | Number of parent classes where methods are invoked | Inter-hierarchical coupling risk indicator. |
| `CBM` | Coupling Between Methods | Total method calls directed at parent classes | Deep coupling to superclasses. |
| `AMC` | Average Method Complexity | Average size and complexity of member method declarations | Long methods represent primary defect carriers. |
| `Max_CC` | Maximum Cyclomatic Complexity | Highest McCabe decision complexity across all methods | Highlights the single most complex method in the class. |
| `Avg_CC` | Average Cyclomatic Complexity | Mean McCabe complexity across all member methods | Baseline control flow complexity. |

---

### 2. AEEEM Metric Family (56 Predictors)

Historical and change-based metrics mined from Git commit logs across 14-day snapshots (D'Ambros et al., 2012):

1. **Source Code Metrics (17 features)**: Chidamber-Kemerer (CK) metrics and class interface definitions.
2. **Change Metrics (15 features)**: Commit frequencies, distinct author counts, and modification deltas.
3. **Entropy of Changes (10 features)**: Shannon entropy measuring change dispersion across system components.
4. **Code Churn Metrics (8 features)**: Lines added, lines deleted, and maximum churn bursts per interval.
5. **Historical Defect Introductions (6 features)**: Defect-fixing commit frequency and previous bug introduction rates.

---

## Defect Hotspot Treemap Visualization

DefectLab integrates the **BugMaps** methodology (Hora et al., 2012) using a **Squarified Treemap layout algorithm** (Bruls et al., 2000):

- **Hierarchical Layout**: Classes are clustered within their parent Java packages.
- **Size Dimension**: Rectangle area corresponds to **Lines of Code (LOC)**, ensuring large classes dominate visual weight.
- **Color Dimension**: Rectangle fill reflects calibrated **Defect Probability $P(\text{bug})$**:
  - 🔴 **Crimson Red (High Risk)**: $P \ge 0.70$
  - 🟡 **Amber Orange (Medium Risk)**: $0.40 \le P < 0.70$
  - 🟢 **Emerald Green (Clean / Low Risk)**: $P < 0.40$
- **Aspect Ratio Optimization**: Iteratively optimizes cell dimensions toward a 1:1 square ratio, preventing thin slivers and ensuring readability across repositories with thousands of classes.

---

## Quick Start with Docker

Pre-built multi-architecture Docker images (`linux/amd64` and `linux/arm64`) are published on Docker Hub under `rakibalnatiq/defectlab-*`. You do not need Java, Maven, Node.js, or Python installed locally.

### Prerequisites

- [Docker Engine](https://docs.docker.com/engine/install/) with Docker Compose v2, or [Docker Desktop](https://www.docker.com/products/docker-desktop/) / [OrbStack](https://orbstack.dev/).
- Ensure the Docker daemon is running:
  ```bash
  docker info
  ```

---

### Method 1: Instant Launch with Pre-built Images (Recommended)

Run the following command from the project directory:

```bash
cd DefectLab-Updated-Component-Based
docker compose -f docker-compose.prod.yml up -d
```

Docker will pull the images and launch the four required containers:

| Container | Image | Port | Description |
|---|---|---|---|
| `postgres` | `postgres:16-alpine` | `5432` | Relational database with automated healthcheck |
| `ml` | `rakibalnatiq/defectlab-ml:latest` | `8000` (internal) | FastAPI Python ML service |
| `backend` | `rakibalnatiq/defectlab-backend:latest` | `8080` | Spring Boot REST API |
| `frontend` | `rakibalnatiq/defectlab-frontend:latest` | `4200` | Angular 19 client served via Nginx |

#### Access Endpoints

- **Web Application**: Open [http://localhost:4200](http://localhost:4200) in your web browser.
- **Backend API**: [http://localhost:8080/api](http://localhost:8080/api)
- **ML Health Check**: [http://localhost:8000/ml/health](http://localhost:8000/ml/health)

---

### Method 2: Native Desktop Application (.dmg / .exe)

DefectLab includes an Electron desktop wrapper for macOS and Windows:
1. Ensure the platform containers are active via `docker compose -f docker-compose.prod.yml up -d`.
2. Launch the desktop application installer located in `DefectLab-Updated-Component-Based/desktop-electron/`:
   - macOS: `DefectLab-mac-arm64.dmg` or `DefectLab-mac-x64.dmg`
   - Windows: `DefectLab Setup.exe`
3. The desktop app provides automatic Docker daemon detection, an integrated splash screen, and offline local operations. For packaging instructions, see [USER-MANUAL.md](DefectLab-Updated-Component-Based/USER-MANUAL.md).

---

### Method 3: Build from Source with Docker

To compile and assemble Docker containers directly from local source code:

```bash
cd DefectLab-Updated-Component-Based
cp .env.example .env
docker compose up --build -d
```

---

### Container Lifecycle Management

```bash
# Check service health and status
docker compose -f docker-compose.prod.yml ps

# Follow container logs
docker compose -f docker-compose.prod.yml logs -f

# Follow logs for the backend container only
docker compose -f docker-compose.prod.yml logs -f backend

# Pull the latest published images from Docker Hub
docker compose -f docker-compose.prod.yml pull && docker compose -f docker-compose.prod.yml up -d

# Stop all containers (preserving persistent database and storage volumes)
docker compose -f docker-compose.prod.yml down

# Stop all containers and remove persistent volumes (full clean wipe)
docker compose -f docker-compose.prod.yml down -v
```

---

## Local Development Setup

To run DefectLab directly on your host machine without Docker:

### Prerequisites

- **Java**: OpenJDK 17 (or Eclipse Temurin 17)
- **Maven**: 3.9+
- **Node.js**: 20 LTS or 22 LTS with `npm`
- **Python**: 3.11 or 3.12 with `venv` and `pip`
- **Database**: PostgreSQL 14+ running locally or cloud [Neon DB](https://neon.tech/)

---

### 1. Automated Setup

Run the setup script to configure dependencies across all three layers:

```bash
cd DefectLab-Updated-Component-Based
chmod +x scripts/*.sh
./scripts/setup.sh
```

---

### 2. Configure Environment

Start a local PostgreSQL instance (or launch the Docker database container):

```bash
cd DefectLab-Updated-Component-Based
docker compose -f docker-compose.prod.yml up -d postgres
```

Export configuration variables in your shell:

```bash
export DEFECTLAB_DB_URL='jdbc:postgresql://localhost:5432/defectlab'
export DEFECTLAB_DB_USER='defectlab'
export DEFECTLAB_DB_PASSWORD='defectlab'
export ML_SERVICE_TOKEN='local-development-token'
```

---

### 3. Concurrent Development Mode

Start all services simultaneously with live reload:

```bash
cd DefectLab-Updated-Component-Based
./scripts/run-dev.sh
```

- **Spring Boot** (`:8080`): Watches Java source code; recompiles and reloads context via Spring Boot DevTools.
- **FastAPI** (`:8000`): Auto-reloads through Uvicorn on changes in `ml-service-python/app`.
- **Angular** (`:4200`): Vite development server with Hot Module Replacement (HMR).

---

## Step-by-Step User Tutorial

### Scenario: Predicting Defects in Apache Ant 1.6 using Ant 1.3

```text
[1. Sign In] ──────► Navigate to http://localhost:4200 and authenticate
       │
[2. Catalog] ──────► Verify canonical benchmarks (Ant-1.3, Ant-1.7, Lucene-2.4, etc.)
       │
[3. Upload]  ──────► Add Target: `sample-data/manual-examples/ant-1.6-manual.csv` (MANUAL)
       │
[4. Predict] ──────► Source: Ant 1.3 (PREDEFINED)
       │             Manual Target: Ant 1.6 (MANUAL)
       │             Predefined Target: Ant 1.6 (PREDEFINED) [for evaluation]
       │             Hyperparameters: K=3, CORAL=true, Threshold=0.5
       ▼
[5. Execute] ──────► ML Engine applies CORAL, trains KNN, computes probabilities
       │
[6. Inspect] ──────► Review ranked classes, Squarified Treemap, and Confusion Matrix
       │
[7. Export]  ──────► Download Apache PDFBox Evaluation Report and Labeled Target CSV
```

---

## REST API Specification

Base URL: `http://localhost:8080`

### Authentication & Account
| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/api/auth/register` | Register a new user account |
| `POST` | `/api/auth/login` | Authenticate and create session |
| `GET` | `/api/auth/me` | Fetch active session profile |
| `POST` | `/api/auth/password` | Update account password |
| `POST` | `/api/auth/logout` | Terminate active session |

### Source Analysis
| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/api/analysis` | Extract metrics from Java ZIP archive or public GitHub repository |

### Datasets
| Method | Endpoint | Description |
|---|---|---|
| `GET` | `/api/datasets` | List visible datasets |
| `POST` | `/api/datasets` | Upload and register CSV/ARFF metric dataset |
| `GET` | `/api/datasets/{id}` | Inspect dataset schema and quality metadata |
| `GET` | `/api/datasets/{id}/preview` | Preview initial 25 rows |
| `GET` | `/api/datasets/{id}/download` | Stream the original metric file |
| `DELETE` | `/api/datasets/{id}` | Delete user-owned dataset (if unreferenced) |

### Predictions & Machine Learning
| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/api/predictions` | Execute CPDP prediction run (single or dual target) |
| `GET` | `/api/predictions` | List individual target prediction runs |
| `GET` | `/api/predictions/groups` | List grouped dual-target runs |
| `GET` | `/api/predictions/{id}` | Get run summary and evaluation metrics |
| `GET` | `/api/predictions/{id}/predictions` | Get ranked class predictions |
| `GET` | `/api/predictions/{id}/prediction.csv` | Download labeled target CSV |
| `GET` | `/api/predictions/{id}/report.pdf` | Download formal evaluation report PDF |
| `DELETE` | `/api/predictions/{id}` | Delete prediction run and purge filesystem artifacts |

### Metric Comparison
| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/api/metric-comparisons` | Run metric tolerance comparison between MANUAL and PREDEFINED datasets |
| `GET` | `/api/metric-comparisons` | List saved metric comparisons |
| `GET` | `/api/metric-comparisons/{id}` | Retrieve metric deltas and distribution statistics |
| `GET` | `/api/metric-comparisons/{id}/report.pdf` | Download comparison report PDF |
| `DELETE` | `/api/metric-comparisons/{id}` | Delete comparison run and associated artifacts |

---

## Database Schema & Storage

### Relational Schema (PostgreSQL)

```text
users
├── id (BIGSERIAL, PRIMARY KEY)
├── email (VARCHAR, UNIQUE)
├── password_hash (VARCHAR, BCrypt)
├── full_name (VARCHAR)
└── created_at (TIMESTAMP)

metric_datasets
├── id (BIGSERIAL, PRIMARY KEY)
├── user_id (BIGINT, NULLABLE -> references users.id; NULL for global predefined)
├── project_name (VARCHAR)
├── project_version (VARCHAR)
├── dataset_family (VARCHAR: 'PROMISE' | 'AEEEM')
├── dataset_type (VARCHAR: 'PREDEFINED' | 'MANUAL')
├── row_count, feature_count (INT)
├── has_labels (BOOLEAN)
└── file_path (VARCHAR -> internal storage pointer)

prediction_runs
├── id (BIGSERIAL, PRIMARY KEY)
├── user_id (BIGINT -> references users.id)
├── source_dataset_id (BIGINT -> references metric_datasets.id)
├── target_dataset_id (BIGINT -> references metric_datasets.id)
├── comparison_group_id (VARCHAR, UUID grouping dual-target runs)
├── model_name (VARCHAR: 'KNN')
├── model_config (JSONB: k, coral, threshold, seed)
├── evaluation_metrics (JSONB: roc_auc, pr_auc, mcc, f1, confusion_matrix)
├── prediction_csv_path (VARCHAR)
└── report_pdf_path (VARCHAR)

metric_comparisons
├── id (BIGSERIAL, PRIMARY KEY)
├── user_id (BIGINT -> references users.id)
├── manual_dataset_id (BIGINT -> references metric_datasets.id)
├── predefined_dataset_id (BIGINT -> references metric_datasets.id)
├── comparison_config (JSONB: tolerances, matching rules)
├── comparison_results (JSONB: metric deltas, match percentages)
└── report_pdf_path (VARCHAR)
```

### Durable Storage Organization

```text
backend-java/storage/
├── metrics/
│   ├── predefined/          # Bundled benchmark datasets
│   └── {userId}/            # User uploads and AST extraction outputs
├── predictions/
│   └── {userId}/
│       ├── {uuid}-labeled.csv         # Labeled prediction dataset
│       ├── {uuid}-report.pdf          # Apache PDFBox evaluation report
│       └── {uuid}-report.pdf.json     # Metadata sidecar
└── comparisons/
    └── {userId}/
        ├── {uuid}-metric-comparison.pdf
        └── {uuid}-metric-comparison.pdf.json
```

---

## Verification & Testing

To execute automated tests across all tiers:

```bash
cd DefectLab-Updated-Component-Based
./scripts/run-tests.sh
```

- **Backend (Java)**: 154 automated unit, integration, and architecture contract tests (`mvn test`).
- **ML Microservice (Python)**: 37 automated tests verifying CORAL covariance transformations, KNN classification, median imputation, and route security (`pytest tests/`).
- **Frontend (Angular)**: Production AOT compilation and TypeScript strict type checking (`npm run build`).

---

## Academic & Research Attribution

DefectLab was developed as a final software engineering degree project (**SPL-3 / SE801 Project Defense**) at the **Institute of Information Technology (IIT), University of Dhaka**.

### Author
- **Md. Rakibul Islam** (Roll: BSSE1411)
- Degree: Bachelor of Science in Software Engineering (BSSE)
- Institute of Information Technology (IIT), University of Dhaka

### Core Citations

1. **BugMaps & Defect Hotspot Visualization**:
   Hora, A., Anquetil, N., Ducasse, S., Bhatti, M. U., Couto, C., Valente, M. T., & Martins, J. (2012). *BugMaps: A Tool for the Visual Exploration and Analysis of Bugs*. In *Proceedings of the 16th European Conference on Software Maintenance and Reengineering (CSMR)*.
2. **Squarified Treemap Layout Algorithm**:
   Bruls, M., Huizing, K., & van Wijk, J. J. (2000). *Squarified Treemaps*. In *Joint Eurographics and IEEE TCVG Symposium on Visualization (VisSym)*.
3. **AEEEM Dataset & Empirical Benchmark**:
   D'Ambros, M., Lanza, M., & Robbes, R. (2012). *Evaluating Defect Prediction Approaches: A Benchmark and an Extensive Comparison*. *IEEE Transactions on Software Engineering (TSE)*, 38(3), 531–544.
4. **Correlation Alignment (CORAL)**:
   Sun, B., Feng, J., & Saenko, K. (2016). *Return of Frustratingly Easy Domain Adaptation*. In *Proceedings of the AAAI Conference on Human Computation and Crowdsourcing*.
5. **PROMISE Repository of Empirical Software Engineering**:
   Menzies, T., Turhan, B., Bener, A., Gay, G., Cukic, B., & Jiang, Y. (2012). *Metrics Data from the PROMISE Repository of Empirical Software Engineering Data*. West Virginia University.

---

## License

This project is licensed under the **MIT License**. See the [LICENSE](LICENSE) file for details.
