CREATE TABLE IF NOT EXISTS spike_manifest (
    job_key text PRIMARY KEY REFERENCES spike_job(job_key),
    definition_id text NOT NULL,
    plan_digest text NOT NULL,
    config_digest text NOT NULL,
    provider text NOT NULL,
    activity text NOT NULL,
    capability text NOT NULL
);
CREATE TABLE IF NOT EXISTS spike_authorization (
    job_key text PRIMARY KEY REFERENCES spike_manifest(job_key),
    fingerprint text NOT NULL,
    approved_by text NOT NULL
);
