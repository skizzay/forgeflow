import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/** Trusted host-side Java controller; job containers never receive a socket or host mount. */
class DockerIsolationProbe {
    static Process start(String... arguments) throws Exception {
        return new ProcessBuilder(arguments).redirectError(ProcessBuilder.Redirect.DISCARD).start();
    }

    static String command(String... arguments) throws Exception {
        var process = start(arguments);
        if (!process.waitFor(20, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            throw new IllegalStateException("Docker controller command timed out");
        }
        String output = new String(process.getInputStream().readAllBytes()).strip();
        if (process.exitValue() != 0) throw new IllegalStateException("Docker controller command failed");
        return output;
    }

    static void run(String mode) throws Exception {
        String name = "forgeflow-isolation-" + UUID.randomUUID();
        Process execution = null;
        try {
            String script = mode.equals("success") ? """
                    test "$(id -u)" = 10001 &&
                    test ! -S /var/run/docker.sock &&
                    test "$(wc -l < /proc/net/route)" -eq 1 &&
                    ! touch /usr/forgeflow-write-test 2>/dev/null &&
                    touch /workspace/writable-fixture &&
                    test "$(expr 2 + 3)" = 5 && test "$(expr 1 - 1)" = 0 &&
                    printf 'fixture-tests-passed'
                    """ : "sleep 30";
            command("docker", "create", "--name", name, "--read-only", "--user", "10001:10001",
                    "--cap-drop=ALL", "--security-opt=no-new-privileges", "--pids-limit=64",
                    "--memory=128m", "--cpus=1", "--network=none",
                    "--tmpfs", "/workspace:rw,nosuid,nodev,uid=10001,gid=10001,size=16m",
                    "-w", "/workspace", "ubuntu:24.04", "/bin/sh", "-c", script);
            String controls = command("docker", "inspect", "--format",
                    "{{.Config.User}}|{{.HostConfig.NetworkMode}}|{{.HostConfig.ReadonlyRootfs}}|{{json .HostConfig.Binds}}", name);
            if (!controls.equals("10001:10001|none|true|null")) {
                throw new IllegalStateException("Unexpected container isolation configuration");
            }
            execution = start("docker", "start", "-a", name);
            if (mode.equals("success")) {
                if (!execution.waitFor(15, TimeUnit.SECONDS) || execution.exitValue() != 0
                        || !new String(execution.getInputStream().readAllBytes()).equals("fixture-tests-passed")) {
                    throw new IllegalStateException("Isolated fixture checks failed");
                }
            } else {
                long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
                while (!command("docker", "inspect", "--format", "{{.State.Running}}", name).equals("true")) {
                    if (System.nanoTime() > deadline) throw new IllegalStateException("Container did not start");
                    Thread.sleep(50);
                }
                if (mode.equals("timeout") && execution.waitFor(1, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Timeout fixture exited too early");
                }
                command("docker", "rm", "-f", name);
                if (!execution.waitFor(10, TimeUnit.SECONDS) || execution.exitValue() == 0) {
                    throw new IllegalStateException("Container termination was not observed");
                }
            }
            System.out.println("PASS: isolated-container-" + mode);
        } finally {
            var cleanup = start("docker", "rm", "-f", name);
            if (!cleanup.waitFor(15, TimeUnit.SECONDS)) cleanup.destroyForcibly();
            if (execution != null && execution.isAlive()) execution.destroyForcibly();
        }
    }

    public static void main(String[] args) throws Exception {
        for (String mode : List.of("success", "timeout", "cancellation")) run(mode);
        String remaining = command("docker", "ps", "-aq", "--filter", "name=forgeflow-isolation-");
        if (!remaining.isEmpty()) throw new IllegalStateException("Fixture containers remain");
        System.out.println("PASS: container-cleanup");
    }
}
