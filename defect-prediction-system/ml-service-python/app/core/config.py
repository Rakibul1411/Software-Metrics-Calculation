from pydantic import BaseSettings


class Settings(BaseSettings):
    project_name: str = "Defect Prediction ML Service"
    ml_service_host: str = "0.0.0.0"
    ml_service_port: int = 8000
    temp_dir: str = "temp"

    class Config:
        env_file = ".env"


settings = Settings()
