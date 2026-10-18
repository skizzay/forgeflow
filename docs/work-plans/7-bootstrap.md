# #7: Bootstrap Spring Boot and PostgreSQL schema

Issue: [#7](https://github.com/skizzay/forgeflow/issues/7).
Parent: [Foundation #1](https://github.com/skizzay/forgeflow/issues/1).
Status: **draft plan; dependencies unsatisfied**.
Dependencies: #18 decision evidence.

## Smallest coherent implementation

Implement one modular Java/Spring Boot control plane only after the #18 decision is reviewed and its prerequisite PR is merged into the epic branch. Select versions from the validated compatibility matrix rather than treating the initial spike as adoption evidence.

Keep Flyway-owned ForgeFlow tables separate from engine-managed tables. Persist repository, epic, story, job/attempt, worker, webhook inbox and pinned workflow/activity/plan/configuration identifiers. Job success must not update a story to MERGED. Record engine/domain transaction ownership, outbox boundaries and a rehearsed schema-upgrade procedure.

Verification matrix:
- Empty PostgreSQL database: application starts and migrations apply.
- Repeated startup: migrations are repeatable and data remains intact.
- Engine restart: approved definition/configuration identity remains pinned.
- Migration/registry incompatibility: startup fails clearly rather than mutating an active definition.

No production schema or selected-engine runtime is introduced by this planning draft. #18 currently remains HOLD. Prototype code belongs to #18 until the adoption decision is supported.

## Review and integration

This story branch was created from `epic/1-foundation`, which was created from
main. The user explicitly requested story PRs targeting main; this is a review
target exception to the documented epic-target workflow. A PR into main does not
satisfy the requirement that prerequisite PRs merge into the epic branch before
unlocking dependent execution. Do not merge, retarget, or enable auto-merge without
explicit human direction. Human approval is required for every merge.

Verification for this initial draft: reviewed current issue dependencies,
architecture and roadmap, and checked Markdown formatting with `git diff --check`.
There is no runtime code in this draft and no runtime verification is claimed.
