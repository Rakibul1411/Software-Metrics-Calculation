# Prompt: Software Defect Prediction System

## Project Title

**Static Java Source Code Defect Prediction Using Eclipse JDT AST Parser, CORAL Transfer Learning, and KNN Classification**

---

## Objective

Develop a full-stack software defect prediction system that predicts defect-prone Java classes in a new software project by using static source-code metrics and labelled historical defect datasets.

The system must support:

- Java source-code metrics extraction.
- PROMISE-compatible metrics generation.
- AEEEM-compatible static metric subset generation.
- Transfer learning/domain adaptation using CORAL.
- Classification using K-Nearest Neighbors (KNN).
- Prediction output mapped back to Java class names or file paths.
- Angular frontend, Java Spring Boot backend, and Python FastAPI ML service.

The system should be understood as a **defect-proneness prediction and testing-prioritization tool**, not an exact bug detector. It predicts which Java classes are more likely to be buggy based on historical patterns.

---

## Core Concept

The system has two dataset types:

### 1. Source Dataset

The source dataset is a labelled historical defect dataset, such as PROMISE or AEEEM.

It contains:

```text
metric columns + label column
```

The label column may be named:

```text
bug
class
label
```

The label indicates whether a class/module is:

```text
buggy / clean
```

or:

```text
1 / 0
```

### 2. Target Dataset

The target dataset is generated from the user's new Java source code.

It contains:

```text
identifier column + metric columns
```

The identifier column may be:

```text
class_name
file_path
name
```

The target dataset does not contain a true defect label, because the new project's actual buggy/clean status is unknown.

Therefore:

```text
Source dataset = labelled training data
Target dataset = unlabelled prediction data
```

---

## Complete System Workflow

1. The user opens the Angular frontend.
2. The user selects the dataset format: `PROMISE` or `AEEEM`.
3. The user provides Java source code by uploading a ZIP file or entering a GitHub repository URL.
4. Angular calls the Java Spring Boot backend.
5. The Java backend parses the Java files using Eclipse JDT AST Parser.
6. The system extracts the required static metrics for the selected dataset format.
7. The system generates a target metrics CSV file containing `class_name` or `file_path` plus metric columns.
8. Angular displays a preview of the extracted metrics CSV file.
9. The user uploads one or multiple labelled source datasets.
10. Angular calls the Java backend prediction API.
11. The Java backend sends the generated target CSV and labelled source datasets to the Python FastAPI model service.
12. The Python service validates feature compatibility between source and target datasets.
13. The Python service preprocesses the data, including numeric conversion, missing value handling, and scaling.
14. The Python service applies CORAL using source metrics and target metrics.
15. The Python service trains KNN using adapted source metrics and source labels.
16. The Python service predicts buggy or clean labels for the target classes.
17. The Python service returns the prediction results to the Java backend.
18. The Java backend returns the final prediction result to the Angular frontend.
19. Angular displays the result as `class_name/file_path`, `predicted_label`, and optional `risk_score`.

---

## High-Level Architecture

```text
Angular Frontend
      ↓
Java Spring Boot Backend
      ↓
Eclipse JDT AST Parser
      ↓
Generated Target Metrics CSV
      ↓
Python FastAPI ML Service
      ↓
Preprocessing + CORAL + KNN
      ↓
Prediction JSON
      ↓
Java Spring Boot Backend
      ↓
Angular Result Table
```

Angular should call only the Java backend. The Java backend should internally call the Python FastAPI service.

---

## Main Modules

### Module 1: Metrics Extraction Module

The metrics extraction module is implemented in Java.

Responsibilities:

- Receive Java source code from Angular.
- Support GitHub repository URL.
- Support uploaded ZIP file.
- Extract Java files.
- Parse Java files using Eclipse JDT AST Parser.
- Calculate static object-oriented metrics.
- Generate PROMISE-compatible or AEEEM static-subset-compatible target CSV.
- Save generated CSV on the server.
- Return preview data and `targetDatasetId` to Angular.

The generated CSV must contain:

```text
class_name, file_path, metric_1, metric_2, metric_3, ...
```

The identifier columns are used only for result mapping, not for model training.

---

### Module 2: Prediction Module

The prediction module uses both Java and Python.

Java backend responsibilities:

- Receive `targetDatasetId`.
- Receive one or multiple labelled source datasets.
- Receive settings such as `labelColumn`, `idColumn`, `kValue`, and `useCoral`.
- Load the generated target CSV.
- Send target CSV and source datasets to Python FastAPI.
- Receive prediction result from Python.
- Return result to Angular.

Python FastAPI responsibilities:

- Read source and target datasets.
- Combine multiple source datasets.
- Separate source features and labels.
- Separate target identifiers and target features.
- Validate feature compatibility.
- Apply preprocessing.
- Apply CORAL.
- Train KNN.
- Predict target labels.
- Return JSON prediction results.

