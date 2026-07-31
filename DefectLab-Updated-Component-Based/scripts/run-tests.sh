#!/usr/bin/env bash
set -euo pipefail

project_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

"$project_root/scripts/verify-structure.sh"

(
  cd "$project_root/backend-java"
  mvn test
)

(
  cd "$project_root/ml-service-python"
  if [[ -x "venv/bin/python" ]]; then
    venv/bin/python -m compileall -q app tests
    venv/bin/python -m pytest tests -q
  else
    python3 -m compileall -q app tests
    python3 -m pytest tests -q
  fi
)

(
  cd "$project_root/frontend-angular"
  npm run build
)
