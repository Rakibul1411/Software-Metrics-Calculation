"""Covers app.domain.dataset_preparation: schema validation and label parsing."""

from __future__ import annotations

import numpy as np
import pytest

from app.domain.dataset_preparation import SchemaError, prepare, validate
from app.domain.feature_profile import aeeem_profile
from helpers import promise_rows


def test_rejects_unknown_schema():
    with pytest.raises(SchemaError):
        validate([{"alpha": 1, "beta": 2}])


def test_rejects_duplicate_columns():
    rows = promise_rows(4)
    frame_rows = [dict(row) for row in rows]
    with pytest.raises(SchemaError):
        # Two headers normalising to the same canonical name.
        validate([{**frame_rows[0], "WMC": 1}])


def test_reports_unexpected_negative_in_non_negative_column():
    rows = promise_rows(6)
    rows[0]["loc"] = -42.0
    report = validate(rows)
    assert not report["usable"]
    assert any("negative" in issue for issue in report["blockingIssues"])


def test_reports_non_numeric_value():
    rows = promise_rows(6)
    rows[1]["wmc"] = "not-a-number"
    report = validate(rows)
    assert any("non-numeric" in issue for issue in report["blockingIssues"])


def test_configured_missing_marker_becomes_nan_not_an_error():
    rows = promise_rows(6)
    rows[2]["loc"] = -1  # documented missing marker
    report = validate(rows)
    assert report["usable"], report["blockingIssues"]
    assert any("missing marker" in warning for warning in report["warnings"])


def test_ratio_outside_unit_range_is_rejected():
    rows = promise_rows(6)
    rows[3]["cam"] = 4.2
    report = validate(rows)
    assert any("outside [0,1]" in issue for issue in report["blockingIssues"])


def test_bug_count_is_binarised():
    rows = promise_rows(4)
    rows[0]["bug"] = 7
    prepared = prepare(rows)
    assert set(np.unique(prepared.labels)).issubset({0, 1})
    assert prepared.labels[0] == 1


def test_public_aeeem_clean_buggy_labels_are_mapped():
    columns = {column: 1.0 for column in aeeem_profile().features}
    rows = [
        {"name": "demo.Clean", "class": "clean", **columns},
        {"name": "demo.Buggy", "class": "buggy", **columns},
    ]
    prepared = prepare(rows, require_labels=True)
    assert prepared.labels.tolist() == [0, 1]
