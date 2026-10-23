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

One modular Spring Boot control plane uses one datasource and one Spring Boot auto-configured JDBC
transaction manager. `forgeflow` contains Flyway-managed domain tables, registry
metadata, pinned run identity, dispatch skeleton and webhook delivery metadata.
`flowable` contains engine-managed tables. Both names are configurable through
`FORGEFLOW_DOMAIN_SCHEMA` / `FORGEFLOW_ENGINE_SCHEMA` (defaults shown here),
or runtime YAML `forgeflow.database.domain-schema` / `engine-schema`. Dedicated
lowercase PostgreSQL identifiers are validated, must differ, and cannot target
public/system schemas. Flyway placeholders, runtime validation, engine metadata
and registry queries use the same settings; changing a name selects a different
schema, it does not rename/move existing data. There are no cross-schema foreign keys
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
incompatible tables/checksums stop startup. The separate `-migrate.jar` release executable runs
Flyway first, then Flowable schema maintenance and exits. It uses no Spring
application context, HTTP server or workflow registration. The service entry point
has no migration profile or profile-dependent shutdown logic. Use a separate migration identity with DDL
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

## Deployment sequencing and blue-green constraints

Run the migration executable once as a release job with DDL credentials before
rolling out runtime containers with DML-only credentials. Complex data backfills
must be separate resumable/batched jobs, not application-startup callbacks.
Use expand/contract migrations: add backwards-compatible schema first, run old/new
versions together only after explicit compatibility tests, switch traffic, then
retire old instances before destructive contract changes. Flyway locking serializes
schema-history changes; it is not a lock for the entire Flyway-plus-engine upgrade.
The release system must serialize the whole migration job. Schema changes and
engine upgrades are separate compatibility decisions: do not assume two Flowable
versions can share an upgraded engine schema. This bootstrap does not provision a
blue-green deployment or prove cross-version engine coexistence.

## HTTP and deployment boundary

Runtime uses Spring WebFlux/Reactor Netty and reactive Spring Security, and binds
to loopback by default. JDBC/Flowable remain imperative/blocking and share Boot’s
JDBC transaction manager. Application-owned persistence work currently runs only during startup; Actuator
adapts the JDBC health contributor for reactive execution. Future HTTP
handlers must delegate the whole imperative transaction to a bounded blocking
executor (for example `Mono.fromCallable(...).subscribeOn(Schedulers.boundedElastic())`),
never block a Netty event loop or span a JDBC transaction across reactive hops.
No R2DBC conversion is implied. Runtime binds to loopback by default. Only GET `/actuator/health` is public, with
status only; other requests are denied. Worker and GitHub routes do not exist yet.
The local Docker command binds inside its container and publishes only host loopback.
No external identity service, tunnel or Docker host socket is configured. The
execution plane remains a separate Java controller and isolated job containers.
