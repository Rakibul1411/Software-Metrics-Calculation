# DefectLab FastAPI ML Service

This module implements schema validation, preparation, optional shallow
CORAL, KNN prediction, and evaluation. It is an internal stateless service
called by Spring Boot.

For the complete product workflow, see the [project README](../README.md).

## Technology

- Python
- FastAPI
- pandas
- NumPy
- scikit-learn
- Uvicorn

Pinned versions are listed in `requirements.txt`.

## Security boundary

The browser must not call this service directly.

`app.main` middleware protects every `/ml/*` route except `/ml/health`.
Spring Boot must send:

```text
X-DefectLab-Service-Token: <shared ML_SERVICE_TOKEN>
```

The service has no database credentials, user sessions, or durable artifact
storage.

## Source structure

The layout follows the clean-architecture dependency rule: `domain/` is pure
business logic with zero framework imports, `services/` holds
domain-independent algorithms, and `api/` is the *only* package allowed to
import FastAPI and translate HTTP payloads to and from domain calls.
`domain/` and `services/` never import from `api/`.

```text
app/
├── main.py                          composition root: FastAPI app, token middleware, router wiring
├── core/
│   └── config.py                    environment settings
├── domain/                          framework-free business logic
│   ├── feature_profile.py           PROMISE/AEEEM feature registries and header aliases
│   ├── dataset_preparation.py       schema validation, label parsing, PreparedFrame
│   ├── prediction_pipeline.py       preparation + KNN pipeline (PipelineOutcome)
│   └── evaluation.py                classification evaluation metrics
├── services/                        generic, domain-independent algorithms
│   └── shallow_coral_service.py     covariance alignment (linear CORAL)
└── api/                             the only package that imports fastapi
    └── routes.py                    /ml route handlers
```

Adding a new capability: put the business rule in `domain/` (or a new module
there) with no FastAPI import, unit-test it directly, then add a thin
`api/routes.py` handler that calls it and shapes the JSON response. Removing
a capability is symmetric — delete the route handler, then the now-unused
`domain/` function, and the corresponding test file.

## Internal API

| Method | Route | Purpose |
|---|---|---|
| `GET` | `/ml/health` | Public internal-health check |
| `POST` | `/ml/predict` | Prepare, train, predict, and rank |
| `POST` | `/ml/evaluate` | Calculate metrics from actual/predicted results |

`/` and `/health` also return basic service information. Spring Boot uses
`/ml/health`.

## Supported families

### PROMISE

- canonical identifier: `name`;
- 20 registered predictors;
- recognized actual-label aliases.

### AEEEM

- 56 registered static/history predictors;
- recognized clean/buggy label forms.

Source and target must use the same family.

## Fixed standard pipeline

Every `/ml/predict` request uses:

```text
Header normalization
  -> family/feature validation
  -> numeric coercion
  -> source-median imputation
  -> zero-variance source-feature removal
  -> StandardScaler fit independently on each domain (zero mean, unit variance)
  -> optional shallow CORAL source-to-target alignment
  -> KNN fit with user-selected K=1-5
  -> probability and thresholded label
  -> descending risk rank
```

The `coral` boolean controls whether dataset alignment runs before fitting.
Each domain is standardized with its own mean/variance (not a source-fit
scaler reused on target), matching CORAL's assumption that both domains
independently reach zero mean and unit variance before alignment (Sun, Feng
& Saenko, Section 2.1).

### Leakage prevention

Target labels are excluded from:

- imputation;
- scaling;
- CORAL;
- fitting; and
- prediction.

Actual labels are returned only for post-prediction evaluation when a labeled
target supplies them.

## KNN behavior

- K is selected by the user from 1 through 5.
- K cannot exceed the number of source rows.
- Uniform weights and Euclidean/Minkowski `p=2` distance are used.
- Probabilities are the Buggy-neighbor proportion.

## Prediction output

Each target row produces:

```json
{
  "classIdentifier": "example.Class",
  "defectScore": 0.82,
  "defectProbability": 0.82,
  "predictedLabel": 1,
  "riskRank": 1,
  "riskBand": "HIGH"
}
```

The response also includes family, model, used features, selected K,
threshold, seed, applied-pipeline flags, covariance distances, and warnings.

## Evaluation

`/ml/evaluate` calculates:

- confusion matrix;
- accuracy;
- precision;
- recall;
- specificity;
- F1;
- balanced accuracy;
- Matthews correlation coefficient;
- ROC-AUC;
- PR-AUC;
- Recall@20% LOC when applicable;
- AUCEC when applicable.

Undefined metrics return `value: null` and a reason.

## Configuration

| Variable | Default | Purpose |
|---|---|---|
| `PROJECT_NAME` | `Defect Prediction ML Service` | FastAPI title |
| `ML_SERVICE_TOKEN` | local development token | Shared Spring/FastAPI secret |

Use a long random `ML_SERVICE_TOKEN` outside local development. Bind host and
port are fixed in the Dockerfile/uvicorn command, not read from environment
settings.

## Install

```bash
python3 -m venv venv
venv/bin/python -m pip install --upgrade pip
venv/bin/python -m pip install -r requirements.txt pytest
```

## Run

From the repository root, start FastAPI with change detection:

```bash
export ML_SERVICE_TOKEN='replace-with-the-shared-value'
scripts/run-python-dev.sh
```

Uvicorn watches `ml-service-python/app` and restarts the worker after Python
changes. The launcher uses `ml-service-python/venv/bin/python` when available
and otherwise falls back to `python3`.

Health:

```bash
curl http://localhost:8000/ml/health
```

## Test

From this directory:

```bash
PYTHONPATH=. venv/bin/python -m pytest tests -q
```

Compile check:

```bash
venv/bin/python -m compileall -q app tests
```

Test files mirror the `domain/` modules one-to-one (`test_feature_profile.py`,
`test_dataset_preparation.py`, `test_prediction_pipeline.py`,
`test_evaluation.py`), plus `test_shallow_coral_service.py` for the
`services/` layer. Shared row-building fixtures live in `tests/helpers.py`.

## Development rules

- Keep this service stateless.
- Do not add database or browser authentication logic.
- Preserve source-only fitting and target-label isolation.
- Keep feature aliases/registries in `domain/feature_profile.py`.
- Return readable `SchemaError` messages for invalid data.
- Keep the preparation pipeline fixed unless the product contract explicitly
  changes.
- Preserve one output prediction for every input target record.
- Nothing under `domain/` or `services/` may import `fastapi`; only `api/`
  may. This keeps every business rule directly unit-testable without an HTTP
  client.
