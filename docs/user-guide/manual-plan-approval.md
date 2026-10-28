# Manually approve one configured plan

[#46](https://github.com/skizzay/forgeflow/issues/46) adds an opt-in approval
command for one pre-provisioned epic/story pair. It records approval only; it does
not start a workflow, create a job or merge code. The feature defaults to disabled.

Use a fresh disposable development database after this V1 schema change; the
pre-release schema has no upgrade path. Follow the
[local setup guide](local-control-plane.md) to migrate before runtime startup.
Provision the repository, epic and story records through trusted setup; no public
provisioning endpoint is added. Both epic and story begin DRAFT. Choose the
workflow key/version and content SHA-256 from its trusted registry entry, and
review the plan/configuration snapshot out of band.

Configure Spring Boot's standard OAuth2 resource-server issuer, audiences and
optional JWKS URI as described in the [worker OAuth2 guide](worker-authentication.md).
Obtain a short-lived human access token using your external provider's approved
login flow. Configure that human's exact subject and grant `forgeflow.plan.approve`.
Do not use the worker service client's token or grant it human approval authority.
No ForgeFlow login server, LDAP adapter or OAuth client-secret configuration is needed.

Supply these additional YAML properties in your external Spring configuration
(all IDs and hashes below are illustrative):

```yaml
forgeflow:
  manual-approval:
    enabled: true
    epic-id: 00000000-0000-0000-0000-000000000101
    story-id: 00000000-0000-0000-0000-000000000102
    subject: human-subject-from-your-provider
    approval-id: 00000000-0000-0000-0000-000000000103
    plan-revision: plan-v1
    configuration-sha256: bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb
    workflow-key: bootstrap
    workflow-version: 1
    definition-sha256: aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
```

The definition hash must match the actual registered workflow, not the example.
The configuration hash identifies your reviewed snapshot; ForgeFlow does not
calculate or resolve that snapshot in this slice. Each changed plan/configuration
or definition needs a newly reviewed approval identity. Settings changes require
restarting the control plane. The issuer and subject together identify the audited
human; changing the issuer changes that identity even when the subject is equal.

Send HTTPS POST `/api/operator/v1/plan-approval` with JSON and an
`Authorization: Bearer <access-token>` header. Keep tokens out of command arguments,
URLs, Git and logs. The request must echo the configured target and reviewed pins:

```json
{
  "commandId": "00000000-0000-0000-0000-000000000104",
  "expectedVersion": 0,
  "epicId": "00000000-0000-0000-0000-000000000101",
  "storyId": "00000000-0000-0000-0000-000000000102",
  "approval": {
    "approvalId": "00000000-0000-0000-0000-000000000103",
    "planRevision": "plan-v1",
    "configurationSha256": "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
  },
  "workflowKey": "bootstrap",
  "workflowVersion": 1,
  "definitionSha256": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
}
```

Success is HTTP 200 with `commandId`, `state: APPROVED` and the incremented epic
`version`. The story remains DRAFT. A lost response can be retried with the same
command ID and identical inputs by the same authenticated human; it returns the
historical receipt without another transition. Do not interpret that receipt as
proof of current approval after a later invalidation. A changed command must use
a new UUID and the latest known epic version. No approval read endpoint is added.

To invalidate before activation, POST `/api/operator/v1/plan-approval/invalidate`
with the same human authority and only these fields:

```json
{
  "commandId": "00000000-0000-0000-0000-000000000105",
  "expectedVersion": 1,
  "epicId": "00000000-0000-0000-0000-000000000101",
  "storyId": "00000000-0000-0000-0000-000000000102"
}
```

The epic returns to DRAFT and its version increments; immutable approval evidence
and lifecycle audit are retained. Reapproval uses the new expected version and a
new command ID. Changed pins require a new approval ID. Active work requires
separate stop/recovery handling; this command cannot reset it. Invalidate against
the originally approved target before configuring a different story/epic pair.

| HTTP | Meaning |
|---|---|
| 200 | Committed receipt, or identical historical replay |
| 400 | Invalid JSON/fields, missing command/version or invalid command shape |
| 401 | Missing or invalid OAuth2 access token |
| 403 | Wrong subject/scope, missing expiry or unauthorized replay actor |
| 404 | Configured epic does not exist |
| 409 | Stale state/version, pin/target/registry mismatch or conflicting command |
| 503 | Storage unavailable; transaction rolled back |

Configured WebFlux in-memory decoding limits apply; this
command accepts no arbitrary plan body, source or logs. Use HTTPS and a protected
TLS-termination hop. JWT revocation limits from the OAuth2 guide also apply.

Run `./scripts/verify-bootstrap.sh` for the full regression suite. Tests use a local
public-key endpoint and disposable PostgreSQL, not a deployed identity provider
or TLS setup. See [architecture](../architecture/manual-plan-approval.md) for
transaction, authority and Flowable boundaries.

The internal `PrepareApprovedStory` Java port can prepare approved parent state
for a later starter; it has no consumer endpoint and creates no workflow/job.
Its transaction joins an outer starter transaction when present. Automatic
execution remains unimplemented; see the [lifecycle guide](../architecture/lifecycle-states.md).
