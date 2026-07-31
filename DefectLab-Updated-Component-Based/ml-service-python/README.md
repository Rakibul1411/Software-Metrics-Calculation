# DefectLab FastAPI ML Service

This module implements schema validation, preprocessing, shallow CORAL,
KNN/SVM prediction, evaluation, and comparison. It is an internal stateless
service called by Spring Boot.

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

```text
app/
├── main.py                         FastAPI app and token middleware
├── core/
│   └── config.py                  environment settings
├── defectlab/
│   ├── registry.py                PROMISE/AEEEM feature registries
│   ├── preparation.py             header/schema/label preparation
│   ├── pipeline.py                fixed preparation + KNN/SVM pipeline
│   ├── evaluation.py              classification evaluation
│   └── routes.py                  /ml route handlers
└── services/
    └── shallow_coral_service.py   covariance alignment
```

## Internal API

| Method | Route | Purpose |
|---|---|---|
| `GET` | `/ml/health` | Public internal-health check |
| `POST` | `/ml/schema/validate` | Validate family schema and data quality |
| `POST` | `/ml/preprocessing/preview` | Show registered raw/transformed feature statistics |
| `POST` | `/ml/predict` | Prepare, train, predict, and rank |
| `POST` | `/ml/evaluate` | Calculate metrics from actual/predicted results |
| `POST` | `/ml/compare` | Calculate comparison output |
| `GET` | `/ml/registry/{family}` | Return PROMISE/AEEEM registry metadata |

`/` and `/health` also return basic service information. Spring Boot uses
`/ml/health`.

## Supported families

### PROMISE

- canonical identifier: `name`;
- 20 registered predictors;
- recognized actual-label aliases;
- selected non-negative skewed features receive `log1p`.

### AEEEM

- 56 registered static/history predictors;
- recognized clean/buggy label forms;
- comparison can operate without portable class identifiers.

Source and target must use the same family.

## Fixed standard pipeline

Every `/ml/predict` request uses:

```text
Header normalization
  -> family/feature validation
  -> numeric coercion
  -> source-median imputation
  -> registered log1p transforms
  -> zero-variance source-feature removal
  -> source-fitted StandardScaler
  -> shallow CORAL source-to-target alignment
  -> KNN or SVM fit
  -> probability and thresholded label
  -> descending risk rank
```

There is no preprocessing mode parameter. Log transformation and CORAL always
run as normal pipeline steps.

### Leakage prevention

Target labels are excluded from:

- imputation;
- log transformation;
- scaling;
- CORAL;
- fitting; and
- prediction.

Actual labels are returned only for post-prediction evaluation when a labeled
target supplies them.

## KNN behavior

- K is manual and must be from 1 through 5.
- K cannot exceed the number of source rows.
- Uniform weights and Euclidean/Minkowski `p=2` distance are used.
- An exact even-K tie at threshold `0.5` uses the closest neighbor's label.
- Probabilities are the defective-neighbor proportion.

## SVM behavior

- C must be greater than zero.
- Kernels: `linear`, `rbf`, `poly`, `sigmoid`.
- Gamma defaults to `scale`.
- Balanced class weights are used.
- Both clean and defective source classes are required.
- The seed controls reproducible probability fitting behavior.

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

The response also includes family, model, used features, removed constants,
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
| `ML_SERVICE_HOST` | `0.0.0.0` | Bind host |
| `ML_SERVICE_PORT` | `8000` | Bind port |
| `ML_SERVICE_TOKEN` | local development token | Shared Spring/FastAPI secret |
| `TEMP_DIR` | `temp` | Temporary-work directory |

Use a long random `ML_SERVICE_TOKEN` outside local development.

## Install

```bash
python3 -m venv venv
venv/bin/python -m pip install --upgrade pip
venv/bin/python -m pip install -r requirements.txt pytest
```

## Run

```bash
export ML_SERVICE_TOKEN='replace-with-the-shared-value'
venv/bin/python -m uvicorn app.main:app --reload --host 0.0.0.0 --port 8000
```

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

The suite covers schema preparation, labels, fixed pipeline behavior, KNN/SVM,
CORAL covariance distance, deterministic output, evaluation, and comparison.

## Development rules

- Keep this service stateless.
- Do not add database or browser authentication logic.
- Preserve source-only fitting and target-label isolation.
- Keep feature aliases/registries in `registry.py`.
- Return readable `SchemaError` messages for invalid data.
- Keep the preparation pipeline fixed unless the product contract explicitly
  changes.
- Preserve one output prediction for every input target record.
