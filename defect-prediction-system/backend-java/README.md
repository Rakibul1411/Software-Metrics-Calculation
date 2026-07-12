# Backend Java — Metrics Calculator & Prediction API

Spring Boot application providing REST APIs to extract object-oriented metrics from Java source code and run defect predictions.

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
│   ├── client/
│   │   └── PythonPredictionClient.java     ← Connects to FastAPI ML service
│   ├── common/
│   │   ├── dto/                            ← DTOs for request/response
│   │   ├── enums/                          ← DatasetType (PROMISE/AEEEM)
│   │   ├── exception/                      ← GlobalExceptionHandler
│   │   ├── validation/                     ← Feature schema validator
│   │   └── csv/                            ← CsvWriterService
│   ├── promise/                            ← PROMISE metrics domain
│   │   ├── analyzer/PromiseProjectAnalyzer.java
│   │   ├── calculator/                     ← Legacy calculators used by AEEEM adapters
│   │   ├── model/PromiseMetricResult.java
│   │   └── export/PromiseCsvExporter.java
│   └── aeeem/                              ← AEEEM metrics domain
│       ├── calculator/AeeemStaticMetricsCalculator.java
│       ├── model/AeeemMetricResult.java
│       ├── parser/AeeemJavaSourceParser.java
│       └── export/AeeemCsvExporter.java
├── src/main/resources/
│   ├── application.properties
│   └── metric-profiles/
│       ├── promise-metrics.json
│       └── aeeem-static-metrics.json
└── storage/
    ├── uploads/
    ├── extracted-projects/
    ├── generated-datasets/
    └── prediction-results/
```

## API Endpoints

| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/api/metrics/extract` | Extract metrics from a source directory |
| `GET` | `/api/metrics/download/{datasetId}` | Download generated CSV |
| `POST` | `/api/prediction/run` | Run defect prediction via Python service |

## Build & Run

```bash
mvn clean package
java -jar target/metrics-calculator-1.0.0.jar

# Or with Maven dev server:
mvn spring-boot:run
```

Server listens on port `8080` by default.
