from __future__ import annotations

from dataclasses import dataclass, field
import numpy as np
import pandas as pd

from app.domain.feature_profile import (
    FeatureProfile,
    detect_profile,
    find_label_column,
    is_excluded_history_column,
    normalize_header,
)

# Standard sentinel values representing missing measurements in software metrics
MISSING_MARKERS: tuple[float, ...] = (-1.0, -999.0)
NEGATIVE_ZERO_TOLERANCE: float = 1e-9

IDENTIFIER_COLUMNS: tuple[str, ...] = (
    "name", "classname", "class_name", "file", "classidentifier", "identifier"
)

BUGGY_LABELS = frozenset({"buggy", "defective", "defect", "true", "yes"})
CLEAN_LABELS = frozenset({"clean", "nonbuggy", "nondefective", "false", "no"})
NULL_LABELS = frozenset({"", "?", "na", "nan", "none", "null"})


class SchemaError(ValueError):
    """Raised when dataset schema or row data violates domain rules."""


@dataclass
class PreparedFrame:
    profile: FeatureProfile
    features: pd.DataFrame
    identifiers: list[str]
    labels: np.ndarray | None
    warnings: list[str] = field(default_factory=list)


def _parse_binary_label(value: object) -> int | None:
    if value is None or pd.isna(value):
        return None

    raw_text = str(value).strip()
    norm = raw_text.lower().replace("_", "").replace("-", "").replace(" ", "")

    if norm in NULL_LABELS:
        return None
    if norm in BUGGY_LABELS:
        return 1
    if norm in CLEAN_LABELS:
        return 0

    try:
        numeric = float(raw_text)
    except (TypeError, ValueError) as err:
        raise SchemaError(
            f"Unsupported label '{raw_text}'. Expected 0/1, defect count, or clean/buggy text."
        ) from err

    if not np.isfinite(numeric) or numeric < 0:
        raise SchemaError(f"Invalid label '{raw_text}'. Labels must be non-negative finite values.")

    return 1 if numeric > 0 else 0


def _parse_label_series(series: pd.Series) -> np.ndarray | None:
    parsed: list[int | None] = []
    for row_idx, val in enumerate(series.tolist(), start=1):
        try:
            parsed.append(_parse_binary_label(val))
        except SchemaError as err:
            raise SchemaError(f"Label row {row_idx}: {err}") from err

    if not any(v is not None for v in parsed):
        return None
    return np.asarray(parsed, dtype=object)


def normalize_frame(rows: list[dict]) -> pd.DataFrame:
    if not rows:
        raise SchemaError("The dataset contains no rows.")

    df = pd.DataFrame(rows)
    df.columns = [normalize_header(col) for col in df.columns]

    duplicated = df.columns[df.columns.duplicated()].unique().tolist()
    if duplicated:
        raise SchemaError(f"Duplicate columns found after normalization: {sorted(duplicated)}")

    return df


