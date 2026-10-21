# ADR 0002: Flowable feasibility evidence

Status: **Accepted bounded feasibility result — maintainer direction, 2026-10-21.**
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
isolation. Nineteen integration tests, three schema phases, one resource probe
and four container outcomes passed on Linux amd64 with Java 21,
Spring Boot 3.5.4, Flowable 7.2.0 and PostgreSQL 17.6.

## Decision and consequences

Completed experiments support technical fit; no experiment has established a
reason to reject Flowable. On 2026-10-21 the maintainer accepted these results as
sufficient to close the bounded spike. This supersedes the earlier HOLD; it is
not approval to dispatch production jobs or merge PR #21.
The real Codex/container exercise now passes using existing ChatGPT access-only
sign-in in the user-designated local Git sandbox. Five independent Java checks and
workflow completion passed; usage counters and a local story review are recorded.
Actual GitHub issue/branch/PR integration and scoped App credentials remain
follow-up work under #11/#13/#15. Local review artifacts do not satisfy those
criteria, and closure does not claim otherwise. Pi hardware is unavailable; host measurements are
not Pi sizing evidence. Production identity, patched version selection, runtime
schema privileges and redistribution notices remain explicit delivery requirements.
Fixture approval, deterministic providers and simulated SCM must remain visibly
separate from real human/GitHub/Codex evidence. Proceed with the selected Flowable
direction subject to normal review, dependency integration and human approval gates.

Story branches for #7/#8/#14/#15 may hold reviewable implementation plans while
their dependencies remain blocked. A draft PR into main is a review artifact,
not proof that its dependency has merged into the epic branch. All merges need
explicit human approval; no auto-merge is configured here.
