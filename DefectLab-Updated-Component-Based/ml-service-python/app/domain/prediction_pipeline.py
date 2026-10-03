from __future__ import annotations

from dataclasses import dataclass
import numpy as np
import pandas as pd
from sklearn.neighbors import KNeighborsClassifier
from sklearn.preprocessing import StandardScaler

from app.domain.dataset_preparation import PreparedFrame, SchemaError
from app.services.shallow_coral_service import ShallowCoralService

DEFAULT_THRESHOLD = 0.50
DEFAULT_SEED = 42


@dataclass
class PipelineOutcome:
    family: str
    model_name: str
    features: list[str]
    selected_k: int
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
            "selectedK": self.selected_k,
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
            f"Metric family mismatch: source is {source.profile.family} but target is {target.profile.family}. "
            "Cross-family prediction is not supported."
        )
    return list(source.profile.features)


def _impute_with_source_median(
    source: pd.DataFrame, target: pd.DataFrame
) -> tuple[pd.DataFrame, pd.DataFrame, list[str]]:
    warnings: list[str] = []
    medians = source.median(numeric_only=True).fillna(0.0)

    if source.isna().any().any():
        warnings.append("Source missing values were filled with the source median.")
    if target.isna().any().any():
        warnings.append("Target missing values were filled with the source median.")

    return source.fillna(medians), target.fillna(medians), warnings


def _drop_constant_features(
    source: pd.DataFrame, target: pd.DataFrame
) -> tuple[pd.DataFrame, pd.DataFrame, list[str]]:
    variances = source.var(ddof=0)
    constant_cols = [c for c in source.columns if not np.isfinite(variances[c]) or variances[c] == 0.0]
    if not constant_cols:
        return source, target, []
    return source.drop(columns=constant_cols), target.drop(columns=constant_cols), constant_cols


def run(
    source: PreparedFrame,
    target: PreparedFrame,
    threshold: float = DEFAULT_THRESHOLD,
    seed: int = DEFAULT_SEED,
    coral_regularization: float = 1.0,
    apply_coral: bool = True,
    k: int = 3,
) -> PipelineOutcome:
    if source.labels is None:
        raise SchemaError("Source dataset must include defect labels for training.")
    if not 0.0 < threshold < 1.0:
        raise SchemaError("Decision threshold must be strictly between 0 and 1.")

    feature_cols = _align_features(source, target)
    source_df = source.features.loc[:, feature_cols].astype("float64")
    target_df = target.features.loc[:, feature_cols].astype("float64")

    warnings = list(source.warnings) + list(target.warnings)

    source_df, target_df, impute_warns = _impute_with_source_median(source_df, target_df)
    warnings.extend(impute_warns)

    source_df, target_df, dropped_cols = _drop_constant_features(source_df, target_df)
    if dropped_cols:
        warnings.append(
            f"{len(dropped_cols)} zero-variance feature(s) removed from training: {dropped_cols}"
        )
    if source_df.shape[1] == 0:
        raise SchemaError("All features have zero variance in the source dataset.")

    # Standardize domains independently before covariance alignment
    scaled_source = StandardScaler().fit_transform(source_df.to_numpy())
    scaled_target = StandardScaler().fit_transform(target_df.to_numpy())

    dist_before = _covariance_distance(scaled_source, scaled_target)
    dist_after = None

    if apply_coral:
        coral = ShallowCoralService(regularization=coral_regularization)
        scaled_source = coral.align(scaled_source, scaled_target)
        dist_after = _covariance_distance(scaled_source, scaled_target)

    labels = np.asarray(source.labels, dtype=int)
    selected_k = int(k)
    if not 1 <= selected_k <= 5:
        raise SchemaError("K must be selected between 1 and 5.")
    if selected_k > len(labels):
        raise SchemaError(f"Selected K={selected_k} exceeds total source rows ({len(labels)}).")

    knn = KNeighborsClassifier(
        n_neighbors=selected_k,
        weights="uniform",
        metric="minkowski",
        p=2,
    )
    knn.fit(scaled_source, labels)

    scores = _buggy_probability(knn, scaled_target)
    pred_labels = _knn_labels_with_tie_break(knn, scaled_target, labels, scores, threshold)

    ranked_indices = np.argsort(-scores, kind="stable")
    predictions: list[dict] = []

    for rank, idx in enumerate(ranked_indices, start=1):
        score_val = float(scores[idx])
        risk = "HIGH" if score_val >= 0.70 else ("MEDIUM" if score_val >= 0.40 else "LOW")
        predictions.append({
            "classIdentifier": target.identifiers[idx],
            "defectScore": score_val,
            "defectProbability": score_val,
            "predictedLabel": int(pred_labels[idx]),
            "riskRank": rank,
            "riskBand": risk,
        })

    return PipelineOutcome(
        family=source.profile.family,
        model_name="KNN",
        features=list(source_df.columns),
        selected_k=selected_k,
        threshold=threshold,
        seed=seed,
        coral_applied=apply_coral,
        log_applied=False,
        covariance_distance_before=dist_before,
        covariance_distance_after=dist_after,
        predictions=predictions,
        warnings=warnings,
    )


def _buggy_probability(model: KNeighborsClassifier, features: np.ndarray) -> np.ndarray:
    probs = model.predict_proba(features)
    classes = list(model.classes_)
    if 1 not in classes:
        return np.zeros(features.shape[0], dtype="float64")
    return probs[:, classes.index(1)].astype("float64")


def _knn_labels_with_tie_break(
    model: KNeighborsClassifier,
    features: np.ndarray,
    source_labels: np.ndarray,
    scores: np.ndarray,
    threshold: float,
) -> np.ndarray:
    predicted = (scores >= threshold).astype(int)
    if not np.isclose(threshold, 0.5):
        return predicted

    ties = np.where(np.isclose(scores, 0.5))[0]
    if ties.size == 0:
        return predicted

    nearest = model.kneighbors(
        features[ties], n_neighbors=model.n_neighbors, return_distance=False
    )
    for i, target_idx in enumerate(ties):
        predicted[target_idx] = int(source_labels[nearest[i][0]])

    return predicted


def _covariance_distance(source: np.ndarray, target: np.ndarray) -> float:
    if source.shape[0] < 2 or target.shape[0] < 2:
        return float("nan")
    cov_s = np.cov(source, rowvar=False)
    cov_t = np.cov(target, rowvar=False)
    return float(np.linalg.norm(cov_s - cov_t, ord="fro"))
