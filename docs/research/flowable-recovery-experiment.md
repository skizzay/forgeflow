# Flowable external-worker recovery experiment

Issue: [#18](https://github.com/skizzay/forgeflow/issues/18), parent
[Foundation #1](https://github.com/skizzay/forgeflow/issues/1).
Related PR: [#21](https://github.com/skizzay/forgeflow/pull/21), kept open.
Dependencies: none for simulated effects; real Codex/GitHub evidence requires #13.

## Question and scope

Can a durable Flowable wait and a separately leased job recover from process
failure without blindly repeating an external effect or accepting a stale worker?

This is Java/Spring Boot/PostgreSQL prototype code. It uses real PostgreSQL
transactions, a real Flowable engine, concurrent callers and child JVMs that
terminate through `Runtime.halt`. The SCM is a synthetic filesystem fixture,
not GitHub. Approval and authentication are outside this recovery experiment.

## Transaction and retry ownership

1. A trusted fixture BPMN service task inserts a uniquely keyed dispatch intent
   in the same database transaction that reaches a receive task. Duplicate intent
   insertion rejects and rolls back the second process start.
2. The trusted control-plane service atomically claims READY work and increments
   its fencing counter. PostgreSQL supplies lease time. Workers do not hold a
   Flowable lock while doing external work. The durable receive task is the
   engine wait; the application owns the separate lease and reconciliation state.
3. External work happens outside the engine/database transaction. The fake SCM
   permits multiple PRs for the same job, so blindly repeating creation would
   visibly duplicate effects. Tests do not rely on fake SCM deduplication.
4. Completion locks the job, checks worker/fence/live lease, updates its result,
   and triggers the receive task under one Spring transaction manager/datasource.
   Exact replay is acknowledged without triggering the process again; conflicting
   evidence is rejected. Job SUCCEEDED does not mean story MERGED.
5. Expiry does not make work claimable. Reconciliation first fences the old worker,
   then releases the database transaction before external lookup. Its observation
   is applied under a new transaction and generation check. A restarted reconciler
   invalidates earlier observations. Found effects complete the original wait.
6. Absent effects permit retry only when the trusted caller also establishes that
   the previous attempt is stopped and cannot still publish outstanding effects.
   Uncertain observations or insufficient stop evidence produce BLOCKED. There
   is no automatic unblock/retry scheduler in this prototype.
7. Cancellation and completion serialize on the same job row. Whichever commits
   first wins; cancellation fences the old worker and deletes the engine instance
   atomically. This does not undo an external effect or physically stop a process.

## Experiments and pass/fail criteria

| Case | Failure/action injected | Required result |
|---|---|---|
| R1 | Worker child JVM creates a fake PR and halts before reporting; engine context restarts | Job remains RUNNING until expiry/reconciliation; one external effect; stale completion rejected; original wait completes exactly once |
| R2 | Separate control-plane JVM updates job and triggers engine, then halts before transaction commit | Both changes roll back; original wait and RUNNING job survive; replay commits both; duplicate receipt is harmless |
| R3 | Lease expires; a reconciler is restarted before applying its observation | No immediate claim; expired worker rejected; old reconciliation generation rejected; authoritative absence plus stopped-attempt evidence permits a new, higher-fenced claim |
| R4 | External lookup is uncertain, or no effect is found but stopping the prior attempt is unproven | BLOCKED; another worker cannot claim |
| R5 | Two workers claim concurrently; another workflow dispatches the same job key | Exactly one claim succeeds; duplicate process creation rolls back, leaving one active workflow for that key |
| R6 | Cancellation and successful completion race on different threads/connections | One consistent terminal outcome; cancelled attempt cannot resurrect; no active engine instance remains |

Any duplicate effect, accepted stale result, job/engine divergence, or blind retry
fails this experiment. Failures should leave the adoption decision HOLD and be
investigated before further integration.

## Reproduce and evidence

Run the Compose commands in the [spike README](../../spikes/flowable/README.md).
The six cases are executable in `RecoveryExperimentTest`, alongside the three
original `WorkflowPersistenceTest` cases. Leases are expired by setting stored
deadlines in test code; no sleep-based expiry assumptions are used. Child processes
have bounded waits and are forcibly cleaned up on failure. Their output is discarded.

Local result: **9 tests passed, 0 failures/errors/skips** on Linux amd64 using
Java 21, Spring Boot 3.5.4, Flowable 7.2.0 and PostgreSQL 17.6. Worker and
control-plane crash injections reached their expected exit codes (73 and 74).

The initial POC baseline is commit `397461f`. New recovery work is local only;
GitHub PR #21 is not updated or merged by this experiment.

## Limits and next decision

- The worker fixture writes synthetic PR evidence to a separate filesystem, never
  reads PostgreSQL, and is not a deployed/authenticated worker API. Control-plane
  methods are in-process trusted services. Capability matching, heartbeats,
  authorization and actual controller/provider isolation remain untested.
- `Runtime.halt` demonstrates process failure, not host power loss or PostgreSQL
  restart. Filesystem evidence survives child-JVM exit; this is not a storage
  durability or backup/restore test. Compose database data is deliberately ephemeral.
- Lookup is immediate and local. Real GitHub timeouts, eventual visibility,
  in-flight requests and ambiguous effect correlation need their own adapter tests.
  The stop-evidence boolean is a trusted test assertion, not a production proof
  of quiescence. Killing a worker alone cannot prove a remote request will not finish.
- Fences protect accepted control-plane updates; they cannot prevent an already
  running agent from making external API calls. Production must stop/restrict old
  execution and reconcile outstanding effects before allowing another publisher.
- The unique job key prevents duplication for that intent, not a global one-job
  scheduler or a complete story-dependency policy. Approval checks from the initial
  fixture are not integrated with the recovery fixture, and no production dispatch
  endpoint is exposed.
- No engine async retries are enabled. Production retry limits, escalation,
  outbox/notification delivery, schema migrations and upgrade/restore remain open.

**Historical recovery-checkpoint result: the simulated recovery slice passed;
overall adoption was HOLD at that point.** Subsequent results and the maintainer's
acceptance are in the [current results](issue-18-results.md). The next experiments
at that checkpoint were to validate authenticated worker/approval boundaries and real provider
feasibility; do not turn these fixture results into a claim of exactly-once GitHub
effects or completion of #18. See the
[decision record](../architecture/decisions/0002-flowable-spike-in-progress.md).
