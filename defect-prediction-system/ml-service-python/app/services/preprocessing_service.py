from __future__ import annotations

import numpy as np
from sklearn.preprocessing import StandardScaler


class PreprocessingService:
    """Feature preprocessing used before shallow CORAL and classification."""

    @staticmethod
    def standardize_domain(X: np.ndarray) -> np.ndarray:
        """
        Standardize one domain with that domain's own feature statistics.

        In unsupervised domain adaptation, target feature values may be used for
        centering/scaling and covariance estimation, but target labels must not
        be used during training or model selection.
        """
        values = np.asarray(X, dtype=np.float64)

        if values.ndim != 2:
            raise ValueError("The feature matrix must be two-dimensional.")
        if values.shape[0] == 0:
            raise ValueError("The feature matrix contains no rows.")
        if values.shape[1] == 0:
            raise ValueError("The feature matrix contains no columns.")
        if not np.isfinite(values).all():
            raise ValueError("The feature matrix contains NaN or infinite values.")

        scaler = StandardScaler()
        transformed = scaler.fit_transform(values)

        if not np.isfinite(transformed).all():
            raise ValueError("Standardization produced NaN or infinite values.")

        return transformed.astype(np.float64, copy=False)

    def standardize_pair(
        self,
        X_source: np.ndarray,
        X_target: np.ndarray,
    ) -> tuple[np.ndarray, np.ndarray]:
        """Standardize source and target independently."""
        return (
            self.standardize_domain(X_source),
            self.standardize_domain(X_target),
        )

    def normalize(
        self,
        X_source: np.ndarray,
        X_target: np.ndarray,
    ) -> tuple[np.ndarray, np.ndarray]:
        """Backward-compatible alias for independent domain standardization."""
        return self.standardize_pair(X_source, X_target)
