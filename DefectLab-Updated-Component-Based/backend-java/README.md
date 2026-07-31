# DefectLab Spring Boot Backend

This module is the authenticated system boundary and workflow orchestrator. It
owns accounts, source acquisition, metric extraction, dataset persistence,
prediction coordination, metric comparison, artifact generation, and access
control.

For the full product workflow, see the [project README](../README.md).

## Technology

- Java 17
- Spring Boot 2.7.18
- Spring MVC
- Spring Data JPA
- PostgreSQL/Neon
- Eclipse JDT 3.37
- Apache Commons CSV/IO/Compress
- Apache PDFBox
- BCrypt through Spring Security Crypto

## Runtime responsibilities

The backend:

1. authenticates the user through an HTTP session;
2. safely receives Java ZIPs, public GitHub URLs, CSV, and ARFF uploads;
3. extracts PROMISE or AEEEM metrics;
4. validates and stores datasets;
5. enforces dataset ownership and prediction compatibility;
6. calls the internal FastAPI service with a shared token;
7. generates prediction CSV, PDF, and JSON artifacts;
8. saves immutable run/comparison metadata; and
9. exposes authenticated download routes.

The browser never calls FastAPI directly.

## Component structure

All code is under `org.metrics.defectlab`.

```text
src/main/java/org/metrics/defectlab/
├── DefectLabApplication.java
├── analysis/
│   ├── api/                  POST /api/analysis
│   ├── application/          extraction coordination
│   ├── infrastructure/       GitHub, ZIP, and temporary-file adapters
│   ├── javaparser/           Eclipse JDT configuration
│   ├── promise/              20-feature PROMISE extraction
│   └── aeeem/                static/history AEEEM extraction
├── auth/
│   ├── api/
│   ├── application/
│   ├── domain/
│   ├── persistence/
│   └── security/
├── dataset/
│   ├── api/
│   ├── application/
│   ├── domain/
│   ├── infrastructure/
│   └── persistence/
├── prediction/
│   ├── api/
│   ├── application/
│   ├── domain/
│   ├── infrastructure/
│   └── persistence/
├── comparison/
│   ├── api/
│   ├── application/
│   ├── domain/
│   └── persistence/
├── dashboard/api/
├── report/api/
└── shared/
    ├── config/
    ├── csv/
    ├── database/
    ├── exception/
    ├── export/
    ├── model/
    └── report/
```

Within a component:

- `api` defines the HTTP boundary;
- `application` coordinates use cases;
- `domain` contains entities and business rules;
- `persistence` contains repositories;
- `infrastructure` integrates files, Git, and FastAPI.

## Public API groups

| Base route | Component |
|---|---|
| `/api/auth` | Registration, session, logout, password |
| `/api/dashboard` | Workspace summary |
| `/api/analysis` | Java ZIP/GitHub metric extraction |
| `/api/datasets` | Dataset upload, list, preview, quality, download, delete |
| `/api/preprocessing` | Registry and transformation preview |
| `/api/predictions` | Execute, list, group, inspect, and download runs |
| `/api/metric-comparisons` | Independent dataset comparison |
| `/api/reports` | Authenticated prediction PDF download |

See [docs/API.md](../docs/API.md) for fields and examples.

## Database contract

`src/main/resources/schema.sql` creates or validates exactly:

1. `users`
2. `metric_datasets`
3. `metric_comparisons`
4. `prediction_runs`

The initializer removes known obsolete prototype tables and
`flyway_schema_history`. Hibernate uses `ddl-auto=validate`, so the JPA model
must match this schema.

### Table ownership

- `users`: account and BCrypt password hash.
- `metric_datasets`: dataset identity, ownership, type, label state, counts, and
  stored metric path.
- `metric_comparisons`: user-owned manual/predefined pair, JSONB configuration,
  and PDF path.
- `prediction_runs`: user-owned source/target result, JSONB model configuration,
  optional manual CSV path, and PDF path.

Bundled predefined datasets use `metric_datasets.user_id = NULL` and are visible
to all signed-in users.

