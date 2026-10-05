# DefectLab Angular Frontend

This module is the client-facing user interface for the DefectLab platform. It provides an authenticated single-page application (SPA) built with Angular 19, allowing users to analyze Java source repositories, manage software metric datasets, configure and execute cross-project defect prediction models, inspect ranked defect probabilities, evaluate model performance, and compare calculated metrics with standard benchmarks.

The frontend communicates exclusively with the Spring Boot backend via `/api/*` routes; it never communicates with the internal Python ML service directly.

For the complete product overview, see the [project README](../README.md).

## Technology Stack

- **Framework**: Angular 19
- **Language**: TypeScript 5.7
- **Routing & State**: Angular Router, RxJS observables with `BehaviorSubject`
- **Forms & HTTP**: Angular Reactive Forms & HttpClient (`withCredentials: true` for session cookies)
- **Styling**: Vanilla CSS Design System with dark theme variables, responsive grids, and micro-interactions

## Application Routes

| Route | Component | Purpose |
|---|---|---|
| `/login` | `AuthPageComponent` | User registration, login, and password reset |
| `/overview` | `OverviewComponent` | Dashboard displaying high-level system KPIs, dataset counts, and recent activity |
| `/analyze` | `AnalyzeComponent` | Source code analysis: upload Java ZIPs or provide public GitHub repository URLs |
| `/datasets` | `DatasetsComponent` | Metric dataset catalog: upload, search, filter, preview, and download CSV/ARFF datasets |
| `/predictions` | `PredictionsComponent` | Model execution: select source/target datasets, configure KNN and CORAL parameters |
| `/metric-comparisons` | `ComparisonsComponent` | Metric comparison: compare manual datasets against predefined benchmarks with tolerance |
| `/reports` | `ReportsComponent` | Run history and evaluation reports grouped by execution run |
| `/reports/:groupKey` | `ReportDetailComponent` | Detailed view for prediction runs: ranked class table, risk bands, and confusion matrix |
| `/account` | `AccountComponent` | User profile details and password update form |

Authenticated views are rendered inside `ShellComponent`, which includes persistent sidebar navigation and a top user session header.
- `AuthGuard` validates the active session before allowing access to internal views.
- `GuestGuard` redirects authenticated users away from the `/login` page to `/overview`.

## Directory Structure

```text
src/app/
├── app-routing.module.ts              Route definitions, guards, and lazy-loading boundaries
├── app.module.ts                      Root module declaring global providers and interceptors
├── core/                              Singleton services, guards, interceptors, and models
│   ├── base/                          Base components (BaseListComponent, BaseDetailComponent, BaseFormComponent)
│   ├── guards/                        auth.guard.ts, guest.guard.ts
│   ├── interceptors/                  error.interceptor.ts (standardized HTTP error handling)
│   ├── models/                        defectlab.model.ts (TypeScript interface definitions)
│   └── services/                      defectlab-api.service.ts, session.service.ts
├── features/                          Domain feature views
│   ├── account/                       Profile information and password change views
│   ├── analysis/                      Source analysis triggers, progress states, and AST reports
│   ├── auth/                          Registration, login, and password recovery views
│   ├── comparisons/                   Benchmark tolerance configuration and diff views
│   ├── dashboard/                     Aggregated workspace KPIs and recent predictions
│   ├── datasets/                      Dataset table, search filters, and file previewer
│   ├── predictions/                   Prediction configuration, execution, and risk ranking views
│   ├── reports/                       Grouped reports, confusion matrices, and PDF links
│   └── shell/                         Sidebar navigation, top header, and user avatar
├── shared/                            Reusable Design System UI Components (25+ widgets)
│   ├── ui-badge/                      Risk tags (HIGH, MEDIUM, LOW) and family badges
│   ├── ui-bar-chart/                  Visual distribution bar charts
│   ├── ui-butterfly-graph/            Dependency and coupling visualizations
│   ├── ui-button/                     Primary, secondary, outline, and ghost action buttons
│   ├── ui-card/                       Structured content card containers
│   ├── ui-confirm-dialog/             Modal dialogs for destructive action confirmation
│   ├── ui-confusion-matrix/           TP/FP/TN/FN evaluation matrix with metric callouts
│   ├── ui-delete-action/              Consistent delete triggers with confirmation dialogs
│   ├── ui-detail-fields/              Key-value metadata inspection views
│   ├── ui-download-menu/              Dropdown menus for CSV, ARFF, and PDF downloads
│   ├── ui-empty-state/                Consistent empty states with call-to-action buttons
│   ├── ui-file-picker/                Drag-and-drop CSV, ARFF, and ZIP file uploaders
│   ├── ui-icon/                       Inline SVG icon library
│   ├── ui-input/                      Accessible text inputs with validation states
│   ├── ui-metric-card/                KPI summary cards (ROC-AUC, Precision, Recall, Bug Count)
│   ├── ui-page-header/                Consistent page titles, breadcrumbs, and primary actions
│   ├── ui-pagination/                 Accessible table pagination controls
│   ├── ui-radio-group/                Custom radio button groups
│   ├── ui-search-bar/                 Debounced search input filters
│   ├── ui-select/                     Custom dropdown selection controls
│   ├── ui-state/                      Standard 4-state visual controller (loading, empty, error, data)
│   ├── ui-table/                      Sortable, paginated data tables
│   ├── ui-toast/                      Floating toast notification alerts
│   └── ui-treemap/                    Interactive squarified treemap for defect risk hotspots
└── defectlab.css                      Global design system tokens, typography, and utility classes
```

## User Workflows

### 1. Source Code Metric Extraction
- In the **Analyze** view, users choose between PROMISE or AEEEM metric families.
- For PROMISE, users can upload a Java ZIP archive or provide a public GitHub repository URL.
- For AEEEM, users provide a public GitHub URL and choose a benchmark history profile (JDT, PDE, EQ, LC, or ML) to extract change metrics across 14-day commit snapshots.
- Upon completion, an unlabeled `MANUAL` dataset is registered in the catalog.

### 2. Dataset Management
- The **Datasets** view displays all available datasets: global predefined benchmarks and user-uploaded/extracted datasets.
- Users can preview up to 25 rows directly in the browser, review quality statistics, and download raw CSV or ARFF files.

### 3. Model Configuration & Prediction
- In the **Predictions** view, users select a labeled source dataset and one or two target datasets (a manual target, a predefined benchmark target, or both).
- Users configure KNN parameters (K=1 to 5) and decide whether to enable Shallow CORAL covariance alignment to reduce domain shift.
- Defect probability threshold (default 0.5) controls classification.

### 4. Evaluation and Reporting
- After execution, results are ranked in descending order of defect probability.
- If the target has actual ground-truth labels, a full confusion matrix and performance statistics (ROC-AUC, PR-AUC, MCC, F1, Recall@20% LOC) are rendered.
- Users can download the labeled CSV (for manual targets) and high-quality PDF evaluation reports.

## Local Development

Ensure the Spring Boot backend is running on `http://localhost:8080`.

Install dependencies:

```bash
cd frontend-angular
npm ci
```

Start the Angular development server:

```bash
npm start
```

The application will be accessible at `http://localhost:4200`. The local configuration uses `proxy.conf.json` to proxy `/api/*` calls directly to the Spring Boot server running on port 8080.

## Production Build & Verification

To verify TypeScript typing and create a production build:

```bash
npm run build
```

This compiles optimized static bundles into the `dist/` directory.

## Docker Container

In Docker environments, the frontend is built multi-stage and served via Nginx on port 80 (mapped to host port 4200 via `docker-compose.yml`), which proxies `/api/*` traffic to the backend container.
