"""
Evaluation for a saved run.

Undefined metrics report ``None`` with a reason rather than a misleading zero.
"""

from __future__ import annotations

import numpy as np
from sklearn.metrics import (
    average_precision_score,
    confusion_matrix,
    matthews_corrcoef,
    roc_auc_score,
)


def _undefined(reason: str) -> dict:
    return {"value": None, "reason": reason}


def _defined(value: float) -> dict:
    return {"value": float(value), "reason": None}


def evaluate(
    actual: list[int],
    predicted: list[int],
    scores: list[float],
    loc_values: list[float] | None = None,
) -> dict:
    truth = np.asarray(actual, dtype=int)
    guess = np.asarray(predicted, dtype=int)
    score = np.asarray(scores, dtype="float64")
    if truth.size == 0:
        raise ValueError("Evaluation needs at least one labelled target row.")
    if not (truth.size == guess.size == score.size):
        raise ValueError("Actual, predicted and score lists must be the same length.")

    matrix = confusion_matrix(truth, guess, labels=[0, 1])
    true_negative, false_positive, false_negative, true_positive = matrix.ravel()
    accuracy = _defined(float((truth == guess).mean()))

    precision = (
        _defined(true_positive / (true_positive + false_positive))
        if (true_positive + false_positive) > 0
        else _undefined("No row was predicted buggy.")
    )
    recall = (
        _defined(true_positive / (true_positive + false_negative))
        if (true_positive + false_negative) > 0
        else _undefined("The target has no buggy row.")
    )
    specificity = (
        _defined(true_negative / (true_negative + false_positive))
        if (true_negative + false_positive) > 0
        else _undefined("The target has no clean row.")
    )
    if precision["value"] is not None and recall["value"] is not None:
        denominator = precision["value"] + recall["value"]
        harmonic = 0.0 if denominator == 0 \
            else 2 * precision["value"] * recall["value"] / denominator
        f1 = _defined(harmonic)
    else:
        f1 = _undefined("F1 needs both precision and recall to be defined.")

    if recall["value"] is not None and specificity["value"] is not None:
        balanced = _defined((recall["value"] + specificity["value"]) / 2)
    else:
        balanced = _undefined("Balanced accuracy needs both classes present.")

    both_classes_present = len(np.unique(truth)) == 2
    mcc = (
        _defined(matthews_corrcoef(truth, guess))
        if both_classes_present
        else _undefined("MCC needs both classes in the target.")
    )
    roc_auc = (
        _defined(roc_auc_score(truth, score))
        if both_classes_present
        else _undefined("ROC-AUC needs both classes in the target.")
    )
    pr_auc = (
        _defined(average_precision_score(truth, score))
        if both_classes_present
        else _undefined("PR-AUC needs both classes in the target.")
    )

    result = {
        "confusionMatrix": {
            "truePositive": int(true_positive),
            "falsePositive": int(false_positive),
            "trueNegative": int(true_negative),
            "falseNegative": int(false_negative),
        },
        "accuracy": accuracy,
        "precision": precision,
        "recall": recall,
        "specificity": specificity,
        "f1": f1,
        "balancedAccuracy": balanced,
        "mcc": mcc,
        "rocAuc": roc_auc,
        "prAuc": pr_auc,
        "labelledRows": int(truth.size),
        "buggyRows": int(truth.sum()),
    }
    result.update(_effort_aware(truth, score, loc_values))
    return result


def _effort_aware(
    truth: np.ndarray, score: np.ndarray, loc_values: list[float] | None
) -> dict:
    """Optional Recall@20% LOC and AUCEC; both need per-class LOC."""
    if not loc_values or len(loc_values) != truth.size:
        reason = "Recall@20% LOC needs a loc value for every target row."
        return {"recallAt20PercentLoc": _undefined(reason), "aucec": _undefined(reason)}

    loc = np.asarray(loc_values, dtype="float64")
    total_loc = float(loc.sum())
    total_bugs = int(truth.sum())
    if total_loc <= 0 or total_bugs == 0:
        reason = "Effort-aware metrics need positive LOC and at least one buggy row."
        return {"recallAt20PercentLoc": _undefined(reason), "aucec": _undefined(reason)}

    order = np.argsort(-score, kind="stable")
    cumulative_loc = np.cumsum(loc[order]) / total_loc
    cumulative_bugs = np.cumsum(truth[order]) / total_bugs

    within_budget = cumulative_loc <= 0.20
    recall_at_20 = float(cumulative_bugs[within_budget][-1]) if within_budget.any() else 0.0
    aucec = float(np.trapezoid(cumulative_bugs, cumulative_loc))
    return {"recallAt20PercentLoc": _defined(recall_at_20), "aucec": _defined(aucec)}
