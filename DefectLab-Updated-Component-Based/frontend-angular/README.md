# DefectLab Angular Frontend

This module is the authenticated Angular 19 dashboard for the DefectLab
workflow. It calls Spring Boot only; it never calls the FastAPI ML service
directly.

For the complete system flow, see the [project README](../README.md).

## Technology

- Angular 19
- TypeScript 5.7
- Angular Router
- Angular Forms
- Angular HttpClient
- RxJS
- Plain responsive CSS

## Application routes

| Browser route | Component | Purpose |
|---|---|---|
| `/login` | `AuthPageComponent` | Registration and login |
| `/overview` | `OverviewComponent` | Workspace dashboard |
| `/analyze` | `AnalyzeComponent` | PROMISE/AEEEM source analysis |
| `/datasets` | `DatasetsComponent` | Metric storage and upload |
| `/predictions` | `PredictionsComponent` | Model configuration and execution |
| `/metric-comparisons` | `ComparisonsComponent` | Independent metric comparison |
| `/reports` | `ReportsComponent` | Grouped prediction reports |
| `/reports/:groupKey` | `ReportDetailComponent` | Complete group/run detail |
| `/account` | `AccountComponent` | Profile and password |

Authenticated screens render inside `ShellComponent`.

`AuthGuard` restores the cookie-backed session before allowing the dashboard.
`GuestGuard` redirects a signed-in user away from `/login`.

## Source structure

```text
src/app/
├── app-routing.module.ts
├── app.module.ts
├── core/
│   ├── base/                  BaseListComponent, BaseDetailComponent, BaseFormComponent
│   ├── guards/                auth.guard.ts, guest.guard.ts
│   ├── interceptors/          error.interceptor.ts
│   ├── models/                defectlab.model.ts
│   └── services/              defectlab-api.service.ts, session.service.ts
├── features/
│   ├── account/               Profile and password management
│   ├── analysis/              Source analysis, AST extraction, and treemap views
│   ├── auth/                  Login, register, and password reset
│   ├── comparisons/           Metric comparison workspace & tolerance checks
│   ├── dashboard/             Overview dashboard and recent activity
│   ├── datasets/              Dataset storage, upload, preview, and ARFF/CSV downloads
│   ├── predictions/           Prediction run execution, detail views, and confusion matrices
│   ├── reports/               Grouped prediction reports and PDF downloads
│   └── shell/                 Application shell, persistent sidebar, and header navigation
├── shared/                    25+ Reusable Design System Components
│   ├── ui-badge/              Risk badges and metric family tags
│   ├── ui-bar-chart/          Distribution and comparison bar charts
│   ├── ui-butterfly-graph/    Coupling and dependency butterfly visualizations
│   ├── ui-button/             Primary, secondary, and ghost action buttons
│   ├── ui-card/               Containers with consistent border and dark theme
│   ├── ui-confirm-dialog/     Destructive action confirmation dialogs
│   ├── ui-confusion-matrix/   TP/FP/TN/FN evaluation matrix with metrics
│   ├── ui-delete-action/      Reusable delete trigger with confirmation
│   ├── ui-detail-fields/      Key-value inspection metadata layouts
│   ├── ui-download-menu/      Dropdown for CSV, ARFF, and PDF downloads
│   ├── ui-empty-state/        Informative empty screens with actions
│   ├── ui-file-picker/        Drag-and-drop file uploaders
│   ├── ui-icon/               SVG icons
│   ├── ui-input/              Form input elements
│   ├── ui-metric-card/        KPI summary cards (AUC-ROC, Recall, Buggy Count)
│   ├── ui-page-header/        Consistent page headers, breadcrumbs, and actions
│   ├── ui-pagination/         Accessible table pagination and page-size selector
│   ├── ui-radio-group/        Styled radio buttons
│   ├── ui-search-bar/         Debounced search input
│   ├── ui-select/             Accessible dropdown selects
│   ├── ui-state/              Standard 4-state handler (loading, empty, error, data)
│   ├── ui-table/              Sortable, paginated data table
│   ├── ui-toast/              Floating glassmorphic notifications
│   └── ui-treemap/            Squarified treemap for package defect hotspots
└── defectlab.css              Core design system tokens, themes, and utility classes
```

