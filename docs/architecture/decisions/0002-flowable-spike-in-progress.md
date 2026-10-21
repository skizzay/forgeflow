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

The [executable spike](../../../spikes/flowable/README.md) demonstrates PostgreSQL
process persistence, definition pinning across deployment/restart, guarded
activity execution with rollback on rejection, and deterministic test-provider
substitution. The local [recovery experiment](../../research/flowable-recovery-experiment.md)
adds process-crash injection, atomic engine/domain completion, fencing,
reconciliation, competing claims and cancellation races with simulated SCM effects.
The [results matrix](../../research/issue-18-results.md) adds authenticated HTTPS
handoff with separate Java workers, closed registered workflows, manifest-bound
approval, heartbeat/identity checks, schema upgrade/restore and fixed container
isolation. Seventeen integration tests, three schema phases, one resource probe
and four container outcomes passed on Linux amd64 with Java 21,
Spring Boot 3.5.4, Flowable 7.2.0 and PostgreSQL 17.6.

## Decision and consequences

Completed experiments support technical fit; no experiment has established a
reason to reject Flowable. Do not mark #18 complete or dispatch production jobs.
The remaining adoption evidence is the real Codex/container/GitHub run and #13
authentication/usage validation. A disposable repository and test authentication
choice/location are pending. Pi hardware is unavailable; host measurements are
not Pi sizing evidence. Production identity, patched version selection, runtime
schema privileges and redistribution notices remain explicit delivery requirements.
Fixture approval, deterministic providers and simulated SCM must remain visibly
separate from real human/GitHub/Codex evidence. Overall adoption remains HOLD.

Story branches for #7/#8/#14/#15 may hold reviewable implementation plans while
their dependencies remain blocked. A draft PR into main is a review artifact,
not proof that its dependency has merged into the epic branch. All merges need
explicit human approval; no auto-merge is configured here.
