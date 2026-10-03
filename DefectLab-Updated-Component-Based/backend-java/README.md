# DefectLab Spring Boot Backend

This module is the authenticated API server and workflow orchestrator for the DefectLab platform. It manages user accounts, source-code repository cloning and archive ingestion, static and historical metric extraction, dataset storage, prediction coordination, metric benchmark comparison, and artifact generation.

For the full product overview and end-to-end workflow, see the [project README](../README.md).

## Technology Stack

- **Runtime**: Java 17 (tested and compatible with Java 17, 21, and 23)
- **Framework**: Spring Boot 2.7.18 (Spring MVC, Spring Data JPA, Spring Security Crypto)
- **Database & Pooling**: PostgreSQL 14+ / Neon with HikariCP connection pooling
- **Static Analysis & AST Parsing**: Eclipse JDT 3.37
- **Data & Archive Processing**: Apache Commons CSV, Commons IO, Commons Compress
- **Report Generation**: Apache PDFBox
- **Logging**: SLF4J with Logback

## Runtime Responsibilities

The backend server is responsible for:

1. **Authentication & Session Security**: Manages user registration, login, logout, and session lifecycle via HTTP-only secure cookies (`DEFECTLAB_SESSION`).
2. **Source Ingestion & Extraction**: Ingests Java ZIP archives or clones public GitHub repositories, extracts 20 PROMISE object-oriented metrics or 56 AEEEM static/historical metrics using Eclipse JDT and Git history analysis.
3. **Dataset Storage & Validation**: Validates schema consistency, header naming, and data quality for uploaded CSV and ARFF files, persisting metadata in PostgreSQL.
4. **Prediction Orchestration**: Enforces dataset ownership and compatibility, invokes the Python ML service via an authenticated internal HTTP client, and formats prediction results.
5. **Artifact Generation**: Renders reproducible PDF evaluation reports, labeled target CSVs, and JSON summary sidecars.
6. **Benchmark Comparison**: Computes metric-by-metric distribution deltas and tolerance checks between extracted metrics and predefined benchmark datasets.
7. **Secure Downloads**: Exposes authenticated streaming endpoints for datasets and generated report artifacts without exposing underlying filesystem paths.

The browser communicates exclusively with Spring Boot on port 8080 and never contacts the internal ML service directly.

## Package Architecture

The codebase follows a modular, domain-driven package organization under `org.metrics.defectlab`:

```text
src/main/java/org/metrics/defectlab/
├── DefectLabApplication.java          Application entry point and Spring Boot configuration
├── analysis/                          Source code ingestion and metric extraction engine
│   ├── api/                           REST controller (POST /api/analysis)
│   ├── usecase/                       Extraction orchestration and command models
│   │   └── port/                      Interfaces for extractors, Git client, and storage
│   ├── infrastructure/                GitHub clone service, archive extractors, temp files
│   ├── javaparser/                    Eclipse JDT configuration resolver and environment
│   ├── promise/                       20-feature PROMISE AST and bytecode extraction
│   └── aeeem/                         56-feature AEEEM static, change, and entropy miner
├── auth/                              Authentication and user profile management
│   ├── api/                           AuthController (login, register, session, password)
│   ├── domain/                        User entity and password validation policy
│   ├── usecase/                       Registration, authentication, and password use cases
│   │   └── port/                      UserRepository and PasswordHasher interfaces
│   ├── infrastructure/                Spring Data JPA persistence and BCrypt hasher
│   └── security/                      CurrentUser session resolver helper
├── dataset/                           Metric dataset catalog and file storage
│   ├── api/                           DatasetController (upload, list, preview, delete)
│   ├── domain/                        MetricDataset entity and DatasetQuality validator
│   ├── usecase/                       Upload, retrieval, seeding, and deletion use cases
│   │   └── port/                      MetricDatasetRepository and DatasetFileReader interfaces
│   └── infrastructure/                JPA persistence, ARFF/CSV parser, and manifest seeder
├── prediction/                        Cross-project defect prediction orchestration
│   ├── api/                           PredictionController (run, list, inspect, download)
│   ├── domain/                        PredictionRun entity and model configurations
│   ├── usecase/                       Prediction execution and artifact management
│   │   └── port/                      PredictionRunRepository, MlServiceClient, PDF renderer
│   └── infrastructure/                JPA persistence, REST ML client, and PDFBox renderer
├── comparison/                        Extracted vs. benchmark metric comparison
│   ├── api/                           MetricComparisonController
│   ├── domain/                        MetricComparison entity
│   ├── usecase/                       Comparison calculation and report export
│   │   └── port/                      MetricComparisonRepository and report renderer
│   └── infrastructure/                JPA persistence and PDFBox comparison renderer
├── composition/                       Cross-module guards and dependency composition adapters
└── shared/                            Cross-cutting infrastructure and utilities
    ├── api/                           DashboardController and ReportController
    ├── config/                        Web MVC, CORS, and Jackson configuration
    ├── database/                      Schema initializer and contract enforcement
    ├── exception/                     Global exception handling and error responses
    └── storage/                       StorageRoot resolver for on-disk file management
```

### Architectural Principles

- **Controller Layer (`api`)**: Maps incoming HTTP requests to application commands and translates domain outputs to HTTP response entities.
- **Application Services (`usecase`)**: Implements application workflows and business operations, orchestrating interactions between repositories, external clients, and analyzers.
- **Domain Layer (`domain`)**: Encapsulates core business models, invariants, and quality rules.
- **Infrastructure Layer (`infrastructure`)**: Implements data persistence (Spring Data JPA), external service integrations (HTTP REST client), file storage, and PDF rendering.
- **Separation of Concerns**: Cross-component references (such as checking whether a dataset is in use before deletion) are decoupled via interfaces and composition adapters.

