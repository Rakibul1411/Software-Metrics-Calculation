from __future__ import annotations

import logging
from typing import Any
import numpy as np
from sklearn.manifold import TSNE
from sklearn.preprocessing import StandardScaler

from app.domain.dataset_preparation import PreparedFrame
from app.domain.prediction_pipeline import (
    _align_features,
    _covariance_distance,
    _drop_constant_features,
    _impute_with_source_median,
)
from app.services.shallow_coral_service import ShallowCoralService

logger = logging.getLogger("defectlab.ml.coral_tsne")


def compute_coral_tsne(
    source: PreparedFrame,
    target: PreparedFrame,
    coral_regularization: float = 1.0,
    seed: int = 42,
    max_samples_per_domain: int = 180,
) -> dict[str, Any]:
    """Computes before and after 2D t-SNE projections illustrating CORAL domain alignment."""
    feature_cols = _align_features(source, target)
    source_df = source.features.loc[:, feature_cols].astype("float64")
    target_df = target.features.loc[:, feature_cols].astype("float64")

    source_df, target_df, _ = _impute_with_source_median(source_df, target_df)
    source_df, target_df, _ = _drop_constant_features(source_df, target_df)

    if source_df.shape[1] == 0:
        raise ValueError("No valid features remaining for t-SNE projection.")

    scaled_source = StandardScaler().fit_transform(source_df.to_numpy())
    scaled_target = StandardScaler().fit_transform(target_df.to_numpy())

    dist_before = _covariance_distance(scaled_source, scaled_target)

    coral = ShallowCoralService(regularization=coral_regularization)
    aligned_source = coral.align(scaled_source, scaled_target)
    dist_after = _covariance_distance(aligned_source, scaled_target)

    if np.isfinite(dist_before) and dist_before > 0 and np.isfinite(dist_after):
        reduction_pct = max(0.0, float((dist_before - dist_after) / dist_before * 100.0))
    else:
        reduction_pct = 0.0

    n_source_total = len(source.identifiers)
    n_target_total = len(target.identifiers)

    rng = np.random.default_rng(seed)
    if n_source_total > max_samples_per_domain:
        source_idx = rng.choice(n_source_total, size=max_samples_per_domain, replace=False)
        source_idx.sort()
    else:
        source_idx = np.arange(n_source_total)

    if n_target_total > max_samples_per_domain:
        target_idx = rng.choice(n_target_total, size=max_samples_per_domain, replace=False)
        target_idx.sort()
    else:
        target_idx = np.arange(n_target_total)

    s_sub_before = scaled_source[source_idx]
    s_sub_after = aligned_source[source_idx]
    t_sub = scaled_target[target_idx]

    s_ids = [source.identifiers[i] for i in source_idx]
    t_ids = [target.identifiers[i] for i in target_idx]

    s_labels = [int(source.labels[i]) if source.labels is not None else None for i in source_idx]
    t_labels = [int(target.labels[i]) if target.labels is not None else None for i in target_idx]

    n_s = len(source_idx)
    n_t = len(target_idx)
    total_pts = n_s + n_t
    perplexity = min(30, max(5, (total_pts - 1) // 4))

    # 1. Before t-SNE: combine source_before + target (PCA init provides fast global convergence)
    X_before = np.vstack([s_sub_before, t_sub])
    tsne_before = TSNE(
        n_components=2,
        perplexity=perplexity,
        random_state=seed,
        init="pca",
        learning_rate="auto",
        max_iter=250,
        n_jobs=1,
    )
    coords_before = tsne_before.fit_transform(X_before)

    # 2. After t-SNE: combine source_after + target
    X_after = np.vstack([s_sub_after, t_sub])
    tsne_after = TSNE(
        n_components=2,
        perplexity=perplexity,
        random_state=seed,
        init="pca",
        learning_rate="auto",
        max_iter=250,
        n_jobs=1,
    )
    coords_after = tsne_after.fit_transform(X_after)

    # Align coords_after with coords_before on the shared Target frame via orthogonal Procrustes registration.
    # This ensures Target points stay in the exact same orientation without artificial rotation/reflection.
    t_b = coords_before[n_s:]
    t_a = coords_after[n_s:]
    m_b = np.mean(t_b, axis=0)
    m_a = np.mean(t_a, axis=0)
    h = (t_a - m_a).T @ (t_b - m_b)
    u, _, vt = np.linalg.svd(h)
    r = u @ vt
    coords_after = (coords_after - m_a) @ r + m_b

    points_before: list[dict[str, Any]] = []
    for i in range(n_s):
        points_before.append({
            "id": s_ids[i],
            "x": round(float(coords_before[i, 0]), 4),
            "y": round(float(coords_before[i, 1]), 4),
            "domain": "source",
            "label": s_labels[i],
        })
    for j in range(n_t):
        points_before.append({
            "id": t_ids[j],
            "x": round(float(coords_before[n_s + j, 0]), 4),
            "y": round(float(coords_before[n_s + j, 1]), 4),
            "domain": "target",
            "label": t_labels[j],
        })

    points_after: list[dict[str, Any]] = []
    for i in range(n_s):
        points_after.append({
            "id": s_ids[i],
            "x": round(float(coords_after[i, 0]), 4),
            "y": round(float(coords_after[i, 1]), 4),
            "domain": "source_aligned",
            "label": s_labels[i],
        })
    for j in range(n_t):
        points_after.append({
            "id": t_ids[j],
            "x": round(float(coords_after[n_s + j, 0]), 4),
            "y": round(float(coords_after[n_s + j, 1]), 4),
            "domain": "target",
            "label": t_labels[j],
        })

    def _stats(arr: np.ndarray) -> dict[str, float]:
        mean = np.mean(arr, axis=0)
        std = np.std(arr, axis=0)
        return {
            "centerX": round(float(mean[0]), 4),
            "centerY": round(float(mean[1]), 4),
            "radiusX": round(float(std[0]), 4),
            "radiusY": round(float(std[1]), 4),
        }

    return {
        "covarianceDistanceBefore": round(float(dist_before), 4) if np.isfinite(dist_before) else None,
        "covarianceDistanceAfter": round(float(dist_after), 4) if np.isfinite(dist_after) else None,
        "reductionPercent": round(reduction_pct, 1),
        "sourceSampleCount": n_source_total,
        "targetSampleCount": n_target_total,
        "featuresAlignedCount": len(feature_cols),
        "coralRegularization": coral_regularization,
        "before": points_before,
        "after": points_after,
        "stats": {
            "beforeSource": _stats(coords_before[:n_s]),
            "beforeTarget": _stats(coords_before[n_s:]),
            "afterSource": _stats(coords_after[:n_s]),
            "afterTarget": _stats(coords_after[n_s:]),
        },
    }
