#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
# Unique project keeps destructive test fixtures away from development databases.
project="forgeflow-verify-${RANDOM}-$$"
cleanup() { docker compose -p "$project" down --volumes --remove-orphans; }
trap cleanup EXIT
docker compose -p "$project" run --rm build mvn -B verify
