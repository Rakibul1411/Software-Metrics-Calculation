from __future__ import annotations

import numpy as np


class CoralService:
    """
    Shallow/linear CORrelation ALignment (CORAL).

    This is the closed-form feature transformation from the original CORAL
    method, not Deep CORAL. The method aligns source-domain covariance with
    target-domain covariance without using target labels.

    Formula:
        Cs = cov(Xs) + lambda * I
        Ct = cov(Xt) + lambda * I
        A = Cs^(-1/2) @ Ct^(1/2)
        Xs_aligned = Xs @ A

    The caller should center/standardize source and target domains before
    calling ``align``.
    """

    def __init__(
        self,
        regularization: float = 1.0,
        eigenvalue_floor: float = 1e-12,
    ) -> None:
        if regularization <= 0:
            raise ValueError("CORAL regularization must be greater than zero.")
        if eigenvalue_floor <= 0:
            raise ValueError("CORAL eigenvalue floor must be greater than zero.")

        self.regularization = float(regularization)
        self.eigenvalue_floor = float(eigenvalue_floor)

    def align(self, X_source: np.ndarray, X_target: np.ndarray) -> np.ndarray:
        """Align source features to the target covariance structure."""
        source = self._validate_matrix(X_source, "source")
        target = self._validate_matrix(X_target, "target")

        if source.shape[1] != target.shape[1]:
            raise ValueError(
                "Source and target must have the same number of feature columns."
            )
        if source.shape[0] < 2:
            raise ValueError("CORAL requires at least two source rows.")
        if target.shape[0] < 2:
            raise ValueError("CORAL requires at least two target rows.")

        feature_count = source.shape[1]
        identity = np.eye(feature_count, dtype=np.float64)

        covariance_source = self._covariance(source) + self.regularization * identity
        covariance_target = self._covariance(target) + self.regularization * identity

        source_inverse_sqrt = self._symmetric_matrix_power(
            covariance_source,
            power=-0.5,
        )
        target_sqrt = self._symmetric_matrix_power(
            covariance_target,
            power=0.5,
        )

        coral_transform = source_inverse_sqrt @ target_sqrt
        aligned_source = source @ coral_transform

        if not np.isfinite(aligned_source).all():
            raise ValueError(
                "CORAL produced NaN or infinite values. Check the input features."
            )

        return aligned_source

    @staticmethod
    def _covariance(values: np.ndarray) -> np.ndarray:
        covariance = np.cov(values, rowvar=False, ddof=1)
        covariance = np.atleast_2d(np.asarray(covariance, dtype=np.float64))
        return 0.5 * (covariance + covariance.T)

    def _symmetric_matrix_power(
        self,
        matrix: np.ndarray,
        power: float,
    ) -> np.ndarray:
        """Compute a stable real power of a symmetric covariance matrix."""
        symmetric_matrix = 0.5 * (matrix + matrix.T)
        eigenvalues, eigenvectors = np.linalg.eigh(symmetric_matrix)

        eigenvalues = np.clip(
            eigenvalues,
            self.eigenvalue_floor,
            None,
        )
        powered_eigenvalues = np.power(eigenvalues, power)

        result = (
            eigenvectors
            @ np.diag(powered_eigenvalues)
            @ eigenvectors.T
        )
        return 0.5 * (result + result.T)

    @staticmethod
    def _validate_matrix(values: np.ndarray, domain_name: str) -> np.ndarray:
        matrix = np.asarray(values, dtype=np.float64)

        if matrix.ndim != 2:
            raise ValueError(
                f"The {domain_name} feature matrix must be two-dimensional."
            )
        if matrix.shape[0] == 0:
            raise ValueError(f"The {domain_name} feature matrix contains no rows.")
        if matrix.shape[1] == 0:
            raise ValueError(f"The {domain_name} feature matrix contains no columns.")
        if not np.isfinite(matrix).all():
            raise ValueError(
                f"The {domain_name} feature matrix contains NaN or infinite values."
            )

        return matrix
