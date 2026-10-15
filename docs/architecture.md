# Architecture (proposed)

## Control plane
Spring Boot orchestrator hosted on Raspberry Pi, backed by PostgreSQL. GitHub App webhooks enter a durable, idempotent event inbox. GitHub remains authoritative for issues, branches, PRs and checks; PostgreSQL stores job leases, execution history, scheduling state and reconciliation checkpoints.

## Execution plane
A Python worker controller on Ubuntu polls for jobs and advertises capabilities. It launches one isolated, disposable Docker execution container per story, with Codex and project toolchains. Initially one concurrent job. Workers access the orchestrator over an authenticated, versioned API and do not access its database. Do not mount the Docker socket inside untrusted execution containers.

## Workflow
Codex drafts an epic and child stories; human approves the plan. Validate structured dependencies and acceptance-criteria coverage, reject cycles, create epic branch from main. Eligible stories receive isolated branches from the latest epic head. A story's dependencies are satisfied only after their PRs merge into the epic branch. Codex implements and tests, opens PR targeting epic branch. Copilot reviews; actionable feedback may trigger bounded remediation cycles. Required checks and merge permissions gate story integration. Once all stories merge, integration and architecture reviews run, then human approves epic-to-main PR and release.

## Agent identity and attribution
[ADR 0001 — Pluggable agent identity](adr/0001-pluggable-agent-identity.md) records the accepted identity decision; implementation is pending. Stable logical agent IDs are independent of SCM actors, credentials, workers and LLM backends. A configurable identity provider binds each agent and repository scope to a native actor: separate GitHub Apps per agent initially, GitLab service accounts, Bitbucket Cloud resource access-token bots, and Bitbucket Data Center service accounts where supported. Other SCM adapters remain future scope and require capability validation.

A trusted broker resolves versioned bindings and secret-manager references, issues narrowly scoped credentials and supports rotation/revocation. Root secrets stay outside job containers and prompts. Job-local Git attribution is explicit; audit records distinguish actual Git author, committer, authenticated SCM actor and logical agent, correlated with workflow/job/attempt, repository, commits and PR/review events. Identity and execution-provider configuration are separate contracts, so changing models or rotating credentials preserves agent continuity.

Human plan and final epic-to-main approvals are verified, version/head-bound authorization records. Autonomous reviews, including bot `APPROVED` results, never satisfy those human gates. Merge eligibility still requires deterministic checks, freshness and merged dependencies. See the ADR for binding, audit and validation requirements.

## Reliability and safeguards
Use idempotency keys for webhook deliveries and job dispatch, leases and heartbeats, reconciliations before retry, concurrency budgets, stop/pause, timeouts and human escalation. Treat model output as untrusted. Limit repository credentials and permissions; use short-lived tokens, redact logs, and never commit secrets.

## Portability
Worker API abstracts execution location. Local Docker is first; cloud worker adapters are future scope. Toolchain images advertise OS, architecture and resource requirements. Keep orchestration independent of worker location.

## Open questions
Codex unattended authentication/billing, Copilot review event behavior, branch protection rules, GitHub App permissions, and resource limits require prototype validation.
