# Change Manifest

This is a cumulative manifest. The AEEEM repository and UI follow-up is
relative to the supplied
`defect-prediction-system-ui-promise-shallow-coral-final.zip`; the remaining
sections retain the history of the earlier parser/metrics revision.

## AEEEM extraction coverage follow-up

- `BiWeeklySnapshotGenerator.java`
  — rejects disconnected or Java-empty migrated benchmark tags and safely
  resolves the release-date commit on the selected main lineage.
- `ProductionSourceSelector.java`
  — excludes test modules before a production source root without discarding a
  legitimate `test` package below `src/main/java` or `src/java`.
- `GitHistoryAnalyzer.java`
  — reports an explicit published-row/entity-coverage warning and a more
  actionable zero-class migration/ref message.
- `scripts/compare_aeeem_metrics.py`
  — compares all shared predictors in an extracted CSV and predefined ARFF.
- `docs/aeeem-extraction-and-eq-consistency-audit.md`
  — records the five-profile count diagnosis and supplied EQ comparison.

## AEEEM repository and UI follow-up

- `backend-java/src/main/java/org/metrics/aeeem/history/AeeemBenchmarkProfile.java`
  — maps every benchmark profile to one verified GitHub repository and rejects
  a mismatched project before cloning. EQ now correctly uses
  `eclipse-equinox/equinox.framework`, not `eclipse-equinox/equinox`.
- `backend-java/src/main/java/org/metrics/aeeem/git/BiWeeklySnapshotGenerator.java`
  — prefers the historical tag, falls back to the final first-parent commit on
  or before the release date when a migrated tag is absent, and explicitly
  reports shortened mirror history.
- `backend-java/src/main/java/org/metrics/aeeem/history/AeeemAnalysisSummary.java`
  and `backend-java/src/main/java/org/metrics/aeeem/git/GitHistoryAnalyzer.java`
  — expose release resolution and compatibility warnings in API provenance.
- `backend-java/src/main/java/org/metrics/controller/MetricsController.java`
  — validates benchmark repository identity before the expensive clone.
- `ml-service-python/app/services/prediction_service.py`
  — gives a short actionable missing-label message and suggests a detected
  `class`, `bug`, or other common label instead of dumping every metric column.
- `ml-service-python/app/services/project_validation_service.py`
  — explains exactly what the one-source validation warning means and how to
  obtain cross-project performance evidence.
- `frontend-angular/src/app/features/metrics-extraction/metrics-extraction.component.*`
  — auto-fills verified AEEEM links, preflights CSV/ARFF label headers,
  auto-detects a shared label, clears stale errors after label edits, and gives
  amber notices explicit headings.
- `frontend-angular/src/app/core/models/metrics-preview.model.ts`
  — models AEEEM release resolution and warning provenance.

## Parser and benchmark handling

- `backend-java/pom.xml` — Java 17/JDT dependency alignment.
- `backend-java/src/main/java/org/metrics/jdt/JavaLanguageConfiguration.java`
- `backend-java/src/main/java/org/metrics/jdt/JavaParserConfigurationResolver.java`
- `backend-java/src/main/java/org/metrics/jdt/JdtProjectEnvironment.java`
- `backend-java/src/main/java/org/metrics/jdt/ResolvedJavaProject.java`
  — shared Eclipse/Maven/Ant language, source-root, and classpath resolution.
- `backend-java/src/main/java/org/metrics/aeeem/git/GitHistoryAnalyzer.java`
- `backend-java/src/main/java/org/metrics/aeeem/history/AeeemBenchmarkProfile.java`
- `backend-java/src/main/java/org/metrics/aeeem/parser/AeeemJavaSourceParser.java`
  — module-aware AEEEM benchmark parsing and top-level benchmark entities.
- `backend-java/src/main/java/org/metrics/promise/analyzer/PromiseInputValidator.java`
- `backend-java/src/main/java/org/metrics/promise/analyzer/ProductionSourceSelector.java`
- `backend-java/src/main/java/org/metrics/promise/analyzer/PromiseProjectAnalyzer.java`
- `backend-java/src/main/java/org/metrics/service/MetricsExtractionService.java`
  — one-release validation, nested release-root discovery, historical source
  scope selection, per-module parser configuration, and safer binding handling.

## PROMISE metric corrections

- `backend-java/src/main/java/org/metrics/promise/calculator/CboPromiseCalculator.java`
  — unique incoming/outgoing coupling union.
- `backend-java/src/main/java/org/metrics/promise/calculator/CamPromiseCalculator.java`
  — self type plus constructors.