## Session flow

1. The application calls `GET /api/auth/me` when a guard needs session state.
2. `SessionService` stores the resolved user in a `BehaviorSubject`.
3. Register/login updates the same session state.
4. Every API request uses `withCredentials: true`.
5. Logout invalidates the backend session and returns to `/login`.

Passwords and session IDs are not stored in browser application state.

## Feature flow

### Analyze

The user can:

- upload a Java ZIP for PROMISE;
- provide a public GitHub URL for PROMISE/AEEEM;
- enter project name/version;
- select an AEEEM historical profile.

The page submits multipart data to `/api/analysis` and displays the saved
MANUAL dataset returned by Spring Boot.

### Metric storage

The dataset view supports:

- CSV/ARFF upload;
- PROMISE/AEEEM and MANUAL/PREDEFINED metadata;
- search and filters;
- preview;
- quality information;
- download; and
- safe deletion.

The API, not the frontend, is authoritative for family detection, label state,
ownership, and deletion rules.

### Predictions

The current UI exposes:

- labeled source;
- manual target, predefined target, or both;
- KNN with user-selected K from 1 through 5;
- a dataset-alignment checkbox;
- decision threshold.

Log preprocessing is automatic. The UI lets the user enable or disable shallow
CORAL dataset alignment.

A dual-target response contains two saved runs sharing one group ID.

### Reports

The reports area:

- lists grouped and individual runs;
- loads ranked predictions;
- filters Buggy rows;
- switches between manual/predefined targets;
- shows evaluation and comparison summaries;
- downloads the manual labeled CSV; and
- downloads authenticated PDF reports.

### Metric comparisons

The comparison page loads eligible MANUAL/PREDEFINED pairs, submits tolerance
configuration, displays the saved comparison, and downloads its PDF.

## API service

`DefectLabApiService` is the single browser API adapter. It contains typed
methods for:

- authentication;
- dashboard;
- dataset upload/list/preview/delete/download;
- source analysis;
- prediction execution/list/group/detail/download;
- metric comparison execution/list/detail/download.

`environment.apiUrl` supplies the origin. In local development, the proxy sends
`/api` to Spring Boot. In Docker, Nginx performs the same forwarding.

## Models

`core/models/defectlab.model.ts` defines the public UI contract for:

- user profiles;
- dataset summaries/previews;
- model configuration;
- prediction execution, groups, summaries, and rows;
- evaluation metrics;
- metric comparison pairs, summaries, and details;
- dashboard data.

The model configuration retains applied pipeline metadata for reporting, but no
selectable pipeline identifier exists.

## Install

From this directory:

```bash
npm ci
```

Recommended runtime:

- Node.js 20 or 22
- npm matching the lockfile workflow

## Run locally

Start Spring Boot on port `8080`, then:

```bash
npm start
```

Open <http://localhost:4200>.

`proxy.conf.json` forwards local `/api` requests to the backend.

## Build and type-check

Production build:

```bash
npm run build
```

TypeScript application check:

```bash
./node_modules/.bin/tsc -p tsconfig.app.json --noEmit
```

The current `angular.json` has no Angular unit-test target, so the meaningful
frontend verification is application compilation/build unless a test builder
is added.

## Docker

The frontend Docker build produces the Angular bundle and serves it through
Nginx. The container exposes port `80`, mapped to host port `4200` by the root
Compose file.

```bash
docker compose up --build frontend
```

Run this command from the project root so backend dependencies are available.

## UI implementation rules

- Keep session handling in `SessionService`.
- Add API calls through `DefectLabApiService`, not directly in components.
- Add shared response/request shapes to `defectlab.model.ts`.
- Protect signed-in routes with `AuthGuard`.
- Preserve `withCredentials: true`.
- Treat backend validation as authoritative.
- Do not add preprocessing variant controls; the standard pipeline is fixed.
- Use authenticated backend URLs for CSV/PDF downloads.
