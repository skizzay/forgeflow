# Flowable decision experiments

Tracks [Issue #18](https://github.com/skizzay/forgeflow/issues/18), parent
[Foundation #1](https://github.com/skizzay/forgeflow/issues/1), and open PR #21.
Dependencies: independent fixtures; real Codex/GitHub evidence depends on #13.
The original baseline is `397461f`; recovery baseline is `1ec02bf`.

**[Results matrix and provisional synthesis](../../docs/research/issue-18-results.md).**
The maintainer accepted the bounded feasibility result on 2026-10-21; actual
GitHub integration remains follow-up work. The real Codex local experiment passes. This is a
Java/Spring Boot prototype, not the production bootstrap (#7).

## Reproduce

Requires Docker, Docker Compose and a Unix shell. No host Java install or secrets
are required for these deterministic experiments. Run from this directory:

```sh
# 19 PostgreSQL/credential integration tests, including separate Java worker JVMs over HTTPS.
docker compose -p forgeflow-spike-18 up --abort-on-container-exit --exit-code-from tests
docker compose -p forgeflow-spike-18 down

# Three phases: create with 7.1.0, upgrade to 7.2.0, logical restore and validation.
bash schema-experiment.sh

# Four outcomes: fixed isolated workload, timeout, cancellation and cleanup.
# Extracts a temporary JDK for the trusted HOST controller; no socket is mounted.
bash isolation-experiment.sh

# Optional resource sample; this is not a Pi benchmark.
docker compose -p forgeflow-spike-18 up -d --wait database
docker compose -p forgeflow-spike-18 run --rm -T -e MAVEN_OPTS=-Xmx128m tests \
  mvn -B --no-transfer-progress -Dtest=ResourceFootprintProbe -DargLine=-Xmx256m test
docker compose -p forgeflow-spike-18 down

# Generate metadata inventory, not a legal compliance determination.
docker compose -p forgeflow-spike-18 run --rm --no-deps tests \
  mvn -B --no-transfer-progress org.codehaus.mojo:license-maven-plugin:2.7.0:add-third-party \
  -Dlicense.includedScopes=compile,runtime
```

The license report is generated beneath the ignored build output directory.
Its recorded public dependency metadata is in the
[research evidence](../../docs/research/evidence/flowable-runtime-licenses.txt).
No generated test keys/tokens, logs or database dumps belong in Git.

## Stack and test boundaries

Tested: Java 21, Maven 3.9.9, Spring Boot 3.5.4, Flowable OSS 7.2.0 and
PostgreSQL 17.6 on Linux amd64. Schema rehearsal also uses Flowable 7.1.0.
Versions are pinned compatibility experiments, not a recommendation to deploy
these versions without a support/security review. Image tags and apt repositories
are not immutable. Pi/arm64 capacity remains unmeasured.

Compose uses a disposable PostgreSQL tmpfs with no published host port. The
public example password is synthetic fixture data. The trusted Maven container
gets this spike's source and a build cache; it gets no Docker socket or host
credentials. Process-engine async execution, IDM and event registry are disabled.

- `WorkflowPersistenceTest`: original 3 persistence, pinning and guarded-activity cases.
- `RecoveryExperimentTest`: 6 failure/replay/fencing/concurrency cases using simulated SCM.
- `HandoffExperimentTest`: 8 certificate-validated HTTPS cases, including deterministic registered
  providers/variants, authorization, rotation, heartbeats and network-worker restart.
- `SchemaLifecycleProbe`: explicit multi-version/database lifecycle probe, not in the default suite.
- `ResourceFootprintProbe`: explicit warm-engine sample, not in the default suite.
- `probes/DockerIsolationProbe.java`: trusted host-side Java controller; fixed job
  containers are non-root, read-only, resource-limited, have network disabled and
  receive neither host bind mounts nor Docker sockets. No LLM is invoked by that fixed probe.

The HTTPS adapter uses the JDK server alongside Spring-managed services; it is not
production Spring MVC/Security. Worker tokens and capabilities are trusted fixture
configuration; operator approval is not a real human identity-provider integration.
The separate worker receives only its API URL/token and a certificate truststore,
not database credentials. Registry configuration accepts only two fixed workflows
and registered activity/provider choices; arbitrary executable BPMN is not exposed.

Application manifests pin definition/plan/config/activity/provider/capability
references and invalidate approval on change. Fixture digests are identifiers;
canonical content hashing and storage of real approved plans remain production work.
The recovery tests use a fake SCM; passing does not establish exactly-once GitHub
writes, remote cancellation or quiescence of in-flight requests.

## Decision and review notes

Completed experiments support technical feasibility: persistent process state,
atomic engine/domain completion, explicit application-owned leases/reconciliation,
guarded external handoff, schema upgrade/restore and fixed container isolation.
The [real Codex local experiment](../../docs/research/real-codex-local-experiment.md)
now passes using the authorized existing ChatGPT sign-in, an access-only credential
projection, restricted outbound proxy, and the user-designated local Git sandbox.
It does not create an actual GitHub PR; #11/#13/#15 retain that follow-up scope.
Issue #18 is closed at maintainer direction based on the bounded local evidence.

See the [full results](../../docs/research/issue-18-results.md),
[recovery plan](../../docs/research/flowable-recovery-experiment.md), and
[decision record](../../docs/architecture/decisions/0002-flowable-spike-in-progress.md).

Verification and tradeoffs for the eventual PR update are recorded in those files.
No production schema, generic BPMS editor, scheduler, auto-merge or production
approval is introduced. GitHub PR #21 remains open; local continuations are not
pushed. Issue closure does not satisfy prerequisite PR integration into the epic branch.

## Real local Codex exercise (explicit opt-in)

```sh
docker build -t forgeflow-codex-java:0.163.0 codex-container
bash real-codex-experiment.sh
```

This uses the active file-based ChatGPT sign-in in `CODEX_HOME`, defaults to the
user-authorized `$HOME/tmp/forgeflow-sandbox`, and must be run by the trusted host
controller. It creates local branches, a story commit and a review patch only.
It never pushes or creates GitHub objects. A completed sandbox is not reset.
See the research record above for credential handling, measured result and limits.
