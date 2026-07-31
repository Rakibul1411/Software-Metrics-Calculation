"""
Schema validation and the raw-value rules.

The SRS forbids guessing: no ``abs()``, no blanket clipping of negatives, and no
``log1p`` before validation. Configured missing markers become NaN and are then
imputed with the source median; anything else unexpected fails the run.
"""

from __future__ import annotations

from dataclasses import dataclass, field

import numpy as np
import pandas as pd

from app.defectlab.registry import (
    FeatureProfile,
    detect_profile,
    find_label_column,
    is_excluded_history_column,
    normalize_header,
)

# Only these become NaN, and only because they are conventional "no value" codes.
MISSING_MARKERS: tuple[float, ...] = (-1.0, -999.0)

# Values like -1e-12 in a non-negative metric are floating-point noise.
NEGATIVE_ZERO_TOLERANCE = 1e-9


class SchemaError(ValueError):
    """Raised when a dataset cannot be used at all."""


@dataclass
class PreparedFrame:
    profile: FeatureProfile
    features: pd.DataFrame
    identifiers: list[str]
    labels: np.ndarray | None
    warnings: list[str] = field(default_factory=list)
    dropped_history_columns: list[str] = field(default_factory=list)


_BUGGY_LABELS = {"buggy", "defective", "defect", "true", "yes"}
_CLEAN_LABELS = {"clean", "nonbuggy", "nondefective", "false", "no"}
_MISSING_LABELS = {"", "?", "na", "nan", "none", "null"}


def _parse_binary_label(value: object) -> int | None:
    """Maps PROMISE counts and public AEEEM clean/buggy labels to 0/1."""
    if value is None or pd.isna(value):
        return None
    text = str(value).strip()
    normalized = text.lower().replace("_", "").replace("-", "").replace(" ", "")
    if normalized in _MISSING_LABELS:
        return None
    if normalized in _BUGGY_LABELS:
        return 1
    if normalized in _CLEAN_LABELS:
        return 0
    try:
        numeric = float(text)
    except (TypeError, ValueError) as exception:
        raise SchemaError(
            f"Unsupported label '{text}'. Use 0/1, a non-negative defect count, "
            "or clean/buggy."
        ) from exception
    if not np.isfinite(numeric) or numeric < 0:
        raise SchemaError(
            f"Invalid label '{text}'. Labels must be finite and non-negative."
        )
    return int(numeric > 0)


def _parse_label_series(series: pd.Series) -> np.ndarray | None:
    parsed: list[int | None] = []
    for row_number, value in enumerate(series.tolist(), start=1):
        try:
            parsed.append(_parse_binary_label(value))
        except SchemaError as exception:
            raise SchemaError(f"Label row {row_number}: {exception}") from exception
    return None if not any(value is not None for value in parsed) \
        else np.asarray(parsed, dtype=object)


def normalize_frame(rows: list[dict]) -> pd.DataFrame:
    if not rows:
        raise SchemaError("The dataset contains no rows.")
    frame = pd.DataFrame(rows)
    frame.columns = [normalize_header(column) for column in frame.columns]
    duplicated = frame.columns[frame.columns.duplicated()].unique().tolist()
    if duplicated:
        raise SchemaError(f"Duplicate columns found: {sorted(duplicated)}")
    return frame


