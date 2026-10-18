# #8: Implement verified, idempotent GitHub webhook inbox

Issue: [#8](https://github.com/skizzay/forgeflow/issues/8).
Parent: [Foundation #1](https://github.com/skizzay/forgeflow/issues/1).
Status: **draft plan; dependencies unsatisfied**.
Dependencies: #7 bootstrap schema.

## Smallest coherent implementation

Accept GitHub App webhooks only after validating X-Hub-Signature-256 over the exact raw request bytes using a constant-time comparison. Bound request size, validate delivery/event headers and avoid logging payloads or signatures. The signing secret is external configuration, distinct from the GitHub App PEM.

Insert the delivery ID, event type, receipt time and required sanitized metadata into a durable inbox transaction before acknowledging. Enforce a database uniqueness constraint on delivery ID; duplicate delivery is an acknowledgement, not duplicate work. Do not run job dispatch or GitHub side effects on the HTTP request thread. Decide explicitly which encrypted/retained payload fields are required for asynchronous processing.

Verification matrix:
- Correct signature accepted; absent, malformed and invalid signatures rejected.
- Modified body, wrong secret and oversized body rejected.
- Concurrent duplicate delivery creates one durable inbox item.
- Crash after commit/before response followed by replay creates no duplicate work.
- Restart retains pending items; failure before persistence is not acknowledged as accepted.

This draft is a scoped implementation plan, not a webhook service. Schema integration and executable integration tests wait for #7 to merge into the epic branch.

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
