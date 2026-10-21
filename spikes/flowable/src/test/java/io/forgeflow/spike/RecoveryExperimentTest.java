package io.forgeflow.spike;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import static io.forgeflow.spike.RecoveryJobs.*;
import static org.assertj.core.api.Assertions.*;

class RecoveryExperimentTest {
    @TempDir Path effects;

    private ConfigurableApplicationContext engine() {
        var context = new SpringApplicationBuilder(SpikeApplication.class).run();
        new ResourceDatabasePopulator(new ClassPathResource("recovery-schema.sql"))
                .execute(context.getBean(javax.sql.DataSource.class));
        return context;
    }

    private String start(ConfigurableApplicationContext context, String key) {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                    xmlns:flowable="http://flowable.org/bpmn" targetNamespace="forgeflow.recovery">
                  <process id="recovery" isExecutable="true">
                    <startEvent id="start"/>
                    <sequenceFlow id="f1" sourceRef="start" targetRef="dispatch"/>
                    <serviceTask id="dispatch" flowable:delegateExpression="${dispatchV1}"/>
                    <sequenceFlow id="f2" sourceRef="dispatch" targetRef="external"/>
                    <receiveTask id="external"/>
                    <sequenceFlow id="f3" sourceRef="external" targetRef="done"/>
                    <endEvent id="done"/>
                  </process>
                </definitions>
                """;
        var repository = context.getBean(RepositoryService.class);
        var deployment = repository.createDeployment().addString("recovery.bpmn20.xml", xml).deploy();
        var definition = repository.createProcessDefinitionQuery().deploymentId(deployment.getId()).singleResult();
        return context.getBean(RuntimeService.class).startProcessInstanceById(definition.getId(), key).getId();
    }

    private Lease claim(ConfigurableApplicationContext context, String key, String worker) {
        return context.getBean(RecoveryJobs.class).claim(key, worker, Duration.ofMinutes(5)).orElseThrow();
    }

    private String state(ConfigurableApplicationContext context, String key) {
        return context.getBean(JdbcTemplate.class).queryForObject(
                "SELECT state FROM spike_job WHERE job_key = ?", String.class, key);
    }

    private void expire(ConfigurableApplicationContext context, String key) {
        // Advance the stored deadline deterministically; no timing-based sleeps in tests.
        context.getBean(JdbcTemplate.class).update(
                "UPDATE spike_job SET lease_until = clock_timestamp() - interval '1 second' WHERE job_key = ?", key);
    }

    private void crash(String... args) throws Exception {
        List<String> command = new ArrayList<>(List.of(
                Path.of(System.getProperty("java.home"), "bin", "java").toString(), "-cp",
                System.getProperty("surefire.test.class.path", System.getProperty("java.class.path")),
                CrashProcess.class.getName()));
        command.addAll(List.of(args));
        var child = new ProcessBuilder(command).redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD).start();
        try {
            assertThat(child.waitFor(30, TimeUnit.SECONDS)).isTrue();
            assertThat(child.exitValue()).isEqualTo(args[0].equals("worker") ? 73 : 74);
        } finally {
            if (child.isAlive()) {
                child.destroyForcibly();
                child.waitFor(10, TimeUnit.SECONDS);
            }
        }
    }

    @Test
    void workerCrashAfterExternalEffectRecoversWithoutCreatingAnotherPr() throws Exception {
        String key = UUID.randomUUID().toString();
        String instance;
        Lease old;
        try (var context = engine()) {
            instance = start(context, key);
            old = claim(context, key, "worker-a");
            crash("worker", effects.toString(), key);
            assertThat(state(context, key)).isEqualTo("RUNNING");
        }
        try (var context = engine()) {
            var jobs = context.getBean(RecoveryJobs.class);
            assertThat(state(context, key)).isEqualTo("RUNNING");
            expire(context, key);
            var token = jobs.beginReconciliation(key);
            var found = SimulatedScm.lookup(effects, key);
            assertThat(found.finding()).isEqualTo(Finding.FOUND);
            assertThatThrownBy(() -> jobs.complete(old, found.effectId())).hasMessage("Stale attempt");
            assertThat(jobs.claim(key, "worker-b", Duration.ofMinutes(1))).isEmpty();
            assertThat(jobs.finishReconciliation(token, found, true)).isEqualTo(Outcome.ACCEPTED);
            assertThat(jobs.finishReconciliation(token, found, true)).isEqualTo(Outcome.DUPLICATE);
            assertThat(state(context, key)).isEqualTo("SUCCEEDED");
            assertThat(context.getBean(RuntimeService.class).createProcessInstanceQuery()
                    .processInstanceId(instance).count()).isZero();
            try (var files = java.nio.file.Files.list(effects)) {
                assertThat(files.count()).isEqualTo(1);
            }
        }
    }

    @Test
    void controlPlaneHardCrashBeforeCommitRollsBackDomainAndEngineTogether() throws Exception {
        try (var context = engine()) {
            String key = UUID.randomUUID().toString();
            String instance = start(context, key);
            Lease lease = claim(context, key, "worker-a");
            String effect = SimulatedScm.create(effects, key);
            crash("control-plane", key, Long.toString(lease.fence()), lease.worker(), effect);
            assertThat(state(context, key)).isEqualTo("RUNNING");
            assertThat(context.getBean(RuntimeService.class).createExecutionQuery()
                    .processInstanceId(instance).activityId("external").count()).isEqualTo(1);
            var jobs = context.getBean(RecoveryJobs.class);
            assertThat(jobs.complete(lease, effect)).isEqualTo(Outcome.ACCEPTED);
            assertThat(jobs.complete(lease, effect)).isEqualTo(Outcome.DUPLICATE);
            assertThatThrownBy(() -> jobs.complete(lease, "conflicting-evidence"))
                    .hasMessage("Attempt is not live");
            assertThat(context.getBean(HistoryService.class).createHistoricProcessInstanceQuery()
                    .processInstanceId(instance).finished().count()).isEqualTo(1);
        }
    }

    @Test
    void expiryRequiresReconciliationAndProofOfStoppedAttemptBeforeRetry() {
        try (var context = engine()) {
            String key = UUID.randomUUID().toString();
            start(context, key);
            Lease old = claim(context, key, "worker-a");
            var jobs = context.getBean(RecoveryJobs.class);
            expire(context, key);
            assertThat(jobs.claim(key, "worker-b", Duration.ofSeconds(60))).isEmpty();
            assertThatThrownBy(() -> jobs.complete(old, "late-result")).hasMessage("Attempt is not live");
            var first = jobs.beginReconciliation(key);
            var resumed = jobs.beginReconciliation(key);
            assertThatThrownBy(() -> jobs.finishReconciliation(first, new Observation(Finding.ABSENT, null), true))
                    .hasMessage("Stale reconciliation");
            jobs.finishReconciliation(resumed, new Observation(Finding.ABSENT, null), true);
            Lease replacement = claim(context, key, "worker-b");
            assertThat(replacement.fence()).isGreaterThan(old.fence());
            assertThatThrownBy(() -> jobs.complete(old, "late-result")).hasMessage("Stale attempt");
            jobs.complete(replacement, "replacement-result");
            assertThat(state(context, key)).isEqualTo("SUCCEEDED");
        }
    }

    @Test
    void uncertainEffectsOrUnstoppedWorkersBlockRatherThanBlindlyRetry() {
        try (var context = engine()) {
            var jobs = context.getBean(RecoveryJobs.class);
            for (Finding finding : List.of(Finding.UNKNOWN, Finding.ABSENT)) {
                String key = UUID.randomUUID().toString();
                start(context, key);
                claim(context, key, "worker-a");
                expire(context, key);
                var token = jobs.beginReconciliation(key);
                jobs.finishReconciliation(token, new Observation(finding, null), false);
                assertThat(state(context, key)).isEqualTo("BLOCKED");
                assertThat(jobs.claim(key, "worker-b", Duration.ofSeconds(60))).isEmpty();
            }
        }
    }

    private <T> List<T> race(Callable<T> first, Callable<T> second) throws Exception {
        try (var pool = Executors.newFixedThreadPool(2)) {
            var ready = new CountDownLatch(2);
            var go = new CountDownLatch(1);
            List<java.util.concurrent.Future<T>> futures = new ArrayList<>();
            for (var action : List.of(first, second)) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    if (!go.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Race start timed out");
                    return action.call();
                }));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            go.countDown();
            List<T> results = new ArrayList<>();
            for (var future : futures) results.add(future.get(15, TimeUnit.SECONDS));
            return results;
        }
    }

    @Test
    void concurrentClaimsHaveOneWinnerAndDuplicateDispatchRollsBack() throws Exception {
        try (var context = engine()) {
            String key = UUID.randomUUID().toString();
            start(context, key);
            var jobs = context.getBean(RecoveryJobs.class);
            var results = race(() -> jobs.claim(key, "worker-a", Duration.ofMinutes(1)),
                    () -> jobs.claim(key, "worker-b", Duration.ofMinutes(1)));
            assertThat(results.stream().filter(java.util.Optional::isPresent).count()).isEqualTo(1);
            assertThatThrownBy(() -> start(context, key)).isInstanceOf(RuntimeException.class);
            assertThat(context.getBean(RuntimeService.class).createProcessInstanceQuery()
                    .processInstanceBusinessKey(key).count()).isEqualTo(1);
        }
    }

    @Test
    void cancellationRacingCompletionHasOneTerminalOutcomeAndCannotResurrect() throws Exception {
        try (var context = engine()) {
            String key = UUID.randomUUID().toString();
            String instance = start(context, key);
            Lease lease = claim(context, key, "worker-a");
            var jobs = context.getBean(RecoveryJobs.class);
            var results = race(() -> jobs.cancel(key) ? "cancelled" : "already-completed", () -> {
                try {
                    jobs.complete(lease, "fixture-result");
                    return "completed";
                } catch (IllegalStateException stale) {
                    return "rejected";
                }
            });
            String finalState = state(context, key);
            if (finalState.equals("CANCELLED")) {
                assertThat(results).containsExactly("cancelled", "rejected");
                assertThatThrownBy(() -> jobs.complete(lease, "fixture-result")).hasMessage("Stale attempt");
            } else {
                assertThat(finalState).isEqualTo("SUCCEEDED");
                assertThat(results).containsExactly("already-completed", "completed");
            }
            assertThat(context.getBean(RuntimeService.class).createProcessInstanceQuery()
                    .processInstanceId(instance).count()).isZero();
        }
    }
}
