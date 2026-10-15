# Roadmap

Target throughput: 4–6 epics/week across multiple repositories, 6–8 stories/epic. These are capacity targets, not demonstrated throughput.

## Milestone 0 — Flowable decision PoC (#18)
Java-first Spring Boot modular control plane, embedded Flowable and PostgreSQL. Demonstrate two project-specific workflow variants, immutable workflow/activity references, Java worker recovery/fencing and provider swapping. Use Codex plus a second adapter or explicitly identified test provider, without requiring every provider. Verify engine/domain/dispatch transactions, authentication, capability matching, cancellation and Pi resource budget. Record tested versions and a go/no-go decision. Flowable remains preferred pending evidence. See the [architecture decision gate](architecture.md#first-vertical-slice-and-decision-gate).

## Milestone 1 — Vertical slice
Approved test issue -> verified webhook -> durable version-pinned workflow -> Java external worker -> isolated Docker coding/testing -> story branch -> pull request. Single repository and one active job. No autonomous merging.

## Milestone 2 — Epic planning
Structured issue templates; acceptance-criteria coverage; dependency DAG with cycle detection; explicit human plan approval; epic branch creation.

## Milestone 3 — Dependency-aware execution
Global/repo/epic concurrency controls; branch freshness checks; broader recovery scenarios beyond the first-slice leases, heartbeats and idempotent retries; downstream dispatch only after prerequisite merge.

## Milestone 4 — Review loop
Copilot review ingestion; remediation jobs; test verification; two-attempt default limit; escalation for ambiguity and repeated failures.

## Milestone 5 — Integration and release
Epic acceptance verification, architecture review, final epic PR to main, human merge approval and release preparation.

## Milestone 6 — Operations and portability
Pause/resume/cancel, telemetry, AI and compute cost reporting, security hardening, cloud worker compatibility testing.

## First sprint tasks
1. Flowable PoC/go-no-go evidence (#18), then Spring Boot skeleton and database migrations (#7).
2. GitHub App webhook receiver with signature verification and durable inbox.
3. Authenticated versioned job/worker API and activity/provider contracts (#9).
4. Java polling worker with lease, heartbeat, capability matching and recovery (#10, #15).
5. Disposable Docker Codex execution image and safe credential handling.
6. Approved issue -> durable workflow -> isolated coding/testing -> PR demonstration (#11); no full DAG planner required.

Each task must specify dependencies, acceptance criteria, verification, and completion evidence.

Project configuration composes registered activities; mandatory ForgeFlow policies remain application-enforced. Provider integration, model selection and context building are separate. General BPMS editing, extra languages/services and cloud deployment are deferred. Keep #11 behind PoC, API, worker, Codex feasibility and recovery evidence.
