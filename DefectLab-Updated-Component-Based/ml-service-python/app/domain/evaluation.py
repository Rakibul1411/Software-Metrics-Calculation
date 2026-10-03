from __future__ import annotations

import numpy as np
from sklearn.metrics import (
    average_precision_score,
    confusion_matrix,
    matthews_corrcoef,
    roc_auc_score,
)


def _metric(val: float | None, reason: str | None = None) -> dict[str, float | str | None]:
    return {"value": None if val is None else float(val), "reason": reason}


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
        raise ValueError("Evaluation requires at least one labeled target instance.")
    if not (truth.size == guess.size == score.size):
        raise ValueError("Mismatched lengths across actual, predicted, and defect score arrays.")

    matrix = confusion_matrix(truth, guess, labels=[0, 1])
    tn, fp, fn, tp = matrix.ravel()

    accuracy = _metric(float((truth == guess).mean()))

    precision = (
        _metric(tp / (tp + fp))
        if (tp + fp) > 0
        else _metric(None, "No row was predicted buggy.")
    )
    recall = (
        _metric(tp / (tp + fn))
        if (tp + fn) > 0
        else _metric(None, "The target has no buggy row.")
    )
    specificity = (
        _metric(tn / (tn + fp))
        if (tn + fp) > 0
        else _metric(None, "The target has no clean row.")
    )

    if precision["value"] is not None and recall["value"] is not None:
        denom = precision["value"] + recall["value"]
        f1_val = 0.0 if denom == 0 else 2.0 * precision["value"] * recall["value"] / denom
        f1 = _metric(f1_val)
    else:
        f1 = _metric(None, "F1 needs both precision and recall to be defined.")

    if recall["value"] is not None and specificity["value"] is not None:
        balanced = _metric((recall["value"] + specificity["value"]) / 2.0)
    else:
        balanced = _metric(None, "Balanced accuracy needs both classes present.")

    has_both_classes = len(np.unique(truth)) == 2
    mcc = (
        _metric(matthews_corrcoef(truth, guess))
        if has_both_classes
        else _metric(None, "MCC needs both classes in the target.")
    )
    roc_auc = (
        _metric(roc_auc_score(truth, score))
        if has_both_classes
        else _metric(None, "ROC-AUC needs both classes in the target.")
    )
    pr_auc = (
        _metric(average_precision_score(truth, score))
        if has_both_classes
        else _metric(None, "PR-AUC needs both classes in the target.")
    )

    result = {
        "confusionMatrix": {
            "truePositive": int(tp),
            "falsePositive": int(fp),
            "trueNegative": int(tn),
            "falseNegative": int(fn),
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
    result.update(_effort_aware_metrics(truth, score, loc_values))
    return result


def _effort_aware_metrics(
    truth: np.ndarray,
    score: np.ndarray,
    loc_values: list[float] | None,
) -> dict:
    if not loc_values or len(loc_values) != truth.size:
        reason = "Recall@20% LOC needs a loc value for every target row."
        return {"recallAt20PercentLoc": _metric(None, reason), "aucec": _metric(None, reason)}

    loc = np.asarray(loc_values, dtype="float64")
    total_loc = float(loc.sum())
    total_bugs = int(truth.sum())

    if total_loc <= 0 or total_bugs == 0:
        reason = "Effort-aware metrics need positive LOC and at least one buggy row."
        return {"recallAt20PercentLoc": _metric(None, reason), "aucec": _metric(None, reason)}

    order = np.argsort(-score, kind="stable")
    cum_loc = np.cumsum(loc[order]) / total_loc
    cum_bugs = np.cumsum(truth[order]) / total_bugs

    cutoff = cum_loc <= 0.20
    recall_at_20 = float(cum_bugs[cutoff][-1]) if cutoff.any() else 0.0
    aucec_val = float(np.trapezoid(cum_bugs, cum_loc))

    return {
        "recallAt20PercentLoc": _metric(recall_at_20),
        "aucec": _metric(aucec_val),
    }