def validate(rows: list[dict], family: str | None = None) -> dict:
    df = normalize_frame(rows)
    profile = detect_profile(df.columns)
    if profile is None:
        raise SchemaError(
            "Columns do not match supported feature schemas (PROMISE 20 features or AEEEM 56 features)."
        )

    if family and profile.family != family.strip().upper():
        raise SchemaError(
            f"Dataset detected as {profile.family}, but expected {family.upper()}."
        )

    label_col = find_label_column(df.columns, profile)
    excluded = [c for c in df.columns if is_excluded_history_column(c)]
    extra = [
        c for c in df.columns
        if c not in profile.features
        and c != label_col
        and c not in IDENTIFIER_COLUMNS
        and c not in excluded
    ]

    issues: list[str] = []
    warnings: list[str] = []
    column_stats: list[dict] = []

    for feature in profile.features:
        series = pd.to_numeric(df[feature], errors="coerce")
        raw_str = df[feature].astype(str).str.strip()

        non_numeric = int((series.isna() & ~raw_str.isin(["", "?", "NA", "na", "nan", "NaN"])).sum())
        missing = int(series.isna().sum()) - non_numeric
        marker_hits = int(series.isin(MISSING_MARKERS).sum())
        negatives = series[(series < -NEGATIVE_ZERO_TOLERANCE) & ~series.isin(MISSING_MARKERS)]

        if non_numeric > 0:
            issues.append(f"{non_numeric} non-numeric value(s) in '{feature}'")
        if np.isinf(series.to_numpy(dtype="float64", na_value=np.nan)).any():
            issues.append(f"Infinite value(s) in '{feature}'")
        if feature in profile.nonnegative_features and len(negatives) > 0:
            issues.append(f"{len(negatives)} unexpected negative value(s) in non-negative '{feature}'")
        if feature in profile.unit_range_features:
            out_of_range = series[(series < -NEGATIVE_ZERO_TOLERANCE) | (series > 1.0 + 1e-9)]
            out_of_range = out_of_range[~out_of_range.isin(MISSING_MARKERS)]
            if len(out_of_range) > 0:
                issues.append(f"{len(out_of_range)} value(s) outside [0,1] in ratio '{feature}'")

        if marker_hits > 0:
            warnings.append(f"{marker_hits} configured missing marker(s) in '{feature}' become NaN")
        if missing > 0:
            warnings.append(f"{missing} missing value(s) in '{feature}' use median imputation")

        clean_vals = series.dropna()
        column_stats.append({
            "name": feature,
            "missing": missing,
            "nonNumeric": non_numeric,
            "missingMarkers": marker_hits,
            "negative": int(len(negatives)),
            "minimum": None if clean_vals.empty else float(clean_vals.min()),
            "maximum": None if clean_vals.empty else float(clean_vals.max()),
        })

    parsed_labels = None
    if label_col is not None:
        try:
            parsed_labels = _parse_label_series(df[label_col])
        except SchemaError as err:
            issues.append(str(err))

    return {
        "family": profile.family,
        "rowCount": int(len(df)),
        "featureCount": len(profile.features),
        "features": list(profile.features),
        "labelColumn": label_col,
        "hasLabels": parsed_labels is not None,
        "excludedHistoryColumns": excluded,
        "extraColumns": extra,
        "blockingIssues": issues,
        "warnings": warnings,
        "columns": column_stats,
        "usable": len(issues) == 0,
    }


def prepare(
    rows: list[dict],
    family: str | None = None,
    require_labels: bool = False,
) -> PreparedFrame:
    report = validate(rows, family)
    if report["blockingIssues"]:
        raise SchemaError("; ".join(report["blockingIssues"]))

    df = normalize_frame(rows)
    profile = detect_profile(df.columns)
    if profile is None:
        raise SchemaError("Unable to detect feature profile for prepared frame.")

    id_col = next((c for c in IDENTIFIER_COLUMNS if c in df.columns), None)
    identifiers = (
        df[id_col].astype(str).tolist()
        if id_col is not None
        else [f"row_{idx}" for idx in range(len(df))]
    )

    features = df.loc[:, list(profile.features)].apply(pd.to_numeric, errors="coerce")
    features = features.mask(features.isin(MISSING_MARKERS))

    for col in profile.nonnegative_features:
        near_zero = features[col].between(-NEGATIVE_ZERO_TOLERANCE, 0.0, inclusive="both")
        features.loc[near_zero, col] = 0.0

    labels = None
    label_col = report["labelColumn"]
    if label_col is not None:
        labels = _parse_label_series(df[label_col])

    if require_labels:
        if labels is None:
            raise SchemaError("This dataset has no usable label column.")
        if any(v is None for v in labels):
            raise SchemaError("Every source row must have a usable label.")

    return PreparedFrame(
        profile=profile,
        features=features,
        identifiers=identifiers,
        labels=labels,
        warnings=list(report["warnings"]),
    )
