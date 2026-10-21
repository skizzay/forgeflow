package io.forgeflow.spike;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import org.flowable.engine.RuntimeService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Trusted control-plane prototype. No worker DB access, HTTP API, or retry scheduler. */
@Service
public class RecoveryJobs {
    public record Lease(String jobKey, long fence, String worker) {
        public Lease {
            requireText(jobKey);
            requireText(worker);
            if (fence <= 0) throw new IllegalArgumentException("Expected positive fence");
        }
    }
    public record Reconciliation(String jobKey, long fence) {
        public Reconciliation {
            requireText(jobKey);
            if (fence <= 0) throw new IllegalArgumentException("Expected positive fence");
        }
    }
    public enum Outcome { ACCEPTED, DUPLICATE }
    public enum Finding { FOUND, ABSENT, UNKNOWN }
    public record Observation(Finding finding, String effectId) {
        public Observation {
            Objects.requireNonNull(finding);
            if (finding == Finding.FOUND) {
                requireText(effectId);
            } else if (effectId != null) {
                throw new IllegalArgumentException("Only FOUND may carry effect evidence");
            }
        }
    }
    private record Job(String instance, String state, long fence, String worker,
                       boolean live, String effect) {}

    private final JdbcTemplate jdbc;
    private final RuntimeService runtime;
    private final TransactionTemplate transaction;

    public RecoveryJobs(JdbcTemplate jdbc, RuntimeService runtime, PlatformTransactionManager manager) {
        this.jdbc = jdbc;
        this.runtime = runtime;
        this.transaction = new TransactionTemplate(manager);
    }

    public Optional<Lease> claim(String jobKey, String worker, Duration duration) {
        requireText(worker);
        long seconds = duration.toSeconds();
        if (seconds <= 0 || seconds > 3600) {
            throw new IllegalArgumentException("Lease must be between 1 and 3600 seconds");
        }
        return jdbc.query("""
                UPDATE spike_job SET state = 'RUNNING', fence = fence + 1, worker_id = ?,
                    lease_until = clock_timestamp() + (? * interval '1 second')
                WHERE job_key = ? AND state = 'READY'
                RETURNING fence
                """, (rs, n) -> new Lease(jobKey, rs.getLong(1), worker), worker, seconds, jobKey)
                .stream().findFirst();
    }

    public Outcome complete(Lease lease, String effectId) {
        requireText(effectId);
        return transaction.execute(status -> {
            Job job = locked(lease.jobKey());
            if (job.fence() != lease.fence() || !Objects.equals(job.worker(), lease.worker())) {
                throw new IllegalStateException("Stale attempt");
            }
            if (job.state().equals("SUCCEEDED") && effectId.equals(job.effect())) {
                return Outcome.DUPLICATE;
            }
            if (!job.state().equals("RUNNING") || !job.live()) {
                throw new IllegalStateException("Attempt is not live");
            }
            succeed(lease.jobKey(), job, effectId);
            return Outcome.ACCEPTED;
        });
    }

    public void heartbeat(Lease lease, Duration duration) {
        long seconds = duration.toSeconds();
        if (seconds <= 0 || seconds > 3600) throw new IllegalArgumentException("Invalid lease duration");
        transaction.executeWithoutResult(status -> {
            requireLiveOwner(locked(lease.jobKey()), lease);
            jdbc.update("UPDATE spike_job SET lease_until = clock_timestamp() + (? * interval '1 second') WHERE job_key = ?",
                    seconds, lease.jobKey());
        });
    }

    public boolean cancel(Lease lease) {
        return Boolean.TRUE.equals(transaction.execute(status -> {
            requireLiveOwner(locked(lease.jobKey()), lease);
            return cancel(lease.jobKey());
        }));
    }

    private void requireLiveOwner(Job job, Lease lease) {
        if (!job.state().equals("RUNNING") || !job.live() || job.fence() != lease.fence()
                || !Objects.equals(job.worker(), lease.worker())) {
            throw new IllegalStateException("Attempt is not live or owned");
        }
    }

