# AEEEM extraction and EQ consistency audit

## Findings from the supplied runs

The number shown by the UI is a count of unique, parsed top-level production
classes at the selected final Git tree. It is not a count of every `.java`
file in the repository. Test sources, examples, generated sources, sibling
modules, package/module descriptors, nested types, duplicate class names and
unparseable/non-production files are intentionally not output rows.

| Profile | Supplied run | Published rows | Assessment |
|---|---:|---:|---|
| JDT | reported working | 997 | The exact `R3_4` lineage and module are available. |
| PDE | 1,497 | 1,497 | Row count matches the predefined dataset. This does not by itself prove value equality. |
| EQ | 341 | 324 | The migrated Git tree has 17 additional selected entities. No one-to-one row mapping exists because the predefined ARFF has no class-name column. |
| ML | 0 / error | 1,862 | The accepted legacy tag can be disconnected or contain no usable Java source in the migrated aggregate repository. |
| LC | 616 | 691 | The current Git mirror/release-date tree lacks or selects 75 fewer entities than the historical AEEEM entity set. |

The original AEEEM data was produced from historical CVS/SVN snapshots and a
different metric/entity extraction toolchain. The current GitHub mirrors have
migrations, rewritten layouts, generated sources and release-tag differences.
Consequently, exact historical entity counts cannot safely be forced by
including arbitrary files or hard-coding class names.

## Code corrections

1. A benchmark release tag is accepted only when it belongs to the selected
   main lineage and contains Java source in the configured module.
2. A disconnected, empty or module-incompatible legacy tag falls back to the
   final first-parent commit on or before the profile release date.
3. Test modules such as `src/test/java` remain excluded, while a legitimate
   package named `test` below a production source root, such as
   `src/main/java/org/example/test`, is retained.
4. When extracted entity count differs from the predefined row count, the UI
   receives an explicit provenance warning. The system no longer silently
   implies exact reproduction.
5. The zero-class error now explains the migration/ref condition and the
   verified-link/release-date fallback.

## EQ extracted CSV versus predefined ARFF

Files compared:

- extracted: `eq-1.csv`
- predefined: `EQ(2).arff`

Results:

- extracted rows: **341**
- predefined rows: **324**
- extracted columns: **57** (`name` plus 56 predictors)
- predefined columns: **62** (56 predictors, five defect-history fields and
  `class`)
- predictor names shared: **56/56**
- predictor means within 1%: **1/56**
- predictor means within 10%: **9/56**

Therefore the schema is compatible for model input after excluding predefined
label/defect-history fields, but the values are **not an exact or generally
close reproduction**.

Important examples:

| Metric | Extracted mean | Predefined mean | Observation |
|---|---:|---:|---|
| `ck_oo_numberOfAttributes` | 6.7155 | 6.7037 | close |
| `ck_oo_numberOfMethods` | 10.4076 | 9.8704 | moderately close |
| `ck_oo_fanIn` | 7.0704 | 2.9537 | materially different |
| `ck_oo_numberOfMethodsInherited` | 1.8270 | 14.7222 | materially different |
| `LDHH_fanIn` | 0.0490 | 0.00156 | materially different |
| `CvsLogEntropy` | 1.1176 | 0.1494 | materially different |

The strongest discrepancies are in LDHH, inheritance/binding-dependent
metrics and change-history entropy. These depend on exact entity identity,
complete historical SCM coverage, dependency resolution and the original
toolchain's decay/history definitions. Changing numeric formatting cannot
remove these differences.

The predefined ARFF intentionally has no class-name identifier, so row-by-row
pairing with the extracted CSV is impossible. Distribution-level comparison is
the defensible comparison available for these two supplied files.

Use `scripts/compare_aeeem_metrics.py` to reproduce the complete 56-column
comparison CSV.

## Interpretation boundary

The generated datasets are suitable as internally consistent, schema-compatible
targets for this application's extraction and prediction pipeline. They must
not be described as exact reconstructions of the published AEEEM datasets.
Exact reconstruction would require the original SCM snapshots, original
project/module manifests, generated code and libraries, and the original
FAMIX/metric implementation.
