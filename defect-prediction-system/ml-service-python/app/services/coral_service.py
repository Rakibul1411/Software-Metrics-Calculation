import numpy as np


class CoralService:
    """
    Implements CORrelation ALignment (CORAL) domain adaptation.
    Aligns the second-order statistics (covariances) of the source
    and target feature distributions.
    """

    def align(self, X_source: np.ndarray, X_target: np.ndarray) -> np.ndarray:
        """
        Map source features to the target covariance, matching CORAL_map.m:

            cov_src = cov(Xs) + I
            cov_tar = cov(Xt) + I
            A_coral = cov_src^(-1/2) * cov_tar^(1/2)
            Xs_new = Xs * A_coral

        Reference: Sun et al., "Return of Frustratingly Easy Domain Adaptation" (2016).
        """
        covariance_source = np.cov(X_source, rowvar=False) + np.eye(X_source.shape[1])
        covariance_target = np.cov(X_target, rowvar=False) + np.eye(X_target.shape[1])

        source_inverse_sqrt = self._matrix_power(covariance_source, -0.5)
        target_sqrt = self._matrix_power(covariance_target, 0.5)
        coral_transform = source_inverse_sqrt @ target_sqrt

        return X_source @ coral_transform

    def _matrix_power(self, matrix: np.ndarray, power: float) -> np.ndarray:
        """Equivalent to MATLAB's symmetric matrix power for covariance matrices."""
        eigenvalues, eigenvectors = np.linalg.eigh(matrix)
        powered_eigenvalues = np.power(eigenvalues, power)
        return eigenvectors @ np.diag(powered_eigenvalues) @ eigenvectors.T
