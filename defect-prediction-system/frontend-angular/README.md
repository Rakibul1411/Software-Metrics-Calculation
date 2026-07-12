# Metrics Extraction Frontend

Angular 19 UI for the metrics-extraction stage of the defect prediction system.

## Features

- PROMISE or AEEEM-static format selection
- Java project ZIP upload (50 MB maximum)
- Public GitHub repository URL input
- Extraction progress, API error feedback, CSV preview, and CSV download
- Responsive layout and keyboard-accessible controls

The prediction UI is intentionally not included yet.

## Run locally

Start `backend-java` on port 8080, then:

```bash
npm install
npm start
```

Open `http://localhost:4200`. The backend URL is configured in `src/environments/environment.ts`.

## Verify

```bash
npm run build
```