    /** Fence before querying external state. A restarted reconciler invalidates earlier observations. */
    public Reconciliation beginReconciliation(String jobKey) {
        return transaction.execute(status -> {
            Job job = locked(jobKey);
            if (!(job.state().equals("RUNNING") && !job.live())
                    && !job.state().equals("RECONCILING")) {
                throw new IllegalStateException("Job is not eligible for reconciliation");
            }
            jdbc.update("""
                    UPDATE spike_job SET state = 'RECONCILING', fence = fence + 1,
                        worker_id = NULL, lease_until = NULL WHERE job_key = ?
                    """, jobKey);
            return new Reconciliation(jobKey, job.fence() + 1);
        });
    }

    /** Call after trusted SCM lookup, outside the DB transaction. Absence alone never authorizes retry. */
    public Outcome finishReconciliation(Reconciliation token, Observation observed, boolean previousAttemptStopped) {
        Objects.requireNonNull(observed);
        return transaction.execute(status -> {
            Job job = locked(token.jobKey());
            if (job.fence() != token.fence()) {
                throw new IllegalStateException("Stale reconciliation");
            }
            if (job.state().equals("SUCCEEDED") && observed.finding() == Finding.FOUND
                    && observed.effectId().equals(job.effect())) {
                return Outcome.DUPLICATE;
            }
            if (!job.state().equals("RECONCILING")) {
                throw new IllegalStateException("Job is not reconciling");
            }
            if (observed.finding() == Finding.FOUND) {
                succeed(token.jobKey(), job, observed.effectId());
            } else {
                String next = observed.finding() == Finding.ABSENT && previousAttemptStopped ? "READY" : "BLOCKED";
                jdbc.update("UPDATE spike_job SET state = ? WHERE job_key = ?", next, token.jobKey());
            }
            return Outcome.ACCEPTED;
        });
    }

    public boolean cancel(String jobKey) {
        return Boolean.TRUE.equals(transaction.execute(status -> {
            Job job = locked(jobKey);
            if (job.state().equals("SUCCEEDED") || job.state().equals("CANCELLED")) {
                return false;
            }
            jdbc.update("""
                    UPDATE spike_job SET state = 'CANCELLED', fence = fence + 1,
                        worker_id = NULL, lease_until = NULL WHERE job_key = ?
                    """, jobKey);
            runtime.deleteProcessInstance(job.instance(), "spike cancellation");
            return true;
        }));
    }

    private Job locked(String key) {
        Job job = jdbc.queryForObject("""
                SELECT instance_id, state, fence, worker_id,
                    effect_id
                FROM spike_job WHERE job_key = ? FOR UPDATE
                """, (rs, n) -> new Job(rs.getString("instance_id"), rs.getString("state"),
                rs.getLong("fence"), rs.getString("worker_id"), false,
                rs.getString("effect_id")), key);
        // Check time after acquiring the lock: waiting for another transaction may outlast the lease.
        boolean live = Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT COALESCE(lease_until > clock_timestamp(), false) FROM spike_job WHERE job_key = ?
                """, Boolean.class, key));
        return new Job(job.instance(), job.state(), job.fence(), job.worker(), live, job.effect());
    }

    private void succeed(String key, Job job, String effectId) {
        // This and Flowable's receive-task trigger commit or roll back together.
        jdbc.update("UPDATE spike_job SET state = 'SUCCEEDED', effect_id = ?, lease_until = NULL WHERE job_key = ?",
                effectId, key);
        var waiting = runtime.createExecutionQuery().processInstanceId(job.instance())
                .activityId("external").singleResult();
        if (waiting == null) {
            throw new IllegalStateException("Expected process wait is missing");
        }
        runtime.trigger(waiting.getId());
    }

    private static void requireText(String value) {
        if (value == null || value.isBlank() || value.length() > 256) {
            throw new IllegalArgumentException("Expected bounded non-empty identifier");
        }
    }
}
