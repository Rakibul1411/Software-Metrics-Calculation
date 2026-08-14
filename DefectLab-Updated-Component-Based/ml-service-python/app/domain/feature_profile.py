"""
Feature registry.

Each column carries its role, allowed range, missing markers and transformation,
so the pipeline never has to guess whether a value is valid. Family detection
matches the exact required feature set by canonical name, never by position.
"""

from __future__ import annotations

from dataclasses import dataclass, field
from typing import Iterable

PROMISE_LABEL = "bug"
AEEEM_LABEL = "class"

PROMISE_FEATURES: tuple[str, ...] = (
    "wmc", "dit", "noc", "cbo", "rfc", "lcom", "ca", "ce", "npm", "lcom3",
    "loc", "dam", "moa", "mfa", "cam", "ic", "cbm", "amc", "max_cc", "avg_cc",
)

# These 16 are non-negative counts; an unexpected negative value is invalid
# data. The four ratios (PROMISE_SCALE_ONLY) are checked separately below.
PROMISE_NONNEGATIVE_FEATURES: frozenset[str] = frozenset({
    "wmc", "dit", "noc", "cbo", "rfc", "lcom", "ca", "ce",
    "npm", "loc", "moa", "ic", "cbm", "amc", "max_cc", "avg_cc",
})
PROMISE_SCALE_ONLY: frozenset[str] = frozenset({"lcom3", "dam", "mfa", "cam"})

# Ratios that must stay inside [0, 1]; anything outside is invalid data.
PROMISE_UNIT_RANGE: frozenset[str] = frozenset({"dam", "mfa", "cam"})

AEEEM_BASE_METRICS: tuple[str, ...] = (
    "wmc", "dit", "rfc", "noc", "cbo", "lcom", "fanin", "fanout",
    "numberofattributes", "numberofpublicattributes",
    "numberofprivateattributes", "numberofattributesinherited",
    "numberoflinesofcode", "numberofmethods", "numberofpublicmethods",
    "numberofprivatemethods", "numberofmethodsinherited",
)
AEEEM_PREFIXES: tuple[str, ...] = ("ck_oo_", "wchu_", "ldhh_")
AEEEM_ENTROPY_FEATURES: tuple[str, ...] = (
    "cvsentropy", "cvswentropy", "cvslinentropy", "cvslogentropy", "cvsexpentropy",
)

# Prior-defect-history columns. Excluded from X: they need verified
# issue-to-commit linkage and leak defect history into the model.
AEEEM_EXCLUDED_PREFIXES: tuple[str, ...] = (
    "numberofbugsfounduntil",
    "numberofnontrivialbugsfounduntil",
    "numberofmajorbugsfounduntil",
    "numberofcriticalbugsfounduntil",
    "numberofhighprioritybugsfounduntil",
)

_AEEEM_PREFIX_ALIASES: dict[str, str] = {
    "ckoo": "ck_oo_",
    "wchu": "wchu_",
    "ldhh": "ldhh_",
}

_AEEEM_SUFFIX_ALIASES: dict[str, str] = {
    "wmc": "wmc",
    "dit": "dit",
    "rfc": "rfc",
    "noc": "noc",
    "cbo": "cbo",
    "lcom": "lcom",
    "fanin": "fanin",
    "fanout": "fanout",
    "attr": "numberofattributes",
    "numattr": "numberofattributes",
    "numberofattributes": "numberofattributes",
    "publicattr": "numberofpublicattributes",
    "numberofpublicattributes": "numberofpublicattributes",
    "privateattr": "numberofprivateattributes",
    "numberofprivateattributes": "numberofprivateattributes",
    "attrinherited": "numberofattributesinherited",
    "numberofattributesinherited": "numberofattributesinherited",
    "loc": "numberoflinesofcode",
    "numberoflinesofcode": "numberoflinesofcode",
    "method": "numberofmethods",
    "methods": "numberofmethods",
    "numberofmethods": "numberofmethods",
    "publicmethod": "numberofpublicmethods",
    "numberofpublicmethods": "numberofpublicmethods",
    "privatemethod": "numberofprivatemethods",
    "numberofprivatemethods": "numberofprivatemethods",
    "methodinherited": "numberofmethodsinherited",
    "numberofmethodsinherited": "numberofmethodsinherited",
}

