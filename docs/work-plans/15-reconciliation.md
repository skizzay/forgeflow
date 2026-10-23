# #15: Implement GitHub reconciliation and lease fencing

Issue: [#15](https://github.com/skizzay/forgeflow/issues/15).
Parent: [Foundation #1](https://github.com/skizzay/forgeflow/issues/1).
Status: **draft plan; dependencies unsatisfied**.
Dependencies: #8, #9 and #14; consume #18 recovery findings.

## Smallest coherent implementation

Build reconciliation around durable job/attempt/fencing identity and authoritative GitHub reads. An expired lease enters reconciliation; it does not directly enqueue another provider run. Only one logical side effect is permitted per durable intent, while delivery remains at least once.

Reconcile expected branch, PR base/head, checks and container/attempt observations before selecting a deterministic recovery outcome. A discovered PR is correlated and verified before recording completion. Missing or ambiguous evidence escalates rather than creating a second PR. Engine completion and dispatch state use the transaction/outbox ownership established by #18.

Verification matrix:
- Crash after GitHub PR creation but before persistence discovers the existing PR on restart.
- Duplicate completion is safely deduplicated; mismatched or stale fencing tokens are rejected.
- Expired lease does not create a second active job without reconciliation.
- Cancellation racing with completion cannot resurrect a cancelled attempt.
- Missed webhooks converge to GitHub truth; local job success cannot satisfy a story dependency.

This is a dependency-gated implementation plan, not a completed reconciler. #9 belongs to the execution epic and is an explicit cross-epic dependency; do not hide that dependency or claim it merged.

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
