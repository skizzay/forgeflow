# Agent instructions

ForgeFlow is a public open-source orchestration project. Do not commit tokens, credentials, private source code, user data, or sensitive execution logs.

Architecture: Java-first Spring Boot modular control plane with embedded Flowable BPMN engine (preferred pending PoC), PostgreSQL, Java external workers over an authenticated versioned API, isolated Docker jobs, and GitHub as issue/PR/source-artifact authority. Preserve separation of control and execution planes and a cloud-portable worker contract.

For every change: read relevant architecture and roadmap documents; link the GitHub issue; identify dependencies; implement the smallest coherent change; add/update appropriate tests (documentation-only changes require link/consistency checks); document verification and tradeoffs in the PR.

Never bypass human epic approval or final epic-to-main approval. Never auto-merge on an agent's assertion alone. Never treat a story dependency as satisfied until its PR has merged into the epic branch. Never expose Docker host socket to an untrusted development container. Escalate uncertain review feedback; cap remediation attempts.

Favor deterministic scheduling and state transitions over AI-generated control decisions. Design retries for idempotency and recovery.

Keep BPMN lifecycle orchestration separate from epic/story dependency DAG scheduling and ForgeFlow approval, security and merge policies. Project workflows compose trusted registered activities but cannot override mandatory policies. Pin immutable workflow/activity versions, configuration digest and approved plan revision for every run; do not silently migrate running instances.

Use pluggable execution providers (Codex, Claude, Ollama/Qwen, etc.) behind a common contract. Provider integration, model selection and context building are distinct concerns. Capability matching must cover toolchain, resources, provider/model and context limits. Do not assume all providers are validated or equivalent in quality.

First scope: approved issue -> durable workflow -> Java external worker -> isolated coding/testing -> PR. Validate project-specific workflows, recovery and provider swapping in a small Flowable PoC before adopting the engine. Avoid a general-purpose BPMS UI, extra services or extra implementation languages without demonstrated need. Record unresolved assumptions and actual verification evidence.
