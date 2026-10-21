package io.forgeflow.spike;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/** Trusted host-side provider. Docker socket and OAuth refresh credentials never enter the job. */
public final class CodexContainerProvider {
    private static final ObjectMapper JSON = new ObjectMapper();
    public static final String TASK = "Implement a public final Java class Adder in Adder.java with public static int add(int a,int b), "
            + "using Java int addition semantics. Add AdderTest.java with a main method that throws AssertionError on test failure. "
            + "Test positive, negative, zero and overflow cases. Compile with javac Adder.java AdderTest.java and run java AdderTest. "
            + "Only create those two source files. Do not read credentials, home directories or environment variables. "
            + "Do not use the network, git, external dependencies or subagents. Report whether tests passed.";
    public record Result(int exitCode, boolean turnCompleted, boolean testsPassed, Map<String, Long> usage) {}

    static ObjectNode accessOnlyAuth(JsonNode original) {
        if (!original.path("auth_mode").asText().equals("chatgpt")
                || original.path("tokens").path("access_token").asText().isBlank()) {
            throw new IllegalStateException("Active ChatGPT sign-in required");
        }
        var auth = JSON.createObjectNode().put("auth_mode", "chatgpt").put("last_refresh", Instant.now().toString());
        var tokens = auth.putObject("tokens");
        for (String key : List.of("access_token", "account_id", "id_token")) {
            tokens.put(key, original.path("tokens").path(key).asText());
        }
        tokens.put("refresh_token", "");
        return auth;
    }

    static String command(String... arguments) throws Exception {
        var child = new ProcessBuilder(arguments).redirectError(ProcessBuilder.Redirect.DISCARD).start();
        if (!child.waitFor(30, TimeUnit.SECONDS)) {
            child.destroyForcibly();
            throw new IllegalStateException("Controller command timed out");
        }
        byte[] output = child.getInputStream().readAllBytes();
        if (child.exitValue() != 0) throw new IllegalStateException("Controller command failed");
        return new String(output, StandardCharsets.UTF_8).strip();
    }

