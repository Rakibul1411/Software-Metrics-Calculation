"""Covers the SRS rules the pipeline must not break."""

from __future__ import annotations

import numpy as np
import pytest

from app.defectlab import evaluation, pipeline
from app.defectlab.preparation import SchemaError, prepare, validate
from app.defectlab.registry import (
    PROMISE_FEATURES,
    aeeem_profile,
    detect_profile,
    is_excluded_history_column,
    normalize_header,
    promise_profile,
)


def promise_row(index: int, bug: int, scale: float = 1.0) -> dict:
    row = {"name": f"demo.Class{index}", "bug": bug}
    for position, feature in enumerate(PROMISE_FEATURES):
        if feature in {"dam", "mfa", "cam", "lcom3"}:
            row[feature] = round(min(1.0, 0.1 + (position % 7) * 0.1), 3)
        else:
            row[feature] = float((index % 9 + position % 5 + 1) * scale)
    return row


def promise_rows(count: int, scale: float = 1.0) -> list[dict]:
    # Alternating labels keep both classes present for stratified CV.
    return [promise_row(index, index % 2, scale) for index in range(count)]


class TestRegistry:
    def test_promise_has_twenty_predictors_and_four_scale_only(self):
        profile = promise_profile()
        assert len(profile.features) == 20
        assert profile.scale_only_features == {"lcom3", "dam", "mfa", "cam"}

    def test_aeeem_has_fifty_six_features_with_ldhh_scale_only(self):
        profile = aeeem_profile()
        assert len(profile.features) == 56
        assert len(profile.log_features) == 34  # 17 ck_oo + 17 WCHU
        assert all(not column.startswith("ldhh_") for column in profile.log_features)

    def test_detects_family_from_names_not_order(self):
        shuffled = list(reversed(PROMISE_FEATURES))
        assert detect_profile(shuffled).family == "PROMISE"

    def test_flags_prior_defect_history_columns(self):
        assert is_excluded_history_column("numberOfBugsFoundUntil:")
        assert is_excluded_history_column("numberOfCriticalBugsFoundUntil:")
        assert not is_excluded_history_column("ck_oo_wmc")

    def test_normalizes_published_aeeem_aliases(self):
        assert normalize_header("ckooPrivateMethod") == "ck_oo_numberofprivatemethods"
        assert normalize_header("WCHUNumAttr") == "wchu_numberofattributes"
        assert normalize_header("LDHHLOC") == "ldhh_numberoflinesofcode"
        assert normalize_header("NumHPBFU") == "numberofhighprioritybugsfounduntil:"
        assert normalize_header("MAX_CC") == "max_cc"


class TestValidation:
    def test_rejects_unknown_schema(self):
        with pytest.raises(SchemaError):
            validate([{"alpha": 1, "beta": 2}])

    def test_rejects_duplicate_columns(self):
        rows = promise_rows(4)
        frame_rows = [dict(row) for row in rows]
        with pytest.raises(SchemaError):
            # Two headers normalising to the same canonical name.
            validate([{**frame_rows[0], "WMC": 1}])

    def test_reports_unexpected_negative_in_non_negative_column(self):
        rows = promise_rows(6)
        rows[0]["loc"] = -42.0
        report = validate(rows)
        assert not report["usable"]
        assert any("negative" in issue for issue in report["blockingIssues"])

    def test_reports_non_numeric_value(self):
        rows = promise_rows(6)
        rows[1]["wmc"] = "not-a-number"
        report = validate(rows)
        assert any("non-numeric" in issue for issue in report["blockingIssues"])

    def test_configured_missing_marker_becomes_nan_not_an_error(self):
        rows = promise_rows(6)
        rows[2]["loc"] = -1  # documented missing marker
        report = validate(rows)
        assert report["usable"], report["blockingIssues"]
        assert any("missing marker" in warning for warning in report["warnings"])

    def test_ratio_outside_unit_range_is_rejected(self):
        rows = promise_rows(6)
        rows[3]["cam"] = 4.2
        report = validate(rows)
        assert any("outside [0,1]" in issue for issue in report["blockingIssues"])

    def test_bug_count_is_binarised(self):
        rows = promise_rows(4)
        rows[0]["bug"] = 7
        prepared = prepare(rows)
        assert set(np.unique(prepared.labels)).issubset({0, 1})
        assert prepared.labels[0] == 1

    def test_public_aeeem_clean_buggy_labels_are_mapped(self):
        columns = {column: 1.0 for column in aeeem_profile().features}
        rows = [
            {"name": "demo.Clean", "class": "clean", **columns},
            {"name": "demo.Buggy", "class": "buggy", **columns},
        ]
        prepared = prepare(rows, require_labels=True)
        assert prepared.labels.tolist() == [0, 1]


