# Flowable and provider boundaries

Target design for [#77](https://github.com/skizzay/forgeflow/issues/77).
The [worker protocol](../requirements/worker-protocol.md) is the execution-plane
contract within this design, not the workflow definition or provider API.

The [development workflow reference](workflow-reference.md) supplies a matching
non-executable BPMN model and Mermaid process views for this target event cycle.

The [staged review/feedback contract](../requirements/review-and-feedback.md)
defines QA, required specialist-agent reviews and human review for each PR head,
plus a separate durable feedback store and governed skill-improvement workflow.

## Responsibility and implementation status

| Responsibility | Owner | Current status |
|---|---|---|
| Activity order, gateways, durable waits and business retry paths | Flowable BPMN | Bootstrap waits at `awaitImplementation`. Opt-in approved-story v1 queues its initial job and waits at awaitOutcome through the trusted Java starter (#89). Opt-in configured issue-opened dispatch (#84) invokes that starter; worker execution and later progression remain pending. |
| Approval authority, attempt ownership, leases, fencing and verified evidence | Focused Java domain policies and activity handlers | Lifecycle guards, OAuth2 worker authentication and single-pair manual human approval exist; the opt-in atomic Java starter exists; configured inbox startup exists; worker operations remain unimplemented. |
| Job/story/epic transition model | Java lifecycle domain | Implemented today; must not grow into a parallel workflow sequencer. Independent business invariants remain, while BPMN owns process routing. |
| Event ingestion and process correlation | Inbound adapters plus durable inbox and Flowable entry handlers | GitHub inbox verification/classification exists; one configured issue-opened event can start/checkpoint an approved run (#84); general normalization and process resume remain proposed. |
| Review feedback and skill improvement | Separate feedback datastore and evaluated, human-approved skill workflow | Proposed; no feedback-store adapter, review aggregation or skill-promotion runtime is implemented. |
| Ticket operations | Ticket ports and provider adapters | Proposed; no provider-neutral ticket port is implemented. |
| Repository/change-request operations | Repository ports and provider adapters | Proposed; current GitHub-specific schema and webhook code are not a pluggable repository implementation. |

Do not infer a production orchestration path from the presence of Flowable or a
successful Java state transition. Review the existing lifecycle responsibilities
against this boundary before extending story/epic progression.

## Ticket and source-repository ports

Tickets and repositories are separate integrations, even when both use GitHub.
A Jira work item may target a GitLab repository without a combined Jira/GitLab
adapter. Provider selection is trusted server configuration, not worker input.

| Boundary | Minimum activity-facing capability | Provider-neutral result |
|---|---|---|
| Ticket query | Read a work item and its linked references | Work-item details/acceptance input; plan validation remains ForgeFlow policy |
| Repository query | Read branch/head, change request and check observations | Exact normalized refs, commit identities and evidence, including unknown state |
| Branch/commit publication | Publish or confirm the expected branch/commit | Bound branch and commit identity |
| Change-request publication | Publish or confirm a request for the expected base/head | `ChangeRequestRef` and exact binding |
| Change-request merge | Request merge only for the approved exact base/head under mandatory checks and human authority | Verified merged identity, or explicit failure/unknown outcome |

These are small outbound ports required by specific activities, not a mandatory
single interface covering every provider API. GitHub adapters implement the first
concrete integration. Other adapters must explicitly report unsupported required
capabilities; an absent review/check capability must never be treated as passed.
Provider payloads, credentials, SDKs, webhook signatures and status translation
stay in adapters. Provider status or ticket labels do not grant human approval.

`WorkItemRef` and `RepositoryRef` each contain `integrationId` (configured UUID)
and `externalId` (nonempty opaque string, at most 256 characters). They may use
different integrations. `ChangeRequestRef` belongs to a `RepositoryRef` plus its
opaque request ID; `BranchRef` belongs to a repository plus its branch name.
A GitHub number alone is not a global identity. Do not parse provider meaning
from an external ID or use display URLs as credentials or authority.

## Event-driven process entry and continuation

A configured ticket service emits the initial new-ticket event. After a change
request is published, the repository service independently emits PR/MR, review,
comment, check and head-update events; no ticket update is required to progress.
Each provider's inbound adapter verifies its source and persists a deduplicated
delivery before acknowledgement.
Asynchronous processing normalizes the event and correlates it to a work item
and process. It starts a configured intake process once for eligible new work,
or resumes the existing process for later events. Flowable decides whether to
wait, request approval, dispatch an activity, handle review or finish the process.
A new ticket starts intake, not automatic approval or coding.

Inbound event adapters and outbound ticket/repository ports are separate
boundaries. Normalization translates provider payloads into trusted observations;
correlation locates the process. Neither is a Java workflow router. A registered
Flowable event entry point receives the observation; BPMN gateways and waiting
activities determine its business effect. Unknown events may be recorded/ignored,
and unmatched or uncertain events held for reconciliation; do not start a new
execution process for each delivery. There is no dynamically supplied BPMN name
or arbitrary signal target in an external event.

```mermaid
sequenceDiagram
    participant T as Ticket service
    participant I as Verified inbox and correlation
    participant F as Flowable BPMN
    participant A as Activity handlers and assigned agents
    participant R as Repository and QA services
    participant D as Separate feedback datastore
    participant H as Human reviewer
    T->>I: New ticket webhook
    I->>I: Verify and persist deduplicated delivery
    I-->>T: Acknowledge committed receipt
    I->>F: Start eligible intake once
    F->>A: Read ticket and obtain explicit plan approval
    opt Approved story eligible
        F->>A: Dispatch developer agent coding and local tests
        A-->>F: Accepted fenced outcome and test evidence
        F->>A: Publish tested PR and mark ready
        A->>R: Publish bound PR and start or correlate QA
    end
    loop Events for the same process and exact PR head
        R->>I: QA check, agent/human review, comment, head or closure webhook
        I->>I: Verify and durably deduplicate
        I-->>R: Acknowledge committed ingress
        I->>F: Resume matching stage with normalized observation
        F->>A: Read authoritative stage and PR evidence
        A->>R: Query current checks, reviews, head and closure
        R-->>A: Verified facts with explicit unknowns
        A->>D: Idempotent sanitized feedback via durable handoff
        D-->>A: Feedback committed or checkpoint acknowledged
        A-->>F: Current-stage facts and feedback checkpoint
        alt QA passes for current head
            F->>A: Dispatch all required specialist reviewer agents
            A->>R: Agents publish review findings as comments and reviews
        else All required agents pass for current head
            F->>H: Request human review
            H->>R: Approve exact merge binding or request changes
        else QA fails or agent/human requests actionable changes within budget
            F->>A: Dispatch developer agent remediation and local tests
            A-->>F: Accepted outcome and resolution evidence
            alt Local tests pass
                F->>A: Publish new PR head and invalidate prior-stage approval
                A->>R: Update head and restart mandatory QA
                Note over F,R: Repeat QA, all required agent reviews and human review
            else Local tests fail or outcome uncertain
                F->>A: Hold and escalate without publication
            end
        else Human approves exact merge binding after preceding stages pass
            F->>A: Revalidate QA, every required agent review and human authority
            A->>R: Request exact approved merge
            Note over F,R: Await or reconcile verified provider merge. A reply alone is insufficient
        else Verified closure
            F->>A: Record exact authorized merge or closed-without-merge
        else Head changed or stage pending
            F->>A: Restart QA for changed head or retain durable stage wait
        else Ambiguous, revoked, unsupported or attempt cap exhausted
            F->>A: Hold and escalate to human
        end
    end
    Note over D: Curated evidence feeds a separate evaluated, human-approved skill update workflow
```


This is the proposed event cycle, not an executable BPMN definition. The PR/MR
QA/agent/human stage sequence, feedback persistence, remediation, QA restart and guarded
merge are shown explicitly in this sequence. A merge reply alone is not proof of
completion; verified merged state is required. Ticket-only events with no review
binding remain in intake/approval handling rather than invoking review activities. The worker
claim/heartbeat/result sequence is a zoom-in on one execution activity selected
by that process, not the system's entry point. Accepted worker results and trusted
operator commands also enter the correlated process through application handlers;
they do not require a provider webhook.

| Incoming observation | Correlation and Flowable responsibility |
|---|---|
| New ticket | Start one eligible intake process for the configured integration/work-item identity; validate approval before dispatch. |
| Ticket update/comment | Resume that work item's process; inspect changed inputs/authorized approval, invalidate stale execution eligibility or keep waiting as appropriate. A comment/label alone is not approval. |
| Change-request review/comment | Resume the existing review wait through the stored repository/change-request binding. BPMN records feedback and may dispatch authorized bounded remediation, restart at QA, or escalate ambiguity. No ticket event is required. |
| Check/review/head update | Re-evaluate eligibility for that exact head. Changed head invalidates stale checks/review/merge authorization; passing review alone does not authorize merging. |
| Change request closed | Verify exact repository/base/head and merged status through the repository port. Closed without merge must not mark the story MERGED. |

## Repository-driven review and merge

Once a PR/MR is bound to the process, repository events are an independent
catalyst. The configured repository integration plus `ChangeRequestRef` locates
the process directly; correlation does not wait for a ticket-system callback.
The following is a proposed later-milestone BPMN review loop, not implemented
behavior or permission to merge in the first vertical slice.

```mermaid
flowchart TD
    Ready[PR ready for review or updated head] --> QA[Run required automated QA]
    QA --> QWait[Wait for verified QA webhook]
    QWait --> QRecord[Capture QA feedback in separate store]
    QRecord --> QPass{QA passes for exact head?}
    QPass -->|Yes| Agents[Dispatch all required specialist reviewer agents]
    Agents --> AWait[Wait for agent reviews and comment webhooks]
    AWait --> ARecord[Capture agent feedback in separate store]
    ARecord --> APass{Every required agent passes?}
    APass -->|Yes| Human[Request human review and await verified decision]
    Human --> HRecord[Capture human feedback in separate store]
    HRecord --> HPass{Authorized exact-head human approval?}
    QPass -->|Actionable failure within budget| Dev[Developer agent remediates and runs local tests]
    APass -->|Actionable changes requested within budget| Dev
    HPass -->|Actionable changes requested within budget| Dev
    Dev --> Local{Local tests pass?}
    Local -->|Yes| Publish[Publish tested head and invalidate all stage approvals]
    Publish --> QA
    Local -->|No or uncertain| Hold[Hold or escalate to human]
    QPass -->|Pending| QWait
    APass -->|Pending| AWait
    HPass -->|Pending| Human
    QPass -->|Unknown or exhausted| Hold
    APass -->|Ambiguous or exhausted| Hold
    HPass -->|Rejected scope or exhausted| Hold
    HPass -->|Yes| Guards[Revalidate QA all agent reviews authority base and head]
    Guards --> Merge[Request guarded merge and await verified closure]
    Merge --> Outcome{Verified authorized exact merge?}
    Outcome -->|Yes| Done[Record merge and resolution feedback then advance]
    Outcome -->|No or unknown| Hold
```


Remediation uses the existing worker activity path and preserves its approval,
lease and fencing guards. Default cap is two remediation attempts; publishing a
new head must not reset the budget. Failed local remediation tests cannot enter the publish/merge
path. New published heads restart mandatory QA and all subsequent review stages. QA, agent-review and human-review events resume their respective waits; they do not create new story
processes. Deduplicate comments and observations across delivery/reconciliation
so replay cannot consume another attempt or repeat a merge.

Agent reviews make a change eligible for human review and never grant merge
authority. The subsequent authenticated human review/approval must authorize the
exact repository/change-request/base/head; an approved operator command may also
supply that authority. In either case, revalidate it and required evidence at the
merge boundary. Unsupported provider checks/review/merge capabilities, changed
heads and unknown outcomes hold the workflow. A merge request is not proof of a
merge: record completion only from verified provider state. Existing epic and
final epic-to-main human approval gates remain mandatory.

Persist the process/work-item and publication bindings. Duplicate events must not
start another process or repeat an accepted effect. Provider deliveries can arrive
late or out of order: an old observation cannot override a newer accepted state;
when ordering/state is uncertain, query the authoritative provider and reconcile.
Keep events that arrive before their local binding is committed available for
later correlation. Inbox consumption and engine/domain changes must commit
atomically, or retain a durable retryable handoff; acknowledging a webhook is
not proof that Flowable has processed it. No volatile notification alone suffices.

The worker does not advance BPMN, query tickets through the control-plane ports,
or select its next activity. Its `execution` pins correlate an assigned activity
with the waiting process; they do not expose engine APIs. Activity input contains
only the resolved data needed for that activity, rather than raw provider payloads.
Trusted publication code may run in the control plane or trusted controller;
untrusted job containers never receive broad provider credentials or host sockets.

Accepted job completion and the matching Flowable wait completion use the same
Spring transaction (#70), with explicit duplicate correlation. Remote repository
operations are outside that database transaction. If publication succeeds but
local recording fails, reconcile the exact remote binding before another attempt.
No database rollback can undo a remote commit/PR. Keep no database lock open while
waiting for a container or provider network operation.

Flowable owns waits, timers and retry routing. Java supplies verified recovery
facts and enforces mandatory guards. Expiry first invalidates execution ownership;
unknown external effects keep the process waiting. Only a BPMN path guarded by
current approval and verified quiescence can dispatch another attempt. Worker
transport retries repeat the same command; they do not start another workflow
activity. Authorized cancellation must stop execution and notify the matching
process without enabling a competing Java activity sequencer.
