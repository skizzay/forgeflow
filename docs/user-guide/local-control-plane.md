# Run the local control-plane bootstrap

Implements [#7](https://github.com/skizzay/slickflow/issues/7). This starts a local
control plane with PostgreSQL, a health endpoint and optional GitHub webhook
ingress. Worker execution and PR publication remain subsequent work.

Requirements: Docker Engine and Compose v2. Commands run from the repository root.
Java 21 and Maven are included in the build container; neither is required on the host.

```sh
# Real PostgreSQL integration tests in a unique disposable Compose project.
# The script removes only that project's containers and volumes on exit.
./scripts/verify-bootstrap.sh

# Build the application without destructive database fixtures.
docker compose -p slickflow-dev run --rm build mvn -B -DskipTests package

# Run the independent release migration executable, then exit.
docker compose -p slickflow-dev run --rm build java -jar control-plane/target/control-plane-0.1.0-SNAPSHOT-migrate.jar

# Run the server. Host exposure is limited to 127.0.0.1:8080.
docker compose -p slickflow-dev run --rm --service-ports build java -jar control-plane/target/control-plane-0.1.0-SNAPSHOT.jar --server.address=0.0.0.0
```

From a second terminal:

```sh
curl --fail http://127.0.0.1:8080/actuator/health
# {"status":"UP"}
```

Stop the foreground server with Ctrl-C. `docker compose -p slickflow-dev down`
stops the database while retaining its data. Add `--volumes` only when you want
to discard this project's development database and build cache.

Compose uses a visibly local-only database password; no host database port is
published and no Docker socket is mounted. It does not configure a deployment.
To run outside Compose, build with Java 21/Maven, supply `SLICKFLOW_DB_URL`,
`SLICKFLOW_DB_USER`, `SLICKFLOW_DB_PASSWORD` from your environment/secret store and
run the runtime JAR. The separate `-migrate.jar` uses those same environment
variables directly and does not load Spring YAML. `SLICKFLOW_DB_URL` must include
one dedicated domain schema, for example:

```text
jdbc:postgresql://db:5432/slickflow?currentSchema=slickflow
```

Use `&currentSchema=slickflow` when the URI already has other parameters. The
runtime and migrator derive the domain schema from the same URI. Set optional
`SLICKFLOW_ENGINE_SCHEMA` consistently for both commands (default `flowable`).
Schema names must be distinct dedicated lowercase identifiers of at most 63
characters; existing schemas are not renamed when these settings change.
Use a migration identity for the explicit maintenance command
and a distinct DML-only identity for runtime; see the
[persistence architecture](../architecture/control-plane-persistence.md).
Do not put real credentials in source, command arguments, tracked environment
files or logs. Local tests need CREATE DATABASE privileges only on a disposable
PostgreSQL server; they create and remove uniquely named fixture databases.

A failed runtime startup on an empty database is expected: run maintenance first.
A checksum or registry mismatch stops startup and needs investigation. SlickFlow
is developing its initial release and maintains one V1 schema. Use a fresh,
disposable development database when that schema changes; there is no supported
upgrade path between development revisions. The application never repairs history
or deletes data automatically. Preserve any data you need before recreating a database.

The initial schema includes lifecycle versions, audit records and approval pins.
The lifecycle feature has no public command endpoint yet; its default authority
denies guarded transitions until trusted approval/identity/SCM integrations are installed.

## Optional approved execution backend

Set `slickflow.execution.enabled: true` in external Spring YAML to register the
packaged `approved-story` v1 workflow and `develop-story` v1 activity and enable
the internal approved-start backend. It defaults to false. Registration and
[manual approval](manual-plan-approval.md) alone start no process or job. There is
no start HTTP endpoint; trusted Java callers and the optional inbox dispatcher can start
one approved run atomically with its initial job and durable Flowable wait (#89).
Repository-specific workflow selection is not implemented (#93). PostgreSQL tests
verify guarded startup, replay/restart, competing starts and engine/domain rollback;
see [persistence](../architecture/control-plane-persistence.md#approved-execution-startup).


## Optional approved event dispatch

For one configured test story, set the following in an external Spring YAML file
alongside your existing [manual approval](manual-plan-approval.md) settings and
[webhook ingress](github-webhooks.md) secret. Replace the illustrative identifiers
with the already provisioned local repository and exact GitHub installation/issue
IDs. `issue-id` is GitHub's issue object ID, not the issue number.

```yaml
slickflow:
  execution:
    enabled: true
    inbox:
      enabled: true
      repository-id: 00000000-0000-0000-0000-000000000001
      installation-id: 7
      issue-id: 99
      poll-interval: PT1S
```

Inbox dispatch defaults to disabled. Enabling it requires the approved-execution
backend and configured manual approval (including OAuth2 issuer/subject and all
reviewed pins); missing dependencies or invalid positive IDs/interval fail startup.
Webhook ingress and classification must also be enabled to supply new events.
The story comes from the manual approval configuration, and its local issue number
and repository external ID must match the delivery. This is not repository discovery
or provisioning. Do not place real secrets or operator tokens in tracked files.

A verified, classified `issues/opened` delivery waits durably for current human
approval, then starts one `approved-story` process and PENDING job. The poller first
runs after one interval and handles at most 100 successful deliveries per tick;
any failure stops the batch and retries on the next tick with fixed, redacted log
text. Check `workflow_source_delivery` for the delivery/run binding, `workflow_run`
and `job` for intent, and inbox classification for ingress progress. A processed
inbox row without a checkpoint may still be awaiting approval or a storage retry.
Restart or duplicate deliveries reuse the pinned run. Unsupported events and
mismatched targets stay outside this single-story dispatch slice. Disable
`slickflow.execution.inbox.enabled` to stop automatic startup; it does not cancel
existing work. Worker execution, repository writes and PR publication remain absent.

## Project identity and existing local resources

The project is SlickFlow, with domain `slickflow.ai`, Java package root and Maven
group ID `ai.slickflow`, parent artifact `slickflow`, and repository
[skizzay/slickflow](https://github.com/skizzay/slickflow).
Tracking: [#35](https://github.com/skizzay/slickflow/issues/35); the delivery order
is inventory #63, Java/Maven #64, runtime assets #65, then repository administration
#66. Naming clearance follow-up #67 is tracked separately. Implementation changes
do not satisfy story dependencies until their PRs are merged into the required branch.

Use `slickflow.*` Spring properties, `SLICKFLOW_*` environment variables and the
`slickflow.worker` OAuth2 scope consistently in deployments and identity providers.
The Java source and test trees follow `ai/slickflow`; packaged entry points use
that namespace. BPMN definitions use `https://slickflow.ai/workflows`.
These settings have no compatibility aliases.

The development Compose project is `slickflow-dev`; its default database and
domain schema are `slickflow`, with local user `slickflow_local`. Switching project
names selects separate Compose volumes. Existing containers, volumes, database
schemas and external identity-provider settings are never renamed or deleted by
this change. Preserve needed data and use a fresh disposable development database
for the renamed V1 schema and BPMN resources; startup does not rewrite stored
workflow definitions or migration history. Retire previous resources only after
explicitly identifying them and preserving any needed data.

Repository links use the new GitHub name, including links to archived research.
Git history and Git-managed worktree locations remain historical records; the
checkout directory is managed by the development environment. Check external
GitHub App/webhook URLs, OAuth2 scope grants, deployment secrets and domain/DNS
configuration in their owning systems; source changes do not provision them.
