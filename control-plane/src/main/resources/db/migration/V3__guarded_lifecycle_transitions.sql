alter table job add column state_version bigint not null default 0 check (state_version >= 0);
alter table story add column state_version bigint not null default 0 check (state_version >= 0);
alter table epic add column state_version bigint not null default 0 check (state_version >= 0);
alter table story add column approved_identity jsonb;
alter table epic add column approved_identity jsonb;
-- Set by the future scoped SCM publisher, not inferred from job/BPMN completion.
alter table story add column expected_pr_head_sha text check (expected_pr_head_sha ~ '^[0-9a-f]{40}([0-9a-f]{24})?$');
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
