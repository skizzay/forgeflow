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

## Current delivery plan

Bootstrap #7, verified inbox #8 and guarded lifecycle #14 have merged into main.
Milestone 1 is incomplete. Implementation dependencies still require integration
into the proper epic branch; mainline merges alone do not satisfy that gate.

Use the [reviewable delivery slices](delivery-slices.md) and linked GitHub child
tickets for remaining work. Start with the single-worker protocol, then deliver
manual approval/job creation, worker authentication and lease operations, isolated
execution, scoped GitHub publication and bounded recovery as individual PRs.
The final demo verifies those components; it must not absorb their implementation.
Full DAG planning, additional providers, cloud workers and broad operations
remain in their later phases. The provisional rename is a separate workstream.

## Accepted feasibility evidence

[Issue #18](https://github.com/skizzay/forgeflow/issues/18) records accepted bounded
local feasibility. PR #21 is an unmerged experiment archive, not production code
or a dependency. No repeat generic Flowable POC is required. Remaining Codex
sign-in, image and scoped GitHub access checks are individually scoped under
#13. Real publication and recovery remain under #11/#15.
Research acceptance does not authorize dispatch, approve an epic or waive a merge gate.
