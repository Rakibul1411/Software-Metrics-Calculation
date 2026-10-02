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

if command -v lsof >/dev/null 2>&1; then
  stale_pids=$(lsof -ti :8000 2>/dev/null || true)
  if [[ -n "$stale_pids" ]]; then
    echo "Freeing port 8000 (PIDs: $stale_pids)..."
    kill -9 $stale_pids 2>/dev/null || true
    sleep 0.5
  fi
fi

echo "Python auto-reload: watching ml-service-python/app."
exec "$python_command" -m uvicorn app.main:app \
  --host 0.0.0.0 \
  --reload \
  --reload-dir app \
  --reload-delay 0.5 \
  --port 8000