def validate(rows: list[dict], family: str | None = None) -> dict:
    """Reports schema findings without transforming anything."""
    frame = normalize_frame(rows)
    profile = detect_profile(frame.columns)
    if profile is None:
        raise SchemaError(
            "The columns match neither the PROMISE (20 predictors) nor the "
            "AEEEM (56 predictors) profile."
        )
    if family and profile.family != family.strip().upper():
        raise SchemaError(
            f"The dataset looks like {profile.family} but {family.upper()} was expected."
        )

    label_column = find_label_column(frame.columns, profile)
    excluded = [c for c in frame.columns if is_excluded_history_column(c)]
    extra = [
        column
        for column in frame.columns
        if column not in profile.features
        and column != label_column
        and column != "name"
        and column not in excluded
    ]

    issues: list[str] = []
    warnings: list[str] = []
    columns: list[dict] = []
    for feature in profile.features:
        series = pd.to_numeric(frame[feature], errors="coerce")
        raw_present = frame[feature].astype(str).str.strip()
        non_numeric = int(
            (series.isna() & ~raw_present.isin(["", "?", "NA", "na", "nan", "NaN"])).sum()
        )
        missing = int(series.isna().sum()) - non_numeric
        marker_hits = int(series.isin(MISSING_MARKERS).sum())
        negatives = series[(series < -NEGATIVE_ZERO_TOLERANCE) & ~series.isin(MISSING_MARKERS)]
        if non_numeric:
            issues.append(f"{non_numeric} non-numeric value(s) in '{feature}'")
        if np.isinf(series.to_numpy(dtype="float64", na_value=np.nan)).any():
            issues.append(f"Infinite value(s) in '{feature}'")
        if feature in profile.log_features and len(negatives) > 0:
            issues.append(
                f"{len(negatives)} unexpected negative value(s) in non-negative '{feature}'"
            )
        if feature in profile.unit_range_features:
            out_of_range = series[(series < -NEGATIVE_ZERO_TOLERANCE) | (series > 1 + 1e-9)]
            out_of_range = out_of_range[~out_of_range.isin(MISSING_MARKERS)]
            if len(out_of_range) > 0:
                issues.append(
                    f"{len(out_of_range)} value(s) outside [0,1] in ratio '{feature}'"
                )
        if marker_hits:
            warnings.append(
                f"{marker_hits} configured missing marker(s) in '{feature}' become NaN"
            )
        if missing:
            warnings.append(f"{missing} missing value(s) in '{feature}' use median imputation")
        columns.append(
            {
                "name": feature,
                "transform": profile.transform_of(feature),
                "missing": missing,
                "nonNumeric": non_numeric,
                "missingMarkers": marker_hits,
                "negative": int(len(negatives)),
                "minimum": None if series.dropna().empty else float(series.min()),
                "maximum": None if series.dropna().empty else float(series.max()),
            }
        )

    parsed_labels = None
    if label_column is not None:
        try:
            parsed_labels = _parse_label_series(frame[label_column])
        except SchemaError as exception:
            issues.append(str(exception))

    return {
        "family": profile.family,
        "rowCount": int(len(frame)),
        "featureCount": len(profile.features),
        "features": list(profile.features),
        "labelColumn": label_column,
        "hasLabels": parsed_labels is not None,
        "excludedHistoryColumns": excluded,
        "extraColumns": extra,
        "blockingIssues": issues,
        "warnings": warnings,
        "columns": columns,
        "usable": not issues,
    }


def prepare(rows: list[dict], family: str | None = None,
            require_labels: bool = False) -> PreparedFrame:
    """Validates, then returns numeric features in registered order."""
    report = validate(rows, family)
    if report["blockingIssues"]:
        raise SchemaError("; ".join(report["blockingIssues"]))

    frame = normalize_frame(rows)
    profile = detect_profile(frame.columns)
    assert profile is not None  # validate() already proved this

    identifiers = (
        frame["name"].astype(str).tolist()
        if "name" in frame.columns
        else [f"row_{index}" for index in range(len(frame))]
    )

    features = frame.loc[:, list(profile.features)].apply(pd.to_numeric, errors="coerce")
    # Configured markers are the only negatives allowed to mean "missing".
    features = features.mask(features.isin(MISSING_MARKERS))
    # Clean up floating-point noise in non-negative columns.
    for column in profile.log_features:
        near_zero = features[column].between(-NEGATIVE_ZERO_TOLERANCE, 0, inclusive="both")
        features.loc[near_zero, column] = 0.0

    labels = None
    label_column = report["labelColumn"]
    if label_column is not None:
        labels = _parse_label_series(frame[label_column])
    if require_labels and labels is None:
        raise SchemaError("This dataset has no usable label column.")
    if require_labels and any(value is None for value in labels):
        raise SchemaError("Every source row must have a usable label.")

    return PreparedFrame(
        profile=profile,
        features=features,
        identifiers=identifiers,
        labels=labels,
        warnings=list(report["warnings"]),
        dropped_history_columns=list(report["excludedHistoryColumns"]),
    )
