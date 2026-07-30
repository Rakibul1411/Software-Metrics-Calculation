# System Workflow

This document describes the workflow of the Defect Prediction System:

1. The user selects dataset format: PROMISE or AEEEM.
2. The user uploads a ZIP project or enters a GitHub URL.
3. For AEEEM, the user chooses current-project history or an EQ/JDT/LC/ML/PDE
   benchmark profile. A GitHub folder URL supplies the branch and module scope.
4. The backend selects 14-day first-parent snapshots in that window, reuses
   unchanged module trees, and excludes sibling modules, tests, JCL fixtures,
   generated code, and build sources.
5. Eclipse JDT parses production source in bounded batches; Git supplies raw
   commit, rename, and changed-line history.
6. The system calculates 17 static metrics, 17 WCHU metrics, 17 LDHH metrics,
   and five `Cvs*` entropy metrics.
7. The system generates label-free CSV and ARFF targets containing 57 columns:
   `name` plus 56 predictors.
8. The user uploads a labeled historical dataset.
9. The Java backend forwards metrics data to the Python FastAPI service.
10. The Python service optionally applies closed-form shallow/linear CORAL
    (covariance whitening and re-colouring) and trains KNN or linear SVM.
11. Bug/clean predictions are mapped back to Java classes and returned to the UI.

No neural network, CORAL loss, gradient optimisation, or Deep CORAL layer is
present in this workflow.
