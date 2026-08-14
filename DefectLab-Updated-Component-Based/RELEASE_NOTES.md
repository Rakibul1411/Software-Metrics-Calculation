# Release Notes

## 2026-08-14: CORAL fidelity, delete workflows, toasts, and cleanup

### Correctness fix

- The CORAL preprocessing step now matches Sun, Feng & Saenko (2016) exactly:
  removed an extraneous `log1p` transform that was never part of the paper,
  and switched from a single scaler fit on source and reused on target to an
  **independent `StandardScaler` per domain** — source and target each reach
  their own zero mean/unit variance before alignment, matching the paper's
  stated assumption (Section 2.1) rather than carrying source statistics onto
  target.

### New capability: delete workflows

- Prediction runs and metric comparisons can now be deleted independently of
  datasets, each removing both the database row and its on-disk artifacts
  (CSV/PDF and metadata sidecar): `DELETE /api/predictions/{id}` and
  `DELETE /api/metric-comparisons/{id}`, mirroring the existing
  `DELETE /api/datasets/{id}`.
- Deleting a prediction run or comparison frees any dataset it referenced for
  deletion, since dataset deletion is blocked while a saved run/comparison
  still points at it.

### New capability: toast notifications

- A shared top-right toast system (`ToastService` + `<ui-toast>`, mounted
  once at the app root) now confirms every add, delete, update, and download
  action across Analyze Source, Metric Storage, Predictions, Compare
  Metrics, Reports, and Account.
- Add/update flows now return the user to the relevant list page after a
  success toast, instead of navigating into the new record's detail page.

### Frontend structure

- Predictions and Compare Metrics were each split into list, create, and
  detail pages (mirroring the earlier Datasets split), with the native
  `<select>` element replaced app-wide by a custom `ui-select` component
  whose popup direction and size are controllable.
- Extracted two shared components to remove duplicated logic: `ui-delete-action`
  (trigger button + confirm dialog + delete call + toast, previously
  hand-rolled identically on three detail pages) and `ui-search-toggle` (the
  icon-to-inline-input toggle, previously duplicated on three list pages).
- All shared components live under `frontend-angular/src/app/shared/<name>/`,
  one folder per component; styling remains fully global
  (`src/app/defectlab.css`), with no per-component stylesheets.

### ML service restructuring

- `ml-service-python` now follows a clean-architecture layout: `domain/`
  (feature registry, dataset preparation, prediction pipeline, evaluation —
  zero FastAPI imports), `services/` (the CORAL algorithm, domain-independent),
  and `api/` (the only package allowed to import FastAPI). Test files mirror
  the domain modules one-to-one.
- Removed dead code found while restructuring: two FastAPI routes with zero
  callers (`/schema/validate`, `/compare`) and their corresponding unused
  Java `MlServiceClient` methods; unused `config.py` fields
  (`ml_service_host`, `ml_service_port`, `temp_dir`); vestigial pipeline
  output fields (`kCandidates`, `kScores`, always-constant values left over
  from an earlier design) and an unused `dropped_history_columns` field.
- Removed the Java `PreprocessingController` and its two Angular-unreachable
  routes (`GET /api/preprocessing/{family}`, `GET /api/preprocessing/datasets/{id}/preview`),
  which existed solely to preview the now-removed `log1p` transform.

### Verification

- Backend: 128 Maven tests passed.
- ML service: 36 pytest tests passed.
- Frontend: Angular production build passed.

---

## Corrected component-based release

This release replaces the uploaded prototype with a clean, component-oriented
codebase while preserving the working defect-prediction algorithms.

### Corrections

- The database now contains exactly `users`, `metric_datasets`,
  `metric_comparisons`, and `prediction_runs`.
- Startup explicitly removes the obsolete prototype tables and validates the
  final PostgreSQL schema.
- Backend code is grouped by business component: `analysis`, `auth`, `dataset`,
  `prediction`, `dashboard`, `report`, and `shared`.
- Source-code analysis uses `/api/analysis`; stored dataset operations use
  `/api/datasets`; prediction runs use `/api/predictions`.
- The Angular application is organized by the same product features.
- Every CSS gradient was replaced by a solid color.

### Preserved behavior

- PROMISE and AEEEM extraction
- KNN with user-selected K from 1 through 5
- User-selectable shallow linear CORAL dataset alignment
- Independent immutable runs for different alignment/threshold settings
- Class-wise and aggregate comparison, evaluation, CSV export, and reports

### Verification (at the time of this release)

- Backend: 76 Maven tests passed.
- ML service: 31 pytest tests passed.
- Frontend: TypeScript application compilation passed.
- Structural contract: exactly four SQL tables and four JPA table mappings;
  no legacy controller endpoints or CSS gradients.

See `README.md` for setup and workflow, and
`docs/IMPLEMENTATION_LOG.md` for the complete step-by-step rebuild record.
