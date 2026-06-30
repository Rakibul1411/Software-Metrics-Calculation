import os
from dataclasses import dataclass


@dataclass(frozen=True)
class Settings:
    """Runtime settings with no dependency on Pydantic's optional settings package."""

    project_name: str = os.getenv("PROJECT_NAME", "Defect Prediction ML Service")
    ml_service_host: str = os.getenv("ML_SERVICE_HOST", "0.0.0.0")
    ml_service_port: int = int(os.getenv("ML_SERVICE_PORT", "8000"))
    temp_dir: str = os.getenv("TEMP_DIR", "temp")


settings = Settings()
