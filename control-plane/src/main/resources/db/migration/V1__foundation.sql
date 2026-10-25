-- ForgeFlow owns this schema; Flowable owns only the separate flowable schema.
create table repository (
    id uuid primary key,
    github_id bigint not null unique check (github_id > 0),
    full_name text not null unique,
    created_at timestamptz not null default now()
);
create table epic (
    id uuid primary key,
    repository_id uuid not null references repository(id),
    issue_number integer not null check (issue_number > 0),
    state text not null default 'DRAFT' check (state in ('DRAFT','APPROVED','ACTIVE','COMPLETED','CANCELLED')),
    state_version bigint not null default 0 check (state_version >= 0),
    approved_identity jsonb,
    branch_name text,
    unique (repository_id, issue_number), unique (id, repository_id)
);
create table story (
    id uuid primary key,
    epic_id uuid not null,
    repository_id uuid not null references repository(id),
    issue_number integer not null check (issue_number > 0),
    state text not null default 'DRAFT' check (state in ('DRAFT','READY','ACTIVE','PR_OPEN','MERGED','CANCELLED')),
    state_version bigint not null default 0 check (state_version >= 0),
    approved_identity jsonb,
    expected_pr_head_sha text check (expected_pr_head_sha ~ '^[0-9a-f]{40}([0-9a-f]{24})?$'),
    branch_name text,
    pull_request_number integer check (pull_request_number > 0),
    merged_commit_sha text,
    foreign key (epic_id, repository_id) references epic(id, repository_id),
    unique (repository_id, issue_number),
    check (state <> 'MERGED' or (pull_request_number is not null and merged_commit_sha is not null))
);
create table worker (
    id uuid primary key,
    logical_name text not null unique,
    capabilities jsonb not null default '{}',
    last_seen_at timestamptz
);
create table webhook_inbox (
    delivery_id uuid primary key,
    event_type text not null,
    repository_id uuid references repository(id),
    received_at timestamptz not null default now(),
    processed_at timestamptz,
    state text not null default 'RECEIVED' check (state in ('RECEIVED','PROCESSED','FAILED')),
    payload_sha256 text not null check (payload_sha256 ~ '^[0-9a-f]{64}$'),
    payload_bytes integer not null check (payload_bytes >= 0),
    metadata jsonb not null default '{}'
);
create table workflow_definition (
    workflow_key text not null,
    registry_version integer not null check (registry_version > 0),
    content_sha256 text not null check (content_sha256 ~ '^[0-9a-f]{64}$'),
    definition_id text not null unique,
    primary key (workflow_key, registry_version),
    unique (workflow_key, registry_version, definition_id)
);
create table activity_definition (
    activity_key text not null,
    registry_version integer not null check (registry_version > 0),
    input_contract_version integer not null check (input_contract_version > 0),
    output_contract_version integer not null check (output_contract_version > 0),
    primary key (activity_key, registry_version)
);
create table workflow_run (
    id uuid primary key,
    story_id uuid not null references story(id),
    workflow_key text not null,
    workflow_version integer not null,
    process_definition_id text not null references workflow_definition(definition_id),
    process_instance_id text not null unique,
    plan_revision text not null,
    configuration_sha256 text not null check (configuration_sha256 ~ '^[0-9a-f]{64}$'),
    approval_id uuid not null,
    approved_by text not null,
    approved_at timestamptz not null,
    created_at timestamptz not null default now(),
    foreign key (workflow_key, workflow_version, process_definition_id) references workflow_definition(workflow_key, registry_version, definition_id)
);
create table job (
    id uuid primary key,
    run_id uuid not null references workflow_run(id),
    activity_key text not null,
    activity_version integer not null,
    state text not null default 'PENDING' check (state in ('PENDING','RUNNING','RECONCILING','SUCCEEDED','FAILED','CANCELLED')),
    state_version bigint not null default 0 check (state_version >= 0),
    idempotency_key text not null unique,
    foreign key (activity_key, activity_version) references activity_definition(activity_key, registry_version)
);
create table job_attempt (
    id uuid primary key,
    job_id uuid not null references job(id),
    attempt_number integer not null check (attempt_number > 0),
    worker_id uuid not null references worker(id),
    fencing_token bigint not null check (fencing_token > 0),
    lease_expires_at timestamptz not null,
    finished_at timestamptz,
    unique (job_id, attempt_number), unique (job_id, fencing_token)
);
create unique index one_live_attempt_per_job on job_attempt(job_id) where finished_at is null;
create index inbox_unprocessed on webhook_inbox(received_at) where processed_at is null;

create table webhook_event (
    delivery_id uuid primary key references webhook_inbox(delivery_id),
    event_kind text not null check (event_kind in ('PING','ISSUE','PULL_REQUEST','UNSUPPORTED')),
    published_at timestamptz not null default now()
);

create table lifecycle_transition (
    command_id uuid primary key,
    request_sha256 text not null check (request_sha256 ~ '^[0-9a-f]{64}$'),
    entity_kind text not null check (entity_kind in ('JOB','STORY','EPIC')),
    entity_id uuid not null,
    from_state text not null,
    to_state text not null,
    resulting_version bigint not null check (resulting_version > 0),
    actor_id text not null,
    identity jsonb not null,
    occurred_at timestamptz not null,
    unique (entity_kind,entity_id,resulting_version)
);
