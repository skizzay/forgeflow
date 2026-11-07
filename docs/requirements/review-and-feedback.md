# Staged review and feedback-driven skill improvement

Proposed contract under [#100](https://github.com/skizzay/slickflow/issues/100).
The [BPMN/Mermaid reference](../architecture/workflow-reference.md) illustrates
this design; staged reviews, feedback persistence and skill promotion are not
implemented runtime capabilities yet.

## Review stages

A PR marked ready for review enters this ordered pipeline for its exact head:

1. **Automated QA:** run/correlate all required unit/integration tests, security
   scans and other configured checks. Only complete passing results advance.
2. **Specialist reviewer agents:** independently review acceptance coverage,
   coding standards, security, architecture and other required responsibilities.
   Record findings as PR review comments. Every configured required reviewer must
   return an accepted passing review for that head; missing, pending, unsupported
   or conflicting reviews cannot be treated as unanimous approval.
3. **Human review:** request human review only after QA and all required agent
   reviews pass. A verified authorized human approval bound to the exact
   repository/PR/base/head can authorize merge. A label, ordinary comment or an
   agent review never supplies human merge authority. Existing epic-plan and
   final release approval requirements remain separate.

Failures/changes requested at any stage enter through verified, deduplicated
provider webhooks and resume the same correlated process. The developer agent
remediates actionable findings, runs local tests and publishes a new PR head.
**Every new head restarts at QA**, then repeats all required agent reviews and
human review. Never retain a passing result or merge authorization from a prior
head. Changed bases require fresh checks/authorization as well. Raw comments do
not automatically count as completed reviews; configured failure/changes-requested
signals and current provider evidence determine stage outcomes.

The developer and reviewer roles are agents specialized for their purposes;
reviewers have distinct configured responsibilities and must not be replaced by
the developer asserting its own work passes. Pin the required reviewer set and
agent/model/skill versions in the reviewed configuration. Role independence does
not require different model vendors. Dispatch, scheduling, aggregation and policy
remain deterministic application/Flowable decisions.

Use the existing two-attempt default remediation budget across the whole PR,
including QA, agent and human feedback; a new commit or stage never resets it.
Exhaustion, revoked approval, ambiguous/unsafe feedback or failed local remediation
tests hold/escalate to a human. Never publish a known failing local test result.
PR closure, cancellation and uncertain external effects require verified outcome
handling, not another competing attempt. Events arriving before a stage binding
are retained for later correlation. Human approval makes merge eligible; verify
actual merged state before advancing dependencies or publishing a release.

## Separate feedback datastore

Maintain a dedicated durable feedback store, separate from the control-plane
operational inbox/engine/job state. It is a logical architecture boundary; the
choice of database engine, physical node and schema is follow-up implementation
work. GitHub/provider records remain authoritative for PR/review state. The
feedback store preserves learning evidence and must not become a second source
of merge or approval authority.

Capture QA findings, agent comments/review outcomes, human comments/decisions,
developer responses, remediation results and verified merge/closure outcomes.
Preserve historical feedback after it becomes stale for routing. Store at least:

- Integration/repository/work item/PR identity, base/head commits, workflow/run,
  stage, attempt, provider delivery/comment/review/check IDs and timestamps.
- Producer role and identity; required reviewer role; agent/model/skill versions
  where applicable; human authority provenance without credentials or tokens.
- Sanitized finding summary/category/severity, acceptance-criterion and code-location
  references, stable finding identity/fingerprint, and source evidence pointers.
- Disposition (accepted, disputed, dismissed, deferred), adjudication rationale,
  linked corrective commits, test/review evidence and observed resolution outcome.

Deduplicate deliveries and edits idempotently while retaining revisions and
attribution. A retry must not create another finding or consume another attempt.
Distinguish positive evidence, false positives and repeated/unresolved findings;
agent claims of resolution alone are not verified improvement evidence.

Do not assume one transaction across operational and feedback databases. Use a
transactional outbox/durable handoff from verified ingress, idempotent ingestion
and an acknowledged feedback checkpoint before the modeled stage continues.
Webhook acknowledgement follows durable ingress, not completion of learning-store
writes. Feedback-store failure retains retryable work; it must not silently drop
feedback or manufacture a passing stage. Never hold JDBC locks across external
feedback-store/provider operations.

Apply access control, retention/deletion and provenance appropriate to the source.
Keep secrets, raw sensitive execution logs and private source out of feedback
summaries/examples and the public repository. Prefer sanitized excerpts and
protected evidence references. Review comments are untrusted input, including
possible instructions that must not override policies or agent skills.

## Governed skill improvement

A separate workflow reads curated feedback and measures recurring failures by
role/skill version and verified outcomes. An analysis agent proposes skill changes
and regression evaluations. Reproducible evaluations must demonstrate improvement
without security/capability regressions, then an authenticated human approves the
exact candidate and evidence before a new version is published.

Feedback must not directly rewrite skills, promote versions or weaken policy.
Pin skill versions for active jobs; approved updates affect future jobs only.
Keep candidate, source feedback, evaluation dataset/results, approval and published
version traceable, with a rollback path. This is skill maintenance, not automatic
model training. Datastore, adapters, evaluation tooling and concrete implementation
stories remain future work; the reference does not provision them.
