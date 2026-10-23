# Run the local control-plane bootstrap

Implements [#7](https://github.com/skizzay/forgeflow/issues/7). This starts a local
control plane with a PostgreSQL database and health endpoint. Worker execution,
approval, GitHub webhooks and PR publication are subsequent stories.

Requirements: Docker Engine and Compose v2. Commands run from the repository root.
Java 21 and Maven are included in the build container; neither is required on the host.

```sh
# Real PostgreSQL integration tests in a unique disposable Compose project.
# The script removes only that project's containers and volumes on exit.
./scripts/verify-bootstrap.sh

# Build the application without destructive database fixtures.
docker compose -p forgeflow-dev run --rm build mvn -B -DskipTests package

# Apply schemas using the explicit maintenance profile, then exit.
docker compose -p forgeflow-dev run --rm build java -jar control-plane/target/control-plane-0.1.0-SNAPSHOT.jar --spring.profiles.active=migrate

# Run the server. Host exposure is limited to 127.0.0.1:8080.
docker compose -p forgeflow-dev run --rm --service-ports build java -jar control-plane/target/control-plane-0.1.0-SNAPSHOT.jar --server.address=0.0.0.0
```

From a second terminal:

```sh
curl --fail http://127.0.0.1:8080/actuator/health
# {"status":"UP"}
```

Stop the foreground server with Ctrl-C. `docker compose -p forgeflow-dev down`
stops the database while retaining its data. Add `--volumes` only when you want
to discard this project's development database and build cache.

Compose uses a visibly local-only database password; no host database port is
published and no Docker socket is mounted. It does not configure a deployment.
To run outside Compose, build with Java 21/Maven, supply `FORGEFLOW_DB_URL`,
`FORGEFLOW_DB_USER`, `FORGEFLOW_DB_PASSWORD` from your environment/secret store and
run the same JAR. Use a migration identity for the explicit maintenance command
and a distinct DML-only identity for runtime; see the
[persistence architecture](../architecture/control-plane-persistence.md).
Do not put real credentials in source, command arguments, tracked environment
files or logs. Local tests need CREATE DATABASE privileges only on a disposable
PostgreSQL server; they create and remove uniquely named fixture databases.

A failed runtime startup on an empty database is expected: run maintenance first.
A checksum/registry mismatch needs investigation and a new migration/version; do
not repair checksums or overwrite deployed definitions to bypass it.
