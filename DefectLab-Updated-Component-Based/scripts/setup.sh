#!/usr/bin/env bash
set -euo pipefail

project_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

for command in java mvn node npm python3; do
  if ! command -v "$command" >/dev/null 2>&1; then
    echo "Missing required command: $command" >&2
    exit 1
  fi
done

java_major="$(java -version 2>&1 | sed -n '1s/.*version \"\\([0-9]*\\).*/\\1/p')"
if [[ -n "$java_major" && "$java_major" -lt 17 ]]; then
  echo "Java 17 or newer is required." >&2
  exit 1
fi

(
  cd "$project_root/ml-service-python"
  if [[ ! -d venv ]]; then
    python3 -m venv venv
  fi
  venv/bin/python -m pip install --upgrade pip
  venv/bin/python -m pip install -r requirements.txt pytest
)

(
  cd "$project_root/frontend-angular"
  npm ci
)

(
  cd "$project_root/backend-java"
  mvn -q -DskipTests package
)

echo "Setup complete. Start PostgreSQL, then run: scripts/run-dev.sh"
echo "Java Spring DevTools and Python Uvicorn auto-reload are included."
