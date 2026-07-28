from typing import List, Optional

from fastapi import APIRouter, File, Form, HTTPException, UploadFile
from pandas.errors import EmptyDataError, ParserError

from app.services.prediction_service import PredictionService

router = APIRouter()
prediction_service = PredictionService()


@router.post("/predict")
async def run_prediction(
    target_file: UploadFile = File(...),
    source_files: List[UploadFile] = File(...),
    label_column: str = Form(default="bug"),
    classifier: Optional[str] = Form(default=None),
    classifier_type: str = Form(default="knn"),
    knn_value: int = Form(default=5),
    svm_c: float = Form(default=1.0),
    coral_option: bool = Form(default=True),
    top_k: int = Form(default=3),
    auto_tune_k: bool = Form(default=True),
    auto_tune_svm_c: bool = Form(default=True),
    decision_threshold: Optional[float] = Form(default=None),
    threshold_beta: float = Form(default=2.0),
):
    """
    Run cross-project defect prediction on a target that may be unlabelled.

    - classifier_type: ``knn`` or ``svm``
    - source_files: labelled training projects
    - target_file: target project metrics; labels are not required for prediction
    - CORAL uses target feature statistics but never target labels
    - KNN K or SVM C is selected automatically from labelled source data
    - multiple sources use Leave-One-Source-Project-Out validation
    - one source uses repeated stratified source-internal validation
    - decision_threshold: omit to tune macro MCC on source validation only
    - knn_value and svm_c remain backward-compatible fallback values
    """
    try:
        return await prediction_service.run(
            target_file=target_file,
            source_files=source_files,
            label_column=label_column,
            classifier_type=classifier or classifier_type,
            knn_value=knn_value,
            svm_c=svm_c,
            coral_option=coral_option,
            top_k=top_k,
            auto_tune_k=auto_tune_k,
            auto_tune_svm_c=auto_tune_svm_c,
            decision_threshold=decision_threshold,
            threshold_beta=threshold_beta,
        )
    except (ValueError, KeyError, EmptyDataError, ParserError) as error:
        raise HTTPException(status_code=400, detail=str(error)) from error


@router.post("/evaluate")
async def evaluate_prediction(
    target_file: UploadFile = File(...),
    source_files: List[UploadFile] = File(...),
    label_column: str = Form(default="bug"),
    classifier: Optional[str] = Form(default=None),
    classifier_type: str = Form(default="knn"),
    knn_value: int = Form(default=5),
    svm_c: float = Form(default=1.0),
    coral_option: bool = Form(default=True),
    top_k: int = Form(default=3),
    auto_tune_k: bool = Form(default=True),
    auto_tune_svm_c: bool = Form(default=True),
    decision_threshold: Optional[float] = Form(default=None),
    threshold_beta: float = Form(default=2.0),
):
    """
    Evaluate a labelled target without using its labels during model training.

    The target labels are used only after prediction to calculate metrics.
    """
    try:
        return await prediction_service.evaluate(
            target_file=target_file,
            source_files=source_files,
            label_column=label_column,
            classifier_type=classifier or classifier_type,
            knn_value=knn_value,
            svm_c=svm_c,
            coral_option=coral_option,
            top_k=top_k,
            auto_tune_k=auto_tune_k,
            auto_tune_svm_c=auto_tune_svm_c,
            decision_threshold=decision_threshold,
            threshold_beta=threshold_beta,
        )
    except (ValueError, KeyError, EmptyDataError, ParserError) as error:
        raise HTTPException(status_code=400, detail=str(error)) from error
