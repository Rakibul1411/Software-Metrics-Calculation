# Metrics and ML

## PROMISE

PROMISE extraction produces the canonical class identifier `name` plus 20
predictor columns such as WMC, DIT, NOC, CBO, RFC, LCOM, CA, CE, NPM, LCOM3,
LOC, DAM, MOA, MFA, CAM, IC, CBM, AMC, MAX_CC, and AVG_CC.

It can analyze an uploaded Java archive or a public GitHub checkout. A labeled
PROMISE CSV/ARFF includes a recognized bug/defect label column.

## AEEEM

AEEEM extraction produces static and history predictors. History-aware values
such as WCHU, LDHH, entropy, change deltas, and bi-weekly snapshots require a
Git repository, so an archive is intentionally rejected for this family.

The bundled AEEEM benchmark files contain labels and predictors but no portable
class identifier. Therefore:

- their own predictions can be evaluated against their actual labels;
- manual versus predefined class-by-class mapping is unavailable;
- the comparison level is `AGGREGATE`.

Historical JDT, PDE, EQ, LC, and ML profiles contain validated repository,
release, history-window, and module defaults.

## Preparation pipeline

The system uses a standard preparation pipeline for every prediction run.

The deterministic sequence is:

1. normalize header aliases and select the metric family;
2. reject missing required columns;
3. coerce predictors to numeric values;
4. impute missing values using source medians;
5. apply log1p only to configured non-negative skewed features;
6. standardize using source statistics;
7. optionally align source covariance to target with shallow CORAL;
8. fit KNN with the user-selected K from 1 to 5;
9. calculate a defect score and thresholded label;
10. rank classes from highest to lowest risk.

Target labels are never used to train or transform the model. A labeled
predefined target returns its labels only for post-prediction evaluation.

## KNN

K is selected manually from 1 to 5 and is not auto-selected. Probabilities are
the proportion of the selected neighbors labeled Buggy.

## Reproducibility

Every run saves:

- model name and selected K;
- distance details and dataset-alignment choice;
- threshold;
- preparation pipeline settings;
- random seed;
- the three dataset IDs;
- prediction CSV paths and result summaries.

Log transformation is always part of preparation. CORAL is applied only when
the user enables dataset alignment.

## Evaluation

On the labeled predefined target:

- confusion matrix;
- precision and recall;
- specificity;
- F1;
- balanced accuracy;
- Matthews correlation coefficient;
- ROC-AUC and PR-AUC;
- optional Recall@20% LOC and AUCEC.

An undefined measure is returned as `value: null` with an explanation rather
than a misleading zero.

## Comparison

When common `name` identifiers exist, the system reports total classes compared,
buggy matches, clean matches, mismatches, and agreement percentage.

Without identifiers it reports manual predicted buggy count, predefined actual
buggy count, and their absolute difference. Metric distributions always include
row counts and per-feature means/standard deviations when schemas match.