class TestPipeline:
    def test_standard_pipeline_always_applies_log_and_coral(self):
        source = prepare(promise_rows(30))
        target = prepare(promise_rows(15))
        outcome = pipeline.run(source, target)

        assert outcome.log_applied is True
        assert outcome.coral_applied is True

    def test_run_ranks_every_target_row_descending(self):
        source = prepare(promise_rows(40))
        target = prepare(promise_rows(20, scale=1.4))
        outcome = pipeline.run(source, target)

        assert len(outcome.predictions) == 20
        scores = [row["defectScore"] for row in outcome.predictions]
        assert scores == sorted(scores, reverse=True)
        assert all(
            row["defectProbability"] == row["defectScore"]
            for row in outcome.predictions
        )
        assert [row["riskRank"] for row in outcome.predictions] == list(range(1, 21))

    def test_manual_k_is_used_without_auto_selection(self):
        source = prepare(promise_rows(40))
        target = prepare(promise_rows(20))
        outcome = pipeline.run(source, target, fixed_k=4)
        assert outcome.selected_k == 4
        assert outcome.k_candidates == [4]
        assert outcome.k_scores == {}

    def test_svm_uses_manual_c_and_returns_ranked_probabilities(self):
        source = prepare(promise_rows(40))
        target = prepare(promise_rows(12, scale=1.2))
        outcome = pipeline.run(
            source, target, model_name="SVM",
            svm_c=10.0, svm_kernel="rbf",
        )
        assert outcome.model_name == "SVM"
        assert outcome.svm_c == 10.0
        assert outcome.selected_k is None
        assert len(outcome.predictions) == 12

    def test_manual_k_must_be_between_one_and_five(self):
        source = prepare(promise_rows(20))
        target = prepare(promise_rows(10))
        with pytest.raises(SchemaError):
            pipeline.run(source, target, fixed_k=6)

    def test_coral_reduces_covariance_distance(self):
        source = prepare(promise_rows(60))
        target = prepare(promise_rows(40, scale=3.0))
        outcome = pipeline.run(source, target)
        assert outcome.covariance_distance_before is not None
        assert outcome.covariance_distance_after <= outcome.covariance_distance_before

    def test_standard_pipeline_records_coral_covariance_distance(self):
        source = prepare(promise_rows(30))
        target = prepare(promise_rows(15))
        outcome = pipeline.run(source, target)
        assert outcome.covariance_distance_before is not None
        assert outcome.coral_applied is True

    def test_unlabelled_source_is_refused(self):
        rows = promise_rows(20)
        for row in rows:
            row.pop("bug")
        source = prepare(rows)
        target = prepare(promise_rows(10))
        with pytest.raises(SchemaError):
            pipeline.run(source, target)

    def test_mixing_families_is_refused(self):
        promise = prepare(promise_rows(20))
        aeeem_columns = {column: 1.0 for column in aeeem_profile().features}
        aeeem_rows = [{"name": f"c{i}", "class": i % 2, **aeeem_columns} for i in range(10)]
        aeeem = prepare(aeeem_rows)
        with pytest.raises(SchemaError):
            pipeline.run(promise, aeeem)

    def test_threshold_controls_the_predicted_label(self):
        source = prepare(promise_rows(40))
        target = prepare(promise_rows(20))
        strict = pipeline.run(source, target, threshold=0.95)
        loose = pipeline.run(source, target, threshold=0.05)
        assert sum(r["predictedLabel"] for r in loose.predictions) \
            >= sum(r["predictedLabel"] for r in strict.predictions)

    def test_same_seed_gives_identical_output(self):
        source = prepare(promise_rows(40))
        target = prepare(promise_rows(20, scale=1.2))
        first = pipeline.run(source, target, seed=7)
        second = pipeline.run(source, target, seed=7)
        assert first.predictions == second.predictions
        assert first.selected_k == second.selected_k


class TestEvaluation:
    def test_undefined_metrics_report_a_reason_not_zero(self):
        # A target with only clean rows leaves recall and MCC undefined.
        metrics = evaluation.evaluate([0, 0, 0], [0, 0, 0], [0.1, 0.2, 0.3])
        assert metrics["recall"]["value"] is None
        assert metrics["recall"]["reason"]
        assert metrics["mcc"]["value"] is None
        assert metrics["specificity"]["value"] == 1.0

    def test_confusion_matrix_counts(self):
        metrics = evaluation.evaluate([1, 1, 0, 0], [1, 0, 0, 1], [0.9, 0.2, 0.1, 0.8])
        matrix = metrics["confusionMatrix"]
        assert matrix == {
            "truePositive": 1, "falseNegative": 1,
            "trueNegative": 1, "falsePositive": 1,
        }
        assert metrics["accuracy"]["value"] == 0.5

    def test_f1_is_zero_when_defined_precision_and_recall_are_both_zero(self):
        metrics = evaluation.evaluate([1, 0], [0, 1], [0.1, 0.9])
        assert metrics["precision"]["value"] == 0.0
        assert metrics["recall"]["value"] == 0.0
        assert metrics["f1"]["value"] == 0.0

    def test_effort_aware_metrics_need_loc(self):
        without = evaluation.evaluate([1, 0], [1, 0], [0.9, 0.1])
        assert without["recallAt20PercentLoc"]["value"] is None

        with_loc = evaluation.evaluate([1, 0, 1, 0], [1, 0, 1, 0],
                                       [0.9, 0.1, 0.8, 0.2], [10, 100, 10, 100])
        assert with_loc["recallAt20PercentLoc"]["value"] is not None
        assert with_loc["aucec"]["value"] is not None
