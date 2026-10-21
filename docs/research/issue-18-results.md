# Issue #18: experiment results and synthesis

Issue: [#18](https://github.com/skizzay/forgeflow/issues/18), parent
[Foundation #1](https://github.com/skizzay/forgeflow/issues/1), continuation of
[PR #21](https://github.com/skizzay/forgeflow/pull/21). Results are local; no GitHub
pushes, issue edits, sandbox PRs or merges were performed.

## Results matrix

| Experiment | Result | Evidence and limits |
|---|---|---|
| Version compatibility and persistence | PASS, amd64 | Java 21 / Boot 3.5.4 / Flowable 7.2.0 / PostgreSQL 17.6; 3 original integration tests |
| Project variants and provider substitution | PASS, deterministic providers | Direct and review-first registered workflows use the same `coding-test.v1` contract; separate Java worker completes with each provider. Real Codex remains pending |
| Approval and registry enforcement | PASS within the closed prototype configuration surface | Worker cannot approve; altered plan/config/definition/activity/provider/capability invalidates approval; forged process approval cannot authorize work; arbitrary activity/deployment choices are rejected |
| Authenticated external-worker handoff | PASS, prototype HTTPS | Separate JVM has only API URL/token in its environment, no database credentials; server assigns worker identity and capabilities; unknown token, wrong role, mismatched capability and impersonation are rejected |
| Heartbeat, ownership, cancellation and rotation | PASS, prototype HTTPS | Lease renewal succeeds only for a live owner/fence; foreign/stale/expired operations fail; changed approval blocks active work but allows owner cancellation; token replacement revokes old token while preserving logical worker ID |
| Process crash/replay/reconciliation | PASS with simulated SCM | 6 recovery integration tests plus network-worker crash/restart test; one logical effect; stale result/reconciler rejection; no retry before reconciliation; cancellation race has one terminal outcome |
| Engine schema upgrade | PASS for the tested path | A waiting 7.1.0 workflow survives 7.2.0 automatic schema upgrade with its exact definition and plan/config identity intact |
| Backup/restore | PASS for logical restore | PostgreSQL `pg_dump`/`pg_restore` into another disposable database; engine starts with schema updates disabled and completes the restored wait |
| Isolated execution container | PASS, fixed deterministic workload | Host-side Java controller; non-root UID, read-only root, no host mounts/socket, network disabled, CPU/memory/PID bounds; successful checks, timeout, cancellation and cleanup |
| Resource footprint | MEASURED, host only | Approximately 385 MiB sampled JVM RSS after engine startup and 100 sequential waits/completions; Pi hardware unavailable, no Pi result claimed |
| Java dependency-license inventory | RECORDED | 87 compile/runtime artifacts with reported licenses; no missing-license warnings. This is metadata inventory, not redistribution clearance or an OS/JDK license review |
| Real Codex -> isolated coding/tests -> sandbox PR | BLOCKED / NOT RUN | #13 real-provider dependency is still unverified; no disposable repository designated. Existing host ChatGPT sign-in detected; no API key configured; requested job authentication choice/location. Personal authentication cache was not copied into containers |

## Reproduction and recorded checks

See the [spike README](../../spikes/flowable/README.md) for exact commands.

- Default Maven/Compose suite: **17 tests, 0 failures/errors/skips**: 3 persistence,
  6 recovery and 8 HTTPS handoff cases. Two deterministic provider/variant runs
  execute in the positive worker test.
- Schema script: **3 separate probe executions passed** (prepare 7.1.0, upgrade
  7.2.0, restore/schema-validation 7.2.0). Logical restore is same PostgreSQL major;
  it is not a PostgreSQL-version upgrade or point-in-time recovery rehearsal.
- Container probe: **4 passing outcomes** (fixed checks, timeout, cancellation,
  no fixture containers left). Uses Ubuntu 24.04, independent of the Codex image.
- Resource probe: **1 test passed**. Sample: JVM RSS 393,840 KiB, used Java heap
  110,989,776 bytes, startup-plus-100-runs 3,232 ms with `-Xmx256m`. These are a
  single amd64 sample, not throughput/latency guarantees or deployment sizing.
- Full-suite container cgroup peak charge reached 1,610,612,736 bytes (its 1.5 GiB
  limit). This includes Maven, forked JVMs, child worker/control-plane processes
  and charged file cache; it is not the standalone server's memory requirement.
- [Dependency metadata inventory](evidence/flowable-runtime-licenses.txt) generated
  with License Maven Plugin 2.7.0, compile/runtime scopes. License expressions
  retain upstream POM names and alternatives; no automatic legal interpretation.

No raw job logs, tokens, generated private keys, private source or test databases
are included in this record. Test keys/tokens are generated in temporary storage;
only synthetic fixture credentials appear in source/Compose.

## Design conclusions from the experiments

Flowable can durably own the process wait while ForgeFlow owns external-worker
leases and mandatory domain policy. A registered dispatch activity persists intent
in the engine transaction. A receive task needs no engine lock held across remote
work. Accepted completion and the domain result share one Spring transaction.

ForgeFlow still needs a small, explicit lease/reconciliation service. That is
domain dispatch policy, not a second workflow sequencer: process progression stays
in Flowable. Fencing rejects stale accepted updates; it does not stop old workers
from calling GitHub. External effects stay at-least-once and require correlation,
quiescence evidence and reconciliation before retry. See the
[recovery experiment](flowable-recovery-experiment.md).

Approval is server-owned and fingerprint-bound across definition, plan, config,
activity, provider and capability. Workers cannot supply their own identity or
capability authority in request bodies. Changes need reapproval. The prototype
permits only registered declarative variants, not arbitrary project XML. An
administrator with engine/database access remains trusted; this does not prove
that arbitrary BPMN scripts, classes or expressions are safe to accept.

## Authentication and deployment assumptions

- This spike uses generated bearer credentials over certificate-validated HTTPS,
  with distinct operator/worker identities and server-side capability assignment.
  Tokens live in the trusted server/worker environment and never in process
  arguments or output. The child JVM receives no datasource environment variables.
- Rotation is demonstrated by replacing the configured token and restarting the
  API while retaining the logical subject. Production needs expiring credentials,
  repository/job scope, managed issuer/revocation and a real human approver identity.
  The operator token is a trusted test principal, not a production IdP integration.
- The HTTPS adapter is a narrow JDK server hosted alongside Spring-managed
  services. It is not the production Spring MVC/Security deployment or a complete
  production worker API. It intentionally exposes no raw engine/deployment routes.
- Claims are concurrent per job; there is no global one-active-job scheduler,
  provider quality assessment, full context builder or story-dependency engine.
- Heartbeats and completion are rechecked against approval. Cancellation records
  a terminal decision but does not undo an external side effect or itself stop a
  real job container. The separate host container probe demonstrates termination,
  not its production integration with the authenticated API.
- Production should apply reviewed engine upgrade SQL with a migration identity,
  then start runtime with schema updates disabled; the rehearsal used automatic
  upgrade only in a disposable database. Back up before upgrading; rollback means
  restore the matching backup/software, not an assumed in-place downgrade. Engine
  and ForgeFlow schema ownership remain separate. No production schema is adopted.

## Overall synthesis — provisional until the blocked run

**The completed experiments support Flowable's technical fit. They have not
revealed a reason to reject it. Overall Issue #18 remains HOLD, rather than GO.**

The remaining decision evidence is the real Codex/container/GitHub path and #13's
supported authentication/usage evidence. A disposable repository and agreed test
credential/authentication method are required to run that safely and attribute the
result. Pi measurements are explicitly unavailable, as allowed by the issue;
capacity on the Pi remains unverified. Production credential/approval integration,
patched version selection, release notices and deployment hardening remain delivery
requirements; none is implied by passing fixture tests.

The next action is the blocked real-provider run, not another generic POC. Keep
#18 open and #7 dependency-gated until the full evidence and adoption decision are
reviewed. No issue or prerequisite is marked complete by this document.

## Sources

- [Flowable OSS source/license](https://github.com/flowable/flowable-engine)
- [Engine configuration and automatic schema upgrades](https://www.flowable.com/open-source/docs/bpmn/ch03-Configuration/)
- [License inventory plugin](https://www.mojohaus.org/license-maven-plugin/add-third-party-mojo.html)
- [Codex authentication](https://learn.chatgpt.com/docs/auth)
