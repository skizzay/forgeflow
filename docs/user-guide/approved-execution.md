# Approved execution backend

[#83](https://github.com/skizzay/forgeflow/issues/83) provides the atomic backend
for #47. There is no public start endpoint or inbox trigger yet; #84 will connect
one verified, configured test-story event. Enabling this feature alone launches
no process, container or agent.

Use a fresh disposable database after the V1 schema change. Follow the
[local control-plane guide](local-control-plane.md) to migrate before runtime.
The schema now permits only one workflow run per story/approval identity.

Enable registration and the trusted Java ports using external Spring YAML:

```yaml
forgeflow:
  execution:
    enabled: true
```

This registers `approved-story` version 1 and the initial `develop-story` activity
version 1. It also retains the existing bootstrap registration. Query trusted
registry metadata to select the exact reviewed content:

```sql
select workflow_key, registry_version, content_sha256, definition_id
from workflow_definition where workflow_key = 'approved-story';
```

Provision one epic with exactly one DRAFT story through trusted setup. Use the
[manual approval command](manual-plan-approval.md) with `workflow-key: approved-story`,
`workflow-version: 1` and the actual registered definition hash, plus the reviewed
plan/configuration identity. Approval of `bootstrap` cannot authorize this process.
The bounded plan model supports no dependency edges; do not provision a DAG or
multi-story epic through this facility.

A trusted control-plane caller can invoke `StartApprovedExecution.start` with
that reviewed `ManualPlan`. There is no consumer command for invoking it in this
slice. The method verifies current stored approval and returns run/process/job IDs
only after its transaction commits (or after the surrounding transaction commits,
when called within one). It prepares parent states through existing guards,
starts the pinned BPMN definition and leaves one PENDING job and one durable
`awaitOutcome` wait. It does not claim the job, run Codex or complete the workflow.
Repeated/concurrent calls for that approved story return the same correlation.
Changed/revoked approval cannot silently create a new run. No automatic retry of
expired attempts or merge permission is implied.

Run `./scripts/verify-bootstrap.sh` for the full suite. The focused
`ApprovedExecutionIntegrationTest` uses disposable PostgreSQL, trusted approval
fixtures and the actual engine; OAuth2 human authentication is verified separately
by the manual-approval HTTP tests. A trigger failure and an outer-transaction
failure verify that neither engine nor domain records survive rollback. Tests
also verify restart/concurrency deduplication and the actual BPMN wait. They do
not exercise a live webhook/provider, worker or Docker execution.

See [architecture](../architecture/approved-execution.md) for the transaction and
hexagonal boundaries. #84 must preserve them when connecting the inbox.
