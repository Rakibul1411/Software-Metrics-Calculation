from __future__ import annotations

import numpy as np


class ShallowCoralService:
    """Linear Correlation Alignment (CORAL) domain adaptation.

    Aligns second-order statistics (covariance) of the source domain with the
    target domain via closed-form whitening and re-coloring transformations.
    """

    ALGORITHM_NAME = "shallow/linear CORAL"

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
        source = self._validate_matrix(X_source, "source")
        target = self._validate_matrix(X_target, "target")

        if source.shape[1] != target.shape[1]:
            raise ValueError("Source and target must have the same number of feature columns.")
        if source.shape[0] < 2:
            raise ValueError("CORAL requires at least two source rows.")
        if target.shape[0] < 2:
            raise ValueError("CORAL requires at least two target rows.")

        coral_transform = self.transformation_matrix(source, target)
        aligned_source = source @ coral_transform

        if not np.isfinite(aligned_source).all():
            raise ValueError("CORAL produced NaN or infinite values. Check the input features.")

        return aligned_source

    def transformation_matrix(
        self,
        X_source: np.ndarray,
        X_target: np.ndarray,
    ) -> np.ndarray:
        source = self._validate_matrix(X_source, "source")
        target = self._validate_matrix(X_target, "target")

        if source.shape[1] != target.shape[1]:
            raise ValueError("Source and target must have the same number of feature columns.")
        if source.shape[0] < 2 or target.shape[0] < 2:
            raise ValueError("Shallow CORAL requires at least two source and two target rows.")

        d = source.shape[1]
        eye = np.eye(d, dtype=np.float64)

        cov_s = self._covariance(source) + self.regularization * eye
        cov_t = self._covariance(target) + self.regularization * eye

        inv_sqrt_s = self._symmetric_matrix_power(cov_s, power=-0.5)
        sqrt_t = self._symmetric_matrix_power(cov_t, power=0.5)

        return inv_sqrt_s @ sqrt_t

    @staticmethod
    def _covariance(matrix: np.ndarray) -> np.ndarray:
        cov = np.cov(matrix, rowvar=False, ddof=1)
        cov = np.atleast_2d(np.asarray(cov, dtype=np.float64))
        return 0.5 * (cov + cov.T)

    def _symmetric_matrix_power(self, matrix: np.ndarray, power: float) -> np.ndarray:
        sym = 0.5 * (matrix + matrix.T)
        eigenvalues, eigenvectors = np.linalg.eigh(sym)

        clipped_evals = np.clip(eigenvalues, self.eigenvalue_floor, None)
        powered_evals = np.power(clipped_evals, power)

        res = eigenvectors @ np.diag(powered_evals) @ eigenvectors.T
        return 0.5 * (res + res.T)

    @staticmethod
    def _validate_matrix(matrix_in: np.ndarray, domain_name: str) -> np.ndarray:
        mat = np.asarray(matrix_in, dtype=np.float64)

        if mat.ndim != 2:
            raise ValueError(f"The {domain_name} feature matrix must be two-dimensional.")
        if mat.shape[0] == 0:
            raise ValueError(f"The {domain_name} feature matrix contains no rows.")
        if mat.shape[1] == 0:
            raise ValueError(f"The {domain_name} feature matrix contains no columns.")
        if not np.isfinite(mat).all():
            raise ValueError(f"The {domain_name} feature matrix contains NaN or infinite values.")

        return mat