## Public API Endpoints

| Base Path | Responsibility |
|---|---|
| `/api/auth` | User registration, login, logout, password change, current session |
| `/api/dashboard` | Aggregated dashboard KPI metrics, recent activity, and summaries |
| `/api/analysis` | Source code metric extraction from Java ZIP or public GitHub URL |
| `/api/datasets` | Dataset upload, catalog query, data preview, raw download, and deletion |
| `/api/predictions` | Prediction execution, run history, detailed inspections, and artifact retrieval |
| `/api/metric-comparisons` | Baseline comparison execution, tolerance reporting, and PDF download |
| `/api/reports` | Download endpoints for prediction PDF reports |

Detailed request/response contracts and schema examples are documented in [SE801_FINAL_DESIGN_DOCUMENT.md](../research-and-docs/docs/SE801_FINAL_DESIGN_DOCUMENT.md#2-interface-design-rest-api-contracts).

## Database Schema and Initializer

On application startup, `DatabaseSchemaContract` executes `src/main/resources/schema.sql` to initialize or validate the following relational tables:

1. `users`: Account credentials (email, name, BCrypt password hash, timestamps).
2. `metric_datasets`: Dataset catalog records (project name, version, family, type, row/column counts, label presence, and storage file path).
3. `metric_comparisons`: Comparison run records between manual and benchmark datasets, storing tolerance configurations and report paths.
4. `prediction_runs`: Defect prediction execution records with hyperparameters, source/target dataset references, evaluation metrics, and artifact paths.

Hibernate runs with `hibernate.ddl-auto=validate`, ensuring strict alignment between JPA entities and database tables.

Bundled predefined datasets are automatically registered on startup via `DatasetPredefinedSeeder` using entries defined in `sample-data/predefined/manifest.csv` with `user_id = NULL` (globally visible to all users).

## Storage Layout

All persistent files are managed under `StorageRoot` (defaulting to the `storage/` directory relative to the process working directory):

```text
storage/
├── metrics/
│   ├── predefined/             Bundled benchmark datasets (Ant, Lucene, JDT, PDE, EQ, LC, ML)
│   └── {userId}/               User-uploaded or extracted CSV/ARFF metric files
├── predictions/
│   └── {userId}/               Generated prediction artifacts:
│       ├── {uuid}-labeled.csv  Target dataset augmented with predicted defect labels
│       ├── {uuid}-report.pdf   Formatted PDF evaluation report
│       └── {uuid}-report.json  Machine-readable execution summary sidecar
├── comparisons/
│   └── {userId}/               Generated comparison artifacts:
│       └── comparison-{id}.pdf PDF tolerance and distribution comparison report
└── uploads/
    └── {userId}/               Temporary staging directory for uploaded ZIP archives
```

Files are never referenced directly by raw disk paths in client APIs; instead, they are streamed through authenticated endpoints after verifying user ownership.

## Configuration

The application is configured through `application.yml` and overridable via environment variables:

| Environment Variable | Description | Default / Local Value |
|---|---|---|
| `DEFECTLAB_DB_URL` | PostgreSQL JDBC connection URL | `jdbc:postgresql://localhost:5432/defectlab` |
| `DEFECTLAB_DB_USER` | Database username | `defectlab` |
| `DEFECTLAB_DB_PASSWORD` | Database password | `defectlab` |
| `ML_SERVICE_BASE_URL` | Base URL of the internal Python ML service | `http://localhost:8000` |
| `ML_SERVICE_TOKEN` | Shared secret token for authenticating with ML service | `local-dev-service-token-32-chars-ok` |
| `PREDEFINED_DATA_DIR` | Path to benchmark datasets and manifest | `../sample-data/predefined` |
| `STORAGE_ROOT` | Base directory for file storage | `storage` |
| `DEFECTLAB_SESSION_SECURE` | Set `true` in production to enforce HTTPS-only cookies | `false` |
| `SPRING_PROFILES_ACTIVE` | Active Spring profile | `local` |

## Running Locally

Ensure PostgreSQL (port 5432) and the Python ML Service (port 8000) are running.

```bash
cd backend-java
export DEFECTLAB_DB_URL='jdbc:postgresql://localhost:5432/defectlab'
export DEFECTLAB_DB_USER='defectlab'
export DEFECTLAB_DB_PASSWORD='defectlab'
export ML_SERVICE_BASE_URL='http://localhost:8000'
export ML_SERVICE_TOKEN='local-dev-service-token-32-chars-ok'

mvn spring-boot:run -Dspring-boot.run.jvmArguments="-Xmx2g"
```

The server starts on port `8080`. Health can be checked via:

```bash
curl -i http://localhost:8080/api/auth/me
# Returns 401 Unauthorized when unauthenticated
```

## Automated Testing

```bash
mvn clean test
```

The test suite consists of **154 automated tests** (153 passing, 1 network-dependent live GitHub clone test skipped by default):
- Complete AST calculation and feature extraction for all 20 PROMISE and 56 AEEEM metrics.
- Unsupervised domain adaptation (CORAL) configuration and hyperparameter validation.
- Row-wise natural sorting stability across multi-part package hierarchies and class names.
- In-memory CSV and ARFF parsing, dialect detection, and schema quality validation.
- PDFBox rendering for prediction and comparison report documents.
- Authentication, session security, and authorization guard tests.
- Global exception mapping and REST error response formatting.
