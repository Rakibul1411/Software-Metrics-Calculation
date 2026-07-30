#!/usr/bin/env bash
set -euo pipefail

project_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

(
  cd "$project_root/backend-java"
  mvn test
)

(
  cd "$project_root/ml-service-python"
  python3 -m unittest discover -s tests -v
)

(
  cd "$project_root/frontend-angular"
  npm run build
)
