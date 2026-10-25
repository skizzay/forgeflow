# #8: Implement verified, idempotent GitHub webhook inbox

Issue: [#8](https://github.com/skizzay/forgeflow/issues/8).
Parent: [Foundation #1](https://github.com/skizzay/forgeflow/issues/1).
Status: implementation merged into main via [PR #23](https://github.com/skizzay/forgeflow/pull/23).
Dependencies: #7 and #32 integrated into `epic/1-foundation` via #31/#34.

## Delivered implementation

Opt-in, bounded WebFlux endpoint with raw-body HMAC verification and metadata-only
transactional PostgreSQL inbox. Duplicate acknowledgement, changed-identity
rejection, and independent idempotent scheduled event classification. The initial V1 schema requires
complete payload identity on every inbox row. No dispatch, App API calls,
PEM access, plan approval or merge effects.

See [architecture](../architecture/webhook-inbox.md) and
[configuration guide](../user-guide/github-webhooks.md) for exact contracts,
privacy choices and verification limits. Runtime/processor tests cover HTTP
validation, concurrent duplicates, replay and restart, rollback and background
recovery on real PostgreSQL. The accepted bootstrap's connection-URI schema
configuration is retained; no SQL schema placeholders are reintroduced.

## Review and integration

This story branch was created from `epic/1-foundation`, which was created from
main. The user explicitly requested story PRs targeting main; this is a review
target exception to the documented epic-target workflow. A PR into main does not
satisfy the requirement that prerequisite PRs merge into the epic branch before
unlocking dependent execution. Do not merge, retarget, or enable auto-merge without
explicit human direction. Human approval is required for every merge.

Verification results and implementation tradeoffs are recorded in PR #23.

## Verification results

The current regression suite passes all 52 tests, including eight webhook
integration scenarios and initial-schema payload identity constraints. All 55
local Markdown links and `git diff --check` pass. Validation uses synthetic local deliveries, not a live
GitHub App, and commit-before-response/context-restart injection rather than an
OS process-kill experiment.

## Hexagonal refactor

The feature now exposes ReceiveWebhook/ProcessWebhook input ports with a Java-only
application/domain core. SQL, transactions, HMAC, JSON, HTTP and scheduling are
adapters assembled by Spring configuration. See the
[structure and dependency rules](../architecture/hexagonal-control-plane.md).
Core policy tests and compiled-class architecture rules supplement the existing
PostgreSQL/HTTP suite; the initial V1 schema includes inbox and classification storage.
