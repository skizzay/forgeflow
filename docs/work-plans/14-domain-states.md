# #14: Model separate job, story, and epic state machines

Issue: [#14](https://github.com/skizzay/forgeflow/issues/14).
Parent: [Foundation #1](https://github.com/skizzay/forgeflow/issues/1).
Status: **draft plan; dependencies unsatisfied**.
Dependencies: #7 bootstrap schema; consume #18 findings.

## Smallest coherent implementation

Implement separate deterministic transition policies for ForgeFlow jobs, stories and epics. BPMN activity/process state and GitHub PR/check state remain correlated facts, not substitutes for domain state. Store the immutable approved plan/configuration/workflow/activity references used by each execution.

Initial guarded boundaries:
- Approved plan identity and ready dependencies are required before dispatch.
- Job SUCCEEDED records execution completion only; it cannot imply story MERGED.
- Story MERGED requires authoritative GitHub evidence for the expected repository, PR base, head and merge commit.
- Relevant plan/configuration changes invalidate execution authorization until reapproval.
- Cancellation, stale-worker completion and retry/escalation have explicit exceptional transitions.
- Epic integration/release eligibility cannot bypass explicit human approval.

Verification matrix:
- Table-driven tests reject every unspecified transition.
- Successful jobs and completed BPMN instances cannot manufacture merge evidence.
- Stale plan/configuration identities cannot authorize side effects.
- Restart reloads state and correlation without replaying effects.

This draft is a transition-design plan only. Persisted implementation waits for #7 and consumes the #18 transaction/recovery decision; neither prerequisite is marked satisfied.

## Review and integration

This story branch was created from `epic/1-foundation`, which was created from
main. The user explicitly requested story PRs targeting main; this is a review
target exception to the documented epic-target workflow. A PR into main does not
satisfy the requirement that prerequisite PRs merge into the epic branch before
unlocking dependent execution. Do not merge, retarget, or enable auto-merge without
explicit human direction. Human approval is required for every merge.

Verification for this initial draft: reviewed current issue dependencies,
architecture and roadmap, and checked Markdown formatting with `git diff --check`.
There is no runtime code in this draft and no runtime verification is claimed.
