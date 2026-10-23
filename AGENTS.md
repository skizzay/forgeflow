# Agent instructions

ForgeFlow is a public open-source orchestration project. Do not commit tokens, credentials, private source code, user data, or sensitive execution logs.

Architecture: Spring Boot orchestrator + PostgreSQL; Java polling worker; isolated Docker job containers; GitHub as issue/PR authority. Preserve separation of control and execution planes and a cloud-portable worker contract.

For every change: read relevant architecture and roadmap documents; link the GitHub issue; identify dependencies; implement the smallest coherent change; add/update tests; document verification and tradeoffs in the PR.

When creating or updating a PR for an issue, establish a formal GitHub Development link, not just a title mention, comment, or `Refs #N`:
- For a PR intended to resolve the issue, put `Closes #N` (or an equivalent supported closing keyword) in the PR description. Keep parent epics and dependencies as non-closing references unless that PR actually completes them.
- For partial work, a plan-only PR, or supporting documentation that does not resolve the referenced implementation issue, use a manual Development link where available or a dedicated tracking issue for the actual scope. An ongoing implementation draft may carry its eventual closing link, but must explicitly state that implementation/acceptance remain incomplete and must not merge prematurely.
- Closing keywords establish GitHub's automatic relationship only for PRs targeting the repository's default branch. For epic-target PRs or non-closing associations, establish and verify a manual Development link; do not retarget a PR just to obtain automatic linking. If the available tools cannot establish that link, report the exact missing manual step instead of claiming success.
- After creation or edits, read back the PR's linked issues (for example, GraphQL `closingIssuesReferences`) or inspect the issue's Development section. Retry verification if GitHub indexing is delayed. Verify the intended issue number; a body string match alone is insufficient.
- Link creation does not close an issue or satisfy a story dependency. Preserve draft/incomplete status, human merge approval, and the requirement that prerequisite implementation merges into the appropriate epic branch before dependent execution.


Never bypass human epic approval or final epic-to-main approval. Never auto-merge on an agent's assertion alone. Never treat a story dependency as satisfied until its PR has merged into the epic branch. Never expose Docker host socket to an untrusted development container. Escalate uncertain review feedback; cap remediation attempts.

Favor deterministic scheduling and state transitions over AI-generated control decisions. Design retries for idempotency and recovery.

Documentation categories: [research](docs/research/README.md), [requirements](docs/requirements/README.md), [architecture](docs/architecture/README.md), and [consumer user guide](docs/user-guide/README.md). Read the [architecture overview](docs/architecture/overview.md) and [roadmap](docs/requirements/roadmap.md) before changes. Keep reproduction instructions beside spike code and link them from research.
