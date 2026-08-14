"""Internal ML endpoints. Not exposed to the browser; Spring Boot calls these."""

from __future__ import annotations

from typing import Any

from fastapi import APIRouter, HTTPException

from app.domain import evaluation, prediction_pipeline
from app.domain.dataset_preparation import SchemaError, prepare

router = APIRouter()


def _bad_request(exception: Exception) -> HTTPException:
    return HTTPException(status_code=422, detail=str(exception))


def _boolean_value(value: Any, default: bool) -> bool:
    if value is None:
        return default
    if isinstance(value, bool):
        return value
    normalized = str(value).strip().lower()
    if normalized in {"true", "1"}:
        return True
    if normalized in {"false", "0"}:
        return False
    raise SchemaError("coral must be a boolean value.")


@router.get("/health")
def health() -> dict:
    return {"status": "ok", "service": "defectlab-ml"}


@router.post("/predict")
def predict(payload: dict[str, Any]) -> dict:
    """
    Runs the standard pipeline. The target's labels are never read here; when
    the target carries labels they are returned untouched so the caller can
    store them and evaluate afterwards.
    """
    try:
        model_name = str(payload.get("modelName", "KNN")).strip().upper()
        if model_name != "KNN":
            raise SchemaError("Only KNN is supported.")
        source = prepare(payload.get("sourceRows") or [], payload.get("family"),
                         require_labels=True)
        target = prepare(payload.get("targetRows") or [], payload.get("family"))
        outcome = prediction_pipeline.run(
            source=source,
            target=target,
            threshold=float(payload.get("threshold", prediction_pipeline.DEFAULT_THRESHOLD)),
            seed=int(payload.get("seed", prediction_pipeline.DEFAULT_SEED)),
            coral_regularization=float(payload.get("coralRegularization", 1.0)),
            apply_coral=_boolean_value(payload.get("coral"), True),
            k=int(payload.get("k", 3)),
        )
    except SchemaError as exception:
        raise _bad_request(exception) from exception
    except ValueError as exception:
        raise _bad_request(exception) from exception

    result = outcome.to_dict()
    result["targetHasLabels"] = target.labels is not None
    if target.labels is not None:
        by_identifier = dict(zip(target.identifiers, target.labels.tolist()))
        for prediction in result["predictions"]:
            actual = by_identifier.get(prediction["classIdentifier"])
            prediction["actualLabel"] = None if actual is None else int(actual)
    result["sourceRowCount"] = len(source.identifiers)
    result["targetRowCount"] = len(target.identifiers)
    return result


@router.post("/evaluate")
def evaluate_run(payload: dict[str, Any]) -> dict:
    rows = payload.get("results") or []
    if not rows:
        raise HTTPException(status_code=422, detail="No saved prediction rows were supplied.")
    actual: list[int] = []
    predicted: list[int] = []
    scores: list[float] = []
    for row in rows:
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
    except ValueError as exception:
        raise _bad_request(exception) from exception
