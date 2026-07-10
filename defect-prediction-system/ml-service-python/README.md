# ML Service Python (FastAPI)

This folder contains the Python FastAPI microservice responsible for:
- Preprocessing and normalizing metrics.
- Correlation Alignment (CORAL) domain adaptation.
- K-Nearest Neighbors (KNN) defect classification.

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
- `app/services/`: Services for CORAL, KNN, and Preprocessing
- `app/validation/`: Feature schema verification
