# Architecture review — 2026-10-13

Status: **proposed; pending human approval and Codex validation**. This review was prepared in ChatGPT from the current repository documents; it was **not** executed by Codex.

## Follow-up — 2026-10-14
The [preferred architecture](architecture.md) now specifies Java workers and embedded Flowable, subject to a PoC. The original review below is retained as historical analysis; its Python recommendation is superseded. This documentation pass reviewed repository guidance and issues, not a runtime implementation. Preserve reliability/security findings; validate engine/lease transaction boundaries, project workflow versions and provider swapping before adopting Flowable.

## Assessment
The control-plane / execution-plane split is appropriate for laptop-first and cloud-later deployment. GitHub should remain authoritative for issues, PRs, branch refs and merge status, while PostgreSQL records durable orchestration state.

## Decisions recommended before implementation
1. **Vertical slice before full planner.** Define an explicit manual approval marker for one test story, then webhook -> durable inbox -> job -> local worker -> branch -> PR. Avoid coupling first milestone to a complete DAG planner.
2. **Separate job states from GitHub states.** A job can succeed while its PR remains open. Model dispatch, execution, verification, review, merge and cancellation separately. Never unlock a dependent story until the upstream PR is merged into the *correct* epic branch.
3. **Webhook recovery.** Validate HMAC signature, deduplicate by delivery ID, persist before acknowledgment, and reconcile GitHub state after outages or missed events. Webhooks alone are insufficient for correctness.
4. **Branch consistency.** Store expected epic head SHA at dispatch. Before PR merge, verify required checks and freshness against the current epic head; rebase/update and retest when needed. Parallel branches can conflict.
5. **Lease safety.** A worker lease timeout must not blindly rerun a story. Reconcile branch, PR and job artifacts first; use idempotent job IDs and fencing tokens to reject stale workers.
6. **Container isolation.** Separate trusted controller from untrusted execution container. No Docker socket or broad host mounts in job container. Use bounded CPU/memory, network policy, short-lived scoped GitHub credentials, secret redaction and log retention limits.
7. **Codex feasibility spike.** Validate unattended invocation, authentication, model/API billing, CLI availability on target Linux architecture, cancellation and reproducible outputs before investing heavily in scheduler features.
8. **Copilot integration spike.** Verify the exact review events and permission requirements in a sandbox PR, including partial comments, pending reviews, rereviews and duplicate deliveries. Never assume every comment is actionable.
9. **Approval and security.** Define an immutable approved plan version or require reapproval when child stories/dependencies change. Preserve explicit final human approval before epic-to-main merge.
10. **Public project hygiene.** Choose license, contribution/security policies and examples with dummy credentials before inviting contributions. No operational secrets in public issue bodies or logs.

## Suggested state boundaries
- Epic: DRAFT -> VALIDATED -> APPROVED -> EXECUTING -> INTEGRATION_REVIEW -> READY_FOR_RELEASE -> COMPLETED; BLOCKED/CANCELLED as exceptional states.
- Story: BLOCKED -> READY -> RUNNING -> PR_OPEN -> REVIEWING -> MERGED; FAILED/ESCALATED/CANCELLED as exceptional states.
- Job: QUEUED -> LEASED -> RUNNING -> SUCCEEDED/FAILED/CANCELLED/EXPIRED. A SUCCEEDED job does not imply MERGED story.

## Critical path for first demo
1. Spring Boot + migrations and minimal job schema.
2. GitHub App webhook verification and durable inbox.
3. Versioned worker API with lease/heartbeat.
4. Java external worker and disposable Docker job sandbox (supersedes the original Python recommendation).
5. Unattended Codex feasibility check.
6. Branch + PR adapter and end-to-end integration test.

## Validation checklist for Codex architecture pass
Review this document and `docs/architecture.md` against actual GitHub API behavior and Codex execution interfaces. Identify unverified assumptions, propose ADRs, split issues into independently testable stories with explicit dependencies, and do not implement or dispatch production jobs until the plan is approved.

## Open questions
- How is the test story approved without relying on a label that any collaborator can apply?
- Which worker auth scheme and credential broker should be used?
- How will the orchestrator discover supported project toolchains and required checks?
- What is the minimum audit event schema for debugging and cost attribution?