- `backend-java/src/main/java/org/metrics/promise/calculator/LcomPromiseCalculator.java`
- `backend-java/src/main/java/org/metrics/promise/calculator/Lcom3PromiseCalculator.java`
  — all class-owned fields, including static fields.
- `backend-java/src/main/java/org/metrics/promise/calculator/CyclomaticComplexityPromiseCalculator.java`
  — McCabe base 1 for ordinary/interface methods and dataset-compatible base 0
  for constructors.
- `backend-java/src/main/java/org/metrics/promise/calculator/MfaPromiseCalculator.java`
  — safer hierarchy/binding handling retained from the parser revision.
- `backend-java/src/main/resources/metric-profiles/promise-metrics.json`
  — corrected 20-predictor profile description/version.

## Tests

- `backend-java/src/test/java/org/metrics/jdt/JavaParserConfigurationResolverTest.java`
- `backend-java/src/test/java/org/metrics/promise/analyzer/PromiseProjectAnalyzerTest.java`
- `backend-java/src/test/java/org/metrics/aeeem/history/AeeemAnalysisOptionsTest.java`
- `backend-java/src/test/java/org/metrics/aeeem/parser/AeeemJavaSourceParserTest.java`
  — metadata precedence, double-nested archive layout, source scopes, metric
  formulas, AEEEM profiles, and entity-selection tests.
- `ml-service-python/tests/test_shallow_coral_service.py`
  — Algorithm 1 matrix equivalence and explicit non-deep guards.

## Shallow CORAL

- Removed `ml-service-python/app/services/coral_service.py`.
- Added `ml-service-python/app/services/shallow_coral_service.py` with the
  closed-form covariance whitening/re-colouring transform.
- Updated `ml-service-python/app/services/prediction_service.py` and
  `ml-service-python/app/services/project_validation_service.py` to use
  `ShallowCoralService` and report `shallow/linear CORAL`.

## UI

- Rebuilt:
  - `frontend-angular/src/app/app.component.ts`
  - `frontend-angular/src/app/app.component.css`
  - `frontend-angular/src/styles.css`
  - `frontend-angular/src/app/features/metrics-extraction/metrics-extraction.component.ts`
  - `frontend-angular/src/app/features/metrics-extraction/metrics-extraction.component.html`
  - `frontend-angular/src/app/features/metrics-extraction/metrics-extraction.component.css`
- Removed the orphan
  `frontend-angular/src/app/features/prediction/prediction.component.ts`.
- The UI now has three stages only: analyze, review, and predict/evaluate.
  Duplicate client-side model testing and misleading AEEEM ZIP/history controls
  were removed. Shallow CORAL is explicitly labelled “Not Deep CORAL”.

## Structure and documentation

- Added Python package markers under `ml-service-python/app/`.
- Added `scripts/run-dev.sh` and `scripts/run-tests.sh`.
- Removed unused Python schema/helper/validation files:
  - `ml-service-python/app/schemas/prediction_schema.py`
  - `ml-service-python/app/utils/file_reader.py`
  - `ml-service-python/app/utils/response_builder.py`
  - `ml-service-python/app/validation/feature_schema_validator.py`
- Removed obsolete modernization hooks and `terminal command.txt`.
- Updated `.gitignore`, component READMEs, API/workflow/dataset documentation.
- Added:
  - `docs/jdt-parser-compatibility.md`
  - `docs/research-and-dataset-audit.md`
  - `docs/change-manifest.md`

## Verification

- Java 17/ECJ clean source compilation: passed (one existing Commons Compress
  deprecation warning).
- Java focused tests: **47/47 passed**.
- Python tests: **38/38 passed**.
- Angular 19 production build: passed; initial bundle 284.61 kB raw,
  approximately 78.02 kB transferred.
- Live public-repository resolver check: **5/5 profiles passed**. Snapshot plans
  were JDT 91 (`R3_4`), PDE 97 (release-date fallback), EQ 91 (`R3_4` in
  `equinox.framework`), ML 98 (`R_3_1_0`, available history from 2005-06-17),
  and LC 99 (release-date fallback).
- Supplied PROMISE source releases: **38/38 produced non-empty output**.
- Supplied labelled PROMISE files: **41 usable CSVs, 15,775 rows**; the three
  Ivy labelled releases have no source counterpart in the supplied source ZIP.

The public migrated repositories and release plans were checked, but the five
original SCM exports and their full historical dependency classpaths are not in
the attachments. Exact published-row reproduction is therefore not claimed.
