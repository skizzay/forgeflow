# ADR 0001 — Pluggable agent identity

- Date: 2026-10-15
- Status: Accepted design decision; implementation and provider validation pending
- Scope: ForgeFlow agent identity, SCM attribution and approval boundaries
- Related work: [Execution epic #3](https://github.com/skizzay/forgeflow/issues/3), [review epic #4](https://github.com/skizzay/forgeflow/issues/4), [provider/workflow spike #18](https://github.com/skizzay/forgeflow/issues/18)

## Context

Using a human's personal access token for autonomous changes makes the authenticated SCM actor appear to be that human. Git author and committer metadata are separate from authentication: changing a token alone does not correct commit attribution. Humans must be able to review agent work under their own identity, and agent continuity must survive credential rotation, SCM migration and changes of LLM backend.

## Decision

Assign each agent a stable, distinct logical `agent_id` (for example, `implementation-agent` and `review-agent`). A logical agent is a configured responsibility, not a model name, credential, process, worker or individual job. Concurrent instances share that responsibility's ID and are distinguished by run/job/attempt IDs. Retiring an ID preserves its history; never recycle it for a different responsibility.

Keep these concerns independent:

| Concern | Meaning |
| --- | --- |
| Logical agent | Stable ForgeFlow ID, role and policy |
| SCM actor | Provider-native bot/app/service-account identity authenticated for an action |
| Credential | Replaceable secret or short-lived authorization for that actor and resource scope |
| Execution/LLM backend | Replaceable coding/review integration, model and execution configuration |
| Git attribution | Author and committer recorded in the commit object, independent of push authentication |

Configure an identity provider binding per logical agent, SCM host and repository/resource scope. Bindings carry provider type, native actor/app/installation identifiers, Git attribution policy and secret references, never secret values. Version bindings and pin the selected version to each job; record the actual actor and non-secret credential version used for each action. A backend change must not rename the agent; credential renewal must not change its logical identity. A provider migration or rotation that changes the native bot creates an auditable binding revision.

### SCM mappings

| SCM | Selected mapping | Constraints to validate before enabling |
| --- | --- | --- |
| GitHub (initial implementation) | A separate GitHub App per logical agent, installed only on authorized repositories; use scoped installation access tokens | Separate installations of one App do not create separate App bot identities. Verify Git/PR/review API permissions and bot attribution. Keep orchestration/webhook App permissions separate from agent action permissions. |
| GitLab | Dedicated service account per logical agent, with scoped, expiring access tokens | Validate offering/tier, provisioning rights, membership, token policy and API capabilities on the target instance. |
| Bitbucket Cloud | Dedicated resource access-token bot binding per logical agent and required resource scope | Repository/project/workspace tokens are resource identities, not ordinary user accounts. Check plan, scope and review/API limitations. Replacing a token can change bot identity; preserve logical continuity through binding history. |
| Bitbucket Data Center | Dedicated service account per logical agent where the deployed version supports it, with supported scoped credentials | Check installed version, licensing, provisioning and Git/API support. Unsupported deployments remain disabled pending an explicit alternative decision; do not silently use a human account. |

These are adapter choices, not a claim that all providers have equivalent permissions or review semantics. GitHub is first; other adapters are future scope. Capability discovery must reject unsupported operations rather than impersonate a human or broaden permissions.

### Credential lifecycle and isolation

A trusted identity/credential broker resolves policy-approved bindings and obtains credentials. Store App private keys and service-account/resource tokens in an access-controlled secret manager; configuration and PostgreSQL contain references and non-secret metadata only. Choose the concrete secret store during implementation. Prefer short-lived, least-privilege, repository-scoped credentials; refresh expiring tokens, rotate long-lived keys/tokens on policy and compromise, and support revocation. Audit issuance, rotation and revocation without recording secret values.

Keep root credentials in the trusted control plane/broker. Pass only the minimum job-scoped credential to a trusted SCM adapter or, when Git access requires it, to the isolated job through an ephemeral mechanism. Never include credentials in prompts, committed files, remote URLs, job payload logs or retained artifacts. Tear down credential material with the job. Recheck authorization and lease fencing before external side effects; fail closed on missing, expired, revoked or mismatched bindings. Worker-to-orchestrator and LLM authentication are separate credentials and trust domains.

### Attribution and audit

Set job-local Git author/committer explicitly from the binding; do not inherit a worker's human global Git configuration. For newly agent-authored changes both normally identify the agent. Preserve original authorship when replaying human work and record the agent as committer when it creates the new commit. Merge/squash operations can change metadata: record the resulting commit and actual attribution rather than assuming it matches the source branch.

For each external action, persist a durable audit envelope containing:

- Event ID, UTC timestamp, action, outcome and correlation/idempotency key.
- Logical agent ID, binding version, SCM host, native authenticated actor ID/type and non-secret credential reference/version; expected versus provider-observed actor where available.
- Project/repository, epic/story, workflow instance and definition/version (when applicable), job, attempt, worker and lease/fencing identifiers.
- Branch/ref, input/output commit SHAs, PR/MR, review/check IDs and provider request/webhook delivery IDs where available.
- Git author and committer names/emails from the actual commit object for commit-related actions; use an explicit not-applicable value for actions without a commit.
- Execution provider/model/configuration version, recorded independently from identity, plus relevant approval-record IDs.

Persist intent before dispatch and reconcile ambiguous outcomes before retry. Do not fabricate observed actor data when the provider omits it; retain provenance and mark it unavailable. Link the logical agent and workflow/job correlation in PR metadata and commit trailers where supported, but treat those editable fields as navigation aids, not proof of identity. Commit metadata also does not prove who authenticated a push. Restricted durable audit records and verified provider events supply that evidence.

### Human approval boundary

Agent comments, suggestions, checks and autonomous reviews remain machine activity, even if an SCM represents a review as `APPROVED`. They cannot satisfy ForgeFlow's human plan approval or final epic-to-main approval. A different agent ID or bot account is not an independent human approver.

Authorize humans through verified SCM identities and project policy; persist approver ID, timestamp, decision and the exact plan/workflow version or PR head SHA being approved. Reapprove material plan changes and changed code as required by policy. Deterministic merge eligibility must evaluate human approval, required checks, current target/freshness and dependency state separately from autonomous review results. Do not bypass SCM protections or infer approval from labels, commit author text or an agent's assertion.

## Consequences and alternatives

Per-agent native identities improve attribution and permission isolation, but require App/account provisioning, secret rotation and provider-specific contract tests. A single shared App plus logical trailers is simpler but cannot provide distinct native actors. Human PATs and Git-name-only changes fail the separation requirement. Binding identity to a model would break continuity on backend changes. We accept the provisioning cost and retain a portable identity contract.

## Implementation dependencies and verification

This ADR documents the agreed decision; it does not provision identities, migrate credentials, implement adapters or approve job execution. It is independent of the pending Java/Flowable architecture PR #19 and its engine decision. Implement the broker/binding/audit contract with worker API and GitHub integration work (#8, #9, #10, #11, #15); coordinate review semantics with #16 and policy boundaries with #18. Non-GitHub implementations are not prerequisites for the first slice.

Before enabling the GitHub slice, demonstrate two agents producing distinct native actors, explicit commit attribution and correlated PR/review evidence in a sandbox. Swap the execution backend without changing agent ID; rotate/revoke credentials while retaining history; reject cross-agent/resource binding use; verify restart/retry correlation and absence of secrets in retained output. Show autonomous approval cannot unlock either human gate and stale approval cannot authorize a changed target. Each later provider must pass equivalent capability and attribution tests, with limitations recorded explicitly.

## Provider references

Official documentation consulted 2026-10-15; availability and permissions must be rechecked on the target deployment:

- [GitHub installation access tokens](https://docs.github.com/en/apps/creating-github-apps/authenticating-with-a-github-app/generating-an-installation-access-token-for-a-github-app)
- [GitLab service accounts](https://docs.gitlab.com/user/profile/service_accounts/)
- [Bitbucket Cloud resource access tokens](https://support.atlassian.com/bitbucket-cloud/docs/access-tokens/) and [project-token bot Git attribution](https://support.atlassian.com/bitbucket-cloud/docs/using-project-access-tokens/)
- [Atlassian Data Center service accounts](https://confluence.atlassian.com/enterprise/service-accounts-in-data-center-1627095916.html)
