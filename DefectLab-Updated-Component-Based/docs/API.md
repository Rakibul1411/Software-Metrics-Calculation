# API reference

Spring Boot base URL: `http://localhost:8080`.

The Angular application calls Spring Boot with `withCredentials: true`.
Registration and login are public; workflow routes require the authenticated
`DEFECTLAB_SESSION` cookie. FastAPI is an internal service and is never called
directly by the browser.

## Authentication

| Method | Route | Purpose |
|---|---|---|
| `POST` | `/api/auth/register` | Create an account and start a session |
| `POST` | `/api/auth/login` | Authenticate and start a session |
| `POST` | `/api/auth/logout` | Invalidate the current session |
| `GET` | `/api/auth/me` | Return the signed-in user or `401` |
| `POST` | `/api/auth/password` | Change the current user's password |

Registration body:

```json
{
  "name": "Example User",
  "email": "user@example.com",
  "password": "strong-password"
}
```

Login body contains `email` and `password`. Password change contains
`currentPassword` and `newPassword`.

## Dashboard

| Method | Route | Purpose |
|---|---|---|
| `GET` | `/api/dashboard` | Return current-user totals and recent activity |

## Source analysis

| Method | Route | Purpose |
|---|---|---|
| `POST` | `/api/analysis` | Extract and register a MANUAL metric dataset |

The request is `multipart/form-data`.

| Field | Required | Meaning |
|---|---:|---|
| `projectArchive` | One input required | Java ZIP; supported for PROMISE |
| `githubUrl` | One input required | Public GitHub URL; supported for PROMISE/AEEEM |
| `projectName` | No | Inferred when omitted |
| `projectVersion` | Yes | Release/version identity |
| `datasetFamily` | No | `PROMISE` by default, or `AEEEM` |
| `aeeemProfile` | No | Historical AEEEM profile; defaults to `current` |

Provide exactly one of `projectArchive` or `githubUrl`. AEEEM rejects an archive
because its history predictors require Git commits.

## Datasets

| Method | Route | Purpose |
|---|---|---|
| `POST` | `/api/datasets` | Save and register a CSV/ARFF dataset |
| `GET` | `/api/datasets` | List owned plus shared predefined datasets |
| `GET` | `/api/datasets/{id}` | Dataset details and feature names |
| `GET` | `/api/datasets/{id}/preview` | Preview up to 25 rows |
| `GET` | `/api/datasets/{id}/quality` | Quality issues, warnings, and columns |
| `GET` | `/api/datasets/{id}/download` | Download the stored source metrics |
| `DELETE` | `/api/datasets/{id}` | Delete an unused user-owned dataset |

Upload is `multipart/form-data`:

| Field | Required | Meaning |
|---|---:|---|
| `datasetFile` | Yes | `.csv` or `.arff`, maximum 50 MB |
| `projectName` | No | Falls back to the filename |
| `projectVersion` | No | Falls back to `unspecified` |
| `datasetFamily` | No | Selected family; detected headers must agree |
| `datasetType` | No | `PREDEFINED` by default, or `MANUAL` |

Bundled predefined datasets cannot be deleted. A user-owned dataset cannot be
deleted while referenced by a saved prediction or metric comparison.

## Preprocessing inspection

| Method | Route | Purpose |
|---|---|---|
| `GET` | `/api/preprocessing/{family}` | Return PROMISE/AEEEM preprocessing registry |
| `GET` | `/api/preprocessing/datasets/{id}/preview` | Return raw/transformed feature preview |

These routes inspect the fixed standard pipeline. They do not configure
pipeline alternatives.

## Predictions

