#!/usr/bin/env bash
set -euo pipefail

project_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
echo "Java auto-reload: Spring Boot DevTools is enabled."
exec python3 "$project_root/scripts/run-spring-dev.py"
