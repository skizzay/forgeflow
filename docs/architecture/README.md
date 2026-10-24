# Architecture

System structure, component boundaries, contracts and design decisions belong here.

- [Architecture overview](overview.md)

The intended stack is Java/Spring Boot with PostgreSQL and a separate Java worker controller. The [decision spike #18](https://github.com/skizzay/forgeflow/issues/18) established bounded Flowable feasibility; production implementation and GitHub integration remain unverified.

- [Flowable decision record](decisions/0002-flowable-spike-in-progress.md) — accepted bounded feasibility result; GitHub integration remains follow-up work.

- [Control-plane persistence bootstrap](control-plane-persistence.md) — implemented schema ownership, migration executable and WebFlux boundary for #7.

- [Verified webhook inbox](webhook-inbox.md) — HMAC ingress, metadata retention and asynchronous classification for #8.
