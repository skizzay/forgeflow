package io.forgeflow.spike;

import java.nio.file.Path;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Child JVM failure injector. Deliberately exits without finally blocks or Spring shutdown. */
public final class CrashProcess {
    public static void main(String[] args) throws Exception {
        if (args[0].equals("worker")) {
            SimulatedScm.create(Path.of(args[1]), args[2]);
            Runtime.getRuntime().halt(73); // external effect exists, result was never sent
        } else if (args[0].equals("control-plane")) {
            var context = new SpringApplicationBuilder(SpikeApplication.class).run();
            var transaction = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
            transaction.executeWithoutResult(status -> {
                context.getBean(RecoveryJobs.class).complete(
                        new RecoveryJobs.Lease(args[1], Long.parseLong(args[2]), args[3]), args[4]);
                Runtime.getRuntime().halt(74); // domain + engine changed, but DB transaction not committed
            });
        } else {
            throw new IllegalArgumentException("Unknown fixture mode");
        }
        throw new IllegalStateException("Failure injection was not reached");
    }
}
