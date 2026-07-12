# System Workflow

This document describes the workflow of the Defect Prediction System:

1. The user selects dataset format: PROMISE or AEEEM.
2. The user uploads a ZIP project or enters a GitHub URL.
3. The Java backend parses the source code using Eclipse JDT AST Parser.
4. The system calculates object-oriented metrics.
5. The system generates a target CSV file containing metrics and class identifiers.
6. The user uploads a labeled historical dataset.
7. The Java backend forwards metrics data to the Python FastAPI service.
8. The Python service applies CORAL domain adaptation and trains a KNN classifier.
9. Bug/clean predictions are mapped back to Java classes and returned to the UI.