---

## Important Machine Learning Rules

### Rule 1: Do not use identifier columns as ML features

Columns like these must not be used for CORAL or KNN:

```text
class_name
file_path
name
```

They must be stored separately and attached back after prediction.

---

### Rule 2: Only the source dataset has labels

The source dataset contains:

```text
metrics + label
```

The target dataset contains:

```text
identifier + metrics
```

The target dataset does not need a label for prediction.

---

### Rule 3: Labels are required only for evaluation

For a new unlabelled project, the system can only provide predictions. It cannot calculate accuracy, precision, recall, F1-score, MCC, or AUC because true labels are unavailable.

To evaluate the model, use labelled benchmark datasets. In evaluation mode, hide the target labels during prediction and compare predictions with actual labels after prediction.

---

### Rule 4: Source and target feature columns must match

Before CORAL and KNN:

```text
X_source columns == X_target columns
```

The columns must have:

- Same feature names.
- Same order.
- Compatible numeric data types.
- Same preprocessing steps.

If feature columns are mismatched, the system should show an error or use a validated common static metric subset.

---

### Rule 5: AEEEM full dataset may contain non-static metrics

AEEEM may include process/history metrics such as:

```text
CvsEntropy
CvsLogEntropy
numberOfBugsFoundUntil
numberOfCriticalBugsFoundUntil
numberOfMajorBugsFoundUntil
```

These cannot be extracted from Java source code using Eclipse JDT AST Parser alone.

Therefore, if AEEEM is selected, use only the AEEEM static metric subset that can be extracted from Java source code.

---

## CORAL + KNN Workflow

1. Read labelled source dataset.
2. Read unlabelled generated target dataset.
3. Separate source features:

```text
X_source = source metric columns
y_source = source label column
```

4. Separate target features:

```text
target_ids = target class_name/file_path
X_target = target metric columns
```

5. Validate feature compatibility.
6. Convert all metric columns to numeric.
7. Handle missing values.
8. Apply scaling/normalization.
9. Apply CORAL using:

```text
X_source + X_target
```

10. Train KNN using:

```text
adapted X_source + y_source
```

11. Predict:

```text
X_target → predicted_label
```

12. Attach predictions back to identifiers:

```text
class_name/file_path + predicted_label + risk_score
```

---

## Final Prediction Output

The final output should be JSON and CSV-compatible.

Example:

```json
{
  "status": "success",
  "message": "Prediction completed successfully",
  "results": [
    {
      "class_name": "com.project.PaymentService",
      "file_path": "src/main/java/com/project/PaymentService.java",
      "predicted_label": "buggy",
      "risk_score": 0.82
    },
    {
      "class_name": "com.project.LoginController",
      "file_path": "src/main/java/com/project/LoginController.java",
      "predicted_label": "clean",
      "risk_score": 0.21
    }
  ]
}
```

Frontend result table:

```text
Class Name                         File Path                                      Prediction   Risk Score
com.project.PaymentService          src/main/java/com/project/PaymentService.java  buggy        0.82
com.project.LoginController         src/main/java/com/project/LoginController.java clean        0.21
```

---

## Recommended API Flow

### API 1: Extract Metrics

```http
POST /api/metrics/extract
```

Called by:

```text
Angular → Java Spring Boot
```

Request type:

```text
multipart/form-data
```

Request fields:

```text
datasetType = PROMISE / AEEEM
sourceType = GITHUB / ZIP
githubUrl = optional
sourceZip = optional
```

Response:

```json
{
  "status": "success",
  "message": "Metrics extracted successfully",
  "targetDatasetId": "target_001",
  "fileName": "target_metrics.csv",
  "columns": ["class_name", "file_path", "wmc", "dit", "noc", "cbo", "rfc", "lcom", "loc"],
  "preview": [
    {
      "class_name": "com.project.PaymentService",
      "file_path": "src/main/java/com/project/PaymentService.java",
      "wmc": 35,
      "dit": 2,
      "noc": 0,
      "cbo": 18,
      "rfc": 70,
      "lcom": 45,
      "loc": 420
    }
  ],
  "downloadUrl": "/api/metrics/download/target_001"
}
```

---

### API 2: Download Generated Metrics CSV

```http
GET /api/metrics/download/{targetDatasetId}
```

Called by:

```text
Angular → Java Spring Boot
```

Purpose:

- Download `target_metrics.csv`.

---

### API 3: Run Prediction

```http
POST /api/prediction/run
```

Called by:

```text
Angular → Java Spring Boot
```

Request type:

```text
multipart/form-data
```

Request fields:

```text
targetDatasetId = generated target dataset ID
datasetType = PROMISE / AEEEM
sourceFiles = one or multiple labelled source dataset files
labelColumn = bug / class / label
idColumn = class_name / file_path / name
knnValue = 5
coralOption = true / false
```

