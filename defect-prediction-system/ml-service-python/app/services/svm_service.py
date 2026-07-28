from __future__ import annotations

import numpy as np
from sklearn.svm import SVC


class SvmService:
    """Class-weighted linear SVM for binary software-defect prediction."""

    COARSE_C_CANDIDATES = (0.001, 0.01, 0.1, 1.0, 10.0, 100.0)

    def __init__(
        self,
        class_weight: str | dict | None = "balanced",
        random_state: int = 42,
    ) -> None:
        self.class_weight = class_weight
        self.random_state = int(random_state)

    def create_classifier(
        self,
        c_value: float,
        probability: bool = True,
    ) -> SVC:
        if float(c_value) <= 0:
            raise ValueError("SVM C must be greater than zero.")

        return SVC(
            kernel="linear",
            C=float(c_value),
            class_weight=self.class_weight,
            probability=bool(probability),
            random_state=self.random_state,
            cache_size=500,
        )

    def fit(
        self,
        X_source: np.ndarray,
        y_source: np.ndarray,
        c_value: float,
    ) -> SVC:
        X_train, y_train = self._validate_training_data(X_source, y_source)
        classifier = self.create_classifier(c_value, probability=True)
        classifier.fit(X_train, y_train)
        return classifier

    @staticmethod
    def positive_scores(classifier: SVC, X_target: np.ndarray) -> np.ndarray:
        """
        Return the Platt-scaled score for the buggy class (1).

        The score is suitable for ranking and thresholding, although probability
        calibration can still shift between software projects.
        """
        target = np.asarray(X_target, dtype=np.float64)
        if target.ndim != 2:
            raise ValueError("Target features must be two-dimensional.")
        if not np.isfinite(target).all():
            raise ValueError("Target features contain NaN or infinite values.")

        probabilities = classifier.predict_proba(target)
        classes = list(classifier.classes_)
        if 1 not in classes:
            return np.zeros(target.shape[0], dtype=np.float64)

        return probabilities[:, classes.index(1)].astype(np.float64)

    @staticmethod
    def predictions_from_scores(
        scores: np.ndarray,
        threshold: float,
    ) -> np.ndarray:
        if not 0.0 <= float(threshold) <= 1.0:
            raise ValueError("Decision threshold must be between 0 and 1.")

        risk_scores = np.asarray(scores, dtype=np.float64)
        if risk_scores.ndim != 1:
            raise ValueError("Risk scores must be one-dimensional.")
        if not np.isfinite(risk_scores).all():
            raise ValueError("Risk scores contain NaN or infinite values.")

        return (risk_scores >= float(threshold)).astype(int)

    @classmethod
    def coarse_c_candidates(cls) -> list[float]:
        """Return the deterministic logarithmic coarse C grid."""
        return [float(value) for value in cls.COARSE_C_CANDIDATES]

    @staticmethod
    def refined_c_candidates(best_coarse_c: float) -> list[float]:
        """Build a small log-spaced refinement grid around the coarse winner."""
        best = float(best_coarse_c)
        if best <= 0:
            raise ValueError("The coarse C value must be greater than zero.")

        centre = np.log10(best)
        values = np.logspace(centre - 0.5, centre + 0.5, num=7)
        return sorted({float(value) for value in values if float(value) > 0})

    @staticmethod
    def _validate_training_data(
        X_source: np.ndarray,
        y_source: np.ndarray,
    ) -> tuple[np.ndarray, np.ndarray]:
        X_train = np.asarray(X_source, dtype=np.float64)
        y_train = np.asarray(y_source, dtype=int)

        if X_train.ndim != 2:
            raise ValueError("Training features must be two-dimensional.")
        if y_train.ndim != 1:
            raise ValueError("Training labels must be one-dimensional.")
        if len(X_train) != len(y_train):
            raise ValueError(
                "Training features and labels have different row counts."
            )
        if len(X_train) == 0:
            raise ValueError("Training data contains no rows.")
        if not np.isfinite(X_train).all():
            raise ValueError("Training features contain NaN or infinite values.")

        unique_labels = set(np.unique(y_train).tolist())
        if not unique_labels.issubset({0, 1}):
            raise ValueError("Training labels must be binary values 0 and 1.")
        if unique_labels != {0, 1}:
            raise ValueError(
                "Selected source data must contain both clean (0) and buggy "
                "(1) rows."
            )

        return X_train, y_train
