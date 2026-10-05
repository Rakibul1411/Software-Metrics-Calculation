# DefectLab FastAPI ML Service

This service provides machine learning capabilities for cross-project software defect prediction. It handles dataset schema validation, feature preprocessing, domain standardization, optional unsupervised domain adaptation (Shallow CORAL), K-Nearest Neighbors (KNN) classification, risk ranking, and post-prediction model evaluation.

It is an internal, stateless microservice invoked by the Spring Boot backend via authenticated HTTP requests.

For the complete product overview, see the [project README](../README.md).

## Technology Stack

- **Framework**: FastAPI with Uvicorn ASGI server
- **Data Manipulation**: pandas, NumPy
- **Machine Learning**: scikit-learn
- **Testing**: pytest

Pinned dependencies are listed in `requirements.txt`.

## Security and Network Boundary

The ML service is designed to run in a private network or container bridge:
- The browser never communicates directly with FastAPI.
- Every endpoint under `/ml/*` (except `/ml/health`) requires an internal service token sent in the `X-DefectLab-Service-Token` header.
- The service maintains no persistent database connections, session state, or file storage.

## Source Code Organization

The service is organized into modular packages separating API endpoints, core algorithms, and domain validation:

```text
app/
├── main.py                          FastAPI application setup, security middleware, and routes
├── core/
│   └── config.py                    Application settings and service token configuration
├── domain/                          Core domain logic and data transformations
│   ├── feature_profile.py           PROMISE (20 features) and AEEEM (56 features) specifications
│   ├── dataset_preparation.py       Header normalization, numeric coercion, label parsing
│   ├── prediction_pipeline.py       Preprocessing, CORAL alignment, KNN training, and ranking
│   └── evaluation.py                Performance metrics (ROC-AUC, PR-AUC, MCC, Recall@20% LOC)
├── services/                        Domain-independent mathematical services
│   └── shallow_coral_service.py     Correlation Alignment (CORAL) covariance adaptation
└── api/                             HTTP route controllers
    └── routes.py                    Endpoints for /ml/predict, /ml/evaluate, and /ml/health
```

## API Endpoints

| Method | Endpoint | Description |
|---|---|---|
| `GET` | `/ml/health` | Public internal health check endpoint |
| `POST` | `/ml/predict` | Executes data preparation, optional CORAL, KNN training, scoring, and ranking |
| `POST` | `/ml/evaluate` | Computes comprehensive classification evaluation metrics on labeled targets |

## Supported Metric Families

### PROMISE
- **Identifier**: `name` (fully qualified Java class name)
- **Features**: 20 object-oriented and structural metrics (e.g. WMC, DIT, RFC, CBO, LCOM, LOC)
- **Label**: Binarized bug count (`bug > 0` indicates defect)

### AEEEM
- **Identifier**: Class name or file path
- **Features**: 56 static, change history, and entropy metrics across 5 sub-families
- **Label**: Clean / Buggy state

Source and target datasets must share the same metric family. Cross-family prediction is rejected at the API boundary.

## Machine Learning Pipeline

Every `/ml/predict` request executes the following deterministic pipeline:

```text
1. Header Normalization & Validation
   - Normalize case, spaces, and known header aliases
   - Validate presence of required feature predictors
   
2. Data Cleaning & Imputation
   - Coerce numeric features; reject invalid non-numeric records
   - Median imputation using source training set statistics
   - Removal of zero-variance features in the training domain

3. Independent Standardization
   - Apply StandardScaler independently to source and target feature spaces
   - Ensures zero mean and unit variance per domain

4. Domain Adaptation (Optional Shallow CORAL)
   - When enabled, calculates covariance matrices of source and target domains
   - Applies closed-form whitening and re-coloring transformations:
     Cs^(-1/2) * Ct^(1/2)
   - Minimizes domain shift while preserving label topology

5. Model Training & Defect Scoring
   - Trains K-Nearest Neighbors classifier (K configurable from 1 to 5)
   - Uses Euclidean distance (p=2) and uniform distance weighting
   - Calculates posterior probability of defect from nearest neighbor votes

6. Risk Ranking & Categorization
   - Threshold decision boundary (default 0.5) assigns binary prediction
   - Rows are ranked in descending order of defect probability
   - Categorized into risk bands: HIGH (>= 0.7), MEDIUM (>= 0.4), LOW (< 0.4)
```

### Data Leakage Prevention

- Target labels are completely excluded from imputation, scaling, alignment, training, and scoring.
- When an unlabeled dataset is submitted for prediction, no labels are assumed or fabricated.
- Target labels (if available in benchmark datasets) are only supplied to `/ml/evaluate` after predictions are frozen.

## Evaluation Metrics

The `/ml/evaluate` endpoint computes a comprehensive set of performance metrics:

- **Confusion Matrix**: True Positive (TP), False Positive (FP), True Negative (TN), False Negative (FN)
- **Basic Rates**: Accuracy, Precision, Recall / True Positive Rate, Specificity / True Negative Rate
- **Balanced Metrics**: F1-Score, Balanced Accuracy, Matthews Correlation Coefficient (MCC)
- **Curve Areas**: Area Under the ROC Curve (ROC-AUC), Area Under the Precision-Recall Curve (PR-AUC)
- **Effort-Aware Metrics**: Recall@20% LOC and AUCEC (Area Under Cost-Effectiveness Curve) when LOC is present

## Configuration

| Environment Variable | Default Value | Description |
|---|---|---|
| `PROJECT_NAME` | `Defect Prediction ML Service` | Application title reported in OpenAPI docs |
| `ML_SERVICE_TOKEN` | `local-dev-service-token-32-chars-ok` | Shared authorization token matching Spring Boot |

## Installation & Setup

Create a virtual environment and install dependencies:

```bash
cd ml-service-python
python3 -m venv venv
venv/bin/python -m pip install --upgrade pip
venv/bin/python -m pip install -r requirements.txt pytest
```

## Running the Service

Start Uvicorn with auto-reload:

```bash
PYTHONPATH=. ./venv/bin/uvicorn app.main:app --host 0.0.0.0 --port 8000 --reload
```

Verify service status:

```bash
curl http://localhost:8000/ml/health
# {"status":"UP","service":"ml-service-python"}
```

## Testing

Run the automated test suite with pytest:

```bash
PYTHONPATH=. venv/bin/pytest tests -v
```

All **37 automated unit tests** verify:
- Feature profile discovery and alias mapping for PROMISE and AEEEM datasets.
- Schema verification and invalid character handling in dataset preparation.
- Closed-form whitening and re-coloring covariance alignment in Shallow CORAL.
- KNN training, probability thresholding, ranking, and tie-breaking stability.
- Evaluation metric correctness and division-by-zero safety checks.
