from __future__ import annotations

import math
from typing import Iterable

import numpy as np
from sklearn.metrics import (
    accuracy_score,
    auc,
    average_precision_score,
    balanced_accuracy_score,
    confusion_matrix,
    f1_score,
    fbeta_score,
    matthews_corrcoef,
    precision_recall_curve,
    precision_score,
    recall_score,
    roc_auc_score,
)


class MetricsService:
    """Metrics and threshold selection for binary defect prediction."""

    @staticmethod
    def evaluate(
        y_true: np.ndarray,
        risk_scores: np.ndarray,
        threshold: float,
        beta: float = 2.0,
    ) -> dict:
        labels, scores = MetricsService._validate_inputs(y_true, risk_scores)
        if not 0.0 <= float(threshold) <= 1.0:
            raise ValueError("Decision threshold must be between 0 and 1.")
        if float(beta) <= 0:
            raise ValueError("F-beta beta must be greater than zero.")

        predictions = (scores >= float(threshold)).astype(int)
        tn, fp, fn, tp = confusion_matrix(
            labels,
            predictions,
            labels=[0, 1],
        ).ravel()

        unique_labels = np.unique(labels)
        precision = float(
            precision_score(labels, predictions, zero_division=0)
        )
        recall = float(recall_score(labels, predictions, zero_division=0))
        specificity = float(tn / (tn + fp)) if (tn + fp) > 0 else None
        balanced_accuracy = (
            float(balanced_accuracy_score(labels, predictions))
            if len(unique_labels) == 2
            else None
        )
        g_mean = (
            float(math.sqrt(recall * specificity))
            if specificity is not None
            else None
        )

        roc_auc = None
        average_precision = None
        pr_auc = None
        if len(unique_labels) == 2:
            roc_auc = float(roc_auc_score(labels, scores))
            average_precision = float(average_precision_score(labels, scores))
            curve_precision, curve_recall, _ = precision_recall_curve(
                labels,
                scores,
            )
            pr_auc = float(auc(curve_recall, curve_precision))

        prevalence = float(np.mean(labels == 1))
        return {
            "predictions": predictions,
            "metrics": {
                "threshold": float(threshold),
                "accuracy": float(accuracy_score(labels, predictions)),
                "balancedAccuracy": balanced_accuracy,
                "precision": precision,
                "recall": recall,
                "specificity": specificity,
                "f1": float(f1_score(labels, predictions, zero_division=0)),
                "f2": float(
                    fbeta_score(
                        labels,
                        predictions,
                        beta=float(beta),
                        zero_division=0,
                    )
                ),
                "mcc": float(matthews_corrcoef(labels, predictions)),
                "gMean": g_mean,
                "rocAuc": roc_auc,
                "prAuc": pr_auc,
                "averagePrecision": average_precision,
                "positivePrevalence": prevalence,
                "prAucNoSkillBaseline": prevalence,
            },
            "confusionMatrix": {
                "truePositive": int(tp),
                "trueNegative": int(tn),
                "falsePositive": int(fp),
                "falseNegative": int(fn),
            },
        }

    @staticmethod
    def validation_metrics(
        y_true: np.ndarray,
        scores: np.ndarray,
        threshold: float = 0.5,
        beta: float = 2.0,
    ) -> dict:
        """Return metrics used for K/C and threshold selection."""
        labels, continuous_scores = MetricsService._validate_inputs(y_true, scores)
        if not 0.0 <= float(threshold) <= 1.0:
            raise ValueError("Decision threshold must be between 0 and 1.")

        predictions = (continuous_scores >= float(threshold)).astype(int)
        has_both_classes = len(np.unique(labels)) == 2
        average_precision = (
            float(average_precision_score(labels, continuous_scores))
            if has_both_classes
            else None
        )

        tn, fp, fn, tp = confusion_matrix(
            labels,
            predictions,
            labels=[0, 1],
        ).ravel()
        recall = float(recall_score(labels, predictions, zero_division=0))
        specificity = float(tn / (tn + fp)) if (tn + fp) > 0 else None
        balanced_accuracy = (
            float(balanced_accuracy_score(labels, predictions))
            if has_both_classes
            else None
        )

        return {
            "averagePrecision": average_precision,
            "mcc": float(matthews_corrcoef(labels, predictions)),
            "balancedAccuracy": balanced_accuracy,
            "specificity": specificity,
            "f1": float(f1_score(labels, predictions, zero_division=0)),
            "f2": float(
                fbeta_score(
                    labels,
                    predictions,
                    beta=float(beta),
                    zero_division=0,
                )
            ),
            "recall": recall,
            "precision": float(
                precision_score(labels, predictions, zero_division=0)
            ),
        }

    def select_threshold(
        self,
        fold_outputs: list[dict],
        beta: float = 2.0,
    ) -> tuple[float, list[dict]]:
        """
        Select a threshold by macro MCC across source projects/folds.

        MCC and balanced accuracy penalize false alarms as well as missed bugs.
        Tie-breaking order: balanced accuracy, F1, precision, then the threshold
        closest to 0.5.
        """
        if not fold_outputs:
            return 0.5, []
        if float(beta) <= 0:
            raise ValueError("F-beta beta must be greater than zero.")

        candidate_results: list[dict] = []
        best_threshold = 0.5
        best_key = (-math.inf, -math.inf, -math.inf, -math.inf, -math.inf)

        for threshold in np.round(np.arange(0.10, 0.901, 0.05), 2):
            fold_metrics = [
                self.validation_metrics(
                    fold["labels"],
                    fold["scores"],
                    threshold=float(threshold),
                    beta=float(beta),
                )
                for fold in fold_outputs
            ]
            macro_f2 = self.mean_metric(fold_metrics, "f2")
            macro_f1 = self.mean_metric(fold_metrics, "f1")
            macro_recall = self.mean_metric(fold_metrics, "recall")
            macro_precision = self.mean_metric(fold_metrics, "precision")
            macro_specificity = self.mean_metric(fold_metrics, "specificity")
            macro_balanced_accuracy = self.mean_metric(
                fold_metrics, "balancedAccuracy"
            )
            macro_mcc = self.mean_metric(fold_metrics, "mcc")
            result = {
                "threshold": float(threshold),
                "macroMcc": macro_mcc,
                "macroBalancedAccuracy": macro_balanced_accuracy,
                "macroF1": macro_f1,
                "macroF2": macro_f2,
                "macroPrecision": macro_precision,
                "macroRecall": macro_recall,
                "macroSpecificity": macro_specificity,
            }
            candidate_results.append(result)

            key = (
                macro_mcc,
                macro_balanced_accuracy,
                macro_f1,
                macro_precision,
                -abs(float(threshold) - 0.5),
            )
            if key > best_key:
                best_key = key
                best_threshold = float(threshold)

        return best_threshold, candidate_results

    @staticmethod
    def aggregate_metric(values: Iterable[float | None]) -> dict:
        numeric_values = [float(value) for value in values if value is not None]
        if not numeric_values:
            return {"mean": None, "std": None, "validFoldCount": 0}

        return {
            "mean": float(np.mean(numeric_values)),
            "std": float(np.std(numeric_values, ddof=0)),
            "validFoldCount": len(numeric_values),
        }

    @staticmethod
    def mean_metric(items: list[dict], key: str) -> float:
        values = [item.get(key) for item in items if item.get(key) is not None]
        if not values:
            return -math.inf
        return float(np.mean(values))

    @staticmethod
    def _validate_inputs(
        y_true: np.ndarray,
        risk_scores: np.ndarray,
    ) -> tuple[np.ndarray, np.ndarray]:
        labels = np.asarray(y_true, dtype=int)
        scores = np.asarray(risk_scores, dtype=np.float64)

        if labels.ndim != 1 or scores.ndim != 1:
            raise ValueError("Labels and risk scores must be one-dimensional.")
        if len(labels) != len(scores):
            raise ValueError("Labels and risk scores have different lengths.")
        if len(labels) == 0:
            raise ValueError("Evaluation data contains no rows.")
        if not np.isfinite(scores).all():
            raise ValueError("Risk scores contain NaN or infinite values.")

        unique_labels = set(np.unique(labels).tolist())
        if not unique_labels.issubset({0, 1}):
            raise ValueError("Evaluation labels must be binary values 0 and 1.")

        return labels, scores
