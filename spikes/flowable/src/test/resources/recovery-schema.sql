-- Disposable experiment schema, not a production migration or engine-owned table.
CREATE TABLE IF NOT EXISTS spike_job (
    job_key text PRIMARY KEY,
    instance_id text NOT NULL UNIQUE,
    state text NOT NULL DEFAULT 'READY'
        CHECK (state IN ('READY', 'RUNNING', 'RECONCILING', 'BLOCKED', 'SUCCEEDED', 'CANCELLED')),
    fence bigint NOT NULL DEFAULT 0,
    worker_id text,
    lease_until timestamptz,
    effect_id text,
    CHECK ((state = 'RUNNING') = (lease_until IS NOT NULL)),
    CHECK ((state = 'SUCCEEDED') = (effect_id IS NOT NULL))
);
