from __future__ import annotations

import math

import numpy as np
from sklearn.metrics import (
    fbeta_score,
    matthews_corrcoef,
    precision_score,
    recall_score,
)
from sklearn.model_selection import GridSearchCV, StratifiedKFold, cross_val_predict
from sklearn.neighbors import KNeighborsClassifier


class KnnService:
    """Distance-weighted KNN classifier for binary defect prediction."""

    def __init__(
        self,
        metric: str = "euclidean",
        weights: str = "distance",
    ) -> None:
        self.metric = metric
        self.weights = weights

    def create_classifier(self, k: int) -> KNeighborsClassifier:
        if k < 1:
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
        k: int = 5,
    ) -> KNeighborsClassifier:
        X_train, y_train = self._validate_training_data(X_source, y_source)

        if k > len(y_train):
            raise ValueError(
                f"K neighbors ({k}) cannot exceed training rows ({len(y_train)})."
            )

        classifier = self.create_classifier(k)
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
        Return the distance-weighted vote assigned to the buggy class (1).

        This is an estimated bug score from KNN voting, not a guaranteed
        calibrated probability.
        """
        target = np.asarray(X_target, dtype=np.float64)
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
        if not 0.0 <= threshold <= 1.0:
            raise ValueError("Decision threshold must be between 0 and 1.")

        risk_scores = np.asarray(scores, dtype=np.float64)
        if not np.isfinite(risk_scores).all():
            raise ValueError("Risk scores contain NaN or infinite values.")

        return (risk_scores >= threshold).astype(int)

    def tune_k_source_cv(
        self,
        X_source: np.ndarray,
        y_source: np.ndarray,
        candidate_k: tuple[int, ...] = (3, 5, 7, 9, 11, 15),
        random_state: int = 42,
    ) -> int:
        """
        Select K using source labels only and Average Precision scoring.

        Accuracy is intentionally not used because defect datasets are commonly
        imbalanced.
        """
        X_train, y_train = self._validate_training_data(X_source, y_source)
        cv = self._make_cv(y_train, random_state)

        if cv is None:
            return min(5, len(y_train))

        largest_validation_fold = math.ceil(len(y_train) / cv.n_splits)
        minimum_training_rows = len(y_train) - largest_validation_fold

        valid_k = sorted(
            {
                int(k)
                for k in candidate_k
                if 1 <= int(k) <= minimum_training_rows
            }
        )
        if not valid_k:
            return 1

        search = GridSearchCV(
            estimator=self.create_classifier(valid_k[0]),
            param_grid={"n_neighbors": valid_k},
            scoring="average_precision",
            cv=cv,
            n_jobs=1,
            refit=True,
            error_score="raise",
        )
        search.fit(X_train, y_train)
        return int(search.best_params_["n_neighbors"])

    def tune_threshold_source_cv(
        self,
        X_source: np.ndarray,
        y_source: np.ndarray,
        k: int,
        beta: float = 2.0,
        random_state: int = 42,
    ) -> float:
        """
        Tune the hard-label threshold using source-only out-of-fold scores.

        F-beta is the primary objective. With beta=2, recall of buggy classes is
        weighted more heavily than precision. Target labels are never used.
        """
        if beta <= 0:
            raise ValueError("F-beta beta must be greater than zero.")

        X_train, y_train = self._validate_training_data(X_source, y_source)
        cv = self._make_cv(y_train, random_state)

        if cv is None:
            return 0.5

        largest_validation_fold = math.ceil(len(y_train) / cv.n_splits)
        minimum_training_rows = len(y_train) - largest_validation_fold
        if k > minimum_training_rows:
            return 0.5

        classifier = self.create_classifier(k)
        probabilities = cross_val_predict(
            classifier,
            X_train,
            y_train,
            cv=cv,
            method="predict_proba",
            n_jobs=1,
        )

        # Binary labels are validated to be 0 and 1, so column 1 is buggy.
        source_scores = probabilities[:, 1].astype(np.float64)
        candidate_thresholds = np.unique(
            np.concatenate(
                [
                    np.linspace(0.01, 0.99, 199),
                    source_scores,
                ]
            )
        )

        best_threshold = 0.5
        best_key = (-1.0, -2.0, -1.0, -1.0)

        for threshold in candidate_thresholds:
            predictions = (source_scores >= threshold).astype(int)
            f_beta = fbeta_score(
                y_train,
                predictions,
                beta=beta,
                zero_division=0,
            )
            mcc = matthews_corrcoef(y_train, predictions)
            recall = recall_score(y_train, predictions, zero_division=0)
            precision = precision_score(y_train, predictions, zero_division=0)

            # Maximize F-beta first; use MCC, recall, and precision as tie-breakers.
            key = (
                float(f_beta),
                float(mcc),
                float(recall),
                float(precision),
            )
            if key > best_key:
                best_key = key
                best_threshold = float(threshold)

        return best_threshold

    @staticmethod
    def _make_cv(
        y_source: np.ndarray,
        random_state: int,
    ) -> StratifiedKFold | None:
        classes, counts = np.unique(y_source, return_counts=True)

        if len(classes) != 2:
            return None

        minority_count = int(counts.min())
        if minority_count < 2:
            return None

        return StratifiedKFold(
            n_splits=min(5, minority_count),
            shuffle=True,
            random_state=random_state,
        )

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
                "Selected source data must contain both clean (0) and buggy (1) rows."
            )

        return X_train, y_train
