from __future__ import annotations

import math

import numpy as np
from sklearn.neighbors import KNeighborsClassifier


class KnnService:
    """Distance-weighted Euclidean KNN for binary defect prediction."""

    def __init__(
        self,
        metric: str = "euclidean",
        weights: str = "distance",
    ) -> None:
        self.metric = metric
        self.weights = weights

    def create_classifier(self, k: int) -> KNeighborsClassifier:
        if int(k) < 1:
            raise ValueError("K neighbors must be at least 1.")

        return KNeighborsClassifier(
            n_neighbors=int(k),
            metric=self.metric,
            weights=self.weights,
            algorithm="auto",
            n_jobs=1,
        )

    def fit(
        self,
        X_source: np.ndarray,
        y_source: np.ndarray,
        k: int,
    ) -> KNeighborsClassifier:
        X_train, y_train = self._validate_training_data(X_source, y_source)
        selected_k = int(k)

        if selected_k > len(y_train):
            raise ValueError(
                f"K neighbors ({selected_k}) cannot exceed training rows "
                f"({len(y_train)})."
            )

        classifier = self.create_classifier(selected_k)
        classifier.fit(X_train, y_train)
        return classifier

    def predict(
        self,
        X_source: np.ndarray,
        y_source: np.ndarray,
        X_target: np.ndarray,
        k: int = 5,
        threshold: float = 0.5,
    ) -> np.ndarray:
        """Backward-compatible convenience method."""
        classifier = self.fit(X_source, y_source, k=k)
        scores = self.positive_scores(classifier, X_target)
        return self.predictions_from_scores(scores, threshold)

    @staticmethod
    def positive_scores(
        classifier: KNeighborsClassifier,
        X_target: np.ndarray,
    ) -> np.ndarray:
        """
        Return the distance-weighted vote for the buggy class (1).

        This is a useful continuous bug score, but it is not guaranteed to be a
        calibrated probability.
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

    @staticmethod
    def build_dynamic_k_candidates(
        minimum_training_rows: int,
    ) -> list[int]:
        """
        Build an odd K search range from the smallest validation training set.

        The square-root upper bound is a practical search heuristic, not a
        theoretical optimum. The best K is still selected by validation using
        Average Precision, MCC and recall. No arbitrary fixed cap such as 21 is
        imposed.
        """
        rows = int(minimum_training_rows)
        if rows < 1:
            raise ValueError("Minimum training rows must be at least 1.")

        maximum_k = min(rows, max(1, int(math.floor(math.sqrt(rows)))))
        if maximum_k % 2 == 0:
            maximum_k -= 1
        maximum_k = max(1, maximum_k)

        return list(range(1, maximum_k + 1, 2))

    @staticmethod
    def safe_default_k(training_rows: int) -> int:
        """Return a small valid odd K when validation cannot be performed."""
        rows = int(training_rows)
        if rows < 1:
            raise ValueError("Training rows must be at least 1.")

        selected = min(5, rows)
        if selected % 2 == 0:
            selected -= 1
        return max(1, selected)

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
