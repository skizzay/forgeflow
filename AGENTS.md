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
