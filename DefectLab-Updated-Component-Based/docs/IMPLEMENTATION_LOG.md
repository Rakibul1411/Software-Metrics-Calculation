# Implementation log

This is the step-by-step record of the corrected component-based rebuild.

## Step 1 — Audit the uploaded project

- Inspected the uploaded Spring Boot, Angular, and FastAPI code.
- Preserved the existing PROMISE and AEEEM extraction engines.
- Identified why Neon still showed old tables: `CREATE TABLE IF NOT EXISTS`
  created the new schema but never deleted the prototype schema.
- Removed generated build caches and obsolete UI/legacy prediction surfaces.
- Standardized database/service configuration around environment variables and
  removed local H2 artifacts.

## Step 2 — Enforce the exact four-table persistence contract

- Replaced the prototype model with `users`, `metric_datasets`,
  `metric_comparisons`, and `prediction_runs`.
- Added startup cleanup for `datasets`, `prediction_results`,
  `dataset_comparisons`, `compare_metrics`, and `flyway_schema_history`.
- Added a PostgreSQL metadata guard that fails startup if any unexpected table
  remains.
- Added JPA entities/repositories aligned one-to-one with the schema.
- Kept system benchmarks visible through nullable `metric_datasets.user_id`.

## Step 3 — Separate source analysis from dataset storage

- Moved Java archive/GitHub extraction to `/api/analysis`.
- Kept `/api/datasets` focused on storage, validation, preview, and download.
- Added PROMISE/AEEEM schema detection and quality validation.
- Persisted extracted datasets as `MANUAL` without fabricated labels.
- Added list, detail, preview, quality, download, and safe-delete APIs.

## Step 4 — Bundled benchmarks

- Curated labeled Ant, Lucene, JDT, EQ, PDE, LC, and ML data.
- Added a manifest-driven startup seeder without adding a table.
- Added separate manual example datasets for testing.
- Added AEEEM benchmark name/version/repository auto-fill.

## Step 5 — Prediction pipeline

- Added deterministic KNN with manual K=1–5.
- Added nearest-neighbor tie-break for even K.
- Added SVM with manual C and kernel.
- Added imputation, log1p, standard scaling, shallow CORAL, thresholding,
  and risk ranking.
- Consolidated preprocessing into one fixed standard pipeline; the UI and API
  no longer expose pipeline variants or log/CORAL switches.
- Protected the internal FastAPI API with a service token.

## Step 6 — Run persistence and comparison

- Validated labeled source, manual target, predefined target, family, and
  distinct dataset identity.
- Saved a new labeled CSV only for a manual target; predefined targets retain
  their original benchmark file.
- Created one immutable `prediction_runs` row per target.
- Grouped dual-target runs with one `comparison_group_id`.
- Created independent `metric_comparisons` rows for compatible
  MANUAL/PREDEFINED pairs.
- Added PROMISE class-wise and AEEEM aggregate comparison.
- Added labeled predefined-target evaluation and metric distribution comparison.

## Step 7 — Rebuild the backend around components

- Replaced mixed packages such as `org.metrics.service`,
  `org.metrics.controller`, and central `domain/repository` folders.
- Added explicit `analysis`, `auth`, `dataset`, `prediction`, `dashboard`,
  `report`, and `shared` components.
- Split each business component into `api`, `application`, `domain`,
  `persistence`, and `infrastructure` where applicable.
- Removed the unused `/api/metrics` and `/api/comparisons` prototype endpoints.

## Step 8 — Professional dashboard

- Rebuilt navigation around Dashboard, Analyze, Metric storage, Predictions,
  Reports, and Account.
- Added responsive cards, tables, filters, state badges, pipeline displays,
  risk scores, empty/loading/error states, and export actions.
- Added guided source analysis and matching target selection.
- Removed every CSS gradient and kept solid navy/blue surfaces.
- Organized Angular screens by feature instead of a generic `pages` folder.

## Step 9 — Reporting and documentation

- Added reproducible authenticated PDF reports and JSON metadata sidecars.
- Added prediction CSV export and filtered API views.
- Added Docker/local/Neon setup, architecture, database, API, ML, and user docs.
- Added setup, dev, test, and Compose workflows.
- Added a report-ready component diagram and backend package map.

## Step 10 — Verification

- Verified the Angular TypeScript application contract.
- Passed all 76 Maven backend tests.
- Passed all 31 Python tests, including manual KNN, SVM, and shallow CORAL.
- Audited source/package inputs for obsolete persistence references and
  documented that production credentials must come from environment variables.
- Passed the exact table/entity, package-path, legacy-endpoint, and no-gradient
  structural checks.
- Prepared a clean source archive excluding dependencies, build outputs,
  temporary data, and runtime databases.
