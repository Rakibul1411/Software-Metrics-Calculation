# ML Service Python (FastAPI)

This folder contains the Python FastAPI microservice responsible for:
- Preprocessing and normalizing metrics.
- Closed-form shallow/linear Correlation Alignment (CORAL) domain adaptation.
- K-Nearest Neighbors (KNN) and linear-SVM defect classification.
- Labelled-target evaluation with accuracy, precision, recall, F1, ROC AUC,
  and confusion-matrix counts.

The implementation follows the original linear CORAL whitening/re-colouring
transformation:

```text
Cs = cov(Xs) + I
Ct = cov(Xt) + I
A  = Cs^(-1/2) Ct^(1/2)
Xs* = Xs A
```

It is deliberately not Deep CORAL: there is no neural network, trainable
representation, gradient, or CORAL loss.

## Run locally (Python 3.11-3.14)

```bash
python3 -m venv venv
source venv/bin/activate
python -m pip install --upgrade pip
python -m pip install -r requirements.txt
python -m uvicorn app.main:app --host 0.0.0.0 --port 8000 --reload
```

Keep this terminal running while using prediction. Verify the service at
`http://localhost:8000/health`; it should return `{"status":"ok"}`.

## Structure
- `app/api/`: API Routes
- `app/services/`: preprocessing, shallow CORAL, KNN, SVM, prediction, and evaluation
- `app/validation/`: Feature schema verification
