# Architecture (proposed)

## Control plane
Spring Boot orchestrator hosted on Raspberry Pi, backed by PostgreSQL. Provider webhooks enter a durable, idempotent event inbox. Ticket and repository services are separate provider integrations, with GitHub as the first concrete adapter. Each external service is authoritative for its records; PostgreSQL stores job leases, execution history, scheduling state and reconciliation checkpoints.

## Execution plane
A Java worker controller on Ubuntu polls for jobs and advertises capabilities. It launches one isolated, disposable Docker execution container per story, with Codex and project toolchains. Initially one concurrent job. Workers access the orchestrator over an authenticated, versioned API and do not access its database. Do not mount the Docker socket inside untrusted execution containers.

## Orchestration and integrations

Flowable BPMN owns activity order, gateways, durable waits and guarded retry paths.
Java activity handlers call separate ticket and repository ports and enforce
approval, lease/fence and evidence safeguards. The worker API executes an assigned
activity; it is not a second workflow sequencer. Provider-neutral references bind
independent ticket/repository integrations. See the
[workflow and provider boundaries](workflow-boundaries.md) for ownership, current
implementation limits and a Mermaid interaction diagram.

## Workflow

A new ticket first enters through a provider webhook, verified durable inbox and
normalized event-to-process correlation. Flowable starts intake once and decides
the next step. Later ticket updates, review comments and change-request closure
resume that same correlated process; each event does not launch a new job.
After PR/MR publication, repository review/check/head events independently drive
bounded remediation or merge evaluation without another ticket event. Merge
execution still requires verified checks and explicit human authorization.
Worker outcomes and authenticated operator commands are additional process inputs.
Ticket creation does not authorize coding; approval and evidence guards remain
mandatory. See the [event-driven sequence](workflow-boundaries.md#event-driven-process-entry-and-continuation).

Codex drafts an epic and child stories; human approves the plan. Validate structured dependencies and acceptance-criteria coverage, reject cycles, create epic branch from main. Eligible stories receive isolated branches from the latest epic head. A story's dependencies are satisfied only after their PRs merge into the epic branch. Codex implements and tests, opens PR targeting epic branch. Copilot reviews; actionable feedback may trigger bounded remediation cycles. Required checks and merge permissions gate story integration. Once all stories merge, integration and architecture reviews run, then human approves epic-to-main PR and release.

## Reliability and safeguards
Use idempotency keys for webhook deliveries and job dispatch, leases and heartbeats, reconciliations before retry, concurrency budgets, stop/pause, timeouts and human escalation. Treat model output as untrusted. Limit repository credentials and permissions; use short-lived tokens, redact logs, and never commit secrets.

## Portability
Worker API abstracts execution location. Local Docker is first; cloud worker adapters are future scope. Toolchain images advertise OS, architecture and resource requirements. Keep orchestration independent of worker location.

## Open questions
Codex unattended authentication/billing, Copilot review event behavior, branch protection rules, GitHub App permissions, and resource limits require prototype validation.

## Bootstrap implementation (#7)

See [control-plane persistence](control-plane-persistence.md) for
implemented schema ownership, migration modes, version pins and remaining boundaries.
