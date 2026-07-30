# Backend Java — Metrics Calculator & Prediction API

Spring Boot application providing REST APIs to extract object-oriented metrics from Java source code and run defect predictions.

The backend requires JDK 17. Eclipse JDT 3.37 runs on that JDK while target
projects may still be parsed as Java 1.3, 1.4, 1.5, or another metadata-selected
language level.

## Structure

```
backend-java/
├── src/main/java/org/metrics/
│   ├── MetricsCalculatorMain.java          ← Spring Boot entry point
│   ├── controller/                         ← REST API layer
│   │   ├── MetricsController.java
│   │   └── PredictionController.java
│   ├── service/                            ← Business logic
│   │   ├── MetricsExtractionService.java
│   │   ├── PredictionService.java
│   │   ├── FileStorageService.java
│   │   ├── GitHubCloneService.java
│   │   └── ZipExtractionService.java
│   ├── common/
│   │   ├── dto/                            ← Prediction options
│   │   ├── enums/                          ← File format and classifier
│   │   ├── exception/                      ← GlobalExceptionHandler
│   │   └── export/                         ← Shared ARFF exporter
│   ├── promise/                            ← PROMISE metrics domain
│   ├── jdt/                                ← shared language/config resolver
│   └── aeeem/                              ← AEEEM metrics domain
│       ├── calculator/                     ← 17 self-contained JDT source metrics
│       ├── git/                            ← snapshots, numstat, and rename tracking
│       ├── history/                        ← WCHU, LDHH, and Cvs formulas
│       ├── model/AeeemMetricResult.java
│       ├── parser/AeeemJavaSourceParser.java
│       └── export/                         ← authoritative 56-feature schema
├── src/main/resources/
│   ├── application.properties
│   └── metric-profiles/
│       ├── promise-metrics.json
│       └── aeeem-static-metrics.json
└── storage/                                ← Runtime-created uploads/extractions
```

## API Endpoints

| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/api/metrics/extract` | Extract metrics from ZIP/GitHub input |
| `GET` | `/api/metrics/download/{datasetId}/{format}` | Download CSV or ARFF |
| `POST` | `/api/prediction/run` | Run defect prediction via Python service |

## Build & Run

Requirements: JDK 17 and Maven 3.8 or later.

```bash
mvn clean package
java -jar target/metrics-calculator-1.0.0.jar

# Or with Maven dev server:
mvn spring-boot:run
```

Server listens on port `8080` by default.

## Historical Java source resolution

Each source module is resolved independently in this order:

1. nearest `.settings/org.eclipse.jdt.core.prefs`;
2. Maven compiler properties/plugin configuration;
3. Ant `build.xml`, `common-build.xml`, and property files;
4. AEEEM benchmark fallback, when a benchmark profile is selected;
5. oldest source level with the fewest JDT syntax errors.

Only JARs contained in the target project are added to its classpath. The
Spring/JDT backend classpath is not leaked into historical binding resolution.
PROMISE and AEEEM parsing both use bounded groups; set
`PROMISE_JDT_BATCH_SIZE` or `AEEEM_JDT_BATCH_SIZE` from 16 through 512.

PROMISE accepts one project-release archive per extraction. Uploading a
collection that contains multiple nested release archives is rejected.

For large AEEEM repositories:

```bash
mvn spring-boot:run -Dspring-boot.run.jvmArguments="-Xmx2g"
```

## AEEEM history configuration

The extractor preserves the branch and module from GitHub folder URLs and
selects one first-parent source snapshot every 14 days. Current-project mode
uses the latest 26 snapshots by default. JDT, PDE, EQ, ML, and LC benchmark
profiles instead use their complete published 2005–2009 prediction windows.
Unchanged module trees are reused, and sibling modules are not parsed. WCHU uses the paper's fixed
`alpha = 0.01`. The papers define the decay equations but do not publish every
numeric decay-factor choice, so this implementation uses explicit,
configurable defaults:

| Profile | History interval | Final release | Scheduled snapshots |
|---|---|---|---:|
| JDT | 2005-01-01 → 2008-06-17 | `R3_4` | 91 |
| EQ | 2005-01-01 → 2008-06-25 | `R3_4` / `bundles/org.eclipse.osgi` | 91 |
| PDE | 2005-01-01 → 2008-09-11 | `R3_4_1` | 97 |
| LC | 2005-01-01 → 2008-10-08 | `releases/lucene/2.4.0` | 99 |
| ML | 2005-01-17 → 2009-03-17 | `R_3_1_0` | 98 |

Benchmark repository mapping is fixed and validated before cloning:

| Profile | Repository |
|---|---|
| JDT | `eclipse-jdt/eclipse.jdt.core` |
| PDE | `eclipse-pde/eclipse.pde` |
| EQ | `eclipse-equinox/equinox.framework` |
| ML | `eclipse-mylyn/org.eclipse.mylyn` |
| LC | `apache/lucene` |

Exact release refs are preferred. When a migrated mirror does not expose the
legacy tag, the final first-parent commit on or before the fixed release date
is used and the response records a compatibility warning. If a mirror begins
after the requested benchmark start, every available commit is used and the
response explicitly marks the shortened historical coverage.

| Environment variable | Default | Purpose |
|---|---:|---|
| `AEEEM_EXP_DECAY_FACTOR` | `1.0` | Exponential Cvs history decay |
| `AEEEM_LINEAR_DECAY_FACTOR` | `1.0` | Linear Cvs and LDHH decay |
| `AEEEM_LOG_DECAY_FACTOR` | `1.0` | Logarithmic Cvs history decay |
| `AEEEM_RECENT_PERIODS` | `6` | Recent modified-file window used for adaptive entropy |
| `AEEEM_MAX_SNAPSHOTS` | `26` | Bi-weekly history window; `0` means complete history |
| `AEEEM_JDT_BATCH_SIZE` | `96` | Sources per bounded parser batch (`16`–`512`) |

No external metric tool is invoked. JDT analyzes source snapshots, and the Git
CLI supplies commits, renames, and `added + deleted` line counts. Test modules,
JCL/compiler fixtures, generated code, examples, and build infrastructure are
excluded from both source metrics and Git entropy. AEEEM repository clones
download full default-branch history and blobs before analysis so the final
numstat phase cannot trigger a late promisor-remote download.

CSV and ARFF values are rounded only when exported, to at most six fractional
digits (with trailing zeros removed), matching the practical precision of the
provided AEEEM files. Calculations retain full `double` precision internally,
and non-finite values fail validation instead of being written as invalid data.
