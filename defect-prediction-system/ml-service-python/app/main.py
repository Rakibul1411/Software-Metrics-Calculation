from fastapi import FastAPI
from app.api import prediction_routes
from app.core.config import settings

app = FastAPI(title=settings.project_name, version="1.2.0")

app.include_router(prediction_routes.router, prefix="/ml", tags=["prediction"])


@app.get("/")
def root():
    return {"message": "Software Defect Prediction ML Service", "version": "1.2.0"}


@app.get("/health")
def health():
    return {"status": "ok"}
