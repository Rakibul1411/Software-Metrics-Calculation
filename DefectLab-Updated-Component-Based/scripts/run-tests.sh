#!/usr/bin/env bash
set -euo pipefail

project_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
export NG_CLI_ANALYTICS=false
export CI=true

echo "=================================================="
echo "  DefectLab Test Suite Execution"
echo "=================================================="

echo ""
echo "[1/3] Running Java Spring Boot Backend Tests..."
(
  cd "$project_root/backend-java"
  mvn test
)

echo ""
echo "[2/3] Running Python ML Service Tests..."
(
  cd "$project_root/ml-service-python"
  if [[ -x "venv/bin/python" ]]; then
    PYTHONPATH=. venv/bin/python -m pytest tests -q
  else
    PYTHONPATH=. python3 -m pytest tests -q
  fi
)

echo ""
echo "[3/3] Running Angular Frontend Production Build..."
(
  cd "$project_root/frontend-angular"
  npx ng build --configuration=production
)

echo ""
echo "=================================================="
echo "  All Tests & Builds Passed Successfully!"
echo "=================================================="
