# #7: Spring Boot and PostgreSQL bootstrap

Issue: [#7](https://github.com/skizzay/forgeflow/issues/7), parent
[Foundation #1](https://github.com/skizzay/forgeflow/issues/1).
Status: implementation under review in [#22](https://github.com/skizzay/forgeflow/pull/22).
Dependency: #18 accepted research decision. Prototype merge is not required.

## Delivered scope

Fresh Java 21 / Boot 4 / Flowable 8 control-plane module; PostgreSQL/Flyway domain
schema; separately managed engine schema; explicit migration command; safe health
endpoint; packaged no-side-effect wait workflow and immutable-version registration
checks. See [persistence design](../architecture/control-plane-persistence.md) and
[local instructions](../user-guide/local-control-plane.md).

PostgreSQL integration tests exercise empty/repeated migration, restart, definition
pinning across a newer deployment, shared engine/domain rollback, schema and
registry incompatibility, DML-only runtime, and HTTP health/denied routes.

## Boundaries and integration

No POC code, worker/approval endpoints, GitHub side effects, scheduler, or production
identity provisioning. #8/#9/#10/#14/#15 remain separate work. #18's prior HOLD and
prototype-merge requirement are superseded by maintainer acceptance and archive
disposition; see documentation-only PR #26.

This branch originated from `epic/1-foundation` off main. The user requested story
PRs into main as a review-target exception. Dependencies are not integrated until
implementation PRs merge into the appropriate epic branch. Keep the PR open for
review; do not merge, retarget or enable auto-merge without explicit approval.