## Dataset flow

### Source analysis

`POST /api/analysis` accepts either:

- `projectArchive` for PROMISE; or
- `githubUrl` for PROMISE/AEEEM.

AEEEM rejects archive input because history predictors require Git. Successful
analysis creates a validated, unlabeled `MANUAL` dataset.

### Metric upload

`POST /api/datasets` accepts CSV/ARFF files up to 50 MB. The backend detects the
feature family from headers, validates quality, detects actual labels, copies
the source file into durable storage, and saves metadata.

The source metric file is never modified by prediction.

## Prediction orchestration

`PredictionService` requires:

- a labeled source;
- at least one target;
- a MANUAL target and/or a labeled PREDEFINED target;
- the same metric family across source/targets; and
- different source/target dataset identities.

The user configures:

- `KNN` with K from 1 to 5; or
- `SVM` with C and `LINEAR`, `RBF`, `POLY`, or `SIGMOID`;
- threshold strictly between 0 and 1;
- optional seed, default 42.

The backend always records log transformation and CORAL as applied standard
pipeline metadata. It does not accept a selectable pipeline mode.

For each target:

1. load source/target rows;
2. call FastAPI `/ml/predict`;
3. attach predefined actual labels only after prediction;
4. call `/ml/evaluate` for a predefined target;
5. generate a labeled CSV only for a manual target;
6. generate PDF and JSON report artifacts; and
7. insert `prediction_runs` only after all artifacts succeed.

A dual-target request creates two rows with one `comparison_group_id`.
Partially generated artifacts are deleted when execution fails.

## Runtime storage

Paths are created relative to the backend working directory:

```text
storage/
├── metrics/
│   ├── predefined/
│   └── {userId}/
└── predictions/
    └── {userId}/
        ├── {uuid}-labeled.csv
        ├── {uuid}-report.pdf
        └── {uuid}-report.json
```

Internal paths never appear as public download URLs.

## Configuration

Use environment variables:

| Variable | Purpose | Local example |
|---|---|---|
| `DEFECTLAB_DB_URL` | PostgreSQL JDBC URL | `jdbc:postgresql://localhost:5432/defectlab` |
| `DEFECTLAB_DB_USER` | Database user | `defectlab` |
| `DEFECTLAB_DB_PASSWORD` | Database password | local password |
| `ML_SERVICE_BASE_URL` | FastAPI URL | `http://localhost:8000` |
| `ML_SERVICE_TOKEN` | Shared internal token | same value as FastAPI |
| `PREDEFINED_DATA_DIR` | Benchmark manifest directory | `../sample-data/predefined` |
| `DEFECTLAB_SESSION_SECURE` | HTTPS-only session cookie | `false` locally |
| `SPRING_PROFILES_ACTIVE` | Spring profile | `local` |

Never commit real database credentials or service tokens.

## Run locally

Start PostgreSQL and FastAPI first, then:

```bash
export DEFECTLAB_DB_URL='jdbc:postgresql://localhost:5432/defectlab'
export DEFECTLAB_DB_USER='defectlab'
export DEFECTLAB_DB_PASSWORD='defectlab'
export ML_SERVICE_BASE_URL='http://localhost:8000'
export ML_SERVICE_TOKEN='replace-with-the-shared-value'
mvn spring-boot:run -Dspring-boot.run.jvmArguments="-Xmx2g"
```

The API listens on <http://localhost:8080>.

## Test

```bash
mvn test
```

The current suite covers extraction, parsing, history calculations, file
storage, schema structure, PDF generation, comparison, and application rules.

Useful checks:

```bash
curl -i http://localhost:8080/api/auth/me
```

An unauthenticated request should return `401`.

## Security notes

- Password hashes are never returned.
- Session cookies are HTTP-only and SameSite Lax.
- Ownership is checked before every dataset/run/comparison file access.
- ZIP paths and upload sizes are validated.
- Only public GitHub repositories are supported.
- FastAPI protected routes use `X-DefectLab-Service-Token`.
- Enable secure cookies behind production HTTPS.
