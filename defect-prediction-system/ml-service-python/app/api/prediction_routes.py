from fastapi import APIRouter, UploadFile, File, Form, HTTPException
from pandas.errors import EmptyDataError, ParserError
from typing import List

from app.services.prediction_service import PredictionService

router = APIRouter()
prediction_service = PredictionService()


@router.post("/predict")
async def run_prediction(
    target_file: UploadFile = File(...),
    source_files: List[UploadFile] = File(...),
    label_column: str = Form(default="bug"),
    knn_value: int = Form(default=5),
    coral_option: bool = Form(default=True)
):
    """
    Run defect prediction.
    - target_file: CSV of unlabelled target project metrics
    - source_files: One or more labelled source dataset CSVs
    - label_column: Column name for defect labels
    - knn_value: Number of neighbors for KNN classifier
    - coral_option: Whether to apply CORAL domain adaptation
    """
    try:
        return await prediction_service.run(
            target_file=target_file,
            source_files=source_files,
            label_column=label_column,
            knn_value=knn_value,
            coral_option=coral_option
        )
    except (ValueError, KeyError, EmptyDataError, ParserError) as error:
        raise HTTPException(status_code=400, detail=str(error)) from error
