#!/usr/bin/env bash
# Disposable upgrade and logical-restore rehearsal; no production connection parameters.
set -euo pipefail
cd -- "$(dirname -- "${BASH_SOURCE[0]}")"
project="forgeflow-schema-18"
compose=(docker compose -p "$project")
trap '"${compose[@]}" down >/dev/null 2>&1' EXIT
"${compose[@]}" up -d --wait database
"${compose[@]}" run --rm -T tests mvn -B --no-transfer-progress \
  -Dflowable.version=7.1.0 -Dtest=SchemaLifecycleProbe -Dspike.schema.phase=prepare test
"${compose[@]}" run --rm -T tests mvn -B --no-transfer-progress \
  -Dflowable.version=7.2.0 -Dtest=SchemaLifecycleProbe -Dspike.schema.phase=migrate test
"${compose[@]}" exec -T database pg_dump -U spike -d flowable_spike -Fc -f /tmp/fixture.dump
"${compose[@]}" exec -T database createdb -U spike restored_fixture
"${compose[@]}" exec -T database pg_restore -U spike -d restored_fixture --exit-on-error /tmp/fixture.dump
"${compose[@]}" run --rm -T -e SPRING_DATASOURCE_URL=jdbc:postgresql://database:5432/restored_fixture \
  tests mvn -B --no-transfer-progress -Dflowable.version=7.2.0 \
  -Dtest=SchemaLifecycleProbe -Dspike.schema.phase=validate test
printf '%s\n' 'PASS: 7.1.0 creation -> 7.2.0 upgrade -> logical restore -> schema-validated completion'
