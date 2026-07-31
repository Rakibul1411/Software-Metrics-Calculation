"""
The DefectLab pipeline.

Required order:
    schema validation
    -> missing/invalid handling
    -> log1p on registered non-negative features
    -> source-fitted StandardScaler
    -> shallow CORAL (source -> target)
    -> manually configured KNN or SVM
    -> probability, label and risk ranking

Target labels are never read during imputation, transformation, CORAL, fitting,
or prediction. They are used only by :mod:`app.defectlab.evaluation`, and only
after the caller has saved the predictions.
"""

from __future__ import annotations

from dataclasses import dataclass

import numpy as np
import pandas as pd
from sklearn.neighbors import KNeighborsClassifier
from sklearn.preprocessing import StandardScaler
from sklearn.svm import SVC

from app.defectlab.preparation import PreparedFrame, SchemaError
from app.services.shallow_coral_service import ShallowCoralService

DEFAULT_THRESHOLD = 0.50
DEFAULT_SEED = 42

@dataclass
class PipelineOutcome:
    family: str
    model_name: str
    features: list[str]
    removed_constant_features: list[str]
    selected_k: int | None
    svm_c: float | None
    k_candidates: list[int]
    k_scores: dict[int, float]
    threshold: float
    seed: int
    coral_applied: bool
    log_applied: bool
    covariance_distance_before: float | None
    covariance_distance_after: float | None
    predictions: list[dict]
    warnings: list[str]

    def to_dict(self) -> dict:
        return {
            "family": self.family,
            "modelName": self.model_name,
            "features": self.features,
            "removedConstantFeatures": self.removed_constant_features,
            "selectedK": self.selected_k,
            "svmC": self.svm_c,
            "kCandidates": self.k_candidates,
            "kScores": {str(k): v for k, v in self.k_scores.items()},
            "threshold": self.threshold,
            "seed": self.seed,
            "coralApplied": self.coral_applied,
            "logApplied": self.log_applied,
            "covarianceDistanceBefore": self.covariance_distance_before,
            "covarianceDistanceAfter": self.covariance_distance_after,
            "predictions": self.predictions,
            "warnings": self.warnings,
        }


def _align_features(source: PreparedFrame, target: PreparedFrame) -> list[str]:
    if source.profile.family != target.profile.family:
        raise SchemaError(
            "Source and target belong to different families "
            f"({source.profile.family} vs {target.profile.family}). "
            "PROMISE and AEEEM cannot be mixed in one run."
        )
    return list(source.profile.features)


def _impute_with_source_median(
    source: pd.DataFrame, target: pd.DataFrame
) -> tuple[pd.DataFrame, pd.DataFrame, list[str]]:
    """Medians come from the source only, so no target statistics leak in."""
    warnings: list[str] = []
    medians = source.median(numeric_only=True)
    medians = medians.fillna(0.0)
    if source.isna().any().any():
        warnings.append("Source missing values were filled with the source median.")
    if target.isna().any().any():
        warnings.append("Target missing values were filled with the source median.")
    return source.fillna(medians), target.fillna(medians), warnings


def _drop_constant_features(
    source: pd.DataFrame, target: pd.DataFrame
) -> tuple[pd.DataFrame, pd.DataFrame, list[str]]:
    """A zero-variance source feature cannot be scaled and is dropped from both."""
    variances = source.var(ddof=0)
    constant = [c for c in source.columns if not np.isfinite(variances[c]) or variances[c] == 0.0]
    if not constant:
        return source, target, []
    return source.drop(columns=constant), target.drop(columns=constant), constant


