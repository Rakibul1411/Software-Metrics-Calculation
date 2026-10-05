import logging
import secrets

from fastapi import FastAPI, Request
from fastapi.responses import JSONResponse

from app.api.routes import router as ml_router
from app.core.config import settings

logger = logging.getLogger("defectlab.ml")

app = FastAPI(
    title=settings.project_name,
    version="2.0.0",
    docs_url="/docs",
    redoc_url=None,
)


@app.middleware("http")
async def verify_service_token(request: Request, call_next):
    path = request.url.path.rstrip("/")
    if path.startswith("/ml/") and path != "/ml/health":
        token = request.headers.get("X-DefectLab-Service-Token", "")
        if not secrets.compare_digest(token, settings.ml_service_token):
            return JSONResponse(
                status_code=401,
                content={"detail": "A valid internal service token is required."},
            )
    return await call_next(request)


app.include_router(ml_router, prefix="/ml", tags=["ml"])


@app.get("/")
def root():
    return {"service": "DefectLab ML Service", "version": "2.0.0", "status": "running"}


@app.get("/health")
def health():
    return {"status": "ok"}
