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
  exec bash "$project_root/scripts/run-python-dev.sh"
) &

(
  exec bash "$project_root/scripts/run-java-dev.sh"
) &

(
  cd "$project_root/frontend-angular"
  npm start
) &

wait
