"""Covers app.domain.evaluation: undefined metrics report a reason, not zero."""

from __future__ import annotations

from app.domain import evaluation


def test_undefined_metrics_report_a_reason_not_zero():
    # A target with only clean rows leaves recall and MCC undefined.
    metrics = evaluation.evaluate([0, 0, 0], [0, 0, 0], [0.1, 0.2, 0.3])
    assert metrics["recall"]["value"] is None
    assert metrics["recall"]["reason"]
    assert metrics["mcc"]["value"] is None
    assert metrics["specificity"]["value"] == 1.0


def test_confusion_matrix_counts():
    metrics = evaluation.evaluate([1, 1, 0, 0], [1, 0, 0, 1], [0.9, 0.2, 0.1, 0.8])
    matrix = metrics["confusionMatrix"]
    assert matrix == {
        "truePositive": 1, "falseNegative": 1,
        "trueNegative": 1, "falsePositive": 1,
    }
    assert metrics["accuracy"]["value"] == 0.5


def test_f1_is_zero_when_defined_precision_and_recall_are_both_zero():
    metrics = evaluation.evaluate([1, 0], [0, 1], [0.1, 0.9])
    assert metrics["precision"]["value"] == 0.0
    assert metrics["recall"]["value"] == 0.0
    assert metrics["f1"]["value"] == 0.0


def test_effort_aware_metrics_need_loc():
    without = evaluation.evaluate([1, 0], [1, 0], [0.9, 0.1])
    assert without["recallAt20PercentLoc"]["value"] is None

    with_loc = evaluation.evaluate([1, 0, 1, 0], [1, 0, 1, 0],
                                   [0.9, 0.1, 0.8, 0.2], [10, 100, 10, 100])
    assert with_loc["recallAt20PercentLoc"]["value"] is not None
    assert with_loc["aucec"]["value"] is not None
