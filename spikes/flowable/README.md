# Flowable decision spike — initial implementation

Tracks [#18](https://github.com/skizzay/forgeflow/issues/18) under
[Foundation #1](https://github.com/skizzay/forgeflow/issues/1).
This is the first dependency-ready implementation for the milestone 1 foundation.
The initial POC baseline is commit `397461f`. The local continuation adds the
[recovery experiment](../../docs/research/flowable-recovery-experiment.md).
Issue #18 allows independent fixtures; its real provider/PR evidence depends on #13.
It is not the production bootstrap (#7) or a completed adoption decision.

## Reproduce

Requires Docker and Docker Compose; no host Java installation is needed:

```sh
cd spikes/flowable
docker compose -p forgeflow-spike-18 up --abort-on-container-exit --exit-code-from tests
docker compose -p forgeflow-spike-18 down
```

Tests use Java 21, Maven 3.9.9, Spring Boot 3.5.4, Flowable OSS 7.2.0,
and PostgreSQL 17.6. These are a pinned compatibility experiment, not a current
production support/security recommendation. The Flowable 7.2.0
[release notes](https://github.com/flowable/flowable-engine/releases/tag/flowable-7.2.0)
identify Boot 3.5.4 as that release's baseline. Newer rolling
[Spring Boot integration documentation](https://www.flowable.com/open-source/docs/bpmn/ch05a-Spring-Boot/)
describes newer versions; do not mix its version assumptions into this test.
Resolve security/support updates before promoting this stack. Image tags float;
the procedure is reproducible, not bit-identical.

PostgreSQL has no published host port and stores disposable fixture data in tmpfs.
The example password is public fixture data, not a credential for any real system.
Compose mounts this spike's source into a trusted Maven test container. It never
mounts the Docker socket or host credentials and does not launch untrusted jobs.
Only the process engine starter is used; IDM, event registry and async execution
are disabled. There is no public web server or engine REST API.

## Initial POC: implemented and verified

Three PostgreSQL integration tests pass on Linux amd64:

- A waiting v1 process retains its exact process-definition ID when v2 is
  deployed, including after the Spring context/engine closes and restarts.
  Plan/configuration references survive the restart.
- The registered `codingTestV1` delegate checks a separate approval fixture
  against business key, process-definition ID, plan digest and configuration
  digest. Missing/changed approval and an unregistered provider reject activity
  execution; failed task completion rolls back and leaves the task pending.
- Two project fixture definitions invoke the same versioned activity with two
  deterministic test providers and produce distinct sanitized evidence.

The tests assert state from engine/history/database APIs rather than model text.
Expected negative cases produce engine exception logs with synthetic fixture data.
No raw execution logs, credentials, user data or repository source are published.

## Recovery experiment: implemented and verified

Six additional PostgreSQL integration cases exercise dispatch intent, competing
claims, expiry/fencing, reconciliation, idempotent completion and cancellation
races. Separate JVMs hard-exit after a synthetic external effect and before a
control-plane transaction commits. The latter proves that the domain result and
Flowable receive-task completion roll back together. The former recovers one
existing synthetic PR without rerunning creation after an engine-context restart.

**Local result: 9 tests pass in total (3 original + 6 recovery).** See the
[research plan and results](../../docs/research/flowable-recovery-experiment.md)
for pass/fail criteria, transaction/retry ownership and limits. SCM lookup and
stop evidence are simulated; there is no real worker API or GitHub integration.

## Boundaries and open evidence

Approval rows are inserted by trusted test code. There is no human identity or
authorization interface yet. Process variables are pinned references checked by
the delegate, not an immutable approved-plan registry. BPMN deployment is trusted
application code only; there is no project XML upload API. The delegate's guard
does not prove arbitrary configurable BPMN is safe. Registered-activity/schema
validation and enforcement at every real side-effect boundary remain required.

The fixture approval table and Flowable engine share a datasource and Spring
transaction manager. The rollback test proves rejection during engine completion;
it does not prove outbox/dispatch or external side-effect atomicity. Flowable
creates its own tables with `database-schema-update=true` in the disposable test
database. Production schema migrations, least-privilege runtime credentials,
engine upgrade rehearsals and domain/engine transaction ownership remain #7/#18
work. The `spike_approval` table is test-only, not a production migration.

Not implemented or measured yet:

- Authenticated versioned worker API, scoped worker identity/TLS/rotation.
- Capabilities, heartbeat and production worker lease/engine-lock integration.
  Claim/fencing, cancellation and reconciliation now have simulated recovery
  evidence; authenticated/distributed worker integration remains open.
- Docker coding/testing activity, Codex provider, GitHub effect reconciliation,
  or a real sandbox issue-to-PR run (#13 evidence required). Synthetic PR recovery
  does not validate real GitHub behavior.
- Pi resource measurements (hardware unavailable), full-host/database crash tests,
  immutable activity registry and security against untrusted workflow definitions.
- Full dependency license/notice inventory. Flowable OSS source is Apache-2.0;
  no enterprise distribution is included. Review transitive dependencies and
  redistribution obligations before distributing a runtime image.

## Decision

**HOLD — insufficient evidence to adopt Flowable for production.** This initial
slice establishes embedded PostgreSQL persistence/versioning, a guarded
activity seam and simulated external-worker recovery. It does not close #18 or
satisfy #7's decision dependency.
See [the decision record](../../docs/architecture/decisions/0002-flowable-spike-in-progress.md).

Verification: Compose test exit 0; 9 tests, 0 failures/errors/skips; `git diff --check`.
The architecture proposals in PRs #19 and #20 remain unmerged and are not silently
incorporated into main. Their Java/identity refinements are treated as requirements
where reflected in the current GitHub issues, without asserting human adoption.
