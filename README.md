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

**DefectLab** is a cross-project software defect prediction (CPDP) and software metric analysis platform designed for Java codebases. The system provides automated static Abstract Syntax Tree (AST) analysis, Git repository change mining, domain adaptation via Shallow CORAL (Correlation Alignment), and supervised defect prediction using K-Nearest Neighbors (KNN).

In cross-project defect prediction, models are trained on historical, labeled defect datasets from one project (or release) and deployed to predict fault-prone components in another project that lacks historical defect labels. DefectLab addresses the primary technical challenge in CPDP—**domain shift** (discrepancy in feature distributions and covariances across disparate software systems)—by applying covariance matrix alignment to the source feature space while strictly preventing target-label leakage.

### Key Capabilities

- **Static AST Metric Extraction**: Automated calculation of 20 PROMISE object-oriented metrics from Java source archives (`.zip`) or public GitHub repositories using Eclipse JDT 3.37 and Apache BCEL.
- **Historical Git Mining**: Extraction of 56 AEEEM static, change, entropy, and churn metrics across 14-day commit snapshots from Git version control history.
- **Domain Adaptation (Shallow CORAL)**: Covariance whitening and recoloring to align source and target feature distributions prior to classification.
- **Supervised Defect Prediction**: Configurable K-Nearest Neighbors ($K \in [1, 5]$) classifier with calibrated defect probability scoring and descending risk prioritization.
- **Empirical Benchmark Evaluation**: Performance assessment against ground-truth labels computing ROC-AUC, PR-AUC, Matthews Correlation Coefficient (MCC), Precision, Recall, Specificity, F1-Score, and Recall@20% LOC.
- **Interactive Defect Hotspot Visualization**: Squarified Treemap visualization (Bruls et al. algorithm) mapping class hierarchies sized by Lines of Code (LOC) and colored by predicted defect probability.
- **Metric Verification & Comparison**: Class-wise and distribution-level verification comparing user-extracted metrics against canonical benchmarks under configurable tolerance thresholds ($\pm 5\%$, exact).
- **Automated Artifact Generation**: Cryptographically reproducible evaluation reports in PDF format (Apache PDFBox) and annotated CSV datasets for CI/CD integration.

---

## System Architecture

DefectLab is structured according to Clean Architecture and a 4-tier archetype:

