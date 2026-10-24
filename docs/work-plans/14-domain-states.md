# #14: Separate job, story and epic lifecycle

Issue: [#14](https://github.com/skizzay/forgeflow/issues/14), parent
[Foundation #1](https://github.com/skizzay/forgeflow/issues/1).
Status: implementation under review in [PR #24](https://github.com/skizzay/forgeflow/pull/24).
Dependencies: #7/#32 integrated into `epic/1-foundation`; #18 research accepted.

## Delivered scope

A framework-free lifecycle core with typed state families, explicit transition
policies and an input port. JDBC persistence records guarded state/version and
audit receipts atomically, with pinned approval/execution/attempt identity and
idempotent command replay. Trusted authorization/reconciliation is an output port
with a deny-unverified default. No HTTP, dispatch, approval issuance or merge API
is added. See the [state tables and trust boundaries](../architecture/lifecycle-states.md).

V3 is additive; V1/V2 are unchanged. Policy tests cover all same-family state
pairs. PostgreSQL tests exercise concurrency, restart, audit rollback, approval
changes and exact GitHub evidence bindings. Real identity/SCM integrations remain
their existing stories and must supply verified facts before guarded transitions
can operate in production.

## Review and integration

This story branch originated from `epic/1-foundation`. It was updated with the
integrated bootstrap and the approved main documentation/webhook baseline. #8
is not a dependency of #14; importing approved main into this story does not
claim #8 has merged into the epic branch or unlock #15.

The user explicitly requested story PRs into main as a review-target exception.
PR #24 will be marked ready for human review after checks; do not merge, retarget
or enable auto-merge without explicit approval. Dependent work still requires
prerequisite implementation integration into the appropriate epic branch.

## Verification

Maven verify passed all 43 tests (28 PostgreSQL integration tests, nine pure
policy/use-case tests, four configuration tests and two architecture tests), with
zero failures/errors/skips. Lifecycle coverage includes all 97 same-family state
pairs and ten PostgreSQL scenarios. All 55 local Markdown links, import/POM checks
and `git diff --check` pass. Authority/SCM observations are explicitly trusted
fixtures, not live identity or GitHub integration evidence.
