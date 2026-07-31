#!/usr/bin/env bash
set -euo pipefail

project_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
export SPRING_PROFILES_ACTIVE="${SPRING_PROFILES_ACTIVE:-local}"

for command in java mvn node npm python3; do
  if ! command -v "$command" >/dev/null 2>&1; then
    echo "Missing required command: $command" >&2
    echo "Run scripts/setup.sh after installing Java 17, Maven, Node and Python." >&2
    exit 1
  fi
done

cleanup() {
  jobs -p | xargs -r kill
}
trap cleanup EXIT INT TERM

(
  cd "$project_root/ml-service-python"
  if [[ -x "venv/bin/python" ]]; then
    venv/bin/python -m uvicorn app.main:app --reload --port 8000
  else
    python3 -m uvicorn app.main:app --reload --port 8000
  fi
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
