from __future__ import annotations

import logging
from typing import Any

from fastapi import APIRouter, HTTPException

from app.domain import evaluation, prediction_pipeline
from app.domain.dataset_preparation import SchemaError, prepare

logger = logging.getLogger("defectlab.ml.api")
router = APIRouter()


def _parse_bool(value: Any, default: bool = True) -> bool:
    if value is None:
        return default
    if isinstance(value, bool):
        return value
    val = str(value).strip().lower()
    if val in {"true", "1", "yes"}:
        return True
    if val in {"false", "0", "no"}:
        return False
    raise SchemaError("coral parameter must be a boolean.")


@router.get("/health")
def health_check() -> dict[str, str]:
    return {"status": "ok", "service": "defectlab-ml"}


@router.post("/predict")
def run_prediction(payload: dict[str, Any]) -> dict[str, Any]:
    try:
        model = str(payload.get("modelName", "KNN")).strip().upper()
        if model != "KNN":
            raise SchemaError(f"Unsupported model '{model}'. Currently only KNN is supported.")

        source_rows = payload.get("sourceRows") or []
        target_rows = payload.get("targetRows") or []
        family = payload.get("family")

        source = prepare(source_rows, family=family, require_labels=True)
        target = prepare(target_rows, family=family, require_labels=False)

        outcome = prediction_pipeline.run(
            source=source,
            target=target,
            threshold=float(payload.get("threshold", prediction_pipeline.DEFAULT_THRESHOLD)),
            seed=int(payload.get("seed", prediction_pipeline.DEFAULT_SEED)),
            coral_regularization=float(payload.get("coralRegularization", 1.0)),
            apply_coral=_parse_bool(payload.get("coral"), default=True),
            k=int(payload.get("k", 3)),
        )
    except (SchemaError, ValueError) as err:
        logger.warning("Prediction validation failed: %s", err)
        raise HTTPException(status_code=422, detail=str(err)) from err

    response = outcome.to_dict()
    response["targetHasLabels"] = target.labels is not None
    if target.labels is not None:
        label_map = dict(zip(target.identifiers, target.labels.tolist()))
        for pred in response["predictions"]:
            actual = label_map.get(pred["classIdentifier"])
            pred["actualLabel"] = None if actual is None else int(actual)

    response["sourceRowCount"] = len(source.identifiers)
    response["targetRowCount"] = len(target.identifiers)
    return response


@router.post("/evaluate")
def evaluate_predictions(payload: dict[str, Any]) -> dict[str, Any]:
    results = payload.get("results") or []
    if not results:
        raise HTTPException(status_code=422, detail="No prediction records provided for evaluation.")

    actual: list[int] = []
    predicted: list[int] = []
    scores: list[float] = []

    for row in results:
        if row.get("actualLabel") is None:
            continue
        actual.append(int(row["actualLabel"]))
        predicted.append(int(row["predictedLabel"]))
        scores.append(float(row["defectScore"]))

    if not actual:
        raise HTTPException(
            status_code=422,
            detail="The target dataset has no labels, so this run cannot be evaluated.",
        )

    try:
        return evaluation.evaluate(actual, predicted, scores, payload.get("locValues"))
    except ValueError as err:
        logger.warning("Evaluation failed: %s", err)
        raise HTTPException(status_code=422, detail=str(err)) from err
