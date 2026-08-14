"""Covers app.domain.prediction_pipeline: the SRS rules the pipeline must not break."""

from __future__ import annotations

import pytest

from app.domain import prediction_pipeline
from app.domain.dataset_preparation import SchemaError, prepare
from app.domain.feature_profile import aeeem_profile
from helpers import promise_rows


def test_standard_pipeline_applies_coral_by_default_without_log():
    source = prepare(promise_rows(30))
    target = prepare(promise_rows(15))
    outcome = prediction_pipeline.run(source, target)

    assert outcome.log_applied is False
    assert outcome.coral_applied is True


def test_dataset_alignment_can_be_disabled():
    source = prepare(promise_rows(30))
    target = prepare(promise_rows(15, scale=1.4))
    outcome = prediction_pipeline.run(source, target, apply_coral=False)

    assert outcome.log_applied is False
    assert outcome.coral_applied is False
    assert outcome.covariance_distance_before is not None
    assert outcome.covariance_distance_after is None


def test_run_ranks_every_target_row_descending():
    source = prepare(promise_rows(40))
    target = prepare(promise_rows(20, scale=1.4))
    outcome = prediction_pipeline.run(source, target)

    assert len(outcome.predictions) == 20
    scores = [row["defectScore"] for row in outcome.predictions]
    assert scores == sorted(scores, reverse=True)
    assert all(
        row["defectProbability"] == row["defectScore"]
        for row in outcome.predictions
    )
    assert [row["riskRank"] for row in outcome.predictions] == list(range(1, 21))


@pytest.mark.parametrize("selected_k", [1, 2, 3, 4, 5])
def test_knn_uses_user_selected_k(selected_k):
    source = prepare(promise_rows(40))
    target = prepare(promise_rows(20))
    outcome = prediction_pipeline.run(source, target, k=selected_k)
    assert outcome.selected_k == selected_k


@pytest.mark.parametrize("invalid_k", [0, 6])
def test_k_outside_supported_range_is_rejected(invalid_k):
    source = prepare(promise_rows(20))
    target = prepare(promise_rows(10))
    with pytest.raises(SchemaError):
        prediction_pipeline.run(source, target, k=invalid_k)


def test_coral_reduces_covariance_distance():
    source = prepare(promise_rows(60))
    target = prepare(promise_rows(40, scale=3.0))
    outcome = prediction_pipeline.run(source, target)
    assert outcome.covariance_distance_before is not None
    assert outcome.covariance_distance_after <= outcome.covariance_distance_before


def test_standard_pipeline_records_coral_covariance_distance():
    source = prepare(promise_rows(30))
    target = prepare(promise_rows(15))
    outcome = prediction_pipeline.run(source, target)
    assert outcome.covariance_distance_before is not None
    assert outcome.coral_applied is True


def test_unlabelled_source_is_refused():
    rows = promise_rows(20)
    for row in rows:
        row.pop("bug")
    source = prepare(rows)
    target = prepare(promise_rows(10))
    with pytest.raises(SchemaError):
        prediction_pipeline.run(source, target)


def test_mixing_families_is_refused():
    promise = prepare(promise_rows(20))
    aeeem_columns = {column: 1.0 for column in aeeem_profile().features}
    aeeem_rows = [{"name": f"c{i}", "class": i % 2, **aeeem_columns} for i in range(10)]
    aeeem = prepare(aeeem_rows)
    with pytest.raises(SchemaError):
        prediction_pipeline.run(promise, aeeem)


def test_threshold_controls_the_predicted_label():
    source = prepare(promise_rows(40))
    target = prepare(promise_rows(20))
    strict = prediction_pipeline.run(source, target, threshold=0.95)
    loose = prediction_pipeline.run(source, target, threshold=0.05)
    assert sum(r["predictedLabel"] for r in loose.predictions) \
        >= sum(r["predictedLabel"] for r in strict.predictions)


def test_same_seed_gives_identical_output():
    source = prepare(promise_rows(40))
    target = prepare(promise_rows(20, scale=1.2))
    first = prediction_pipeline.run(source, target, seed=7)
    second = prediction_pipeline.run(source, target, seed=7)
    assert first.predictions == second.predictions
    assert first.selected_k == second.selected_k
