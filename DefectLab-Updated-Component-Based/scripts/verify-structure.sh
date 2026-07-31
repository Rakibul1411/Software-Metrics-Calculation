#!/usr/bin/env bash
set -euo pipefail

project_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
schema="$project_root/backend-java/src/main/resources/schema.sql"
java_root="$project_root/backend-java/src/main/java/org/metrics"
frontend_root="$project_root/frontend-angular/src"

table_names="$(
  grep -Eio '^[[:space:]]*CREATE TABLE( IF NOT EXISTS)? [a-z_]+' "$schema" \
    | awk '{print $NF}' \
    | sort -u
)"
expected_tables=$'metric_comparisons\nmetric_datasets\nprediction_runs\nusers'
if [[ "$table_names" != "$expected_tables" ]]; then
  echo "Expected users plus the three workflow tables; found: $table_names" >&2
  exit 1
fi

entity_count="$(
  grep -Rho '@Table(name = "[^"]*")' "$java_root/defectlab" \
    | sort -u \
    | wc -l \
    | tr -d ' '
)"
if [[ "$entity_count" -ne 4 ]]; then
  echo "Expected exactly four unique JPA table mappings; found $entity_count." >&2
  exit 1
fi

if find "$java_root" -mindepth 1 -maxdepth 1 -type d ! -name defectlab \
    | grep -q .; then
  echo "Legacy Java packages remain outside org.metrics.defectlab." >&2
  exit 1
fi

if grep -RqiE '(linear|radial|conic)-gradient' "$frontend_root"; then
  echo "A UI gradient remains in the Angular source." >&2
  exit 1
fi

if grep -RqsE '@RequestMapping\\(\"/api/(metrics|comparisons)\"\\)' "$java_root"; then
  echo "A legacy controller endpoint remains." >&2
  exit 1
fi

echo "Structure verification passed."
