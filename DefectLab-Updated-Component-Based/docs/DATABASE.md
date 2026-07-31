# Database

DefectLab uses exactly four PostgreSQL tables: `users` plus the three workflow
tables `metric_datasets`, `metric_comparisons`, and `prediction_runs`.
`schema.sql` removes obsolete prototype tables, migrates the former dataset
column names, and Hibernate validates the final mappings.

## `metric_datasets`

Stores dataset identity, ownership, type (`MANUAL` or `PREDEFINED`), actual-label
availability, row/metric counts, and the immutable source metrics file path.
The identity tuple `(user_id, dataset_family, project_name, project_version,
dataset_type)` is unique. Shared bundled predefined rows use a null `user_id`;
all user-created rows have an owner.

Prediction never updates `metrics_file_path`.

## `metric_comparisons`

Stores one unique MANUAL/PREDEFINED pair, its JSONB comparison configuration,
and the generated PDF report path. Dataset IDs must differ.

Supported configuration:

```json
{
  "hasIdentifierColumn": false,
  "identifierColumnName": null,
  "comparisonMode": "AGGREGATE",
  "absoluteTolerance": 0.0001,
  "relativeTolerance": 0.01
}
```

AEEEM data without identifiers uses aggregate statistics. PROMISE data with a
`name` identifier uses normalized instance-wise matching.

## `prediction_runs`

Stores one source/target result per row. `model_config` contains `modelName`
(`KNN` or `SVM`), K/C/kernel settings, and the applied standard-pipeline
metadata. MANUAL targets store a
labeled CSV path; PREDEFINED targets keep `prediction_file_path` null. Every row
stores a non-null PDF report path.

A dual-target request inserts two rows with the same `comparison_group_id`.
Rows are inserted only after prediction, CSV (when applicable), PDF, and result
metadata files have all been generated.

The JSONB model configuration records KNN/SVM settings, threshold, seed, family,
and the applied standard-pipeline metadata. It does not expose a selectable
pipeline mode.

## Ownership

Every dataset lookup permits only the current user's rows plus shared predefined
rows. Prediction and comparison lookups require an exact `user_id` match.
Database file paths are never returned as public API fields; authenticated
download routes resolve them server-side.

User-owned datasets cannot be deleted while referenced by a prediction or
metric comparison. Shared predefined rows have `user_id = NULL` and cannot be
deleted through the user API.