def run(
    source: PreparedFrame,
    target: PreparedFrame,
    threshold: float = DEFAULT_THRESHOLD,
    seed: int = DEFAULT_SEED,
    coral_regularization: float = 1.0,
    model_name: str = "KNN",
    fixed_k: int | None = None,
    svm_c: float = 1.0,
    svm_kernel: str = "rbf",
    svm_gamma: str = "scale",
) -> PipelineOutcome:
    if source.labels is None:
        raise SchemaError("The source dataset must be labelled.")
    if not 0.0 < threshold < 1.0:
        raise SchemaError("The decision threshold must be between 0 and 1.")
    model_key = (model_name or "KNN").strip().upper()
    if model_key not in {"KNN", "SVM"}:
        raise SchemaError("Model must be KNN or SVM.")

    feature_order = _align_features(source, target)
    source_features = source.features.loc[:, feature_order].astype("float64")
    target_features = target.features.loc[:, feature_order].astype("float64")

    warnings = list(source.warnings) + list(target.warnings)
    source_features, target_features, impute_warnings = _impute_with_source_median(
        source_features, target_features
    )
    warnings.extend(impute_warnings)

    log_columns = [c for c in source_features.columns if c in source.profile.log_features]
    source_features[log_columns] = np.log1p(source_features[log_columns])
    target_features[log_columns] = np.log1p(target_features[log_columns])

    source_features, target_features, removed = _drop_constant_features(
        source_features, target_features
    )
    if removed:
        warnings.append(
            f"{len(removed)} zero-variance source feature(s) were removed: {removed}"
        )
    if source_features.shape[1] == 0:
        raise SchemaError("Every feature had zero variance in the source dataset.")

    # StandardScaler is fitted on the source only and applied to the target.
    scaler = StandardScaler().fit(source_features.to_numpy())
    scaled_source = scaler.transform(source_features.to_numpy())
    scaled_target = scaler.transform(target_features.to_numpy())

    distance_before: float | None = None
    distance_after: float | None = None
    coral = ShallowCoralService(regularization=coral_regularization)
    distance_before = _covariance_distance(scaled_source, scaled_target)
    scaled_source = coral.align(scaled_source, scaled_target)
    distance_after = _covariance_distance(scaled_source, scaled_target)

    labels = np.asarray(source.labels, dtype=int)
    selected_k: int | None = None
    selected_c: float | None = None
    k_scores: dict[int, float] = {}
    usable_candidates: list[int] = []
    predicted_labels: np.ndarray
    if model_key == "KNN":
        selected_k = 3 if fixed_k is None else int(fixed_k)
        if selected_k < 1 or selected_k > 5:
            raise SchemaError("K must be selected manually from 1 to 5.")
        if selected_k > len(labels):
            raise SchemaError(
                f"K={selected_k} is larger than the {len(labels)} source rows."
            )
        usable_candidates = [selected_k]

        model = KNeighborsClassifier(
            n_neighbors=selected_k, weights="uniform", metric="minkowski", p=2
        )
        model.fit(scaled_source, labels)
        scores = _buggy_probability(model, scaled_target)
        predicted_labels = _knn_labels_with_tie_break(
            model, scaled_target, labels, scores, threshold
        )
    else:
        if len(np.unique(labels)) < 2:
            raise SchemaError("SVM requires both clean and buggy rows in the source.")
        kernel = (svm_kernel or "rbf").strip().lower()
        if kernel not in {"linear", "rbf", "poly", "sigmoid"}:
            raise SchemaError("SVM kernel must be linear, RBF, poly or sigmoid.")
        if svm_c <= 0:
            raise SchemaError("SVM C must be greater than 0.")
        selected_c = float(svm_c)
        model = SVC(
            C=selected_c,
            kernel=kernel,
            gamma=svm_gamma or "scale",
            probability=True,
            class_weight="balanced",
            random_state=seed,
        )
        model.fit(scaled_source, labels)
        scores = _buggy_probability(model, scaled_target)
        predicted_labels = (scores >= threshold).astype(int)

    order = np.argsort(-scores, kind="stable")
    predictions: list[dict] = []
    for rank, index in enumerate(order, start=1):
        score = float(scores[index])
        predictions.append(
            {
                "classIdentifier": target.identifiers[index],
                "defectScore": score,
                "defectProbability": score,
                "predictedLabel": int(predicted_labels[index]),
                "riskRank": rank,
                "riskBand": "HIGH" if score >= 0.70 else ("MEDIUM" if score >= 0.40 else "LOW"),
            }
        )

    return PipelineOutcome(
        family=source.profile.family,
        model_name=model_key,
        features=list(source_features.columns),
        removed_constant_features=removed,
        selected_k=selected_k,
        svm_c=selected_c,
        k_candidates=usable_candidates,
        k_scores=k_scores,
        threshold=threshold,
        seed=seed,
        coral_applied=True,
        log_applied=True,
        covariance_distance_before=distance_before,
        covariance_distance_after=distance_after,
        predictions=predictions,
        warnings=warnings,
    )


def _buggy_probability(
    model: KNeighborsClassifier | SVC, features: np.ndarray
) -> np.ndarray:
    """Returns the model's class-1 probability in a consistent shape."""
    probabilities = model.predict_proba(features)
    classes = list(model.classes_)
    if 1 not in classes:
        return np.zeros(features.shape[0], dtype="float64")
    return probabilities[:, classes.index(1)].astype("float64")


def _knn_labels_with_tie_break(
    model: KNeighborsClassifier,
    features: np.ndarray,
    source_labels: np.ndarray,
    scores: np.ndarray,
    threshold: float,
) -> np.ndarray:
    """
    Even K can produce a 50/50 vote. At the default threshold, the closest
    neighbour decides that exact tie; other thresholds use the chosen cutoff.
    """
    predicted = (scores >= threshold).astype(int)
    if not np.isclose(threshold, 0.5):
        return predicted
    ties = np.where(np.isclose(scores, 0.5))[0]
    if ties.size == 0:
        return predicted
    neighbour_indices = model.kneighbors(
        features[ties], n_neighbors=model.n_neighbors, return_distance=False
    )
    for position, target_index in enumerate(ties):
        predicted[target_index] = int(source_labels[neighbour_indices[position][0]])
    return predicted


def _covariance_distance(source: np.ndarray, target: np.ndarray) -> float:
    """Frobenius norm between the two covariance matrices."""
    if source.shape[0] < 2 or target.shape[0] < 2:
        return float("nan")
    source_cov = np.cov(source, rowvar=False)
    target_cov = np.cov(target, rowvar=False)
    return float(np.linalg.norm(source_cov - target_cov, ord="fro"))
