from __future__ import annotations

import numpy as np
from sklearn.metrics import (
    fbeta_score,
    matthews_corrcoef,
    precision_score,
    recall_score,
)
from sklearn.model_selection import GridSearchCV, StratifiedKFold, cross_val_predict
from sklearn.svm import SVC


class SvmService:
    """
    Class-weighted linear SVM for binary defect prediction.

    The SVM is supervised: it is trained only with labelled source rows. It may
    then predict an unlabelled target project. Target labels are not required for
    prediction and must not be used for tuning in cross-project evaluation.
    """

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
        if c_value <= 0:
            raise ValueError("SVM C must be greater than zero.")

        return SVC(
            kernel="linear",
            C=float(c_value),
            class_weight=self.class_weight,
            probability=probability,
            random_state=self.random_state,
            cache_size=500,
        )

    def fit(
        self,
        X_source: np.ndarray,
        y_source: np.ndarray,
        c_value: float = 1.0,
    ) -> SVC:
        X_train, y_train = self._validate_training_data(X_source, y_source)
        classifier = self.create_classifier(c_value, probability=True)
        classifier.fit(X_train, y_train)
        return classifier

    @staticmethod
    def positive_scores(classifier: SVC, X_target: np.ndarray) -> np.ndarray:
        """
        Return the estimated probability for the buggy class (1).

        SVC(probability=True) uses Platt-style probability estimation. The value
        is useful as a bug score, but cross-project probabilities may still be
        imperfectly calibrated.
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
        if not 0.0 <= threshold <= 1.0:
            raise ValueError("Decision threshold must be between 0 and 1.")

        risk_scores = np.asarray(scores, dtype=np.float64)
        if not np.isfinite(risk_scores).all():
            raise ValueError("Risk scores contain NaN or infinite values.")
        return (risk_scores >= threshold).astype(int)

    def tune_c_source_cv(
        self,
        X_source: np.ndarray,
        y_source: np.ndarray,
        candidate_c: tuple[float, ...] = (0.001, 0.01, 0.1, 1.0, 10.0, 100.0),
        random_state: int | None = None,
    ) -> float:
        """
        Select C using labelled source data only and Average Precision.

        The target labels are never used. probability=False is sufficient during
        this search because Average Precision can use SVM decision scores.
        """
        X_train, y_train = self._validate_training_data(X_source, y_source)
        cv = self._make_cv(
            y_train,
            self.random_state if random_state is None else int(random_state),
        )
        if cv is None:
            return 1.0

        valid_c = sorted({float(value) for value in candidate_c if float(value) > 0})
        if not valid_c:
            raise ValueError("At least one positive SVM C candidate is required.")

        search = GridSearchCV(
            estimator=self.create_classifier(valid_c[0], probability=False),
            param_grid={"C": valid_c},
            scoring="average_precision",
            cv=cv,
            n_jobs=1,
            refit=True,
            error_score="raise",
        )
        search.fit(X_train, y_train)
        return float(search.best_params_["C"])

    def tune_threshold_source_cv(
        self,
        X_source: np.ndarray,
        y_source: np.ndarray,
        c_value: float,
        beta: float = 2.0,
        random_state: int | None = None,
    ) -> float:
        """
        Tune a probability threshold using source-only out-of-fold predictions.

        F-beta is the main objective. beta=2 gives buggy-class recall more
        importance than precision. No target labels are used.
        """
        if beta <= 0:
            raise ValueError("F-beta beta must be greater than zero.")

        X_train, y_train = self._validate_training_data(X_source, y_source)
        cv = self._make_cv(
            y_train,
            self.random_state if random_state is None else int(random_state),
        )
        if cv is None:
            return 0.5

        classifier = self.create_classifier(c_value, probability=True)
        probabilities = cross_val_predict(
            classifier,
            X_train,
            y_train,
            cv=cv,
            method="predict_proba",
            n_jobs=1,
        )

        source_scores = probabilities[:, 1].astype(np.float64)
        candidate_thresholds = np.unique(
            np.concatenate([
                np.linspace(0.01, 0.99, 199),
                source_scores,
            ])
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
            raise ValueError("Training features and labels have different row counts.")
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
