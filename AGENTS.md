# Agent instructions

ForgeFlow is a public open-source orchestration project. Do not commit tokens, credentials, private source code, user data, or sensitive execution logs.

Architecture: Spring Boot orchestrator + PostgreSQL; Java polling worker; isolated Docker job containers; GitHub as issue/PR authority. Preserve separation of control and execution planes and a cloud-portable worker contract.

For every change: read relevant architecture and roadmap documents; link the GitHub issue; identify dependencies; implement the smallest coherent change; add/update tests; document verification and tradeoffs in the PR.

Never bypass human epic approval or final epic-to-main approval. Never auto-merge on an agent's assertion alone. Never treat a story dependency as satisfied until its PR has merged into the epic branch. Never expose Docker host socket to an untrusted development container. Escalate uncertain review feedback; cap remediation attempts.

Favor deterministic scheduling and state transitions over AI-generated control decisions. Design retries for idempotency and recovery.

Java conventions: use explicit imports for types instead of fully qualified class names in code, including tests; qualify a name only to resolve an actual collision. Use YAML for Spring configuration. Use Spring WebFlux for the HTTP layer; keep blocking JDBC/Flowable operations off reactive event-loop threads and preserve imperative transaction boundaries.

Documentation categories: [research](docs/research/README.md), [requirements](docs/requirements/README.md), [architecture](docs/architecture/README.md), and [consumer user guide](docs/user-guide/README.md). Read the [architecture overview](docs/architecture/overview.md) and [roadmap](docs/requirements/roadmap.md) before changes. Keep reproduction instructions beside spike code and link them from research.

Use hexagonal feature boundaries: keep domain and application code framework-free; expose use cases through inbound ports and persistence/provider/transaction needs through outbound ports. HTTP, scheduling, JDBC and serialization belong in adapters. Wire concrete implementations in configuration; adapters depend on core ports rather than one another. Preserve transaction semantics and architecture dependency tests when refactoring.

Before the first release, evolve the single V1 domain schema directly; do not maintain upgrade paths between development revisions. Architecture and user guides describe the current design, not a history of refactors.

## Where to read next

Start with the architecture overview and roadmap linked above, then the GitHub
issue's scope/dependencies and only the task-relevant guide below. Use that guide
to scope source inspection; a repository-wide scan is not the onboarding path.

| Task | Canonical guide |
|---|---|
| Java package layout, ports, adapters and wiring boundaries | [Hexagonal control plane](docs/architecture/hexagonal-control-plane.md) |
| PostgreSQL, schema initialization and engine/domain transactions | [Control-plane persistence](docs/architecture/control-plane-persistence.md) |
| Webhook authentication, inbox and asynchronous processing | [Webhook inbox](docs/architecture/webhook-inbox.md) |
| Human plan approval, trusted pins and invalidation | [Manual plan approval](docs/architecture/manual-plan-approval.md) |
| Job/story/epic entities, transitions, approval and recovery guards | [Lifecycle](docs/architecture/lifecycle-states.md) |
| Approved run startup, initial activity and engine/domain atomicity | [Approved execution](docs/architecture/approved-execution.md) — Java backend; inbox trigger pending |
| Flowable progression and ticket/repository integration boundaries | [Workflow and provider boundaries](docs/architecture/workflow-boundaries.md) — target design and implementation status |
| Spring Boot worker OAuth2 settings and principal mapping | [Worker authentication](docs/user-guide/worker-authentication.md) — external identity provider; authentication boundary only |
| Worker API messages, leases, fencing and cancellation | [Worker protocol](docs/requirements/worker-protocol.md) — proposed contract, not implemented endpoints |
| Local build, verification and runtime commands | [Local control plane](docs/user-guide/local-control-plane.md) |
| Evidence behind a design decision or experiment reproduction | [Research index](docs/research/README.md) — historical evidence, not the current specification |

When a change adds or changes a feature contract, update its canonical guide and
category README in the same PR. Add/update this task map when a new reading path
is needed. Keep technical details in those guides rather than duplicating them
here; mark proposed behavior explicitly and keep current architecture/user guides
free of refactor history. Read further only when the task crosses a boundary or
the relevant guide points to a necessary decision.

## Documentation is part of done

For every change, assess documentation impact before coding and recheck it against
the final diff before marking the PR ready. This includes code/package structure,
behavior, contracts, configuration, build/run/test commands and operational limits.

- Update affected canonical requirements, architecture and consumer guides in the
  same PR as the change. Update research/reproduction instructions when evidence
  or experiments change. Do not defer necessary documentation to another agent.
- Keep agent discovery current: update affected category READMEs and AGENTS.md
  navigation when guides, entry points, package boundaries or file locations
  change. A new agent must be able to follow links to the relevant guide without
  scanning the repository. Keep detailed knowledge in one canonical guide.
- Document the current structure and behavior. Distinguish proposed, implemented
  and verified behavior; remove stale instructions. Update existing Mermaid
  diagrams when their flow changes, and add one when it materially clarifies a
  workflow or boundary. Preserve historical evidence in research, not as a
  running refactor history in current guides.
- Verify affected links, examples and commands as appropriate; parse changed
  Mermaid diagrams where tooling is available. Report unexecuted commands or
  unverified rendering explicitly. Keep sensitive data out of examples/evidence.
- In the PR, name the documentation updated and its verification. If no update is
  needed, state the specific reason after reviewing the relevant guides; a bare
  “docs not needed” is insufficient. Missing necessary documentation means the
  change is not complete and must not be marked ready for review.