Java should internally call Python.

---

### API 4: Python Prediction API

```http
POST /ml/predict
```

Called by:

```text
Java Spring Boot → Python FastAPI
```

Request type:

```text
multipart/form-data
```

Request fields:

```text
target_file
source_files
dataset_type
label_column
id_column
k_value
use_coral
```

Response:

```json
{
  "status": "success",
  "message": "Prediction completed successfully",
  "results": [
    {
      "class_name": "com.project.PaymentService",
      "file_path": "src/main/java/com/project/PaymentService.java",
      "predicted_label": "buggy",
      "risk_score": 0.82
    }
  ]
}
```

---

## Recommended Folder Structure

```text
defect-prediction-system/
│
├── frontend-angular/
│   ├── src/
│   │   ├── app/
│   │   │   ├── core/
│   │   │   │   ├── services/
│   │   │   │   │   ├── metrics-api.service.ts
│   │   │   │   │   └── prediction-api.service.ts
│   │   │   │   └── models/
│   │   │   │       ├── metrics-preview.model.ts
│   │   │   │       └── prediction-result.model.ts
│   │   │   │
│   │   │   ├── features/
│   │   │   │   ├── metrics-extraction/
│   │   │   │   │   ├── metrics-extraction.component.ts
│   │   │   │   │   ├── metrics-extraction.component.html
│   │   │   │   │   └── metrics-extraction.component.css
│   │   │   │   │
│   │   │   │   └── prediction/
│   │   │   │       ├── prediction.component.ts
│   │   │   │       ├── prediction.component.html
│   │   │   │       └── prediction.component.css
│   │   │   │
│   │   │   ├── shared/
│   │   │   │   ├── components/
│   │   │   │   │   ├── file-upload/
│   │   │   │   │   └── data-table/
│   │   │   │   └── utils/
│   │   │   │
│   │   │   └── app.module.ts
│   │   │
│   │   └── environments/
│   │       └── environment.ts
│   │
│   ├── angular.json
│   ├── package.json
│   └── README.md
│
├── backend-java/
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/
│   │   │   │   └── org/
│   │   │   │       └── metrics/
│   │   │   │           ├── MetricsCalculatorMain.java
│   │   │   │           ├── controller/
│   │   │   │           │   ├── MetricsController.java
│   │   │   │           │   └── PredictionController.java
│   │   │   │           ├── service/
│   │   │   │           │   ├── MetricsExtractionService.java
│   │   │   │           │   ├── PredictionService.java
│   │   │   │           │   ├── FileStorageService.java
│   │   │   │           │   ├── GitHubCloneService.java
│   │   │   │           │   └── ZipExtractionService.java
│   │   │   │           ├── client/
│   │   │   │           │   └── PythonPredictionClient.java
│   │   │   │           ├── common/
│   │   │   │           │   ├── enums/
│   │   │   │           │   │   └── DatasetType.java
│   │   │   │           │   ├── dto/
│   │   │   │           │   │   ├── MetricsExtractionResponse.java
│   │   │   │           │   │   ├── PredictionRequest.java
│   │   │   │           │   │   └── PredictionResponse.java
│   │   │   │           │   ├── exception/
│   │   │   │           │   │   └── GlobalExceptionHandler.java
│   │   │   │           │   ├── validation/
│   │   │   │           │   │   └── FeatureSchemaValidator.java
│   │   │   │           │   └── csv/
│   │   │   │           │       └── CsvWriterService.java
│   │   │   │           ├── promise/
│   │   │   │           │   ├── parser/
│   │   │   │           │   │   └── PromiseJavaSourceParser.java
│   │   │   │           │   ├── calculator/
│   │   │   │           │   │   ├── PromiseMetricsCalculator.java
│   │   │   │           │   │   ├── WmcCalculator.java
│   │   │   │           │   │   ├── DitCalculator.java
│   │   │   │           │   │   ├── NocCalculator.java
│   │   │   │           │   │   ├── CboCalculator.java
│   │   │   │           │   │   ├── RfcCalculator.java
│   │   │   │           │   │   ├── LcomCalculator.java
│   │   │   │           │   │   └── LocCalculator.java
│   │   │   │           │   ├── model/
│   │   │   │           │   │   └── PromiseMetricResult.java
│   │   │   │           │   └── export/
│   │   │   │           │       └── PromiseCsvExporter.java
│   │   │   │           └── aeeem/
│   │   │   │               ├── parser/
│   │   │   │               │   └── AeeemJavaSourceParser.java
│   │   │   │               ├── calculator/
│   │   │   │               │   └── AeeemStaticMetricsCalculator.java
│   │   │   │               ├── model/
│   │   │   │               │   └── AeeemMetricResult.java
│   │   │   │               └── export/
│   │   │   │                   └── AeeemCsvExporter.java
│   │   │   │
│   │   │   └── resources/
│   │   │       ├── application.properties
│   │   │       └── metric-profiles/
│   │   │           ├── promise-metrics.json
│   │   │           └── aeeem-static-metrics.json
│   │   │
│   │   └── test/
│   │       └── java/
│   │
│   ├── storage/
│   │   ├── uploads/
│   │   ├── extracted-projects/
│   │   ├── generated-datasets/
│   │   └── prediction-results/
│   │
│   ├── pom.xml
│   ├── .gitignore
│   └── README.md
│
├── ml-service-python/
│   ├── app/
│   │   ├── main.py
│   │   ├── api/
│   │   │   └── prediction_routes.py
│   │   ├── core/
│   │   │   └── config.py
│   │   ├── services/
│   │   │   ├── prediction_service.py
│   │   │   ├── preprocessing_service.py
│   │   │   ├── coral_service.py
│   │   │   └── knn_service.py
│   │   ├── validation/
│   │   │   └── feature_schema_validator.py
│   │   ├── schemas/
│   │   │   └── prediction_schema.py
│   │   └── utils/
│   │       ├── file_reader.py
│   │       └── response_builder.py
│   │
│   ├── temp/
│   ├── requirements.txt
│   └── README.md
│
├── docs/
│   ├── system-workflow.md
│   ├── api-documentation.md
│   └── dataset-format.md
│
├── .gitignore
└── README.md
```

