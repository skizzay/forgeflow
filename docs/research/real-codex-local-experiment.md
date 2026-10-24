# Real Codex execution with existing ChatGPT sign-in

Tracks [#18](https://github.com/skizzay/forgeflow/issues/18) and the execution/auth
questions in [#13](https://github.com/skizzay/forgeflow/issues/13). This exercise
uses the existing ChatGPT sign-in and the local sandbox requested by the user.
It does not create a GitHub repository, issue or PR and does not update PR #21.

## Result

**PASS for the authorized local path:** approved fixture -> durable Flowable wait
-> authenticated HTTPS -> separate Java worker -> isolated real Codex CLI -> Java
compilation/tests -> independent verification -> local story commit/review patch.

The local sandbox is `$HOME/tmp/forgeflow-sandbox`. Its main and epic branches
remain at the initial seed; the story branch contains only the generated Java
source commit. It has no Git remote. The excluded `.forgeflow` directory contains
the approved fixture identity, sanitized result, review description and patch.

[Recorded numeric evidence](evidence/real-codex-local-result.json):

- Codex CLI 0.163.0 exited 0 and emitted `turn.completed`.
- Five image-owned oracle checks passed, independently of Codex's own final answer
  and generated test class: positive, negative, zero, mixed signs and Java overflow.
- Usage event: input 89,556; cached input 80,896; output 1,007 tokens. These are
  CLI-reported usage counters; cached input is included in input, not additional.
- The job was accepted as SUCCEEDED and the Flowable wait completed.
- Local story commit: `40a1ab4388a44bb2c3e394a8afa3e24633e85889`.
- Containers, temporary network, raw output, temporary JDK and TLS keys were removed.

## Credential, execution and network boundaries

The trusted host Java controller reads the existing file-based sign-in cache.
Only access token, ID token and account ID are projected into a temporary tmpfs
inside the job. The refresh token is blanked, API-key/unknown fields are omitted,
and the host cache is not modified. Authentication therefore depends on a currently
valid host sign-in; this is not an unattended credential refresh implementation.
No token values, account identifiers or raw model output are committed or printed.
The authentication projection has separate regression tests.

The job image contains Codex and JDK 21, never credentials. The job is UID 10001,
has a read-only root and bounded tmpfs, memory, CPU and process counts. It has no
host bind mounts, GitHub credentials, API worker token, database credentials or
Docker socket. Seed files are transferred using `git archive`; the host `.git`
directory is not copied. Only the two independently verified source files are
exported, with known credential-value checks before they can be committed.

The job's Docker network is internal with isolated gateway mode. A separate,
credential-free CONNECT proxy has external connectivity and permits only port 443
to `chatgpt.com`, `api.openai.com` and `auth.openai.com`. Other proxy destinations
are denied. No global host firewall rules are modified. The runtime check observed
a 403 for a disallowed HTTP destination; successful Codex execution exercised the
permitted HTTPS path. The Java proxy source also rejects unlisted CONNECT hosts
and non-443 ports. This requires Docker support for isolated bridge gateway mode.

The worker renews its 60-second application lease every 10 seconds during Codex
execution. Rejected renewal triggers cancellation. Provider timeout is 600 seconds;
raw output has a 16 MiB limit. Shutdown/normal-finally cleanup removes the job,
proxy and network. Host SIGKILL/power loss still requires external reconciliation.

## Reproduce

From the spike directory, with Docker/Compose and an active file-based ChatGPT
login in `CODEX_HOME` (default `$HOME/.codex`):

```sh
docker build -t forgeflow-codex-java:0.163.0 codex-container
bash real-codex-experiment.sh
```

The script defaults to the user-requested local sandbox and does not overwrite an
existing unrelated repository. A failed pre-export run may resume from its clean
recorded baseline. A completed sandbox is deliberately not reset or rerun in place;
use `SPIKE_SANDBOX` with another empty location for an intentional repeat.

The host-only probe extracts a temporary JDK, builds trusted classes with Maven,
starts PostgreSQL bound to an ephemeral loopback port, starts the authenticated
prototype API, and runs a separate worker JVM. No Docker socket is mounted into
development or job containers. The `.forgeflow` evidence and local review are
outside the tracked sandbox files. Never copy authentication caches into either
repository or into a Docker build context.

## Interpretation and remaining evidence

The user explicitly authorized the current ChatGPT sign-in for this disposable
exercise. The access token is still a personal/account-scoped credential readable
inside the job until it expires; stripping refresh credentials does not turn it
into a job-scoped credential. Production credential brokering/rotation is unresolved.

This run used ChatGPT authentication, not an API key. The observed counters do
not prove a dollar cost, remaining subscription allowance, or a separate API bill.
Model selection used the CLI default; no specific model was pinned or quality
equivalence claimed. CLI, image, task and approved configuration were fixed for
the experiment. The fixed oracle catches ordinary functional mistakes, not a
malicious implementation deliberately attacking its verifier.

The local review patch is **not a GitHub PR**. No real webhook/issue delivery,
scoped GitHub App token, remote branch push, PR publication/reconciliation, checks
or required-review behavior was exercised. Those acceptance criteria remain open;
the original no-push/no-merge instruction and the user's local sandbox scope are
preserved. This closes the local authentication/Codex execution evidence gap,
not all of #13 or #18. See the [overall results](issue-18-results.md).

Sources: [OpenAI authentication guidance](https://learn.chatgpt.com/docs/auth),
[non-interactive invocation](https://learn.chatgpt.com/docs/non-interactive-mode),
[Docker isolated gateway mode](https://docs.docker.com/engine/network/port-publishing/).
