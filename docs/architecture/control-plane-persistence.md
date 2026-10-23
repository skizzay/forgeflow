# Control-plane persistence bootstrap

Tracks [#7](https://github.com/skizzay/forgeflow/issues/7), parent
[Foundation #1](https://github.com/skizzay/forgeflow/issues/1). Consumes the accepted
[#18 research decision](https://github.com/skizzay/forgeflow/issues/18); no POC code
is adopted. [Documentation PR #26](https://github.com/skizzay/forgeflow/pull/26)
publishes the separate research record and category layout.

## Selected stack

Java 21, Spring Boot 4.0.9, Flowable OSS 8.0.0, PostgreSQL 17.11. Maven 3.9.9 is
provided by the development build container. Boot manages JDBC, Flyway and test
dependency versions. This advances the research stack deliberately: Flowable 8
requires Spring Framework 7 / Boot 4 and defaults to Jackson 3. Boot 4.0.9 is the
current patch in that compatible minor line; Boot 4.1 is not needed for this
bootstrap. PostgreSQL remains on the research major with current maintenance fixes.
Compatibility is tested here; these pins do not establish Pi capacity, legal
clearance or a complete security/deployment review. Re-evaluate patches before deployment.

Sources checked for this implementation:
[Flowable 8 release](https://github.com/flowable/flowable-engine/releases/tag/flowable-8.0.0),
[Boot 4.0 requirements](https://docs.spring.io/spring-boot/4.0/system-requirements.html),
[PostgreSQL maintenance policy](https://www.postgresql.org/support/versioning/).

## Ownership and transactions

One modular Spring Boot control plane uses one datasource and one Spring JDBC
transaction manager. `forgeflow` contains Flyway-managed domain tables, registry
metadata, pinned run identity, dispatch skeleton and webhook delivery metadata.
`flowable` contains engine-managed tables. There are no cross-schema foreign keys
into engine internals; engine IDs are opaque correlation values. The registry
uses a composite foreign key so a run's key/version cannot name another engine definition.

Starting a workflow and storing its domain correlation must use the same Spring
transaction. A PostgreSQL-backed rollback test demonstrates both writes roll back
together. Bootstrap registration is serialized with a transaction-scoped advisory
lock and deploys the packaged side-effect-free workflow once; content mismatch or
a missing registered definition fails startup. A version change must use a new
registry version, never overwrite an existing registered definition.

This story has no run-start endpoint, approval authority, scheduler or worker
claim/completion API. Its packaged process waits at a receive task and has no
side effects. The restart test creates a clearly marked approval fixture, deploys
a second definition, and verifies the original run and stored plan/config/approval
identity remain unchanged. That is persistence evidence, not authorization enforcement.
No job update implicitly marks a story merged. #14 owns guarded domain transitions;
#9/#15 own lease/fencing/recovery policy, and #8 owns verified inbox ingestion.
Schema states are a constrained initial vocabulary, not those completed behaviors.

## Migration and upgrade boundary

Runtime uses Flowable schema-update `false` and Flyway validation only. Missing or
incompatible tables/checksums stop startup. The explicit `migrate` profile runs
Flyway first, then Flowable schema maintenance, registers the packaged workflow,
and exits; it has no HTTP server. Use a separate migration identity with DDL
privileges. Runtime needs schema USAGE and table DML/sequence privileges, not DDL.
No Flyway clean, baseline-on-migrate, process auto-migration, engine REST routes,
arbitrary BPMN upload or async executor is enabled.

The development Compose database uses a disposable local administrator. Do not
reuse that identity/password for deployment. Provision deployment roles separately;
the integration test also verifies runtime startup with a DML-only identity.

For an engine upgrade: stop dispatch and control-plane writers, save matching
application artifact/configuration and a logical database backup, restore it into
an isolated database, run the proposed migration command with the migration role,
then start runtime without DDL and verify waiting instances and domain correlation.
Review the exact upstream engine SQL and release notes before running migration
against a deployed database. Engine automatic migration is confined to the explicit
maintenance command, not application startup. ForgeFlow and engine migration steps
are not one atomic upgrade: a later failure requires investigation/restore, not
blind retry or an assumed downgrade. Roll back by restoring the matching backup
and old artifact. No deployed upgrade or backup rehearsal is claimed by #7.

## HTTP and deployment boundary

Runtime binds to loopback by default. Only GET `/actuator/health` is public, with
status only; other requests are denied. Worker and GitHub routes do not exist yet.
The local Docker command binds inside its container and publishes only host loopback.
No external identity service, tunnel or Docker host socket is configured. The
execution plane remains a separate Java controller and isolated job containers.