---

## Implementation Requirements

### Java Backend

Use Spring Boot.

Responsibilities:

- Provide REST APIs.
- Handle file upload.
- Handle ZIP extraction.
- Handle GitHub cloning.
- Run Eclipse JDT AST Parser.
- Generate metrics CSV.
- Store generated target dataset.
- Call Python FastAPI prediction service.
- Return prediction results to Angular.

Recommended Java server port:

```text
http://localhost:8080
```

---

### Python ML Service

Use FastAPI.

Responsibilities:

- Provide `/ml/predict`.
- Receive target dataset and one or multiple source datasets.
- Validate feature compatibility.
- Combine multiple source datasets.
- Preprocess metric columns.
- Apply CORAL.
- Train KNN.
- Return prediction JSON.

Recommended Python server port:

```text
http://localhost:8000
```

---

### Angular Frontend

Responsibilities:

- Dataset type selection: PROMISE/AEEEM.
- Source code input: GitHub URL or ZIP upload.
- Call `/api/metrics/extract`.
- Show generated target metrics preview.
- Upload one or multiple labelled source datasets.
- Call `/api/prediction/run`.
- Display final prediction result table.

Recommended Angular server port:

```text
http://localhost:4200
```

---

## Runtime Commands

### Run Angular

```bash
cd frontend-angular
npm install
ng serve
```

### Run Java Backend

```bash
cd backend-java
mvn spring-boot:run
```

### Run Python ML Service

```bash
cd ml-service-python
pip install -r requirements.txt
uvicorn app.main:app --reload --port 8000
```

---

## Git Ignore Requirements

Use this root `.gitignore`:

```gitignore
# Java
target/
*.class

# Angular
node_modules/
dist/
.angular/

# Python
__pycache__/
*.pyc
.venv/
venv/

# Runtime storage
backend-java/storage/uploads/
backend-java/storage/extracted-projects/
backend-java/storage/generated-datasets/
backend-java/storage/prediction-results/
ml-service-python/temp/

# OS/editor
.DS_Store
.idea/
.vscode/
```

---

## Expected Final Behavior

The user should be able to:

1. Select PROMISE or AEEEM.
2. Upload Java source project or provide GitHub URL.
3. Extract static metrics.
4. Preview generated target metrics dataset.
5. Upload one or multiple labelled source datasets.
6. Run CORAL + KNN prediction.
7. View final defect-prone class predictions.

Final output example:

```text
class_name,file_path,predicted_label,risk_score
com.project.PaymentService,src/main/java/com/project/PaymentService.java,buggy,0.82
com.project.LoginController,src/main/java/com/project/LoginController.java,clean,0.21
```

---

## Development Priority

Implement in this order:

1. Java Spring Boot metrics extraction API.
2. CSV generation and storage.
3. Angular metrics extraction UI.
4. Python FastAPI prediction endpoint.
5. Java-to-Python API client.
6. Angular prediction UI.
7. Feature validation and error handling.
8. Model evaluation mode using labelled benchmark datasets.

---

## Final Note

For a new unlabelled Java project, the system cannot know the true correctness of each prediction immediately. It can only predict defect-prone classes. Evaluation requires labelled test data. The system should therefore present the result as:

```text
Predicted Defect-Prone
Predicted Clean
```

rather than:

```text
Bug Found
No Bug Found
```