```mermaid
flowchart TD
    subgraph Presentation_Layer["Presentation Layer (Port 4200)"]
        UI["Angular 19 SPA (Vite / Vanilla CSS)"]
        DESKTOP["Electron Desktop Wrapper (.dmg / .exe)"]
    end

    subgraph Application_Layer["Application & Business Layer (Port 8080)"]
        API["Spring Boot 2.7.18 REST API"]
        AUTH["Authentication & Session Manager"]
        ANALYSIS["AST Analyzer (Eclipse JDT + Git Miner)"]
        DATASET["Dataset Catalog & Quality Validator"]
        PREDICT["CPDP Prediction Orchestrator"]
        COMPARE["Metric Comparison Interactor"]
        REPORT["Apache PDFBox Report Generator"]
    end

    subgraph ML_Microservice["ML & Analytics Microservice (Port 8000 - Internal)"]
        ML["FastAPI Python 3.12 Engine"]
        PREP["Data Sanitizer & Median Imputer"]
        CORAL["Shallow CORAL Covariance Alignment"]
        KNN["Scikit-Learn KNN Classifier (K=1..5)"]
        EVAL["Evaluation Engine (ROC-AUC, PR-AUC, MCC)"]
    end

    subgraph Persistence_Layer["Persistence Layer"]
        DB[("PostgreSQL 16 / Neon DB")]
        STORAGE[("Durable File Storage (Metrics & PDF Artifacts)")]
    end

    DESKTOP --> UI
    UI -->|HTTP / JSON + Secure Cookie| API
    API --> AUTH
    API --> ANALYSIS
    API --> DATASET
    API --> PREDICT
    API --> COMPARE
    API --> REPORT

    PREDICT -->|REST (X-DefectLab-Service-Token)| ML
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

### Architectural Boundaries & Security

1. **Client Isolation**: The browser and desktop client interact exclusively with the Spring Boot backend on port `8080` (or through the frontend Nginx reverse proxy on port `4200`).
2. **Internal ML Isolation**: The Python FastAPI service operates inside an isolated Docker bridge network (`defectlab-net`). It is never exposed directly to public traffic and validates all requests using the internal `X-DefectLab-Service-Token` header.
3. **Artifact Immutability**: Executions never overwrite original dataset files. Every prediction run generates immutable database records and unique filesystem artifacts referenced by UUIDs.

---

## Repository Structure

```text
.
├── docker-compose.yml              # Root Docker Compose specification (pre-built images)
├── README.md                       # Canonical project documentation
├── DefectLab-Updated-Component-Based/
│   ├── backend-java/               # Spring Boot 2.7 REST API (Java 17, Eclipse JDT)
│   ├── frontend-angular/           # Angular 19 SPA (TypeScript, Vanilla CSS Design System)
│   ├── ml-service-python/          # FastAPI ML service (Python 3.12, scikit-learn, CORAL)
│   ├── desktop-electron/           # Native Electron application wrapper (.dmg / .exe)
│   ├── sample-data/
│   │   ├── predefined/             # Canonical benchmark datasets (Ant, Lucene, JDT, PDE, etc.)
│   │   └── manual-examples/        # Sample datasets for testing manual workflows
│   ├── scripts/
│   │   ├── setup.sh                # Local environment dependency installation script
│   │   ├── run-dev.sh              # Concurrent multi-service development launcher
│   │   └── run-tests.sh            # Automated verification across Java, Python, and Angular
│   ├── docker-compose.yml          # Source-build Docker Compose file
│   ├── docker-compose.prod.yml     # Production Docker Compose file
│   ├── push-docker.sh              # Multi-arch Docker Hub publication script
│   └── USER-MANUAL.md              # Operational guide for desktop packaging and updates
```

---

## Quick Start with Docker

Pre-built multi-architecture Docker images (`linux/amd64` and `linux/arm64`) are published on Docker Hub under `rakibalnatiq/defectlab-*`. You do not need Java, Maven, Node.js, or Python installed locally.

### Prerequisites

- [Docker Engine](https://docs.docker.com/engine/install/) with Docker Compose v2, or [Docker Desktop](https://www.docker.com/products/docker-desktop/) / [OrbStack](https://orbstack.dev/).
- Ensure the Docker daemon is active:
  ```bash
  docker info
  ```

---

### Method 1: Instant Launch (Recommended)

Run the following command directly from the cloned repository root:

```bash
docker compose up -d
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
1. Ensure the platform containers are active via `docker compose up -d`.
2. Launch the desktop application installer located in `DefectLab-Updated-Component-Based/desktop-electron/`:
   - macOS: `DefectLab-mac-arm64.dmg` or `DefectLab-mac-x64.dmg`
   - Windows: `DefectLab Setup.exe`
3. The desktop app provides automatic Docker daemon detection, an integrated splash screen, and offline local operations.

---

### Method 3: Build from Source with Docker

To compile and assemble Docker containers from local source code:

```bash
cd DefectLab-Updated-Component-Based
cp .env.example .env
docker compose up --build -d
```

---

### Container Lifecycle Management

```bash
# Check service health and status
docker compose ps

# Follow container logs
docker compose logs -f

# Follow logs for the backend container only
docker compose logs -f backend

# Pull the latest published images from Docker Hub
docker compose pull && docker compose up -d

# Stop all containers (preserving persistent database and storage volumes)
docker compose down

# Stop all containers and remove persistent volumes (full data wipe)
docker compose down -v
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

The script:
1. Creates `ml-service-python/venv` and installs required packages (`fastapi`, `uvicorn`, `scikit-learn`, `pandas`, `numpy`, `pytest`).
2. Installs Angular dependencies via `npm ci` in `frontend-angular`.
3. Compiles the Java backend using Maven.

---

### 2. Configure Environment

Start a local PostgreSQL instance (or launch the Docker database container):

```bash
docker compose up -d postgres
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
./scripts/run-dev.sh
```

- **Spring Boot** (`:8080`): Watches Java source code; recompiles and reloads context via Spring Boot DevTools.
- **FastAPI** (`:8000`): Auto-reloads through Uvicorn on changes in `ml-service-python/app`.
- **Angular** (`:4200`): Vite development server with Hot Module Replacement (HMR).

---

### 4. Running Services Individually

```bash
# Terminal 1: Python FastAPI ML Service
cd DefectLab-Updated-Component-Based/ml-service-python
source venv/bin/activate
uvicorn app.main:app --host 0.0.0.0 --port 8000 --reload

# Terminal 2: Spring Boot Backend
cd DefectLab-Updated-Component-Based/backend-java
mvn spring-boot:run -Dspring-boot.run.jvmArguments="-Xmx2g"

