# Separate job, story and epic lifecycle

Implements [#14](https://github.com/skizzay/forgeflow/issues/14), parent
[Foundation #1](https://github.com/skizzay/forgeflow/issues/1).

## Boundaries

`ChangeLifecycle` is a Java input port with a framework-free application/domain
core. The JDBC adapter updates the existing job/story/epic tables. Spring supplies
the transaction adapter and composes a default deny-unverified authority adapter.
The lifecycle port itself has no HTTP endpoint, scheduler, GitHub client or Docker action.
The opt-in [manual approval command](manual-plan-approval.md) supplies narrowly
scoped human authority for the configured epic; the general authority still denies
unverified transitions.

Flowable owns process/activity progression. The domain entities own guarded
ForgeFlow domain transitions; they do not sequence or complete BPMN activities.
GitHub PR/check state is independent authoritative evidence. DAG eligibility is
an independently verified prerequisite, not a workflow-generated decision. Job
SUCCEEDED changes only the job; it cannot imply story MERGED, epic COMPLETED,
BPMN completion, a mainline merge or release.

## Domain composition

The model is organized by concept within the lifecycle hexagon. These are in-process domain
boundaries, not separate deployments or database schemas.

| Package | Building blocks | Responsibility |
|---|---|---|
| `domain.job` | `Job` entity; execution/attempt identities and lease evidence; job transitions and reconciliation policy | Pinned execution, authenticated attempt ownership, terminal outcomes and bounded recovery |
| `domain.story` | `Story` entity; merge target and PR observation values; story transitions | Readiness, approval invalidation and exact verified PR binding |
| `domain.epic` | `Epic` entity and epic transitions | Plan approval, activation and final completion gates |
| `domain.approval` | Approval identity value and shared approval policy | Current/stored approval equality and parent approval consistency |
| `domain.evidence` | Actor authority, recovery, delivery and completion values | Separate trusted observations with explicit meanings |
| `domain` | Entity reference, parent context, closed state vocabulary, command, receipt and failures | Small common contract for transactional lifecycle operations |

`Job`, `Story` and `Epic` are immutable aggregate roots whose equality follows
entity identity across versions. Value objects retain structural equality. Their transition methods
check the addressed identity, expected state/version, allowed transition and
required evidence, then return a new entity version. Approval pinning,
invalidation and cancellation behavior belongs to those entities. Entity
construction is also used for persistence rehydration; it does not confer
permission to execute or persist a transition. The common sealed state families
remain together because this Maven module uses Java's unnamed module.

The repository loads a typed root with parent/completion observations under
root-to-leaf locks. A child does not
embed or mutate its parent entity. `ApprovalPolicy` composes cross-root identity
rules; `ReconciliationPolicy` combines recovery authority, quiescence and lease
observations. Each entity receives only the evidence relevant to its rules.
`AuthorityFacts` is an adapter-result composition of these evidence values. None of these types independently
verifies GitHub or a human identity; that remains the trusted adapter's job.

`LifecycleService` coordinates locking, trusted evidence and local lease/job
queries, actor-bound replay, aggregate invocation, receipts and the transaction.
The JDBC adapter persists the returned state/version/approval and appends audit
history atomically. Clearing an approval pin retains the prior approval in the
audit record. `TransitionPolicy` is a read-only table-query facade; the
individual aggregate transition tables own its rules. The shared repository port
uses one transaction for state and audit persistence. Attempt lease allocation
and heartbeat behavior remain #9 rather than introducing a second lease model.

## Internal approved-parent preparation

`PrepareApprovedStory` verifies immutable approval evidence and the exact registered
definition for the selected `ManualPlan`, under root-to-leaf locks. The supported
plan has one child and carries no dependency edges; multi-story/DAG plans are
rejected. It applies existing guarded epic activation and story readiness/activation
in one transaction. Already-active matching parents return the definition without
new transitions. Missing/revoked/mismatched approval fails closed. Its private
system authority never replaces the general deny-unverified authority.

This internal port starts no engine instance or job and has no HTTP/polling entry.
The later starter (#89) must wrap preparation and engine/domain writes in the
same outer transaction. Tests verify valid/idempotent preparation, denial and
outer rollback; the original human approval remains immutable evidence.

## Transition contract

Each command identifies one aggregate, the actor authenticated by its inbound
adapter, expected state/version, target state and a unique command ID. The actor
is an explicit authorization reference, never a principal read from global thread
state or accepted from a request body without authentication. Job commands additionally pin run/workflow/activity identity
and, for worker operations, attempt/worker/fence identity. Unlisted transitions,
cross-aggregate states and direct terminal-state changes are rejected. An identical
command is a replay, not another transition; only the verified original logical
actor can receive the cached receipt. Reusing the command ID with changed input
is a conflict. Length-prefixed scalar fields produce the request fingerprint.

### Jobs

| From | To | Required evidence |
|---|---|---|
| PENDING | RUNNING | Current matching approval, active parents with the same approval, merged dependencies, authenticated live attempt owner/fence |
| RUNNING | SUCCEEDED / FAILED | Same pinned execution/approval and authenticated live attempt owner/fence; active parents |
| RUNNING | RECONCILING | Authorized recovery and no live lease |
| RECONCILING | PENDING | Current matching approval/parents, authorized recovery, quiescent reconciled external effects, no live lease |
| RECONCILING | SUCCEEDED / FAILED | Current matching approval and authorized, quiescent reconciliation reporting that exact outcome; no live lease |
| PENDING / RUNNING / RECONCILING | CANCELLED | Independently authorized stop, including after approval revocation |

SUCCEEDED, FAILED and CANCELLED are terminal. A replay returns its historical
receipt without changing current state. Retry is held at RECONCILING until
external effects are reconciled; lease expiry alone cannot authorize another
attempt. Finishing, cancelling or releasing a reconciled retry closes the old
attempt. Allocating/incrementing fences, claiming jobs and heartbeats remain #9.

### Stories

| From | To | Required evidence |
|---|---|---|
| DRAFT | READY | Current approval matching the active parent epic and merged dependencies; persist the approval pin |
| READY | ACTIVE | Matching stored/current/parent approval and merged dependencies |
| READY | DRAFT | Authorized invalidation; clear the stored approval pin |
| ACTIVE | PR_OPEN | Successful job under that approval and a verified expected PR binding |
| PR_OPEN | MERGED | Verified GitHub merge for the exact repository ID, PR number, epic base branch and head SHA, with a merge commit SHA |
| DRAFT / READY / ACTIVE / PR_OPEN | CANCELLED | Independently authorized stop |

MERGED and CANCELLED are terminal. The existing PR number and expected head
SHA bind observations to the story; the expected base comes from its epic branch.
The scoped publisher/reconciler must populate/verify this binding (#11/#15).
An open PR or a completed job is insufficient merge evidence. State cancellation
does not close a PR, kill a container or undo external effects. Conflicting later
SCM observations require reconciliation/escalation, not automatic resurrection.

### Epics

| From | To | Required evidence |
|---|---|---|
| DRAFT | APPROVED | Verified current human plan approval; persist its identity |
| APPROVED | ACTIVE | The same stored/current approval |
| APPROVED | DRAFT | Authorized invalidation; clear the stored approval pin |
| ACTIVE | COMPLETED | At least one child story, every child MERGED, matching approval, integration checks passed and a verified final human approval ID |
| DRAFT / APPROVED / ACTIVE | CANCELLED | Independently authorized stop |

COMPLETED and CANCELLED are terminal. COMPLETED records accepted domain integration;
it does not assert that an epic-to-main PR merged or a release shipped. Those
GitHub/release gates remain #5. Active identities are not silently rewritten;
a changed plan blocks authorization. Stopping/reconciling and a new approved
execution/plan are required when active work cannot continue under its old pin.

## Identity, authority and trust

`ApprovalIdentity` binds approval UUID, plan revision and configuration SHA-256.
Jobs additionally bind workflow key/version, exact engine definition, activity
key/version and run UUID loaded from existing normalized records. Story/epic
approval pins are stored by guarded approval/readiness transitions. Child pins
must agree with the parent hierarchy; changing a configuration/current approval
cannot make an old job eligible or overwrite its pinned execution identity.

`LifecycleAuthority` supplies facts for the exact entity and explicit authenticated
principal: verified actor/worker identity, current approval, merged dependencies,
recovery/quiescence, reconciled outcomes, SCM observations and final approval/check
proof. Commands do not carry authorization booleans. A supplied worker ID must
match the server-authenticated worker as well as the live lease/fence record.
Configured workflows, labels and model output cannot manufacture these facts.

The general production default supplies no verified facts and denies guarded transitions.
The manual approval use case privately supplies authenticated human facts for
approval/invalidation of its configured epic only; it cannot authorize dispatch.
There is no configuration switch to waive policy. Future trusted identity/approval
and GitHub adapters (#9/#12/#15/#5) must implement this contract with real evidence;
replacing the authority bean is reviewed server code, not project-controlled data.
The authority adapter must return transactionally current, previously verified
facts; remote GitHub reconciliation belongs outside the state-lock transaction.
Tests inject explicit trusted fixtures and do not claim actual IdP or GitHub
validation. Inbound APIs must authenticate and scope every command/replay before
calling this port. No dispatch or merge is enabled by the fixture tests.

## Persistence, transactions and recovery

The initial V1 schema defines state versions, story/epic approval pins, the
story's expected PR head and `lifecycle_transition` audit records. Approval pins
are nullable until the corresponding guarded approval/readiness transition.

A transaction locks root-to-leaf (epic, story, job), checks expected state/version,
authorizes, writes state/identity and appends its audit receipt atomically. CAS
protects the update and a unique command/version prevents duplicate history.
Audit records include trusted actor, approval/execution/attempt identity, final
approval/SCM evidence and timestamp; the application exposes no audit rewrite API.
Storage failures produce a typed failure and roll back state and audit together.
A receipt returned within a larger existing transaction must be acknowledged only
after that outer transaction commits. Future activity-completion callers must
join the same transaction when advancing Flowable; this service does not do that.

Root locks serialize state transitions within an epic, a deliberate first-slice
tradeoff for one active job. Future story insertion/binding operations must use
the same parent lock and reject closed parents to preserve completion checks.
State version is separate from the worker fence. Production worker/reconciler
adapters still own atomic lease allocation/heartbeat and external-effect recovery;
no cross-process or remote-effect exactly-once claim is made.

## Verification

Table-driven policy tests cover all 97 same-aggregate state pairs and reject
cross-aggregate states. PostgreSQL tests cover isolated job/story/engine state,
expected SCM bindings, parent/child approval and workflow version mismatch,
authenticated ownership/fences, expired lease holds and reconciled retry, explicit
plan invalidation/reapproval, final approval/check requirements, command replay
across restart, conflicting commands, cancellation races and audit rollback.
Pure entity tests additionally exercise immutable version progression, approval
pinning/invalidation, cancellation after revocation, authenticated ownership,
recovery evidence, exact PR bindings and final human gates without application
or persistence infrastructure. Compiled-class architecture rules enforce a
Java-only core, port direction and separation between aggregate packages.
The full regression suite also retains webhook and bootstrap/restart coverage.
