#!/usr/bin/env bash
# Extract a temporary JDK to run the trusted Java controller on the host.
# No Docker socket is mounted into any container, including the compiler image.
set -euo pipefail
cd -- "$(dirname -- "${BASH_SOURCE[0]}")"
workspace="$(mktemp -d)"
compiler_container=""
cleanup() {
  if [[ -n "$compiler_container" ]]; then docker rm -f "$compiler_container" >/dev/null 2>&1 || true; fi
  rm -rf -- "$workspace"
}
trap cleanup EXIT
compiler_container="$(docker create maven:3.9.9-eclipse-temurin-21)"
mkdir "$workspace/jdk"
docker cp "$compiler_container:/opt/java/openjdk/." "$workspace/jdk"
docker rm "$compiler_container" >/dev/null
compiler_container=""
"$workspace/jdk/bin/java" --source 21 probes/DockerIsolationProbe.java
