# Architecture (preferred proposal)

Status: Java-first direction; **Flowable is preferred pending a small PoC, not a validated production choice**. ForgeFlow is a specialized, project-configurable software-development workflow/BPMS platform. Implementation is not yet available.

## Control plane and authority
A modular Spring Boot Java application, initially hosted on Raspberry Pi, embeds Flowable BPMN and uses PostgreSQL. Modules cover GitHub integration, workflow/activity registry, domain policy, DAG scheduling, worker dispatch and audit/reconciliation. Start with one deployable control plane.

GitHub remains authoritative for source artifacts, issues, branches, PRs, reviews and checks. PostgreSQL stores engine persistence, approved plan identities, immutable definition references, job leases, history and reconciliation checkpoints. Persist verified GitHub deliveries in an idempotent inbox before acknowledgment. Reconcile GitHub state after missed events or outages; local state cannot prove a PR merged.

## Separate responsibilities
- **BPMN orchestration:** durable process lifecycle, activity sequencing, human waits, timers, bounded retry/escalation and cancellation. Each project may choose a different reviewed process.
- **Epic/story DAG scheduler:** validate dependencies, reject cycles, deterministically select ready stories and enforce concurrency budgets. A dependency is satisfied only after its PR merges into the correct epic branch. Do not generate a BPMN definition for every story dependency graph.
- **ForgeFlow domain policies:** authorized approvals, branch/base correctness, required checks/reviews, freshness, security and merge eligibility. Enforce these in application services at every side-effect boundary, including BPMN activity calls. Configurable workflows and model output cannot bypass them.

## Definitions and execution contracts
A **versioned workflow definition** contains reviewed BPMN, project configuration and compatible activity references. A run pins an immutable definition version, configuration digest and approved plan revision. New versions apply to new runs; migration requires an explicit reviewed strategy. Changes to approved stories, dependencies or security-relevant configuration require reapproval.

A **registered activity definition** has a stable ID/version, typed input/output schema, required capabilities, timeout/retry/cancellation semantics and allowed side effects. Examples include approval, context preparation, coding/testing, PR creation and verification. Registration is trusted administrative configuration; project BPMN cannot invoke arbitrary scripts or Java classes.

A **pluggable execution provider** implements a common invocation/result contract: inputs, context artifact references, model configuration, cancellation, usage and sanitized evidence. Codex is the initial candidate; Claude and Ollama/Qwen illustrate future adapters, not promised integrations. A provider is the execution integration; a model is a selected inference capability; a context builder assembles bounded repository/task/dependency evidence and provenance. Keep these independently replaceable. Provider swapping must preserve approval/merge semantics and does not imply equivalent output quality.

## Execution plane
A Java external worker controller on Ubuntu polls ForgeFlow's authenticated, versioned API and advertises capabilities. Workers never access PostgreSQL or engine internals directly. ForgeFlow adapts durable BPMN activity work to its worker contract. The PoC must resolve engine locks versus ForgeFlow leases, transaction boundaries, completion correlation and retry ownership; native engine endpoints alone do not establish this contract.

Use scoped worker identity over TLS; select authentication and rotation in the PoC. Claim, heartbeat, result and cancel operations carry job/attempt identity, lease/fencing token, idempotency key and schema version. Capability matching covers OS/architecture, toolchain image, CPU/memory, provider/model availability, context limits and security constraints. Reject unsupported work before claim.

The trusted Java controller launches one isolated disposable Docker job per story, initially concurrency one. Provider tools and project toolchains run inside the sandbox. Never expose the Docker host socket or broad host mounts to it. Bound resources and network access; use short-lived scoped credentials, redact logs and enforce retention. Repository content and model output are untrusted. Keep orchestration independent of worker location; cloud worker adapters remain later scope.

## Default development workflow
Draft epic/stories with acceptance mapping; an authorized human approves an immutable plan. Create epic branch from main. Schedule ready stories from the latest epic head. Build bounded context, code/test in isolation, and open story PRs targeting the epic branch. Copilot feedback may trigger bounded remediation (default two attempts); ambiguous feedback escalates. A successful job is not a merged story.

Before any merge, verify GitHub permissions, correct target, current head/base SHAs, required reviews/checks and freshness. Update/retest stale branches. Only verified story merges unlock dependents. Integrated acceptance and architecture review precede explicit final human approval of epic-to-main merge and release. A label or worker assertion alone is insufficient approval evidence.

## Durability and recovery
Use durable inbox/outbox or an equivalently demonstrated transaction/reconciliation design for engine/domain/dispatch boundaries. Persist correlation IDs and uniquely keyed side effects. Heartbeats, leases and fencing reject stale workers. After expiry or restart, reconcile containers, branches, PRs and GitHub checks before retrying; assume at-least-once delivery, not exactly-once external effects. Demonstrate no duplicate PR or active job after a crash between external success and durable completion. Preserve pause/cancel, bounded retries, budgets and audit events. Job, story, epic and BPMN instance states remain distinct.

## First vertical slice and decision gate
One repository, one approved test issue, one active job: verified webhook → durable workflow instance → Java external worker → isolated coding/testing → branch and PR. Use a minimal manual approval interface without requiring the full planner. No autonomous merging.

A small PoC must demonstrate:
1. Two project-specific workflow variants composing registered activities with mandatory policies enforced; definition version pinning across a new deployment.
2. Control-plane/worker restart, lease expiry, duplicate completion and stale-worker rejection with reconciled external effects.
3. Provider swapping through the same activity contract using Codex plus a second adapter or deterministic test provider; distinguish simulated from real-provider evidence.
4. Authenticated API, capability mismatch rejection, cancellation and scoped Docker execution; a tested PR in a sandbox repository.

Record a go/no-go decision, tested Java/Spring Boot/Flowable/PostgreSQL versions, resource measurements, limitations and unresolved failures. Documentation review is not PoC completion.

## Risks and unresolved assumptions
- Flowable compatibility, licensing/distribution obligations, BPMN deployment governance, schema upgrades and Pi CPU/memory budget require validation. Enable only needed engine modules.
- Engine transactions, worker leases, retry ownership and cancellation races may duplicate side effects unless the PoC proves recovery.
- Worker authentication/credential broker, authorized approver identity, audit schema, retention and project isolation remain to be selected.
- Codex unattended authentication/billing and Linux architecture support, other providers' tooling/context limits and actual context quality need sandbox evidence.
- Copilot event semantics, GitHub App permissions and protection/check configuration remain unverified. Enforce eligibility even when repository protection is absent; do not claim protection is configured.
- Running-instance migration, rich visual editing, multi-tenant hosting, cloud adapters and general-purpose BPMS features are outside the first slice.

## References and tracking
Official [Flowable Spring Boot integration](https://www.flowable.com/open-source/docs/bpmn/ch05a-Spring-Boot) and [external worker Java API](https://www.flowable.com/open-source/docs/all-javadocs/org/flowable/job/api/package-summary.html) motivate the PoC; they do not validate this deployment or contract. Verify selected open-source release capabilities rather than assuming enterprise features are available.

See the [roadmap](roadmap.md), [earlier review](architecture-review-2026-10-13.md), [Flowable PoC #18](https://github.com/skizzay/forgeflow/issues/18), [foundation #1](https://github.com/skizzay/forgeflow/issues/1), [execution #3](https://github.com/skizzay/forgeflow/issues/3), [API #9](https://github.com/skizzay/forgeflow/issues/9), [worker #10](https://github.com/skizzay/forgeflow/issues/10) and [vertical slice #11](https://github.com/skizzay/forgeflow/issues/11).
