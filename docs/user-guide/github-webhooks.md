# Configure the local GitHub webhook inbox

Tracks [#8](https://github.com/skizzay/forgeflow/issues/8). First follow the
[local control-plane guide](local-control-plane.md) to build and migrate. Run the
independent migration executable again to apply V2 to an existing V1 database;
there is no V1 checksum rewrite in this story.

Webhooks are disabled by default. To enable them, supply these environment
variables to the runtime container from your environment or secret manager:

- `FORGEFLOW_GITHUB_WEBHOOK_ENABLED=true`
- `FORGEFLOW_GITHUB_WEBHOOK_SECRET`: the secret configured for the GitHub App webhook.

This is a signing secret, not the GitHub App private-key PEM. Do not put it in
tracked files, URLs, process arguments or logs. Compose does not forward these
host variables implicitly: pass `-e FORGEFLOW_GITHUB_WEBHOOK_ENABLED` and
`-e FORGEFLOW_GITHUB_WEBHOOK_SECRET` to its `run` command, without literal values.
For example, after exporting the variables securely in your shell:

```sh
docker compose -p forgeflow-dev run --rm --service-ports \
  -e FORGEFLOW_GITHUB_WEBHOOK_ENABLED -e FORGEFLOW_GITHUB_WEBHOOK_SECRET \
  build java -jar control-plane/target/control-plane-0.1.0-SNAPSHOT.jar --server.address=0.0.0.0
```

The webhook URL is `/webhooks/github`. Configure GitHub to send JSON and the
matching secret. Local Compose still publishes only host loopback; configuring
an external HTTPS route and a live GitHub App is separate deployment/integration
work. The implementation does not read the PEM, provision a tunnel or contact GitHub.

A valid delivery returns 202 after persistence; a matching retry returns 200.
Same UUID with changed content returns 409. A 503 is not an acknowledgement:
retry the original delivery after database recovery. Unsupported event types are
classified as UNSUPPORTED; no event starts an agent or approves a plan.

Optional YAML settings under `forgeflow.github.webhook`:

| Setting | Default | Purpose |
|---|---|---|
| `max-bytes` | `1048576` | Maximum raw request size; up to 10 MiB supported |
| `processing-enabled` | `true` | Poll/classify persisted inbox deliveries |
| `poll-interval` | `PT1S` | Delay between bounded processing batches |

For inspection, `webhook_inbox` stores only sanitized pointers and a body hash;
`webhook_event` stores the processing classification. Raw payloads are deliberately
unavailable. Pending verified records resume after restart. See the
[inbox design](../architecture/webhook-inbox.md) for limits and recovery semantics.

Run the entire PostgreSQL regression suite with `./scripts/verify-bootstrap.sh`.
The tests use synthetic events and temporary secrets and never send a GitHub webhook.
