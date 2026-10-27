# Hexagonal control-plane structure

Tracks [#8](https://github.com/skizzay/forgeflow/issues/8) in
[PR #23](https://github.com/skizzay/forgeflow/pull/23). The webhook feature provides
the first ports-and-adapters application core in the control plane.

## Dependency direction

Business rules and use cases depend on Java and core contracts. They have no
Spring annotations, HTTP status codes, Reactor types, SQL, Jackson types or
scheduler dependencies. Framework configuration creates concrete adapters and
injects them into the use case. Existing application entry, schema/migration setup
and packaged workflow initialization remain infrastructure at the control-plane
composition boundary.

The arrows below show source-code dependencies, not execution order:

```mermaid
flowchart LR
  HTTP[WebFlux adapter] --> IN[Input ports]
  POLL[Scheduled poller] --> IN
  APP[WebhookService] --> IN
  APP --> OUT[Output ports]
  APP --> DOMAIN[Domain model]
  IN --> DOMAIN
  OUT --> DOMAIN
  JDBC[JDBC and transaction adapters] --> OUT
  CODEC[HMAC and JSON adapters] --> OUT
  CONFIG[Spring composition] --> APP
  CONFIG --> JDBC
  CONFIG --> CODEC
  CONFIG --> POLL
```

| Package under `webhook` | Responsibility |
|---|---|
| `domain` | Delivery identity, validated metadata, classification, receipt and typed failures |
| `application` | Authenticate before decode/store; duplicate/conflict policy; classify and complete pending work |
| `application.port.in` | `ReceiveWebhook` and `ProcessWebhook` entry points; immutable signed input |
| `application.port.out` | Repository, transaction, signature-verification and payload-decoding contracts |
| `adapter.in.http` | Body bounds, headers/media handling, bounded-elastic execution and HTTP error mapping |
| `adapter.in.scheduling` | Poll timing, bounded batches and safe failure logging |
| `adapter.out.persistence` | SQL, row locks, JSONB persistence and Spring transaction implementation |
| `adapter.out.security` | JDK SHA-256 HMAC with the configured signing secret |
| `adapter.out.json` | Jackson/GitHub payload decoding into typed allowlisted metadata |
| `configuration` | Spring properties, conditional enablement and concrete dependency wiring |

## Transaction and error contracts

The application explicitly requests `TransactionBoundary.required`. The persistence
adapter implements it using the existing Spring transaction manager. Receive does
insert-if-absent plus identity comparison in one transaction. Process claims a
verified row, chooses its domain classification, publishes it and marks completion
in one transaction. Repository locks remain held until that boundary finishes;
the PostgreSQL adapter uses `FOR UPDATE SKIP LOCKED`. These atomicity requirements
belong to the port contract and must be preserved by any replacement adapter.

Adapters translate database/transaction failures into a core storage-unavailable
failure without carrying raw SQL or payload details. The HTTP adapter maps core
failures to 401/400/409/503. The core never chooses an HTTP response. The poller
uses the processing input port and cannot reach JDBC directly.

`WebhookMetadata` is typed and validated; it contains no arbitrary JSON or raw
payload. Serialization happens in the persistence adapter. Signature verification
and JSON decoding are replaceable output ports, while their ordering and the
requirement to authenticate before parsing remain application policy. Signed input
defensively copies payload bytes and redacts its string representation.

## Verification and evolution

Architecture tests inspect compiled production classes with ArchUnit and enforce:

- Domain and application depend only on Java and their own core packages.
- Domain cannot depend on application; ports cannot depend on implementation classes.
- Adapters cannot depend on Spring composition or concrete application service.
- Inbound and outbound adapters cannot depend on each other.

Framework-free use-case tests exercise authentication-before-storage, replay
conflicts, duplicate acknowledgement, required routing identity and classification.
The existing PostgreSQL/HTTP suite continues to verify concrete adapters,
transactions, concurrent delivery, rollback, restart and scheduling. The boundaries are packages inside the current Maven
module; an additional deployment or Maven module is not needed for these boundaries.

Apply the same dependency direction to future worker and orchestration features.
Keep repository/SCM provider concerns behind outbound ports and human approval,
idempotency and state-transition policy in their application/domain core.

The [lifecycle feature](lifecycle-states.md) follows the same core/port/adapter
direction. Its authority adapter defaults to deny-unverified; transition policy
belongs to its job/story/epic domain entities and focused domain policies;
transaction and replay orchestration stay in the framework-free application core.

## OAuth2 worker authentication

The #38 implementation delegates JWT verification to Spring Security's reactive
OAuth2 resource server. Spring Boot auto-configures the reactive JWT decoder from
standard `spring.security.oauth2.resourceserver.jwt` properties;
`worker.configuration` wires the worker route policy and requires issuer/audience
configuration. The identity adapter additionally requires an expiry claim. The external provider
owns accounts, client credentials, directories and token issuance; LDAP/AD
federation belongs there. ForgeFlow does not implement an identity platform.

`worker.adapter.in.security.WorkerAuthentication` maps the validated subject from
the trusted issuer to a stable `worker.domain.WorkerPrincipal`. This framework-free
value carries the worker UUID into future use cases; JWT and Spring types stay
in the adapter/configuration boundary. Only `forgeflow.worker` scope becomes a
worker authority, never human approval authority. Resource ownership, fencing and
approval checks remain contextual application/domain guards.

The stateless chain matches only `/api/worker/v1` and descendants. Other paths
retain the existing health/webhook/default-deny chain. No request-body identity
is trusted; CSRF is disabled only on this bearer-only chain. No worker use case,
lease, provider port or Flowable routing is introduced. See the
[configuration guide](../user-guide/worker-authentication.md) for settings and limits.

## Manual human approval

The lifecycle hexagon additionally exposes `RecordManualApproval`, backed by
`ManualApprovalService` and the `ManualApprovalStore` outbound port. A WebFlux
adapter authenticates the operator and hands the imperative operation to bounded
elastic threads. `ManualPlan` belongs in `domain.approval`; JDBC evidence storage
and OAuth2 wiring remain adapters/configuration. The private scoped authority
does not replace the general deny-unverified lifecycle authority. See
[manual approval](manual-plan-approval.md) for the transaction and current limits.

The [execution hexagon](approved-execution.md) uses `StartApprovedExecution` and
`QueueInitialActivity` input ports, framework-free application services and
`ExecutionStore`/`StartWorkflow` output ports. It reuses lifecycle policies; JDBC
and Flowable adapters remain independent and are wired by configuration.
