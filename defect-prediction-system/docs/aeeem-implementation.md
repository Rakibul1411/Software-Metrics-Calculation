# AEEEM 56-feature implementation

This project implements the non-defect predictors described in:

- *Evaluating Defect Prediction Approaches: A Benchmark and an Extensive Comparison*
- *Evaluating defect prediction approaches: a benchmark and an extensive comparison*
  (`s10664-011-9173-9`)

It does not invoke CK, CKJM, PyDriller, or another metric calculator. Eclipse JDT
parses Java source, Git provides raw history, and this codebase implements the
metric equations.

## Data flow

1. Preserve the branch and module from a GitHub `tree/branch/folder` URL, or
   follow the repository's default `origin/HEAD` for a root URL.
2. In current-project mode, use the configured recent window ending at HEAD.
   In benchmark mode, use the selected dataset's published start and release.
3. Select one first-parent revision at every 14-day boundary and use the exact
   release ref where the profile supplies one.
4. Parse only the selected module in each distinct revision with JDT and calculate the 17 CK/OO
   source metrics.
5. Export the final revision's source metrics as `ck_oo_*`.
6. Calculate WCHU and LDHH from absolute metric deltas between adjacent
   snapshots.
7. Read every intervening in-scope Java change with
   `git log --numstat --find-renames`.
8. Calculate HCM, WHCM, EDHCM, LDHCM, and LGDHCM and export them under the
   five historic AEEEM `Cvs*Entropy` names.

Production source selection excludes test modules and source sets, Eclipse JCL
compiler fixtures, generated sources, examples, benchmarks, and build
infrastructure. JDT parsing is performed in bounded batches so large
repositories do not require every source AST and source string in memory at
once. The same production filter is applied to Git changed-line entropy.

The most recent 26 bi-weekly snapshots are used by default only for the current
project profile. This is configurable with `AEEEM_MAX_SNAPSHOTS`; use `0` for
the complete first-parent history. Benchmark profiles always use their complete
fixed historical window. The parser batch size defaults to 96 and can be
changed with `AEEEM_JDT_BATCH_SIZE` (16 through 512).

## Supplied benchmark profiles

The five attached ARFF files have the same 62-column training schema: 56
non-defect predictors, five defect-history fields, and `class`. They do not
contain class names. Their row counts and the paper's time periods are:

| Profile | Prediction system/release | History period | Paper versions | ARFF rows |
|---|---|---|---:|---:|
| `jdt` | Eclipse JDT Core 3.4 | 2005-01-01 to 2008-06-17 | 91 | 997 |
| `pde` | Eclipse PDE UI 3.4.1 | 2005-01-01 to 2008-09-11 | 97 | 1,497 |
| `eq` | Equinox framework 3.4 | 2005-01-01 to 2008-06-25 | 91 | 324 |
| `ml` | Mylyn 3.1 | 2005-01-17 to 2009-03-17 | 98 | 1,862 |
| `lc` | Apache Lucene 2.4.0 | 2005-01-01 to 2008-10-08 | 99 | 691 |

JDT uses release tag `R3_4`, PDE uses `R3_4_1`, Mylyn uses
`R_3_1_0`, and Lucene uses `releases/lucene/2.4.0`. Equinox falls
back to the last first-parent commit on or before its release date.

The public `eclipse-jdt/eclipse.jdt.core` Git mirror retains migrated history
back to 2001. Its master lineage contains the 2005-01-01 commits
`9964e67e0648dde89190888df23332d4288d27d7`,
`8d011b03381d3efb22f096963964840a728e892c`, and
`637d66e0d1e2e285ddeeaf1de62c4418b17f95fd`. Tag `R3_4` resolves to commit
`8ac82b15173c11f12dcb04b961f0ddaace907a44` (2008-06-13). The JDT profile
therefore has the history and release revision needed for the paper's
2005-01-01 to 2008-06-17 window.

The extractor validates repository coverage. If a modern mirror starts after
the selected profile's history date or lacks its release ref, extraction stops
with a clear error instead of silently calculating a shorter, mostly-zero
history.

Folder scope is applied before parsing and to Git pathspecs. The full set of
production classes inside that folder remains available for NOC, FanIn,
inheritance, and LDHH system entropy; output rows outside the folder are never
created. Identical module Git trees are cached and reused.

Only one AEEEM extraction is admitted at a time. A second simultaneous request
receives HTTP 429 instead of competing for heap space with the active JDT
analysis.

GitHub AEEEM sources use a full, single-branch clone with all historical blobs.
Blobless partial clones are deliberately not used: `git log --numstat` needs
historical file contents, and fetching them only at the final entropy phase can
otherwise fail after all snapshots have already been parsed.

Only classes present in the final revision become rows. A missing class at either
end of a metric interval is equivalent to the paper's `-1` sentinel and does not
contribute.

## WCHU

For a positive absolute metric delta `D(c,m,t)`:

```text
WPCHU(c,m,t) = 1 + 0.01 × D(c,m,t)
WCHU(c,m)    = sum(WPCHU(c,m,t))
```

Zero deltas are not changes and missing values are excluded.

## LDHH

For one metric and interval:

```text
total = sum of positive class deltas
p(c)  = D(c) / total
R     = number of classes with a positive delta
H     = -sum(p(c) × log base R of p(c))
```

`H` is zero when fewer than two classes changed. Every class with a positive
delta receives:

```text
H / (linearDecayFactor × (age + 1))
```

The class-level contributions are summed over history.

## Git change entropy

For each Java file and 14-day period:

```text
changedLines(file) = addedLines + deletedLines
p(file)             = changedLines(file) / totalChangedLines
```

The adaptive entropy logarithm base is the union of files changed in the current
and previous five periods. Renames keep one file identity. File-level history
values are copied to every final class declared in that file.

| Output column | Paper metric | Per-period contribution |
|---|---|---|
| `CvsEntropy` | HCM | `H` |
| `CvsWEntropy` | WHCM | `p(file) × H` |
| `CvsExpEntropy` | EDHCM | `H / exp(phi1 × age)` |
| `CvsLinEntropy` | LDHCM | `H / (phi2 × (age + 1))` |
| `CvsLogEntropy` | LGDHCM | `H / (phi3 × ln(age + 1.01))` |

The papers specify the decay equations but do not provide every numeric factor
needed for a universally reproducible run. Defaults are `phi1 = phi2 = phi3 =
1.0`; all three are configurable.

## Output boundary

The extractor emits:

```text
name + 17 ck_oo + 17 WCHU + 17 LDHH + 5 Cvs = 57 columns
```

It deliberately omits five defect-history predictors and the `class` label.
Those require issue tracker data and known outcomes and must not be replaced
with fabricated zeros.

## Validation

The backend tests include:

- the paper's two-interval WCHU example (`2.50` and `2.15`);
- adaptive entropies `0.722` and `0.918` with linear decay;
- the `60/30/10` changed-lines example for all five Cvs variants;
- Git numstat and rename parsing;
- interface and nested-type static metric isolation;
- an end-to-end two-class Git repository extraction;
- exact 57-column CSV/ARFF schema and absence of labels.
- the five supplied benchmark periods and ARFF row counts;
- JDT's 91-snapshot 2005-to-2008 release window;
- module-only parsing and module-only Git changed-line mining.

Historic AEEEM files were produced from CVS/SVN and a FAMIX toolchain. Modern
Git mirrors can preserve the relevant commits, but transaction grouping, parser
semantics, class inclusion, and unpublished decay factors can still differ.
Formula, scope, and time-window alignment are implemented here; byte-for-byte
equality is not claimed.
