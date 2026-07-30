# Defect Prediction Frontend

Angular 19 UI for the complete extraction-to-prediction workflow.

## Features

- PROMISE or AEEEM format selection
- PROMISE Java project ZIP or public GitHub input
- AEEEM GitHub/history profile input
- extraction progress, schema review, CSV/ARFF preview and download
- unlabelled-target prediction or labelled-target evaluation
- KNN or linear-SVM selection with source-only model tuning
- an explicit shallow-CORAL toggle labelled “Not Deep CORAL”
- responsive layout and keyboard-accessible controls

The interface is deliberately organised as three stages:

1. analyze a Java project;
2. review the generated dataset; and
3. predict or evaluate.

## Run locally

Start `backend-java` on port 8080, then:

```bash
npm install
npm start
```

Open `http://localhost:4200`. API paths are configured in the core service
classes under `src/app/core/services/`.

## Verify

```bash
npm run build
```
