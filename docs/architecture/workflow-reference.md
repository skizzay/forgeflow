# Development workflow reference

Tracks [#100](https://github.com/skizzay/forgeflow/issues/100).
[Download/open the BPMN model](workflows/development-lifecycle.bpmn20.xml).

**Proposed architecture reference, not an executable workflow.** All three BPMN
processes have `isExecutable="false"`; the file lives outside runtime resources
and is never registered by ForgeFlow. Tasks, decisions and call outcomes describe
required responsibilities, not implemented delegates, expressions or IO mappings.
The embedded BPMN diagram coordinates are a basic layout, not a polished rendering.

The Mermaid views below use the same node IDs, edge labels and sequence flows as
the BPMN processes. A call activity invokes the named reference process; it does
not create another unrelated ticket process. The review/merge process is shared
by story-to-epic and epic-to-main PRs, with their distinct human approval bindings.

## Epic coordination

```mermaid
flowchart TD
    ticketReceived(["Verified new-ticket webhook"])
    planWork["Read ticket and draft validated plan"]
    awaitPlanApproval["Wait for explicit human plan decision"]
    planDecision{"Plan approved?"}
    prepareEpic["Prepare epic branch from approved main"]
    selectStory["Select dependency-ready story"]
    storyEligibility{"Story eligibility?"}
    awaitDependency["Wait for ticket or verified dependency merge"]
    deliverStory[["Deliver one approved story"]]
    storyResult{"Story result?"}
    verifyEpic["Verify epic acceptance and architecture"]
    epicChecks{"Epic checks passed?"}
    publishEpicPR["Publish epic-to-main PR"]
    reviewEpic[["Review epic PR and obtain final human merge approval"]]
    epicResult{"Final PR result?"}
    epicComplete(["Verified epic-to-main merge"])
    epicHold["Hold and escalate to human"]
    epicHeld(["Held; no automatic continuation"])
    ticketReceived --> planWork
    planWork --> awaitPlanApproval
    awaitPlanApproval --> planDecision
    planDecision -->|"approved exact plan"| prepareEpic
    planDecision -->|"rejected or uncertain"| epicHold
    prepareEpic --> selectStory
    selectStory --> storyEligibility
    storyEligibility -->|"ready and currently approved"| deliverStory
    storyEligibility -->|"blocked"| awaitDependency
    awaitDependency -->|"re-evaluate current facts"| selectStory
    storyEligibility -->|"all children verified merged"| verifyEpic
    deliverStory --> storyResult
    storyResult -->|"verified merged; unlock dependents"| selectStory
    storyResult -->|"closed or held"| epicHold
    verifyEpic --> epicChecks
    epicChecks -->|"passed"| publishEpicPR
    epicChecks -->|"failed or unknown"| epicHold
    publishEpicPR --> reviewEpic
    reviewEpic --> epicResult
    epicResult -->|"verified authorized merge"| epicComplete
    epicResult -->|"closed or held"| epicHold
    epicHold --> epicHeld
```

## Story delivery

```mermaid
flowchart TD
    storyStart(["Approved eligible story"])
    prepareStoryBranch["Prepare fresh story branch from epic"]
    dispatchImplementation["Dispatch isolated coding and testing"]
    awaitImplementation["Wait for accepted fenced worker outcome"]
    implementationResult{"Verified tests passed?"}
    publishStoryPR["Publish tested story PR targeting epic"]
    reviewStory[["Review story PR and obtain human merge approval"]]
    storyFinished(["Return verified review outcome to epic"])
    storyHold["Hold and escalate to human"]
    storyHeld(["Return held outcome"])
    storyStart --> prepareStoryBranch
    prepareStoryBranch --> dispatchImplementation
    dispatchImplementation --> awaitImplementation
    awaitImplementation --> implementationResult
    implementationResult -->|"passed for exact tested head"| publishStoryPR
    implementationResult -->|"failed or uncertain"| storyHold
    publishStoryPR --> reviewStory
    reviewStory -->|"merged or closed or held"| storyFinished
    storyHold --> storyHeld
```

## Repository review and merge

```mermaid
flowchart TD
    reviewStart(["Bound story or epic PR"])
    requestReview["Request Copilot or configured reviewer for current head"]
    awaitReviewEvent["Wait for verified correlated observation"]
    inspectReview["Read authoritative review checks head closure and approval"]
    reviewDecision{"Verified review outcome?"}
    dispatchRemediation["Increment attempt budget and dispatch remediation with tests"]
    awaitRemediation["Wait for accepted fenced remediation outcome"]
    remediationResult{"Verified remediation tests passed?"}
    publishRemediation["Publish tested new head; invalidate stale approvals"]
    awaitMergeApproval["Wait for explicit human approval of exact PR base and head"]
    revalidateMerge["Revalidate human authority checks and exact binding"]
    mergeGuards{"Merge guards satisfied?"}
    requestMerge["Request merge through repository port"]
    reviewMerged(["Return verified authorized merged outcome"])
    reviewClosed(["Return closed-without-merge outcome"])
    reviewHold["Hold and escalate; reconcile uncertain effects"]
    reviewHeld(["Return held outcome"])
    reviewStart --> requestReview
    requestReview --> awaitReviewEvent
    awaitReviewEvent -->|"webhook or trusted reconciliation observation"| inspectReview
    inspectReview --> reviewDecision
    reviewDecision -->|"incomplete review or checks; comment alone"| awaitReviewEvent
    reviewDecision -->|"head changed; fresh review needed"| requestReview
    reviewDecision -->|"completed review; actionable; authorized; attempts below 2"| dispatchRemediation
    dispatchRemediation --> awaitRemediation
    awaitRemediation --> remediationResult
    remediationResult -->|"passed"| publishRemediation
    remediationResult -->|"failed or uncertain"| reviewHold
    publishRemediation -->|"request re-review; retain attempt count"| requestReview
    reviewDecision -->|"current-head review and required checks pass"| awaitMergeApproval
    awaitMergeApproval -->|"authenticated human command"| revalidateMerge
    revalidateMerge --> mergeGuards
    mergeGuards -->|"authorized exact base and head"| requestMerge
    mergeGuards -->|"head changed; approval stale"| requestReview
    mergeGuards -->|"revoked or unsupported or uncertain"| reviewHold
    requestMerge -->|"await or reconcile provider closure"| awaitReviewEvent
    reviewDecision -->|"verified authorized merge with exact binding"| reviewMerged
    reviewDecision -->|"verified closed without merge"| reviewClosed
    reviewDecision -->|"ambiguous or exhausted budget or revoked approval"| reviewHold
    reviewHold --> reviewHeld
```

## Events, authority and recovery

- Ticket/repository adapters verify and durably deduplicate webhooks before
  acknowledgement. Correlation uses integration, work item, repository, PR and
  exact reviewed head; events never supply arbitrary BPMN targets or authority.
- A completed Copilot review resumes the existing review wait through a normalized
  observation. Individual comments are retained/deduplicated but do not imply
  review completion. Repository review, check, head and closure events progress
  independently of ticket callbacks. Reconciliation may supply the same trusted
  observations when a webhook is late or missing.
- Worker waits receive authenticated, fenced, accepted results. Failed or unknown
  test outcomes cannot publish code. Current plan approval, dependencies, ownership
  and cancellation must be checked again at dispatch and publication boundaries.
- Two remediation attempts is the default maximum across the PR's review cycles;
  publishing a new head or requesting another review never resets that count.
  Only actionable completed review evidence for the current head permits dispatch.
- Review/check success is eligibility, not merge authority. A separate authenticated
  human approves the exact repository/PR/base/head; guards are revalidated before
  a merge request. New heads invalidate old review/check/merge approval evidence.
  Only independently verified, authorized merged state returns a merged outcome.
- Call outcomes are `merged`, `closed` or `held`. Story dependencies unlock only on
  verified merge into the epic. Epic acceptance/architecture checks and a separate
  final human merge approval guard epic-to-main integration.
- Database inbox/engine/domain handoffs are atomic or durable/retryable. Remote
  publication and merge effects require idempotency and reconciliation; a database
  rollback cannot undo them. No database locks span worker/provider waits.
  Hold paths stop automatic dispatch and require human/recovery handling; they do
  not claim containers or remote effects have already stopped. Detailed lease,
  cancellation, timer and recovery subflows are intentionally not expanded here.

## Current implementation and verification

Verified ingress/classification, lifecycle guards, manual approval and the opt-in
atomic approved-story starter exist. That packaged process only queues its first
job and waits. The intake, planning/DAG scheduling, worker execution, publication,
review webhook loop and final integration above span later roadmap work and are
not delivered by this reference. #84 owns the next inbox-to-starter integration;
#93 owns repository-specific workflow configuration. See the
[provider/workflow boundaries](workflow-boundaries.md) and
[persistence guide](control-plane-persistence.md#approved-execution-startup).

Run the structural/schema checks with the project's Java build container:

```sh
docker compose -p forgeflow-reference-check run --rm build mvn -B -Dtest=WorkflowReferenceTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Remove only that disposable verification project after the check:

```sh
docker compose -p forgeflow-reference-check down --volumes --remove-orphans
```

The check validates BPMN XML against Flowable's bundled BPMN schema, resolves
sequence/call references, checks diagram coverage, and compares Mermaid nodes,
labels and edges. Mermaid syntax is also parsed during this change's verification.
These checks establish document consistency, not executable process behavior or
visual readability in every BPMN editor. No reference process is deployed.
