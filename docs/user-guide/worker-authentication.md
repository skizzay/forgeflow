# Configured-worker authentication

Draft implementation for [#38](https://github.com/skizzay/forgeflow/issues/38).
This enables authentication for worker v1 routes, not job execution: production
claim/heartbeat/result endpoints are not implemented. A valid credential on an
absent route returns 404. Worker identity does not grant human approval or SCM
access. The [protocol](../requirements/worker-protocol.md) defines future messages.

Access is disabled by default. Supply these settings through your environment or
secret store; Compose needs explicit `-e VARIABLE_NAME` forwarding without values:

| Environment variable | Purpose |
|---|---|
| `FORGEFLOW_WORKER_ENABLED` | `true` enables the dedicated worker v1 security chain |
| `FORGEFLOW_WORKER_ID` | Pre-provisioned worker UUID, stable across credential replacement |
| `FORGEFLOW_WORKER_SECRET` | Cryptographically random URL-safe credential, 32–256 ASCII letters/digits/underscores/hyphens |

Provision that worker record before future claim operations; this feature does
not enroll it in PostgreSQL. Enabled settings require an ID and valid credential
shape; shape validation does not prove entropy. Configuration string output
redacts the secret. Do not place secrets in Git, URLs, command arguments or logs.

Each request uses one `Authorization: Bearer` header. Missing, invalid, repeated
or malformed credentials return 401 with a Bearer challenge and a fixed
UNAUTHENTICATED/STOP JSON error. The body is not read for authentication, so that
error has a null command ID. The authenticated principal always comes from
configuration. No session cookie persists access across requests.

Replace the credential in the server secret configuration and worker, keeping
the UUID, then restart the control plane. The restarted instance rejects the old
credential. There is no hot reload or distributed rotation protocol; retire all
old instances before considering the old credential revoked everywhere. Use HTTPS
and a protected TLS-termination hop. TLS/proxy deployment and live credential
operations are not established by the tests.

Four focused reactive security tests use test-only routes to verify principal
binding, body-identity substitution resistance, route isolation, malformed/absent
credentials, disabled/invalid settings, redaction and replacement. The test probe
is not a production endpoint. Existing PostgreSQL/HTTP regression checks cover
health, signed webhook security and lifecycle behavior. Run the suite with
`./scripts/verify-bootstrap.sh` from the repository root.
