# Reviewable delivery slices

Tracks [#36](https://github.com/skizzay/forgeflow/issues/36). GitHub
issues are authoritative for scope, acceptance and dependencies. Parent tracking
issues collect outcomes; their child tickets are the implementation units.

## PR scope

- Deliver one observable behavior per PR, including its meaningful tests and
  necessary documentation. A feature may cross a port and adapter when that is
  required to verify the behavior.
- Aim for roughly 100–300 substantive changed lines as a planning signal. This is
  not a hard ceiling: preserve atomic invariants and tests. Split before coding
  when a ticket bundles independent behaviors or is no longer readily reviewable.
- State the objective, explicit exclusions, named acceptance checks and exact
  prerequisite tickets before implementation. Do not hide missing features in an
  integration/demo PR.
- Add abstractions, compatibility layers or extension points only when the
  current acceptance criteria require them. Start with one configured worker,
  one repository, one active job and one concrete provider.
- Keep human epic approval and every merge explicit. Dependency implementation
  must integrate into the appropriate epic branch; a mainline merge alone is
  insufficient. Story review PRs target main per the owner's current exception.
- Defer later-milestone design until it is needed; split that work into comparable
  one-behavior tickets before starting it.

## Delivery map

| Outcome tracker | Child tickets |
|---|---|
| [#9](https://github.com/skizzay/forgeflow/issues/9) | [#37](https://github.com/skizzay/forgeflow/issues/37) Specify the single-worker protocol, [#38](https://github.com/skizzay/forgeflow/issues/38) Authenticate configured worker principals, [#39](https://github.com/skizzay/forgeflow/issues/39) Atomically claim one eligible job, [#40](https://github.com/skizzay/forgeflow/issues/40) Renew the current attempt lease, [#41](https://github.com/skizzay/forgeflow/issues/41) Accept a fenced, idempotent worker outcome, [#68](https://github.com/skizzay/forgeflow/issues/68) Authorize one operator job cancellation command |
| [#10](https://github.com/skizzay/forgeflow/issues/10) | [#42](https://github.com/skizzay/forgeflow/issues/42) Poll and report a fake activity from the Java worker, [#43](https://github.com/skizzay/forgeflow/issues/43) Launch and clean up one isolated Docker job, [#44](https://github.com/skizzay/forgeflow/issues/44) Run a Codex activity through the Java worker, [#45](https://github.com/skizzay/forgeflow/issues/45) Stop and clean up a cancelled or timed-out job |
| [#11](https://github.com/skizzay/forgeflow/issues/11) | [#46](https://github.com/skizzay/forgeflow/issues/46) Record minimal authorized manual plan approval, [#47](https://github.com/skizzay/forgeflow/issues/47) Create one pinned job from an approved inbox event, [#48](https://github.com/skizzay/forgeflow/issues/48) Publish one story branch from the approved epic head, [#49](https://github.com/skizzay/forgeflow/issues/49) Publish one tested story pull request, [#50](https://github.com/skizzay/forgeflow/issues/50) Demonstrate the approved issue-to-PR vertical slice, [#69](https://github.com/skizzay/forgeflow/issues/69) Obtain short-lived GitHub App access for one repository, [#71](https://github.com/skizzay/forgeflow/issues/71) Publish one tested commit to the prepared story branch |
| [#13](https://github.com/skizzay/forgeflow/issues/13) | [#51](https://github.com/skizzay/forgeflow/issues/51) Validate repository-scoped GitHub App access, [#52](https://github.com/skizzay/forgeflow/issues/52) Verify Codex sign-in lifecycle and failure handling, [#53](https://github.com/skizzay/forgeflow/issues/53) Pin a minimal Codex and Java execution image |
| [#15](https://github.com/skizzay/forgeflow/issues/15) | [#54](https://github.com/skizzay/forgeflow/issues/54) Read normalized GitHub branch, PR and check observations, [#55](https://github.com/skizzay/forgeflow/issues/55) Recover a lost PR publication acknowledgement, [#56](https://github.com/skizzay/forgeflow/issues/56) Reconcile an expired lease before permitting retry, [#70](https://github.com/skizzay/forgeflow/issues/70) Advance one waiting workflow activity after an accepted result |
| [#12](https://github.com/skizzay/forgeflow/issues/12) | [#57](https://github.com/skizzay/forgeflow/issues/57) Parse one versioned epic/story plan format, [#58](https://github.com/skizzay/forgeflow/issues/58) Validate missing dependencies and cycles, [#59](https://github.com/skizzay/forgeflow/issues/59) Resolve pinned plan definitions and mandatory policy, [#60](https://github.com/skizzay/forgeflow/issues/60) Approve a validated plan revision explicitly |
| [#17](https://github.com/skizzay/forgeflow/issues/17) | [#61](https://github.com/skizzay/forgeflow/issues/61) Reject a PR targeting the wrong epic branch, [#62](https://github.com/skizzay/forgeflow/issues/62) Invalidate stale story checks when the epic head moves |
| [#35](https://github.com/skizzay/forgeflow/issues/35) | [#63](https://github.com/skizzay/forgeflow/issues/63) Inventory the provisional SlickFlow rename, [#64](https://github.com/skizzay/forgeflow/issues/64) Rename Java packages and Maven coordinates, [#65](https://github.com/skizzay/forgeflow/issues/65) Rename runtime configuration and Docker assets, [#66](https://github.com/skizzay/forgeflow/issues/66) Rename the GitHub repository and update current links, [#67](https://github.com/skizzay/forgeflow/issues/67) Record provisional-name clearance follow-up |

The hierarchy is also recorded as native GitHub sub-issues. Each child names its
specific dependencies; parent trackers must not become coarse all-or-nothing
implementation dependencies.

## First work and integration gates

Start with the single-worker protocol [#37](https://github.com/skizzay/forgeflow/issues/37).
Approval/job creation, worker authentication/claim/heartbeat/result, isolated
execution and remote publication each have their own PR. Feasibility/image work
can be planned independently where its leaf dependencies permit.

Bootstrap #7, webhook #8 and lifecycle #14 have merged into main. At this
breakdown, #7/#32 are integrated into `epic/1-foundation`; #8/#14 still require
explicitly approved epic integration. Do not infer that a dependent
implementation is unblocked from a closed issue. No branch synchronization or
merge is authorized by this planning change.

Full structured planning (#12) belongs to Milestone 2. Branch freshness (#17),
review automation (#4/#16), integration/release (#5) and operations/portability
(#6) retain their roadmap phases. Rename #35 is a separate coordination lane;
it is not a prerequisite for the functional vertical slice. Existing docs/link
issues #27/#28/#29 remain bounded to their existing documentation PRs.

The final demonstration [#50](https://github.com/skizzay/forgeflow/issues/50)
consumes completed components and verifies one approved issue-to-PR run. It does
not introduce a new execution framework, complete later milestones or merge a PR.
