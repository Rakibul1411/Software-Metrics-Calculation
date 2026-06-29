import numpy as np


class CoralService:
    """
    Implements CORrelation ALignment (CORAL) domain adaptation.
    Aligns the second-order statistics (covariances) of the source
    and target feature distributions.
    """

    def align(self, X_source: np.ndarray, X_target: np.ndarray) -> np.ndarray:
        """
        Apply CORAL transformation to align target features to source distribution.
        Reference: Sun et al., "Return of Frustratingly Easy Domain Adaptation" (2016).
        """
        # Compute covariance of source
        Cs = np.cov(X_source, rowvar=False) + np.eye(X_source.shape[1])

        # Compute covariance of target
        Ct = np.cov(X_target, rowvar=False) + np.eye(X_target.shape[1])

        # Whitening: transform target to decorrelated space
        # X_target_whitened = X_target * Ct^{-1/2}
        Ct_inv_sqrt = np.linalg.inv(self._matrix_sqrt(Ct))

        # Re-color: apply source covariance
        Cs_sqrt = self._matrix_sqrt(Cs)

        # Aligned target = X_target * Ct^{-1/2} * Cs^{1/2}
        X_target_aligned = X_target @ Ct_inv_sqrt @ Cs_sqrt

        return X_target_aligned

    def _matrix_sqrt(self, M: np.ndarray) -> np.ndarray:
        """Compute the matrix square root using eigendecomposition."""
        eigenvalues, eigenvectors = np.linalg.eigh(M)
        eigenvalues = np.maximum(eigenvalues, 0)
        return eigenvectors @ np.diag(np.sqrt(eigenvalues)) @ eigenvectors.T