# Terminal 3: Angular Frontend
cd DefectLab-Updated-Component-Based/frontend-angular
npm start
```

---

## Configuration Reference

| Environment Variable | Affected Service | Default Value | Description |
|---|---|---|---|
| `DEFECTLAB_DB_URL` | Backend | `jdbc:postgresql://postgres:5432/defectlab` | JDBC database URL |
| `DEFECTLAB_DB_USER` | Backend | `defectlab` | PostgreSQL username |
| `DEFECTLAB_DB_PASSWORD` | Backend | `defectlab` | PostgreSQL password |
| `ML_SERVICE_BASE_URL` | Backend | `http://ml:8000` | URL of the internal FastAPI microservice |
| `ML_SERVICE_TOKEN` | Backend & ML | `local-development-token` | Shared secret header (`X-DefectLab-Service-Token`) |
| `PREDEFINED_DATA_DIR` | Backend | `/app/sample-data/predefined` | Directory containing canonical benchmark files |
| `STORAGE_ROOT` | Backend | `storage` | Filesystem root for uploads and PDF reports |
| `DEFECTLAB_SESSION_SECURE` | Backend | `false` | When `true`, enforces `Secure` attribute on cookies |
| `SPRING_PROFILES_ACTIVE` | Backend | `docker` | Active Spring profile (`docker` or `local`) |

---

## Metric Families Specification

DefectLab supports two standard empirical defect prediction metric families. Features across different families cannot be combined within a single run.

### 1. PROMISE Metric Family (20 Predictors)

Static object-oriented and structural code metrics extracted directly from Java source code:

| Metric | Name | Definition & Measurement Scope |
|---|---|---|
| `WMC` | Weighted Methods per Class | Sum of McCabe cyclomatic complexities across all methods in the class. |
| `DIT` | Depth of Inheritance Tree | Maximum length of inheritance path from the class to the root object. |
| `NOC` | Number of Children | Count of direct subclasses inheriting from this class. |
| `CBO` | Coupling Between Object Classes | Count of other classes to which this class is coupled. |
| `RFC` | Response for a Class | Number of methods in the class plus methods called by those methods. |
| `LCOM` | Lack of Cohesion in Methods | Difference between method pairs sharing no instance fields and those that do. |
| `Ca` | Afferent Couplings | Number of external classes that depend on this class. |
| `Ce` | Efferent Couplings | Number of external classes on which this class depends. |
| `NPM` | Number of Public Methods | Total methods declared with public visibility in the class. |
| `LCOM3` | Normalized Lack of Cohesion | Henderson-Sellers normalized cohesion metric, scaled between 0 and 2. |
| `LOC` | Lines of Code | Total non-blank, non-comment source lines in the class. |
| `DAM` | Data Access Metric | Ratio of private and protected attributes to total attributes. |
| `MOA` | Measure of Aggregation | Count of complex user-defined object types declared as member fields. |
| `MFA` | Measure of Functional Abstraction | Ratio of inherited methods to total methods accessible by the class. |
| `CAM` | Cohesion Among Methods | Parameter-type similarity across methods within the class [0, 1]. |
| `IC` | Inheritance Coupling | Number of parent classes where methods are invoked. |
| `CBM` | Coupling Between Methods | Total method calls directed at parent superclasses. |
| `AMC` | Average Method Complexity | Average size and complexity of member method declarations. |
| `Max_CC` | Maximum Cyclomatic Complexity | Highest McCabe decision complexity found in any method within the class. |
| `Avg_CC` | Average Cyclomatic Complexity | Mean McCabe cyclomatic complexity across all member methods. |

---

### 2. AEEEM Metric Family (56 Predictors)

