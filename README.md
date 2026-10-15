# ForgeFlow

**Project-configurable software-development workflows with controlled autonomous execution.**

ForgeFlow is an early-stage, open-source project to provide a specialized software-development workflow/BPMS platform coordinating GitHub epics and stories, pluggable execution providers, pull-request reviews, and controlled releases. **Status: architecture and planning; implementation not yet available.**

## Intended workflow

1. Codex drafts an epic and child stories with explicit dependencies and acceptance criteria.
2. A human approves the execution plan.
3. ForgeFlow creates an epic branch and schedules only dependency-ready stories.
4. A worker executes each story in an isolated Docker container, using a registered execution provider to implement and test changes.
5. Story PRs target the epic branch. Copilot feedback can trigger bounded automated remediation.
6. A story unlocks dependents only after its PR merges into the epic branch.
7. An integrated architecture review precedes human approval of the epic-to-main merge and release.

## Planned components

- **Control plane:** Java-first Spring Boot modular application, PostgreSQL, GitHub App/webhooks, embedded Flowable BPMN engine (preferred pending PoC), and a separate dependency scheduler.
- **Worker:** Java controller polling an authenticated, versioned API; disposable Docker job containers.
- **Initial hosting:** Raspberry Pi orchestrator and Ubuntu laptop worker; cloud-worker portability designed in.
- **Safeguards:** explicit approvals, dependency enforcement, CI gates, pause/cancel, idempotent retries, budgets and auditability.

Project configuration selects versioned workflows, registered activities and capability-matched providers such as Codex, Claude or Ollama/Qwen. Provider availability and unattended operation require validation. First scope: approved issue → durable workflow → Java external worker → isolated coding/testing → PR, with no autonomous merge.

## Getting started

Implementation is not yet available. Start with the [architecture](docs/architecture.md), [roadmap](docs/roadmap.md), and [agent conventions](AGENTS.md). Development work is tracked in GitHub Issues.

## Project principles

GitHub is the authority for repository artifacts; AI writes code but deterministic rules control scheduling and merge eligibility. Never commit credentials or sensitive logs. Contributions and license terms will be established before accepting outside contributions.
