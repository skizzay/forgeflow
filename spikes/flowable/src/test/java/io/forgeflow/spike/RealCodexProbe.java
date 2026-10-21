package io.forgeflow.spike;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;

/** Explicit host-only experiment; never part of the unattended/default test suite. */
public class RealCodexProbe {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String STORY = "story/18-real-codex";

    private static String git(Path directory, String... arguments) throws Exception {
        var command = new java.util.ArrayList<>(List.of("git", "-C", directory.toString()));
        command.addAll(List.of(arguments));
        return CodexContainerProvider.command(command.toArray(String[]::new));
    }

    private static String digest(String value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }

    public static void main(String[] args) throws Exception {
        Path sandbox = Path.of(System.getenv("SPIKE_SANDBOX")).toAbsolutePath();
        Path privateDirectory = Path.of(System.getenv("SPIKE_PRIVATE_DIRECTORY"));
        Files.createDirectories(sandbox);
        if (!Files.exists(sandbox.resolve(".git"))) {
            try (var entries = Files.list(sandbox)) { assertThat(entries.count()).isZero(); }
            git(sandbox, "init", "-b", "main");
            git(sandbox, "config", "user.name", "ForgeFlow sandbox");
            git(sandbox, "config", "user.email", "forgeflow-sandbox@example.invalid");
            Files.writeString(sandbox.resolve("README.md"), "# Local ForgeFlow Java fixture\n\nSynthetic Issue #18 experiment; no GitHub remote or production data.\n");
            Files.writeString(sandbox.resolve(".gitignore"), ".forgeflow/\n*.class\n");
            Files.writeString(sandbox.resolve("AGENTS.md"), "Create only Adder.java and AdderTest.java. Do not access credentials, home directories, environment variables, network, or git.\n");
            git(sandbox, "add", "README.md", ".gitignore", "AGENTS.md");
            git(sandbox, "commit", "-m", "Initialize public Java experiment fixture");
            git(sandbox, "switch", "-c", "epic/18-experiment");
            git(sandbox, "switch", "-c", STORY);
            Files.createDirectories(sandbox.resolve(".forgeflow"));
            Files.writeString(sandbox.resolve(".forgeflow/baseline"), git(sandbox, "rev-parse", "HEAD"));
        }
        assertThat(git(sandbox, "branch", "--show-current")).isEqualTo(STORY);
        assertThat(git(sandbox, "status", "--porcelain")).isEmpty();
        assertThat(git(sandbox, "rev-parse", "HEAD")).isEqualTo(Files.readString(sandbox.resolve(".forgeflow/baseline")));
        assertThat(git(sandbox, "remote")).isEmpty();
        HandoffExperimentTest.certificates = privateDirectory.resolve("tls");
        Files.createDirectories(HandoffExperimentTest.certificates);
        HandoffExperimentTest.generateTestCertificates();
        try (var fixture = new HandoffExperimentTest.Sandbox()) {
            String key = fixture.create("codex");
            String plan = digest(CodexContainerProvider.TASK);
            String image = CodexContainerProvider.command("docker", "image", "inspect", "--format", "{{.Id}}", "forgeflow-codex-java:0.163.0");
            String config = digest("image=" + image + ";activity=coding-test.v1;provider=codex;isolated-proxy;timeout=600");
            fixture.jdbc.update("UPDATE spike_manifest SET plan_digest = ?, config_digest = ? WHERE job_key = ?", plan, config, key);
            fixture.runtime.setVariable(fixture.instance(key), "planDigest", plan);
            fixture.runtime.setVariable(fixture.instance(key), "configDigest", config);
            fixture.approve(key);
            Files.writeString(sandbox.resolve(".forgeflow/issue.json"), JSON.writeValueAsString(Map.of(
                    "kind", "local-approved-fixture", "issue", "forgeflow#18", "jobKey", key,
                    "planDigest", plan, "configDigest", config, "provider", "codex", "githubIssue", false)) + "\n");
            var command = List.of(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                    "-Djavax.net.ssl.trustStore=" + HandoffExperimentTest.certificates.resolve("trust.p12"),
                    "-Djavax.net.ssl.trustStorePassword=" + HandoffExperimentTest.PASSWORD,
                    "-cp", System.getProperty("java.class.path"), NetworkWorker.class.getName());
            var builder = new ProcessBuilder(command).redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(privateDirectory.resolve("worker-error.private").toFile());
            builder.environment().clear();
            builder.environment().putAll(Map.of("PATH", "/usr/bin:/bin", "SPIKE_API_URL", fixture.url(key, "").replaceAll("/$", ""),
                    "SPIKE_WORKER_TOKEN", fixture.worker, "SPIKE_SANDBOX", sandbox.toString(),
                    "SPIKE_AUTH_FILE", System.getenv("SPIKE_AUTH_FILE")));
            var worker = builder.start();
            try {
                assertThat(worker.waitFor(660, TimeUnit.SECONDS)).isTrue();
                if (worker.exitValue() != 0) {
                    throw new IllegalStateException("Real worker failed; inspect sanitized sandbox result");
                }
            } finally {
                if (worker.isAlive()) { worker.destroy(); if (!worker.waitFor(15, TimeUnit.SECONDS)) worker.destroyForcibly(); }
            }
            assertThat(fixture.jdbc.queryForObject("SELECT state FROM spike_job WHERE job_key = ?", String.class, key)).isEqualTo("SUCCEEDED");
            assertThat(fixture.runtime.createProcessInstanceQuery().processInstanceBusinessKey(key).count()).isZero();
            assertThat(Files.readString(sandbox.resolve(".forgeflow/codex-result.json"))).contains("\"testsPassed\":true");
            git(sandbox, "add", "Adder.java", "AdderTest.java");
            git(sandbox, "commit", "-m", "Implement and test Java addition fixture");
            String commit = git(sandbox, "rev-parse", "HEAD");
            String patch = git(sandbox, "format-patch", "epic/18-experiment.." + STORY, "--stdout");
            Files.writeString(sandbox.resolve(".forgeflow/review.patch"), patch);
            Files.writeString(sandbox.resolve(".forgeflow/review.md"), "# Local review bundle\n\nHead: " + STORY
                    + "\nBase: epic/18-experiment\nCommit: " + commit + "\n\nReal Codex execution and five independent checks passed. This is not a GitHub PR. Nothing is merged.\n");
            Files.writeString(sandbox.resolve(".forgeflow/workflow-result.json"), JSON.writeValueAsString(Map.of(
                    "workflowCompleted", true, "provider", "codex", "verificationChecks", 5, "localCommit", commit,
                    "githubPrCreated", false, "reviewBundleCreated", true, "authMode", "chatgpt-access-only")) + "\n");
            System.out.println("PASS: approved workflow -> HTTPS Java worker -> isolated real Codex -> independent Java tests -> local story commit/review bundle");
        }
    }
}
