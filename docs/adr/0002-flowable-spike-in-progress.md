# ADR 0002: Flowable feasibility evidence (in progress)

Status: **HOLD; no production adoption decision**.
Issue: [#18](https://github.com/skizzay/forgeflow/issues/18).
Parent: [Foundation #1](https://github.com/skizzay/forgeflow/issues/1).

## Context

Milestone 1 needs durable orchestration with mandatory approval/security policy,
a separate external worker, and recoverable side effects. Issue #18 is the
decision gate before production bootstrap (#7). The Java-first architecture
proposal is pending review in #19; no documentation PR is merged by this change.

## Evidence

The [executable spike](../../spikes/flowable/README.md) demonstrates PostgreSQL
process persistence, definition pinning across deployment/restart, guarded
activity execution with rollback on rejection, and deterministic test-provider
substitution. Three integration tests passed on Linux amd64 with Java 21,
Spring Boot 3.5.4, Flowable 7.2.0 and PostgreSQL 17.6.

## Decision and consequences

Continue the bounded spike. Do not call Flowable validated, mark #18 complete,
or dispatch production jobs. Before a GO, validate worker authentication,
lease/engine-lock mapping, fencing/cancellation/replay, side-effect reconciliation,
registered activity/workflow policy enforcement, schema upgrades, licensing,
Pi limits and real-provider feasibility (#13). Simulated approvals and providers
must remain visibly separate from real human/GitHub/Codex evidence.

Story branches for #7/#8/#14/#15 may hold reviewable implementation plans while
their dependencies remain blocked. A draft PR into main is a review artifact,
not proof that its dependency has merged into the epic branch. All merges need
explicit human approval; no auto-merge is configured here.
