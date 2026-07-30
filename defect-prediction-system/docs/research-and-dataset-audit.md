# Research, PROMISE Dataset, and CORAL Audit

This document records the decisions made from the three supplied papers and the
two supplied PROMISE archives. It separates the historical studies from the
classifiers implemented by this application.

## Supplied archive inventory

`bug-data.zip` contains 41 usable PROMISE CSV files and two macOS resource-fork
entries that are not datasets. The usable releases are:

| Family | Releases |
|---|---:|
| Ant | 5 |
| Camel | 4 |
| Ivy | 3 |
| jEdit | 5 |
| Log4j | 3 |
| Lucene | 3 |
| POI | 4 |
| Synapse | 3 |
| Velocity | 3 |
| Xalan | 4 |
| Xerces | 4 |
| **Total** | **41** |

All 41 usable CSVs share the same schema:

```text
name,wmc,dit,noc,cbo,rfc,lcom,ca,ce,npm,lcom3,loc,dam,moa,mfa,
cam,ic,cbm,amc,max_cc,avg_cc,bug
```

They contain 15,775 class rows in total. `source code(2).zip` contains 38
corresponding source releases. The three Ivy releases occur in the labelled
CSV archive but are not present in the supplied source archive. Consequently,
source-to-labelled-data validation can cover 38 releases, not all 41.

## What the PROMISE paper actually did

The supplied paper *Towards identifying software project clusters with regard
to defect prediction* (Jureczko and Madeyski, 2010) reports that source metrics
were collected using CKJM, with LOC and McCabe cyclomatic complexity added.

Its prediction experiment did not use KNN or SVM and did not use classification
accuracy as the primary outcome. On page 4 it states that:

1. project groups were investigated with hierarchical clustering, k-means, and
   Kohonen neural networks, followed by discriminant analysis;
2. a defect model for each cluster was built with stepwise linear regression;
3. classes were ranked by the regression model's predicted defect count; and
4. efficiency was the number/percentage of classes that had to be inspected to
   find 80% of the known defects.

KNN and linear SVM in this application are therefore application model choices,
not a reproduction of that paper's regression experiment.

## What the unified Java bug-dataset paper did

The supplied paper *A public unified bug dataset for Java and its assessment
regarding metrics and bug prediction* (Ferenc et al., 2020) converted defect
counts to a binary label: zero defects is clean and at least one defect is
buggy.

For within-project prediction, page 34 reports:

- Weka J48 (C4.5 decision tree) with default parameters;
- ten-fold cross-validation;
- no sampling/balancing; and
- F-measure and ROC AUC as the reported predictive measures.

It also evaluates merged-data and cross-system settings using J48 and reports
F-measure/AUC. The application's prediction/evaluation output includes
accuracy for convenience, but F1 and ROC AUC are the closer comparisons to this
paper. J48 is not currently implemented.

## PROMISE metric implementation decisions

The supplied CSV values exposed four inconsistencies in the previous source
implementation:

| Metric | Corrected behaviour |
|---|---|
| `CBO` | Unique union of incoming and outgoing coupled in-project classes |
| `CAM` | Includes the declaring/self type and constructors |
| `LCOM`, `LCOM3` | Uses all class-owned attributes, including static fields |
| `WMC`, `max_cc`, `avg_cc` | Non-constructor methods have McCabe base 1; constructors use dataset-compatible base 0 |

`Ca` and `Ce` remain the incoming and outgoing halves of coupling. The formulas
are implemented in the named calculator classes under
`backend-java/src/main/java/org/metrics/promise/calculator/`.

These corrections make the JDT extractor more consistent with the supplied
dataset's observable conventions. They do not turn source analysis into an
exact CKJM reproduction: the original CKJM analysis used compiled bytecode and
historical dependencies, while this project analyzes source ASTs.

The release-scope audit also found that several supplied archives contain an
outer extraction folder and a second, identically named release root. The
selector now discovers that single nested root before applying Lucene, POI,
Synapse, Log4j, and Velocity scope rules. It also distinguishes a test module
outside a source root from a legitimate historical package named `test` inside
`src/java` or `src/main`.

All 38 supplied source releases produce non-empty output after these changes.
For the release families with an unambiguous source root, the selected row
counts match all four POI CSVs, all three Synapse CSVs, and Velocity 1.5/1.6.
The remaining historic source/CSV pairs expose benchmark-manifest differences:
for example, each Lucene CSV contains a generated
`RemoteSearchable_Stub` that is absent from source, and the Log4j CSVs include
some but not all classes under test-named source packages. Arbitrary class-name
hardcoding is intentionally not used to conceal these differences.

Metric values can still differ even when entity counts match. For example, the
corrected source calculation for Camel 1.0 `RuntimeCamelException` observes 30
incoming project bindings, while the CKJM CSV reports 33. Its CAM (`2/3`),
LCOM3 (`4/3`), WMC (`4`), and constructor complexity (`0`) conventions do
match. This is evidence that formula compatibility and historical binding/
bytecode reproduction are separate requirements.

## Which CORAL is implemented

The supplied CORAL paper/chapter separates two techniques:

- page 6, Algorithm 1: shallow/linear CORAL, a closed-form covariance
  whitening and re-colouring transform;
- page 10 onward: Deep CORAL, which introduces a differentiable CORAL loss in
  a deep neural network.

This project implements only Algorithm 1:

```text
Cs = cov(Xs) + I
Ct = cov(Xt) + I
Xs* = Xs Cs^(-1/2) Ct^(1/2)
```

The implementation is
`ml-service-python/app/services/shallow_coral_service.py`. It has no network,
loss layer, learned representation, back-propagation, or gradient optimisation.
Target labels are not used for alignment. Target labels are used only after
prediction when the user explicitly chooses evaluation mode.

## Interpretation boundary

The application now supports two honest uses:

1. **Prediction:** train on labelled historical releases, optionally align
   source features to an unlabelled target with shallow CORAL, then predict
   class labels.
2. **Evaluation:** use a labelled target and report accuracy, precision, recall,
   F1, ROC AUC, and confusion-matrix counts.

Neither mode should be described as reproducing the Jureczko regression study
or the Ferenc J48 experiment. Reproducing those papers would require separate
stepwise-regression/ranking and J48/ten-fold-CV experiment modules.
