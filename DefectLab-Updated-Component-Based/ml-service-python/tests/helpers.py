"""Shared row-building helpers for the domain-layer test suites."""

from __future__ import annotations

from app.domain.feature_profile import PROMISE_FEATURES


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
