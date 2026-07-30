# API Documentation

## Java Backend (Spring Boot — Port 8080)

### POST /api/metrics/extract
Extract object-oriented metrics from a Java project ZIP or a public GitHub repository.

**Parameters (form-data):**
| Param | Type | Required | Description |
|---|---|---|---|
| `projectZip` | file | one source required | Java project ZIP, maximum 50 MB |
| `githubUrl` | string | one source required | Public GitHub repository, `tree/branch/folder`, or `blob/branch/file.zip` URL |
| `datasetFormat` | string | ❌ | `promise` or `aeeem` (default: `promise`) |
| `aeeemProfile` | string | ❌ | `current`, `jdt`, `pde`, `eq`, `ml`, or `lc` |
| `aeeemModulePath` | string | ❌ | Repository-relative module; folder URL scope is used when omitted |
| `aeeemHistoryStart` | date | ❌ | Custom current-profile start in `YYYY-MM-DD` |
| `aeeemReleaseDate` | date | ❌ | Custom current-profile release date |
| `aeeemReleaseRef` | string | ❌ | Custom current-profile tag or commit ref |
| `aeeemMaxSnapshots` | integer | ❌ | Current-profile cap; `0` means unlimited |

Supply exactly one of `projectZip` or `githubUrl`. A form-urlencoded `sourceDirectory` endpoint remains available for trusted local development clients.

**Response:**
```json
{
  "targetDatasetId": "abc-123-uuid",
  "datasetFormat": "promise",
  "rowCount": 42,
  "extractedColumns": ["wmc", "dit", "noc", ...],
  "csvPreview": ["name,wmc,dit,...", "org.example.Foo,3,2,...", "...all extracted classes..."],
  "csvDownloadUrl": "/api/metrics/download/abc-123-uuid/csv",
  "arffDownloadUrl": "/api/metrics/download/abc-123-uuid/arff",
  "aeeemAnalysis": {
    "profileId": "jdt",
    "profileName": "Eclipse JDT Core",
    "historyStart": "2005-01-01",
    "releaseDate": "2008-06-17",
    "snapshotCount": 91,
    "modulePath": "org.eclipse.jdt.core",
    "releaseCommit": "8ac82b15173c...",
    "releaseResolution": "release ref R3_4",
    "warnings": []
  }
}
```

Benchmark profiles accept only their mapped historical project. The UI supplies
the verified repository automatically. A missing migrated release tag uses the
last first-parent commit on or before the profile release date; the response
then includes `releaseResolution: "release-date fallback"` and an explanatory
warning. A mirror whose available history starts late is also reported in
`warnings`.

PROMISE output contains `name` and 20 numeric metric features. AEEEM output
contains `name` plus 56 non-defect predictors: 17 final static metrics, 17
WCHU metrics, 17 LDHH metrics, and five Git changed-line entropy metrics. The
five issue-tracker defect-history fields and `class` label are excluded because
the generated target is unlabelled.

---

### GET /api/metrics/download/{datasetId}/{fileFormat}
Download the generated metrics file. `fileFormat` is `csv` or `arff`; the
shorter `/api/metrics/download/{datasetId}` form returns CSV.

**Response:** CSV or ARFF file download.

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
| `coralOption` | boolean | ❌ | Apply closed-form shallow/linear CORAL (default: `true`) |

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

### POST /api/prediction/evaluate
Evaluate the model without metric extraction by training on labelled source CSVs and scoring a labelled target CSV.

**Parameters (multipart/form-data):**
| Param | Type | Required | Description |
|---|---|---|---|
| `targetFile` | file | ✅ | Labelled target metrics CSV |
| `sourceFiles` | file[] | ✅ | One or more labelled source metrics CSVs |
| `labelColumn` | string | ❌ | Label column present in both source and target (default: `bug`) |
| `knnValue` | int | ❌ | KNN neighbors (default: `5`) |
| `coralOption` | boolean | ❌ | Apply closed-form shallow/linear CORAL (default: `true`) |

The response includes accuracy, precision, recall, F1, ROC AUC when defined,
confusion-matrix counts, and per-class actual/predicted labels. Shallow CORAL
uses target features but never target labels; labels are read only for the final
evaluation.

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
| `coral_option` | bool | Whether to apply shallow/linear CORAL |

**Response:** Same as `/api/prediction/run` above.

### POST /ml/evaluate
Internal evaluation endpoint used by `/api/prediction/evaluate`.
