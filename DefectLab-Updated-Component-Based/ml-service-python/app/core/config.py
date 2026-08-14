import os
from dataclasses import dataclass


@dataclass(frozen=True)
class Settings:
    """Runtime settings with no dependency on Pydantic's optional settings package."""

    project_name: str = os.getenv("PROJECT_NAME", "Defect Prediction ML Service")
    ml_service_token: str = os.getenv(
        "ML_SERVICE_TOKEN", "local-development-token"
    )


settings = Settings()
