import unittest

import numpy as np

from app.services.shallow_coral_service import ShallowCoralService


class ShallowCoralServiceTest(unittest.TestCase):
    def setUp(self):
        self.service = ShallowCoralService(
            regularization=1.0,
            eigenvalue_floor=1e-12,
        )

    def test_closed_form_whitening_and_recoloring_matches_algorithm_one(self):
        source = np.array([
            [-2.0, -1.0],
            [-1.0, 0.0],
            [1.0, 0.0],
            [2.0, 1.0],
        ])
        target = np.array([
            [-3.0, 2.0],
            [-1.0, 1.0],
            [1.0, -1.0],
            [3.0, -2.0],
        ])

        source_covariance = np.cov(source, rowvar=False, ddof=1) + np.eye(2)
        target_covariance = np.cov(target, rowvar=False, ddof=1) + np.eye(2)
        expected_transform = (
            self._symmetric_power(source_covariance, -0.5)
            @ self._symmetric_power(target_covariance, 0.5)
        )

        actual_transform = self.service.transformation_matrix(source, target)
        aligned = self.service.align(source, target)

        np.testing.assert_allclose(actual_transform, expected_transform, atol=1e-12)
        np.testing.assert_allclose(aligned, source @ expected_transform, atol=1e-12)

    def test_service_is_explicitly_shallow_not_deep_coral(self):
        self.assertEqual("shallow/linear CORAL", self.service.ALGORITHM_NAME)
        self.assertFalse(hasattr(self.service, "fit"))
        self.assertFalse(hasattr(self.service, "loss"))

    def test_requires_two_rows_per_domain(self):
        with self.assertRaisesRegex(ValueError, "at least two"):
            self.service.align(
                np.array([[1.0, 2.0]]),
                np.array([[1.0, 2.0], [2.0, 3.0]]),
            )

    @staticmethod
    def _symmetric_power(matrix: np.ndarray, power: float) -> np.ndarray:
        eigenvalues, eigenvectors = np.linalg.eigh(matrix)
        return eigenvectors @ np.diag(np.power(eigenvalues, power)) @ eigenvectors.T


if __name__ == "__main__":
    unittest.main()
