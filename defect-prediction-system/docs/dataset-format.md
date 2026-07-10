# Dataset Format Guide

## PROMISE Dataset Format

Extracted using Eclipse JDT AST with semantic binding resolution. Generated target datasets contain the class identifier plus 20 object-oriented metrics. They do not include a `bug` label.

### CSV Schema
| Column | Type | Description |
|---|---|---|
| `name` | string | Fully qualified Java class name |
| `wmc` | int | Weighted Methods per Class |
| `dit` | int | Depth of Inheritance Tree |
| `noc` | int | Number of Children |
| `cbo` | int | Coupling Between Objects |
| `rfc` | int | Response For a Class |
| `lcom` | int | Lack of Cohesion of Methods (C&K) |
| `ca` | int | Afferent Coupling |
| `ce` | int | Efferent Coupling |
| `npm` | int | Number of Public Methods |
| `lcom3` | float | LCOM variant 3 (Henderson-Sellers) |
| `loc` | int | Lines of Code |
| `dam` | float | Data Access Metric |
| `moa` | int | Measure of Aggregation |
| `mfa` | float | Measure of Functional Abstraction |
| `cam` | float | Cohesion Among Methods |
| `ic` | int | Inheritance Coupling |
| `cbm` | int | Coupling Between Methods |
| `amc` | float | Average Method Complexity |
| `max_cc` | int | Maximum Cyclomatic Complexity |
| `avg_cc` | float | Average Cyclomatic Complexity |

Labelled PROMISE training datasets may contain a separate `bug` column, but the source-code metrics extractor never adds it to generated target CSV files.

---

## AEEEM Dataset Format

Static analysis metrics from three tools (ck_oo, LDHH, WCHU) plus five entropy measures (Cvs*) and bug count attributes.

### Label Column
| Column | Values | Description |
|---|---|---|
| `class` | `buggy` / `clean` | Defect class label |

### Full Feature List (61 attributes)
See [aeeem-static-metrics.json](../backend-java/src/main/resources/metric-profiles/aeeem-static-metrics.json) for the complete ordered list.

Tool Groups:
- **ck_oo_*** — CK metrics from ck_oo tool (DIT, WMC, CBO, RFC, LOC, etc.)
- **LDHH_*** — CK metrics from LDHH tool
- **WCHU_*** — CK metrics from WCHU tool
- **Cvs*** — Entropy-based change history metrics (CvsWEntropy, CvsEntropy, CvsLogEntropy, CvsLinEntropy, CvsExpEntropy)
- **numberOfXxxBugsFoundUntil:** — Historical bug count attributes
