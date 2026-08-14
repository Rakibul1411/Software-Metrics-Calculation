import secrets

from fastapi import FastAPI, Request
from fastapi.responses import JSONResponse
from app.api import routes
from app.core.config import settings

app = FastAPI(title=settings.project_name, version="2.0.0")


@app.middleware("http")
async def require_internal_service_token(request: Request, call_next):
    """Only Spring Boot may call ML operations; public health checks stay open."""
    path = request.url.path.rstrip("/")
    if path.startswith("/ml/") and path != "/ml/health":
        supplied = request.headers.get("X-DefectLab-Service-Token", "")
        if not secrets.compare_digest(supplied, settings.ml_service_token):
            return JSONResponse(
                status_code=401,
                content={"detail": "A valid internal service token is required."},
            )
    return await call_next(request)


# DefectLab owns the documented internal /ml paths.
app.include_router(routes.router, prefix="/ml", tags=["defectlab"])


@app.get("/")
def root():
    return {"message": "Software Defect Prediction ML Service", "version": "2.0.0"}


@app.get("/health")
def health():
    return {"status": "ok"}