_SIMPLE_ALIASES: dict[str, str] = {
    "maxcc": "max_cc",
    "avgcc": "avg_cc",
    "lcom3": "lcom3",
    "numbfu": "numberofbugsfounduntil:",
    "nntbfu": "numberofnontrivialbugsfounduntil:",
    "nummbfu": "numberofmajorbugsfounduntil:",
    "ncbfu": "numberofcriticalbugsfounduntil:",
    "numhpbfu": "numberofhighprioritybugsfounduntil:",
    "cvsentropy": "cvsentropy",
    "cvswentropy": "cvswentropy",
    "cvslinentropy": "cvslinentropy",
    "cvslogentropy": "cvslogentropy",
    "cvsexpentropy": "cvsexpentropy",
}


def normalize_header(header: str) -> str:
    """Trim, lowercase, and map published AEEEM/PROMISE aliases."""
    normalized = str(header).strip().lower()
    compact = "".join(character for character in normalized if character.isalnum())
    if compact in _SIMPLE_ALIASES:
        return _SIMPLE_ALIASES[compact]
    for alias, canonical_prefix in _AEEEM_PREFIX_ALIASES.items():
        if compact.startswith(alias):
            suffix = compact[len(alias):]
            if suffix in _AEEEM_SUFFIX_ALIASES:
                return canonical_prefix + _AEEEM_SUFFIX_ALIASES[suffix]
    return normalized


def is_excluded_history_column(header: str) -> bool:
    collapsed = normalize_header(header).replace(":", "").replace("_", "")
    return collapsed.startswith(AEEEM_EXCLUDED_PREFIXES)


@dataclass(frozen=True)
class FeatureProfile:
    family: str
    features: tuple[str, ...]
    nonnegative_features: frozenset[str]
    label_column: str
    unit_range_features: frozenset[str] = field(default_factory=frozenset)


def promise_profile() -> FeatureProfile:
    return FeatureProfile(
        family="PROMISE",
        features=PROMISE_FEATURES,
        nonnegative_features=PROMISE_NONNEGATIVE_FEATURES,
        label_column=PROMISE_LABEL,
        unit_range_features=PROMISE_UNIT_RANGE,
    )


def aeeem_profile() -> FeatureProfile:
    features: list[str] = []
    nonnegative_features: set[str] = set()
    for prefix in AEEEM_PREFIXES:
        for base in AEEEM_BASE_METRICS:
            column = f"{prefix}{base}"
            features.append(column)
            # LDHH values are signed deltas, so they can legitimately be negative.
            if prefix != "ldhh_":
                nonnegative_features.add(column)
    features.extend(AEEEM_ENTROPY_FEATURES)
    return FeatureProfile(
        family="AEEEM",
        features=tuple(features),
        nonnegative_features=frozenset(nonnegative_features),
        label_column=AEEEM_LABEL,
    )


def detect_profile(headers: Iterable[str]) -> FeatureProfile | None:
    """Returns the profile whose complete feature list is present, else None."""
    normalized = {normalize_header(header) for header in headers}
    for profile in (promise_profile(), aeeem_profile()):
        if set(profile.features).issubset(normalized):
            return profile
    return None


def find_label_column(headers: Iterable[str], profile: FeatureProfile) -> str | None:
    """Accepts the family's own label column or the shared ``bug`` column."""
    normalized = {normalize_header(header) for header in headers}
    for candidate in (profile.label_column, PROMISE_LABEL):
        if candidate in normalized:
            return candidate
    return None
