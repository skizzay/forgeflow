# ForgeFlow

**Dependency-aware orchestration for autonomous software development.**

ForgeFlow is an early-stage, open-source project to coordinate GitHub epics and stories, Codex development workers, pull-request reviews, and controlled releases. **Status: architecture and planning; implementation not yet available.**

## Intended workflow

1. Codex drafts an epic and child stories with explicit dependencies and acceptance criteria.
2. A human approves the execution plan.
3. ForgeFlow creates an epic branch and schedules only dependency-ready stories.
4. A worker executes each story in an isolated Docker container, using Codex to implement and test changes.
5. Story PRs target the epic branch. Copilot feedback can trigger bounded automated remediation.
6. A story unlocks dependents only after its PR merges into the epic branch.
7. An integrated architecture review precedes human approval of the epic-to-main merge and release.

## Planned components

- **Control plane:** Spring Boot, PostgreSQL, GitHub App/webhooks, durable scheduler.
- **Worker:** Python controller polling a versioned API; disposable Docker job containers.
- **Initial hosting:** Raspberry Pi orchestrator and Ubuntu laptop worker; cloud-worker portability designed in.
- **Safeguards:** explicit approvals, dependency enforcement, CI gates, pause/cancel, idempotent retries, budgets and auditability.

## Getting started

Implementation is not yet available. Start with the [architecture](docs/architecture.md), [roadmap](docs/roadmap.md), and [agent conventions](AGENTS.md). Development work is tracked in GitHub Issues.

## Architecture decisions

- [ADR 0001 — Pluggable agent identity](docs/adr/0001-pluggable-agent-identity.md): separate logical agents, SCM actors, credentials and LLM backends while preserving attribution and human approvals.

## Project principles

GitHub is the authority for repository artifacts; AI writes code but deterministic rules control scheduling and merge eligibility. Never commit credentials or sensitive logs. Contributions and license terms will be established before accepting outside contributions.
