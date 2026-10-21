package io.forgeflow.spike;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;
import org.flowable.engine.RuntimeService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Trusted application gate shared by all prototype worker entry points. */
@Service
public class ApprovedHandoff {
    public record Work(RecoveryJobs.Lease lease, String provider, String activity) {}
    private final JdbcTemplate jdbc;
    private final RecoveryJobs jobs;
    private final RuntimeService runtime;
    private final TransactionTemplate transaction;

    public ApprovedHandoff(JdbcTemplate jdbc, RecoveryJobs jobs, RuntimeService runtime,
                           PlatformTransactionManager manager) {
        this.jdbc = jdbc;
        this.jobs = jobs;
        this.runtime = runtime;
        this.transaction = new TransactionTemplate(manager);
    }

    public void approve(String key, String approver) {
        transaction.executeWithoutResult(status -> {
            var manifest = manifest(key);
            jdbc.update("""
                    INSERT INTO spike_authorization VALUES (?, ?, ?)
                    ON CONFLICT (job_key) DO UPDATE SET fingerprint = excluded.fingerprint,
                        approved_by = excluded.approved_by
                    """, key, fingerprint(manifest), approver);
        });
    }

    public Work claim(String key, String worker, Set<String> capabilities) {
        return transaction.execute(status -> {
            var manifest = approved(key);
            if (!capabilities.contains(manifest.get("capability"))) {
                throw new SecurityException("Capability mismatch");
            }
            var lease = jobs.claim(key, worker, Duration.ofSeconds(60))
                    .orElseThrow(() -> new IllegalStateException("Job is not claimable"));
            return new Work(lease, manifest.get("provider"), manifest.get("activity"));
        });
    }

    public void heartbeat(RecoveryJobs.Lease lease) {
        transaction.executeWithoutResult(status -> {
            approved(lease.jobKey());
            jobs.heartbeat(lease, Duration.ofSeconds(60));
        });
    }

    public RecoveryJobs.Outcome complete(RecoveryJobs.Lease lease, String effect) {
        return transaction.execute(status -> {
            approved(lease.jobKey());
            return jobs.complete(lease, effect);
        });
    }

    private Map<String, String> manifest(String key) {
        return jdbc.queryForObject("SELECT * FROM spike_manifest WHERE job_key = ? FOR UPDATE",
                (rs, row) -> Map.of("definition", rs.getString("definition_id"),
                "plan", rs.getString("plan_digest"), "config", rs.getString("config_digest"),
                "provider", rs.getString("provider"), "activity", rs.getString("activity"),
                "capability", rs.getString("capability")), key);
    }

    private Map<String, String> approved(String key) {
        var value = manifest(key);
        if (!Set.of("deterministic-a", "deterministic-b", "codex").contains(value.get("provider"))
                || !value.get("activity").equals("coding-test.v1")) {
            throw new IllegalStateException("Unregistered activity or provider");
        }
        var authorized = jdbc.queryForList("SELECT fingerprint FROM spike_authorization WHERE job_key = ?", String.class, key);
        if (!authorized.equals(java.util.List.of(fingerprint(value)))) {
            throw new IllegalStateException("Approval does not match execution identity");
        }
        String instance = jdbc.queryForObject("SELECT instance_id FROM spike_job WHERE job_key = ?", String.class, key);
        var process = runtime.createProcessInstanceQuery().processInstanceId(instance).singleResult();
        // Finished processes still permit exact receipt replay; stored manifest remains authoritative.
        if (process != null && (!process.getProcessDefinitionId().equals(value.get("definition"))
                || !value.get("plan").equals(runtime.getVariable(instance, "planDigest"))
                || !value.get("config").equals(runtime.getVariable(instance, "configDigest")))) {
            throw new IllegalStateException("Process identity differs from approved manifest");
        }
        return value;
    }

    private String fingerprint(Map<String, String> value) {
        // Length prefixes avoid delimiter ambiguity. Fixed field order is part of the fixture contract.
        var canonical = new StringBuilder();
        for (String key : java.util.List.of("definition", "plan", "config", "provider", "activity", "capability")) {
            String field = value.get(key);
            canonical.append(field.length()).append(':').append(field);
        }
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
