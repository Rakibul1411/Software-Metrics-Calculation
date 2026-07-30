#!/usr/bin/env bash
set -euo pipefail

project_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

cleanup() {
  jobs -p | xargs -r kill
}
trap cleanup EXIT INT TERM

(
  cd "$project_root/ml-service-python"
  python3 -m uvicorn app.main:app --reload --port 8000
) &

(
  cd "$project_root/backend-java"
  mvn spring-boot:run -Dspring-boot.run.jvmArguments="-Xmx2g"
) &

(
  cd "$project_root/frontend-angular"
  npm start
) &

wait
