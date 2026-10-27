# Roadmap

Target throughput: 4–6 epics/week across multiple repositories, 6–8 stories/epic. These are capacity targets, not demonstrated throughput.

## Milestone 1 — Vertical slice
Approved test story -> webhook -> durable job -> local Docker Codex worker -> story branch -> tests -> pull request. Single repository and one active job. No autonomous merging.

## Milestone 2 — Epic planning
Structured issue templates; acceptance-criteria coverage; dependency DAG with cycle detection; explicit human plan approval; epic branch creation.

## Milestone 3 — Dependency-aware execution
Global/repo/epic concurrency controls; branch freshness checks; leases, heartbeats, retries and worker recovery; downstream dispatch only after prerequisite merge.

## Milestone 4 — Review loop
Copilot review ingestion; remediation jobs; test verification; two-attempt default limit; escalation for ambiguity and repeated failures.

## Milestone 5 — Integration and release
Epic acceptance verification, architecture review, final epic PR to main, human merge approval and release preparation.

## Milestone 6 — Operations and portability
Pause/resume/cancel, telemetry, AI and compute cost reporting, security hardening, cloud worker compatibility testing.

## First sprint tasks
1. Spring Boot skeleton and database migrations.
2. GitHub App webhook receiver with signature verification and durable inbox.
3. Job and worker API contract.
4. Java polling worker with lease and heartbeat.
5. Disposable Docker Codex execution image and safe credential handling.
6. Test story -> branch -> PR end-to-end demonstration.

Each task must specify dependencies, acceptance criteria, verification, and completion evidence.

## Feasibility checkpoint — 2026-10-22

[Issue #18](https://github.com/skizzay/forgeflow/issues/18) is closed with an
accepted bounded research result; see the [synthesis](../research/issue-18-results.md).
PR #21 is retained as an unmerged experiment archive, not a production dependency.
Bootstrap, webhook inbox, lifecycle guards and worker OAuth2 authentication are
implemented. The current delivery sequence is manual plan approval (#46), pinned
event-to-job creation (#47), then atomic worker claiming (#39). Approval alone
does not dispatch work; prerequisite PRs must still integrate into the Execution
epic before being treated as satisfied. #13 still owns scoped GitHub credential and provider operational
validation; #11 owns the actual approved-issue-to-PR demonstration; #15 owns real
GitHub reconciliation. Milestone 1 is not complete. No new generic Flowable POC
is required; use the existing evidence while implementing and testing those stories.
Research acceptance does not waive human epic approval or implementation merge gates.