Historical and change-based metrics mined from Git commit logs across 14-day snapshots (D'Ambros et al., 2012):

1. **Source Code Metrics (17 features)**: Chidamber-Kemerer (CK) metrics and class interface definitions.
2. **Change Metrics (15 features)**: Commit frequencies, distinct author counts, and modification deltas.
3. **Entropy of Changes (10 features)**: Shannon entropy measuring change dispersion across system components.
4. **Code Churn Metrics (8 features)**: Lines added, lines deleted, and maximum churn bursts per interval.
5. **Historical Defect Introductions (6 features)**: Defect-fixing commit frequency and previous bug introduction rates.

---

## Machine Learning & Domain Adaptation

### Standard Preparation Pipeline

```text
Input Datasets (Source + Target)
  └── Schema Normalization (Header alias mapping)
  └── Source-Median Imputation (Missing values imputed strictly using source medians)
  └── Zero-Variance Feature Removal (Constant columns pruned from both domains)
  └── Independent Domain Standardization (Source and target independently scaled via StandardScaler)
  └── Domain Adaptation via Shallow CORAL (Source covariance aligned to target covariance)
  └── Supervised Model Training (K-Nearest Neighbors, K ∈ [1, 5])
  └── Calibrated Probability Inference & Classification (P(bug) ≥ threshold)
  └── Risk Prioritization (Rows ranked descending by defect probability)
```

### Mathematical Formulation of Shallow CORAL

To minimize the distance between source and target feature distributions without using target labels, DefectLab implements the CORAL algorithm (Sun et al., 2016):

Given source feature matrix $X_S \in \mathbb{R}^{n_S \times d}$ and target feature matrix $X_T \in \mathbb{R}^{n_T \times d}$, both zero-centered:

1. **Covariance Calculation**:
   $$C_S = \frac{1}{n_S - 1} X_S^T X_S + \epsilon I_d$$
   $$C_T = \frac{1}{n_T - 1} X_T^T X_T + \epsilon I_d$$
   where $\epsilon = 10^{-5}$ is a regularization constant guaranteeing positive definiteness.

2. **Covariance Whitening & Recoloring**:
   $$\hat{X}_S = X_S \cdot C_S^{-1/2} \cdot C_T^{1/2}$$

3. **Classification**:
   The KNN classifier is trained on aligned source representations $\hat{X}_S$ with source labels $y_S$, and evaluated directly on normalized target representations $X_T$. Target labels $y_T$ are never exposed during preprocessing or transformation.

### Empirical Evaluation Metrics

When predicting against labeled benchmark datasets, the evaluation engine calculates:

- **ROC-AUC**: Area Under the Receiver Operating Characteristic Curve.
- **PR-AUC**: Area Under the Precision-Recall Curve.
- **Matthews Correlation Coefficient (MCC)**:
  $$MCC = \frac{TP \times TN - FP \times FN}{\sqrt{(TP+FP)(TP+FN)(TN+FP)(TN+FN)}}$$
- **Recall@20% LOC**: Proportion of defective classes identified within the top 20% of the codebase ranked by LOC.
- **Confusion Matrix**: True Positives (TP), False Positives (FP), True Negatives (TN), and False Negatives (FN).

---

## Step-by-Step Workflow Walkthrough

### Example: Predicting Defect Risk in Apache Ant 1.6 using Ant 1.3

1. **Sign In**: Navigate to `http://localhost:4200` and authenticate.
2. **Review Catalog**: Open **Datasets** to verify registered benchmark datasets (`Ant-1.3`, `Ant-1.7`, `Lucene-2.4`, `JDT`, `PDE`, `EQ`, etc.).
3. **Add Target Codebase**:
   - Navigate to **Datasets** $\rightarrow$ **Add Dataset**.
   - Upload `sample-data/manual-examples/ant-1.6-manual.csv`.
   - Set Project: `Ant`, Version: `1.6`, Family: `PROMISE`, Type: `MANUAL`.
4. **Configure CPDP Execution**:
   - Navigate to **Predictions** $\rightarrow$ **New Prediction Run**.
   - Source: `Ant 1.3 (PREDEFINED)`
   - Manual Target: `Ant 1.6 (MANUAL)`
   - Predefined Target: `Ant 1.6 (PREDEFINED)` *(optional; enables benchmark evaluation)*
   - Parameters: Set $K = 3$, Check **Apply Dataset Alignment (CORAL)**, Threshold = `0.5`.
   - Click **Run Prediction**.
5. **Analyze Results**:
   - Review ranked classes sorted by descending defect probability.
   - Inspect the **Squarified Treemap** to identify defect hotspots sized by LOC.
   - Review the empirical evaluation metrics (ROC-AUC, PR-AUC, MCC, Confusion Matrix).
6. **Export Artifacts**:
   - Download the generated **PDF Evaluation Report** for documentation and audit purposes.
   - Download the annotated **Target CSV** containing predicted labels and risk bands.

---

## REST API Specification

Base URL: `http://localhost:8080`

### Authentication
| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/api/auth/register` | Register a new user account |
| `POST` | `/api/auth/login` | Authenticate and create session |
| `GET` | `/api/auth/me` | Fetch active session profile |
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

The application manages four primary relational tables:

- **`users`**: User identities, authentication records, and BCrypt password hashes.
- **`metric_datasets`**: Dataset catalog records, ownership (`user_id = NULL` for global benchmarks), file paths, and schema validation flags.
- **`prediction_runs`**: Executed prediction records, model hyperparameters (JSONB), evaluation metrics (JSONB), and artifact paths.
- **`metric_comparisons`**: Comparison runs between manual and predefined datasets, tolerance configurations (JSONB), and comparison results (JSONB).

### Durable Storage Layout

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

DefectLab is developed as a final Software Engineering degree project (**SPL-3 / SE801 Project Defense**) at the **Institute of Information Technology (IIT), University of Dhaka**.

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