    private static void feed(byte[] input, String... command) throws Exception {
        var child = new ProcessBuilder(command).redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD).start();
        try (var pipe = child.getOutputStream()) { pipe.write(input); }
        if (!child.waitFor(30, TimeUnit.SECONDS) || child.exitValue() != 0) {
            child.destroyForcibly();
            throw new IllegalStateException("Private input transfer failed");
        }
    }

    public Result execute(Path sandbox, Path authFile) throws Exception {
        return execute(sandbox, authFile, () -> false);
    }

    public Result execute(Path sandbox, Path authFile, java.util.function.BooleanSupplier cancelled) throws Exception {
        String suffix = UUID.randomUUID().toString();
        String network = "forgeflow-codex-" + suffix;
        String proxy = "forgeflow-proxy-" + suffix;
        String job = "forgeflow-codex-job-" + suffix;
        Path raw = Files.createTempFile("forgeflow-codex-", ".private");
        List<String> secrets = new ArrayList<>();
        Thread shutdown = new Thread(() -> cleanup(job, proxy, network), "codex-container-cleanup");
        Runtime.getRuntime().addShutdownHook(shutdown);
        try {
            JsonNode original = JSON.readTree(Files.readAllBytes(authFile));
            original.path("tokens").properties().forEach(entry -> {
                if (entry.getValue().isTextual() && entry.getValue().asText().length() > 24) secrets.add(entry.getValue().asText());
            });
            ObjectNode auth = accessOnlyAuth(original);

            command("docker", "network", "create", "--internal", "--opt",
                    "com.docker.network.bridge.gateway_mode_ipv4=isolated", network);
            command("docker", "create", "--name", proxy, "--read-only", "--user", "10001:10001",
                    "--cap-drop=ALL", "--security-opt=no-new-privileges", "--pids-limit=128", "--memory=256m", "--cpus=0.5",
                    "--log-driver=none", "forgeflow-codex-java:0.163.0", "java", "-Xmx96m", "-cp", "/opt/forgeflow", "ConnectProxy");
            command("docker", "network", "connect", "--alias", "egress", network, proxy);
            command("docker", "start", proxy);
            command("docker", "create", "--name", job, "--network", network,
                    "--read-only", "--user", "10001:10001", "--cap-drop=ALL", "--security-opt=no-new-privileges",
                    "--pids-limit=256", "--memory=1536m", "--cpus=2", "--log-driver=none",
                    "--tmpfs", "/workspace:rw,nosuid,nodev,uid=10001,gid=10001,size=128m",
                    "--tmpfs", "/home/developer:rw,nosuid,nodev,uid=10001,gid=10001,size=64m",
                    "--tmpfs", "/tmp:rw,nosuid,nodev,size=128m",
                    "-e", "HTTPS_PROXY=http://egress:3128", "-e", "HTTP_PROXY=http://egress:3128",
                    "forgeflow-codex-java:0.163.0");
            command("docker", "start", job);
            // Export only tracked seed content, not the host .git directory or local evidence.
            var archive = new ProcessBuilder("git", "-C", sandbox.toString(), "archive", "HEAD").start();
            var unpack = new ProcessBuilder("docker", "exec", "-i", job, "tar", "-xf", "-", "-C", "/workspace")
                    .redirectError(ProcessBuilder.Redirect.DISCARD).start();
            try (var stream = unpack.getOutputStream()) { archive.getInputStream().transferTo(stream); }
            if (!archive.waitFor(30, TimeUnit.SECONDS) || archive.exitValue() != 0
                    || !unpack.waitFor(30, TimeUnit.SECONDS) || unpack.exitValue() != 0) throw new IllegalStateException("Seed export failed");
            feed(JSON.writeValueAsBytes(auth), "docker", "exec", "-i", job, "sh", "-c",
                    "umask 077; mkdir -p /home/developer/.codex; cat > /home/developer/.codex/auth.json");
            // Prove denied arbitrary proxy destinations and absent host bind mounts/socket.
            String denied = "";
            for (int attempt = 0; attempt < 10; attempt++) {
                try {
                    denied = command("docker", "exec", job, "sh", "-c",
                            "curl --connect-timeout 1 --max-time 2 -s -o /dev/null -w '%{http_code}' -x http://egress:3128 http://example.com");
                    break;
                } catch (Exception notReady) { Thread.sleep(100); }
            }
            if (!denied.equals("403")) throw new IllegalStateException("Egress deny check failed");
            String controls = command("docker", "inspect", "--format",
                    "{{.Config.User}}|{{.HostConfig.ReadonlyRootfs}}|{{json .HostConfig.Binds}}", job);
            if (!controls.equals("10001:10001|true|null")) throw new IllegalStateException("Unexpected job permissions");
            command("docker", "exec", job, "sh", "-c", "test ! -S /var/run/docker.sock");
            var execution = new ProcessBuilder("docker", "exec", "-w", "/workspace", job,
                    "codex", "-c", "cli_auth_credentials_store=\"file\"", "--ask-for-approval", "never",
                    "exec", "--skip-git-repo-check", "--sandbox", "danger-full-access", "--json", TASK)
                    .redirectErrorStream(true).redirectOutput(raw.toFile()).start();
            int code = 0;
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(600);
            while (!execution.waitFor(1, TimeUnit.SECONDS)) {
                if (cancelled.getAsBoolean() || Thread.currentThread().isInterrupted()) { code = 130; break; }
                if (Files.size(raw) > 16 * 1024 * 1024) { code = 125; break; }
                if (System.nanoTime() > deadline) { code = 124; break; }
            }
            if (code != 0) {
                command("docker", "rm", "-f", job);
                execution.destroyForcibly();
            } else code = execution.exitValue();
            boolean completed = false;
            Map<String, Long> usage = new java.util.LinkedHashMap<>();
            if (Files.size(raw) > 16 * 1024 * 1024) throw new IllegalStateException("Output limit exceeded");
            for (String line : Files.readAllLines(raw)) {
                try {
                    JsonNode event = JSON.readTree(line);
                    if (event != null && event.path("type").asText().equals("turn.completed")) {
                        completed = true;
                        for (String name : List.of("input_tokens", "cached_input_tokens", "output_tokens")) {
                            JsonNode value = event.path("usage").path(name);
                            if (value.isIntegralNumber() && value.asLong() >= 0) usage.put(name, value.asLong());
                        }
                    }
                } catch (com.fasterxml.jackson.core.JsonProcessingException ignored) { }
            }
            boolean verified = false;
            if (code == 0 && completed) {
                String check = command("docker", "exec", "-w", "/workspace", job, "sh", "-c",
                        "javac Adder.java AdderTest.java && java -ea AdderTest >/dev/null && java -cp /opt/forgeflow:/workspace SandboxVerifier");
                verified = check.equals("verified-five-checks");
                if (verified) {
                    for (String name : List.of("Adder.java", "AdderTest.java")) {
                        String source = command("docker", "exec", job, "cat", "/workspace/" + name);
                        if (source.length() > 65536 || secrets.stream().anyMatch(source::contains)) {
                            throw new IllegalStateException("Unsafe source export rejected");
                        }
                        Files.writeString(sandbox.resolve(name), source + "\n");
                    }
                }
            }
            return new Result(code, completed, verified, usage);
        } finally {
            cleanup(job, proxy, network);
            try { Runtime.getRuntime().removeShutdownHook(shutdown); } catch (IllegalStateException shuttingDown) { }
            Files.deleteIfExists(raw);
            secrets.clear();
        }
    }

    private static void cleanup(String job, String proxy, String network) {
        for (String name : List.of(job, proxy)) {
            try { command("docker", "rm", "-f", name); } catch (Exception ignored) { }
        }
        try { command("docker", "network", "rm", network); } catch (Exception ignored) { }
    }
}
