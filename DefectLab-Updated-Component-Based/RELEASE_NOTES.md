# Corrected component-based release

This release replaces the uploaded prototype with a clean, component-oriented
codebase while preserving the working defect-prediction algorithms.

## Corrections

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

## Preserved behavior

- PROMISE and AEEEM extraction
- KNN with user-selected K from 1 through 5
- Log transformation and source-fitted standardization
- User-selectable shallow linear CORAL dataset alignment
- Independent immutable runs for different alignment/threshold settings
- Class-wise and aggregate comparison, evaluation, CSV export, and reports

## Verification

- Backend: 76 Maven tests passed.
- ML service: 31 pytest tests passed.
- Frontend: TypeScript application compilation passed.
- Structural contract: exactly four SQL tables and four JPA table mappings;
  no legacy controller endpoints or CSS gradients.

See `README.md` for setup and workflow, and
`docs/IMPLEMENTATION_LOG.md` for the complete step-by-step rebuild record.
