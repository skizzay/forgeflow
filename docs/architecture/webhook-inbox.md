# Verified GitHub webhook inbox

Implements [#8](https://github.com/skizzay/forgeflow/issues/8), parent
[Foundation #1](https://github.com/skizzay/forgeflow/issues/1), in
[PR #23](https://github.com/skizzay/forgeflow/pull/23). Dependencies #7 and the
connection-schema refinement #32 are integrated into the Foundation epic. Uses
the existing WebFlux and PostgreSQL/JDBC control plane and shared transaction manager.
The [hexagonal structure](hexagonal-control-plane.md) separates application policy
and ports from HTTP, scheduling, HMAC/JSON and persistence adapters.

## Ingress and trust

An opt-in POST `/webhooks/github` consumes JSON. It joins at most 1 MiB of raw
body by default, also enforcing the limit when Content-Length is absent. Compressed
requests are rejected. The endpoint requires exactly one SHA-256 HMAC signature,
canonical delivery UUID and event name. HMAC-SHA256 is computed over the exact raw
bytes and compared using `MessageDigest.isEqual` before parsing or persistence.
The webhook signing secret is separate from the GitHub App PEM and externalized;
enabled configuration with no secret fails startup. Settings string output redacts it.

Only this HMAC-authenticated POST route is exempt from CSRF when enabled; other
HTTP routes retain deny-by-default security. There is no cookie/login authorization
on the route. Health remains public with status only. JSON parsing, HMAC and the
entire imperative inbox transaction run on bounded-elastic threads, not Netty's
event loop. Ingress has a bounded per-request buffer, not a deployment-wide rate
limiter or tested sustained-load budget. Deploy behind TLS and a rate-limiting
reverse proxy before external exposure. No tunnel or live webhook is provisioned.

GitHub signs the body, not the delivery/event headers, and supplies no signed
expiry timestamp. TLS protects transport; delivery UUID deduplication alone is not
business-effect replay prevention. Later dispatch/reconciliation must enforce
repository/installation authorization and domain idempotency as well. Labels,
actions and authenticated ingress never grant human plan or merge approval.

## Persistence and acknowledgements

The initial V1 schema defines the inbox and event classification tables. The inbox stores:

- Delivery UUID, event type, receipt/processing timestamps and processing state.
- Exact-body SHA-256 and byte count, not the raw body or signature.
- Allowlisted numeric repository/installation IDs; for issue/PR events, action,
  subject ID and number. Raw titles, descriptions, comments, patches, URLs, sender
  identities, credentials and arbitrary JSON fields are discarded.

Issue/PR events require their routing IDs and a bounded action token. Ping and
other event types may have only repository/installation pointers. The inbox's
local repository FK is not populated automatically; a signed external repository
ID does not establish configured repository authority.

Insert and conflict checks share one Spring transaction. New deliveries return
202 only after commit; matching UUID/event/body hashes return 200 without new
work. Reuse of a delivery UUID with changed event/body returns 409. Invalid
signature is 401, invalid headers/JSON/pointers 400, oversized body 413,
unsupported content/encoding 415 and database/transaction failure 503. Persistence
failure is never an accepted acknowledgement. No request-level raw body logging
is added. A lost response after commit is safe to retry under the same UUID.

## Asynchronous processing and recovery

A separate scheduled processor polls up to 100 records per batch, every second by
default. `FOR UPDATE SKIP LOCKED` permits competing processor instances to claim
pending rows safely. Each transaction inserts one `webhook_event` classification
(PING, ISSUE, PULL_REQUEST or UNSUPPORTED) and marks the inbox delivery processed.
The unique delivery key and the shared transaction prevent partial/duplicate
publication. Pending work is discovered from PostgreSQL after restart; there is
no volatile-only notification requirement. A processor error rolls back the item,
logs a fixed message without exception/payload contents, and leaves it pending
for the next polling cycle. A poll stops after the first failure; operational
retry budgets/dead-letter tooling remain future reliability work.

Processing here means durable classification, not job dispatch, GitHub API calls
or treating a story as approved/merged. Consumers can join the classification to
the sanitized inbox envelope. Full event contents, if needed, must be fetched from
GitHub through the scoped App integration in #11/#13/#15 rather than retaining raw
webhook data. No complete webhook replay or source archive can be reconstructed
from this minimal metadata. Retention/purge automation remains operations scope.

Every inbox row requires a payload hash and byte count. HMAC verification occurs
in the ingress use case before insertion; database constraints alone are not
proof of authentication.

## Verification

Real PostgreSQL tests cover bounded known-length/chunked ingress, altered/missing/
malformed/wrong-secret signatures, metadata filtering, simultaneous duplicates,
changed replay identity, commit-before-response replay, pending delivery across
restart, background scheduling, competing processors, failed persistence and a
failure after event insertion that rolls back both publication and inbox update.
Schema tests reject inbox rows without a payload hash or byte count and reject
negative byte counts.
Crash/acknowledgement loss is injected at the committed service boundary and
context restart; these tests do not claim an OS process-kill experiment or live
GitHub delivery. Existing bootstrap, shared rollback and schema-isolation checks
remain in the default verification suite.

Sources: [GitHub signature validation](https://docs.github.com/en/webhooks/using-webhooks/validating-webhook-deliveries),
[GitHub headers and payloads](https://docs.github.com/en/webhooks/webhook-events-and-payloads).
