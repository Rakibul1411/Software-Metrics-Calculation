#!/usr/bin/env bash
set -euo pipefail

project_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
service_dir="$project_root/ml-service-python"

cd "$service_dir"

if [[ -x "venv/bin/python" ]]; then
  python_command="venv/bin/python"
else
  python_command="python3"
fi

echo "Python auto-reload: watching ml-service-python/app."
exec "$python_command" -m uvicorn app.main:app \
  --reload \
  --reload-dir app \
  --reload-delay 0.5 \
  --port 8000
