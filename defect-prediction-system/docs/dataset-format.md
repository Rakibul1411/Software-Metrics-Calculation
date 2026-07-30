# Dataset Format Guide

## PROMISE target format

The PROMISE extractor produces `name` plus 20 CKJM-style object-oriented
metrics. Generated targets never contain the labelled training column `bug`.
Historical PROMISE CSVs store a defect count in `bug`; the prediction service
maps `bug > 0` to the buggy class and `bug = 0` to the clean class.

The ordered columns are:

```text
name, wmc, dit, noc, cbo, rfc, lcom, ca, ce, npm, lcom3, loc,
dam, moa, mfa, cam, ic, cbm, amc, max_cc, avg_cc
```

Important implementation choices verified against the supplied PROMISE data:

- `CBO` is the unique union of incoming and outgoing in-project type coupling;
  `Ca` and `Ce` retain their separate directions.
- `CAM` includes the class's own type and includes constructors in the method
  average.
- `LCOM` and `LCOM3` include accesses to all class-owned attributes, including
  static attributes.
- ordinary and abstract/interface methods have cyclomatic complexity base 1;
  constructors retain the CKJM dataset convention of base 0.
- `max_cc` and `avg_cc` are computed over the declared method/constructor
  complexities used for the class.

This is a source/JDT implementation of the dataset schema. The historic data
was produced using CKJM/compiled bytecode, so exact value reproduction requires
the original compiled artifacts, dependency classpath, class-selection manifest,
and tool version. Schema compatibility must not be described as an exact CKJM
reproduction.

## AEEEM non-defect target format

The AEEEM extractor produces exactly 57 CSV columns:

| Group | Count | Data source |
|---|---:|---|
| Identifier `name` | 1 | Fully qualified final-release class name |
| `ck_oo_*` | 17 | JDT analysis of the final release |
| `WCHU_*` | 17 | Positive metric deltas between 14-day snapshots |
| `LDHH_*` | 17 | Linearly decayed adaptive entropy of those deltas |
| `Cvs*Entropy` | 5 | Git added/deleted lines grouped into the same periods |
| **Total** | **57** | **1 identifier + 56 predictors** |

The ordered predictor list is authoritative in
[`aeeem-static-metrics.json`](../backend-java/src/main/resources/metric-profiles/aeeem-static-metrics.json).

### Deliberately absent fields

An extracted target does not contain:

- `numberOfNonTrivialBugsFoundUntil`
- `numberOfMajorBugsFoundUntil`
- `numberOfCriticalBugsFoundUntil`
- `numberOfHighPriorityBugsFoundUntil`
- `numberOfBugsFoundUntil`
- `class`

The five bug-history predictors require issue-tracker-to-commit-to-class links.
The `class` field is the known buggy/clean label in labelled AEEEM source data.
Neither can be derived honestly from source and Git history alone, so no zero or
invented values are emitted.

When training with historical AEEEM files, the prediction service selects the 56
features shared with the target. Extra bug-history columns remain in the source
files but are not used.

## Implemented history definitions

Let `X(c,m,t)` be source metric `m` for class `c` at snapshot `t`. For classes
present in both adjacent snapshots:

```text
D(c,m,t) = abs(X(c,m,t) - X(c,m,t-1))
```

Missing class/snapshot pairs are excluded.

- `WCHU(c,m)` sums `1 + 0.01 × D(c,m,t)` for positive deltas.
- `LDHH(c,m)` first computes the adaptive entropy of all positive deltas for
  metric `m` in interval `t`, then assigns the full entropy to every changed
  class with linear time decay.
- `CvsEntropy` is HCM: each changed file receives the adaptive entropy of the
  system for the period.
- `CvsWEntropy` is WHCM: each changed file receives its line-change probability
  multiplied by the period entropy.
- `CvsExpEntropy`, `CvsLinEntropy`, and `CvsLogEntropy` apply the published
  exponential, linear, and logarithmic time-decay denominators to HCM.

For Cvs metrics, file probability is based on `added lines + deleted lines`.
Entropy uses the number of files modified within the current and previous five
periods as its adaptive logarithm base. Git renames are followed and file-level
values are assigned to every final-release class declared in that source file.

The output follows the methodology in the supplied WCHU/LDHH and AEEEM
benchmark papers. Exact equality with the historic five-project datasets is not
promised because their original repository histories, FAMIX parser version, and
all decay parameter choices are not published as a reproducible extractor.