| Method | Route | Purpose |
|---|---|---|
| `POST` | `/api/predictions` | Run one target or a grouped pair of targets |
| `GET` | `/api/predictions` | List target-specific saved runs |
| `GET` | `/api/predictions/groups` | List grouped dual-target runs |
| `GET` | `/api/predictions/{id}` | Complete run details |
| `GET` | `/api/predictions/{id}/predictions` | Ranked prediction rows |
| `GET` | `/api/predictions/{id}/prediction.csv` | Download MANUAL labeled CSV |
| `GET` | `/api/predictions/{id}/report.pdf` | Download prediction PDF |
| `GET` | `/api/reports/{id}.pdf` | Alternate authenticated PDF route used by Angular |

`GET /api/predictions/{id}/predictions` accepts:

- `limit`, default `500`, bounded by the backend;
- `defectiveOnly`, default `false`.

### KNN request

```json
{
  "sourceDatasetId": 10,
  "manualTargetDatasetId": 20,
  "predefinedTargetDatasetId": 21,
  "modelName": "KNN",
  "k": 3,
  "threshold": 0.5,
  "seed": 42
}
```

### SVM request

```json
{
  "sourceDatasetId": 10,
  "predefinedTargetDatasetId": 21,
  "modelName": "SVM",
  "c": 1.0,
  "kernel": "RBF",
  "threshold": 0.5,
  "seed": 42
}
```

Rules:

- at least one target ID is required;
- the source must be labeled;
- targets must differ from the source and use the same family;
- manual target type must be `MANUAL`;
- predefined target type must be `PREDEFINED` and labeled;
- KNN K must be `1..5`;
- SVM C must be greater than `0` and at most `1000`;
- SVM kernel must be `LINEAR`, `RBF`, `POLY`, or `SIGMOID`;
- threshold must be strictly between `0` and `1`.

The standard preparation pipeline is automatic. There are no public
preprocessing mode or log/CORAL switch fields.

A dual response contains:

```json
{
  "comparisonGroupId": "uuid",
  "runIds": [101, 102],
  "runs": []
}
```

One `prediction_runs` row is saved per completed target. Both rows share the
group ID.

## Metric comparisons

| Method | Route | Purpose |
|---|---|---|
| `POST` | `/api/metric-comparisons` | Compare a MANUAL/PREDEFINED pair |
| `GET` | `/api/metric-comparisons` | List saved comparisons |
| `GET` | `/api/metric-comparisons/eligible-pairs` | List compatible dataset pairs |
| `GET` | `/api/metric-comparisons/{id}` | Comparison details |
| `GET` | `/api/metric-comparisons/{id}/report.pdf` | Download comparison PDF |

Example:

```json
{
  "manualDatasetId": 20,
  "predefinedDatasetId": 21,
  "comparisonConfig": {
    "hasIdentifierColumn": true,
    "identifierColumnName": "name",
    "comparisonMode": "CLASS_WISE",
    "absoluteTolerance": 0.0001,
    "relativeTolerance": 0.01
  }
}
```

PROMISE can use `CLASS_WISE` comparison when identifiers match. AEEEM data
without portable identifiers uses `AGGREGATE`.

## Internal FastAPI API

FastAPI normally listens at `http://localhost:8000`. Spring Boot sends
`X-DefectLab-Service-Token` on protected calls.

| Method | Route | Token | Purpose |
|---|---|---:|---|
| `GET` | `/ml/health` | No | Internal service health |
| `POST` | `/ml/schema/validate` | Yes | Validate rows against a family registry |
| `POST` | `/ml/preprocessing/preview` | Yes | Preview registered transformations |
| `POST` | `/ml/predict` | Yes | Prepare, fit, predict, and rank |
| `POST` | `/ml/evaluate` | Yes | Evaluate predictions against actual labels |
| `POST` | `/ml/compare` | Yes | Compare metric/prediction results |
| `GET` | `/ml/registry/{family}` | Yes | Return family registry information |

FastAPI does not access the database or user file storage.

## Error behavior

Validation errors return a client error with a readable `error` or `detail`
message. Missing/unauthorized resources are not exposed across users. Internal
file paths remain server-side; downloads are resolved only after ownership
checks.
