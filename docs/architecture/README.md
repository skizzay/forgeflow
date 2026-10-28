# Architecture

System structure, component boundaries, contracts and design decisions belong here.

- [Architecture overview](overview.md)

The intended stack is Java/Spring Boot with PostgreSQL and a separate Java worker controller. The [decision spike #18](https://github.com/skizzay/forgeflow/issues/18) established bounded Flowable feasibility; production implementation and GitHub integration remain unverified.

- [Flowable decision record](decisions/0002-flowable-spike-in-progress.md) — accepted bounded feasibility result; GitHub integration remains follow-up work.

- [Control-plane persistence bootstrap](control-plane-persistence.md) — implemented schema ownership, migration executable, run/approval uniqueness (#86) and WebFlux boundary for #7.

- [Verified webhook inbox](webhook-inbox.md) — HMAC ingress, metadata retention and asynchronous classification for #8.

- [Hexagonal control-plane structure](hexagonal-control-plane.md) — framework-free core, ports, adapters and enforced dependency rules.

- [Separate job, story and epic lifecycle](lifecycle-states.md) — guarded transitions, pinned identity, audit and recovery boundaries for #14.

- [Worker OAuth2 authentication](../user-guide/worker-authentication.md) — Boot-managed JWT configuration and principal mapping; ForgeFlow is a resource server, not an identity platform.
- [Worker protocol](../requirements/worker-protocol.md) — proposed API messages, lease ownership and cancellation/recovery diagrams; endpoints are not implemented yet.

- [Flowable and provider boundaries](workflow-boundaries.md) — target event-driven orchestration, independent ticket/repository inputs, review/remediation/merge loop and worker boundaries; includes implementation-status distinctions.

- [Configured manual plan approval](manual-plan-approval.md) — human OAuth2 authority, immutable pins and transactional approval/invalidation (#46).
