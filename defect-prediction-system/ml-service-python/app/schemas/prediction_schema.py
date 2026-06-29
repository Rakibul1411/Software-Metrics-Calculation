from pydantic import BaseModel
from typing import List, Dict, Any


class PredictionResultItem(BaseModel):
    class_name: str
    prediction: str


class PredictionResponse(BaseModel):
    status: str
    predictions: List[Dict[str, Any]]
