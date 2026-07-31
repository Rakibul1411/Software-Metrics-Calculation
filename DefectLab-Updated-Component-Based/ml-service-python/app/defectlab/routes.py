"""Internal ML endpoints. Not exposed to the browser; Spring Boot calls these."""

from __future__ import annotations

from typing import Any

import numpy as np
import pandas as pd
from fastapi import APIRouter, HTTPException

from app.defectlab import evaluation, pipeline
from app.defectlab.preparation import SchemaError, normalize_frame, prepare, validate
from app.defectlab.registry import detect_profile, find_label_column, profile_for

router = APIRouter()


def _bad_request(exception: Exception) -> HTTPException:
    return HTTPException(status_code=422, detail=str(exception))


@router.get("/health")
def health() -> dict:
    return {"status": "ok", "service": "defectlab-ml"}


@router.post("/schema/validate")
def validate_schema(payload: dict[str, Any]) -> dict:
    try:
        return validate(payload.get("rows") or [], payload.get("family"))
    except SchemaError as exception:
        raise _bad_request(exception) from exception


@router.post("/preprocessing/preview")
def preprocessing_preview(payload: dict[str, Any]) -> dict:
    """Shows the transformation each feature receives, before and after log1p."""
    try:
        frame = prepare(payload.get("rows") or [], payload.get("family"))
    except SchemaError as exception:
        raise _bad_request(exception) from exception

    profile = frame.profile
    features = frame.features
    rows: list[dict] = []
    for column in profile.features:
        series = features[column].dropna()
        transform = profile.transform_of(column)
        transformed = np.log1p(series) if transform == "log1p" else series
        rows.append(
            {
                "name": column,
                "transform": transform,
                "rawMean": None if series.empty else float(series.mean()),
                "rawSkew": None if len(series) < 3 else float(series.skew()),
                "transformedMean": None if transformed.empty else float(transformed.mean()),
                "transformedSkew": None if len(transformed) < 3 else float(transformed.skew()),
            }
        )
    return {
        "family": profile.family,
        "logFeatureCount": len(profile.log_features),
        "scaleOnlyFeatureCount": len(profile.scale_only_features),
        "excludedHistoryColumns": frame.dropped_history_columns,
        "columns": rows,
        "warnings": frame.warnings,
    }


@router.post("/predict")
def predict(payload: dict[str, Any]) -> dict:
    """
    Runs the standard pipeline. The target's labels are never read here; when
    the target carries labels they are returned untouched so the caller can
    store them and evaluate afterwards.
    """
    try:
        source = prepare(payload.get("sourceRows") or [], payload.get("family"),
                         require_labels=True)
        target = prepare(payload.get("targetRows") or [], payload.get("family"))
        outcome = pipeline.run(
            source=source,
            target=target,
            threshold=float(payload.get("threshold", pipeline.DEFAULT_THRESHOLD)),
            seed=int(payload.get("seed", pipeline.DEFAULT_SEED)),
            coral_regularization=float(payload.get("coralRegularization", 1.0)),
            model_name=str(payload.get("modelName", "KNN")),
            fixed_k=int(payload.get("k", 3))
            if str(payload.get("modelName", "KNN")).upper() == "KNN" else None,
            svm_c=float(payload.get("c", 1.0)),
            svm_kernel=str(payload.get("kernel", "rbf")),
            svm_gamma=str(payload.get("gamma", "scale")).lower(),
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


@router.post("/compare")
def compare(payload: dict[str, Any]) -> dict:
    """Schema, class-overlap and per-feature distribution comparison."""
    try:
        left = normalize_frame(payload.get("rowsA") or [])
        right = normalize_frame(payload.get("rowsB") or [])
    except SchemaError as exception:
        raise _bad_request(exception) from exception

    left_profile = detect_profile(left.columns)
    right_profile = detect_profile(right.columns)
    if left_profile is None or right_profile is None:
        raise HTTPException(
            status_code=422,
            detail="Both datasets must match the PROMISE or AEEEM profile.",
        )

    same_family = left_profile.family == right_profile.family
    left_classes = set(left["name"].astype(str)) if "name" in left.columns else set()
    right_classes = set(right["name"].astype(str)) if "name" in right.columns else set()

    feature_rows: list[dict] = []
    if same_family:
        for column in left_profile.features:
            left_series = pd.to_numeric(left[column], errors="coerce").dropna()
            right_series = pd.to_numeric(right[column], errors="coerce").dropna()
            feature_rows.append(
                {
                    "name": column,
                    "meanA": None if left_series.empty else float(left_series.mean()),
                    "meanB": None if right_series.empty else float(right_series.mean()),
                    "stdA": None if len(left_series) < 2 else float(left_series.std(ddof=0)),
                    "stdB": None if len(right_series) < 2 else float(right_series.std(ddof=0)),
                }
            )

    return {
        "familyA": left_profile.family,
        "familyB": right_profile.family,
        "sameFamily": same_family,
        "comparable": same_family,
        "rowCountA": int(len(left)),
        "rowCountB": int(len(right)),
        "labelColumnA": find_label_column(left.columns, left_profile),
        "labelColumnB": find_label_column(right.columns, right_profile),
        "sharedClasses": len(left_classes & right_classes),
        "onlyInA": len(left_classes - right_classes),
        "onlyInB": len(right_classes - left_classes),
        "features": feature_rows,
    }


@router.get("/registry/{family}")
def registry(family: str) -> dict:
    """Feature registry for the Preprocessing page."""
    try:
        profile = profile_for(family)
    except ValueError as exception:
        raise _bad_request(exception) from exception
    return {
        "family": profile.family,
        "identifier": "name",
        "labelColumn": profile.label_column,
        "featureCount": len(profile.features),
        "columns": [
            {
                "canonicalName": column,
                "role": "feature",
                "minimum": 0 if column in profile.log_features else None,
                "maximum": 1 if column in profile.unit_range_features else None,
                "transform": profile.transform_of(column),
            }
            for column in profile.features
        ],
    }
