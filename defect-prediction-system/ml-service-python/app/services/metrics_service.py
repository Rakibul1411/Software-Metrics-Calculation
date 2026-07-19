from __future__ import annotations

import math

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
    """Metrics for binary defect prediction where 1 means buggy."""

    @staticmethod
    def evaluate(
        y_true: np.ndarray,
        risk_scores: np.ndarray,
        threshold: float,
    ) -> dict:
        labels = np.asarray(y_true, dtype=int)
        scores = np.asarray(risk_scores, dtype=np.float64)

        if labels.ndim != 1 or scores.ndim != 1:
            raise ValueError("Labels and risk scores must be one-dimensional.")
        if len(labels) != len(scores):
            raise ValueError("Labels and risk scores have different lengths.")
        if len(labels) == 0:
            raise ValueError("Evaluation data contains no rows.")
        if not 0.0 <= threshold <= 1.0:
            raise ValueError("Decision threshold must be between 0 and 1.")
        if not np.isfinite(scores).all():
            raise ValueError("Risk scores contain NaN or infinite values.")

        unique_labels = set(np.unique(labels).tolist())
        if not unique_labels.issubset({0, 1}):
            raise ValueError("Evaluation labels must be binary values 0 and 1.")

        predictions = (scores >= threshold).astype(int)
        tn, fp, fn, tp = confusion_matrix(
            labels,
            predictions,
            labels=[0, 1],
        ).ravel()

        precision = float(
            precision_score(labels, predictions, zero_division=0)
        )
        recall = float(recall_score(labels, predictions, zero_division=0))
        specificity = (
            float(tn / (tn + fp))
            if (tn + fp) > 0
            else None
        )
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
                        beta=2,
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
