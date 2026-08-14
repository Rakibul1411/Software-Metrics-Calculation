"""Covers app.domain.feature_profile: family registries and header aliases."""

from __future__ import annotations

from app.domain.feature_profile import (
    PROMISE_FEATURES,
    aeeem_profile,
    detect_profile,
    is_excluded_history_column,
    normalize_header,
    promise_profile,
)


def test_promise_has_twenty_predictors_and_four_scale_only():
    profile = promise_profile()
    assert len(profile.features) == 20
    scale_only = set(profile.features) - profile.nonnegative_features
    assert scale_only == {"lcom3", "dam", "mfa", "cam"}


def test_aeeem_has_fifty_six_features_with_ldhh_scale_only():
    profile = aeeem_profile()
    assert len(profile.features) == 56
    assert len(profile.nonnegative_features) == 34  # 17 ck_oo + 17 WCHU
    assert all(not column.startswith("ldhh_") for column in profile.nonnegative_features)


def test_detects_family_from_names_not_order():
    shuffled = list(reversed(PROMISE_FEATURES))
    assert detect_profile(shuffled).family == "PROMISE"


def test_flags_prior_defect_history_columns():
    assert is_excluded_history_column("numberOfBugsFoundUntil:")
    assert is_excluded_history_column("numberOfCriticalBugsFoundUntil:")
    assert not is_excluded_history_column("ck_oo_wmc")


def test_normalizes_published_aeeem_aliases():
    assert normalize_header("ckooPrivateMethod") == "ck_oo_numberofprivatemethods"
    assert normalize_header("WCHUNumAttr") == "wchu_numberofattributes"
    assert normalize_header("LDHHLOC") == "ldhh_numberoflinesofcode"
    assert normalize_header("NumHPBFU") == "numberofhighprioritybugsfounduntil:"
    assert normalize_header("MAX_CC") == "max_cc"
