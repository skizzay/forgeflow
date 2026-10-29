# Run the local control-plane bootstrap

Implements [#7](https://github.com/skizzay/forgeflow/issues/7). This starts a local
control plane with PostgreSQL, a health endpoint and optional GitHub webhook
ingress. Worker execution and PR publication remain subsequent work.

Requirements: Docker Engine and Compose v2. Commands run from the repository root.
Java 21 and Maven are included in the build container; neither is required on the host.

```sh
# Real PostgreSQL integration tests in a unique disposable Compose project.
# The script removes only that project's containers and volumes on exit.
./scripts/verify-bootstrap.sh

# Build the application without destructive database fixtures.
docker compose -p forgeflow-dev run --rm build mvn -B -DskipTests package

# Run the independent release migration executable, then exit.
docker compose -p forgeflow-dev run --rm build java -jar control-plane/target/control-plane-0.1.0-SNAPSHOT-migrate.jar

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
run the runtime JAR. The separate `-migrate.jar` uses those same environment
variables directly and does not load Spring YAML. `FORGEFLOW_DB_URL` must include
one dedicated domain schema, for example:

```text
jdbc:postgresql://db:5432/forgeflow?currentSchema=forgeflow
```

Use `&currentSchema=forgeflow` when the URI already has other parameters. The
runtime and migrator derive the domain schema from the same URI. Set optional
`FORGEFLOW_ENGINE_SCHEMA` consistently for both commands (default `flowable`).
Schema names must be distinct dedicated lowercase identifiers of at most 63
characters; existing schemas are not renamed when these settings change.
Use a migration identity for the explicit maintenance command
and a distinct DML-only identity for runtime; see the
[persistence architecture](../architecture/control-plane-persistence.md).
Do not put real credentials in source, command arguments, tracked environment
files or logs. Local tests need CREATE DATABASE privileges only on a disposable
PostgreSQL server; they create and remove uniquely named fixture databases.

A failed runtime startup on an empty database is expected: run maintenance first.
A checksum or registry mismatch stops startup and needs investigation. ForgeFlow
is developing its initial release and maintains one V1 schema. Use a fresh,
disposable development database when that schema changes; there is no supported
upgrade path between development revisions. The application never repairs history
or deletes data automatically. Preserve any data you need before recreating a database.

The initial schema includes lifecycle versions, audit records and approval pins.
The lifecycle feature has no public command endpoint yet; its default authority
denies guarded transitions until trusted approval/identity/SCM integrations are installed.

## Optional approved execution backend

Set `forgeflow.execution.enabled: true` in external Spring YAML to register the
packaged `approved-story` v1 workflow and `develop-story` v1 activity and enable
the internal approved-start backend. It defaults to false. Registration and
[manual approval](manual-plan-approval.md) alone start no process or job. There is
no start HTTP endpoint or inbox trigger yet (#84); trusted Java callers can start
one approved run atomically with its initial job and durable Flowable wait (#89).
Repository-specific workflow selection is not implemented (#93). PostgreSQL tests
verify guarded startup, replay/restart, competing starts and engine/domain rollback;
see [persistence](../architecture/control-plane-persistence.md#approved-execution-startup).
