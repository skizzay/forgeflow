#!/usr/bin/env bash
# Real local exercise. Uses access-only ChatGPT auth; never pushes, refreshes host auth or creates GitHub objects.
set -euo pipefail
cd -- "$(dirname -- "${BASH_SOURCE[0]}")"
workspace="$(mktemp -d)"
compiler_container=""
project="forgeflow-real-codex-18"
compose=(docker compose -p "$project" -f compose.yaml -f "$workspace/ports.yaml")
cleanup() {
  "${compose[@]}" down >/dev/null 2>&1 || true
  if [[ -n "$compiler_container" ]]; then docker rm -f "$compiler_container" >/dev/null 2>&1 || true; fi
  rm -rf -- "$workspace"
}
trap cleanup EXIT
cat > "$workspace/ports.yaml" <<'YAML'
services:
  database:
    ports:
      - "127.0.0.1::5432"
YAML
docker image inspect forgeflow-codex-java:0.163.0 >/dev/null
"${compose[@]}" up -d --wait database
"${compose[@]}" run --rm --no-deps -T tests mvn -B --no-transfer-progress \
  test-compile dependency:copy-dependencies -DincludeScope=test -DoutputDirectory=target/host-deps
compiler_container="$(docker create maven:3.9.9-eclipse-temurin-21)"
mkdir "$workspace/jdk"
docker cp "$compiler_container:/opt/java/openjdk/." "$workspace/jdk"
docker rm "$compiler_container" >/dev/null
compiler_container=""
address="$("${compose[@]}" port database 5432)"
export SPRING_DATASOURCE_URL="jdbc:postgresql://127.0.0.1:${address##*:}/flowable_spike"
export SPRING_DATASOURCE_USERNAME=spike
export SPRING_DATASOURCE_PASSWORD=disposable-fixture-only
export SPIKE_SANDBOX="${SPIKE_SANDBOX:-$HOME/tmp/forgeflow-sandbox}"
export SPIKE_AUTH_FILE="${CODEX_HOME:-$HOME/.codex}/auth.json"
export SPIKE_PRIVATE_DIRECTORY="$workspace/private"
mkdir "$SPIKE_PRIVATE_DIRECTORY"
"$workspace/jdk/bin/java" -Xmx384m -cp 'target/classes:target/test-classes:target/host-deps/*' io.forgeflow.spike.RealCodexProbe
