#!/usr/bin/env bash
set -euo pipefail

project_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
export SPRING_PROFILES_ACTIVE="${SPRING_PROFILES_ACTIVE:-local}"

for command in java mvn node npm python3; do
  if ! command -v "$command" >/dev/null 2>&1; then
    echo "Missing required command: $command" >&2
    echo "Run scripts/setup.sh after installing Java 17, Maven, Node, and Python." >&2
    exit 1
  fi
done

cleanup() {
  echo "Shutting down DefectLab local services..."
  jobs -p | xargs kill 2>/dev/null || true
}
trap cleanup EXIT INT TERM

# 1. Start Python FastAPI ML service on port 8000
(
  cd "$project_root/ml-service-python"
  python_cmd="python3"
  [[ -x "venv/bin/python" ]] && python_cmd="venv/bin/python"
  echo "Starting Python ML Service (FastAPI) on port 8000..."
  exec "$python_cmd" -m uvicorn app.main:app --host 0.0.0.0 --port 8000 --reload
) &

# 2. Start Java Spring Boot Backend on port 8080
(
  cd "$project_root/backend-java"
  echo "Starting Java Spring Boot API on port 8080..."
  exec mvn spring-boot:run -Dspring-boot.run.jvmArguments="-Xmx2g"
) &

# 3. Start Angular UI on port 4200
(
  cd "$project_root/frontend-angular"
  echo "Starting Angular UI on port 4200..."
  exec npm start
) &

wait
