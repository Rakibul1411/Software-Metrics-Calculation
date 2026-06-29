# API Documentation

## Java Backend (Spring Boot — Port 8080)

### POST /api/metrics/extract
Extract object-oriented metrics from a local Java source directory.

**Parameters (form-data):**
| Param | Type | Required | Description |
|---|---|---|---|
| `sourceDirectory` | string | ✅ | Absolute path to Java source directory (comma-separated for multiple) |
| `datasetFormat` | string | ❌ | `promise` or `aeeem` (default: `promise`) |
| `filterFile` | string | ❌ | Path to CSV file to filter output classes |

**Response:**
```json
{
  "targetDatasetId": "abc-123-uuid",
  "extractedColumns": ["wmc", "dit", "noc", ...],
  "csvPreview": ["name,wmc,dit,...", "org.example.Foo,3,2,..."],
  "downloadUrl": "/api/metrics/download/abc-123-uuid"
}
```

---

### GET /api/metrics/download/{datasetId}
Download the generated metrics CSV file.

**Response:** `text/csv` file download.

---

### POST /api/prediction/run
Run defect prediction using a target dataset and labelled historical datasets.

**Parameters (multipart/form-data):**
| Param | Type | Required | Description |
|---|---|---|---|
| `targetDatasetId` | string | ✅ | UUID returned from `/api/metrics/extract` |
| `sourceFiles` | file[] | ✅ | One or more labelled source dataset CSVs |
| `labelColumn` | string | ❌ | Column for defect labels (default: `bug`) |
| `knnValue` | int | ❌ | KNN neighbors (default: `5`) |
| `coralOption` | boolean | ❌ | Apply CORAL domain adaptation (default: `true`) |

**Response:**
```json
{
  "status": "success",
  "predictions": [
    { "class": "org.example.Foo", "prediction": "1" },
    { "class": "org.example.Bar", "prediction": "0" }
  ]
}
```

---

## Python ML Service (FastAPI — Port 8000)

### POST /ml/predict
Direct ML endpoint called internally by the Java backend.

**Parameters (multipart/form-data):**
| Param | Type | Description |
|---|---|---|
| `target_file` | file | Unlabelled target metrics CSV |
| `source_files` | file[] | Labelled historical source CSVs |
| `label_column` | string | Column for defect labels |
| `knn_value` | int | KNN neighbors |
| `coral_option` | bool | Whether to apply CORAL |

**Response:** Same as `/api/prediction/run` above.
