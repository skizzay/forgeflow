alter table webhook_inbox add column payload_sha256 text check (payload_sha256 ~ '^[0-9a-f]{64}$');
alter table webhook_inbox add column payload_bytes integer check (payload_bytes >= 0);
alter table webhook_inbox add column metadata jsonb not null default '{}';
-- Legacy rows have no authenticated payload identity and are never promoted as verified events.
create table webhook_event (
    delivery_id uuid primary key references webhook_inbox(delivery_id),
    event_kind text not null check (event_kind in ('PING','ISSUE','PULL_REQUEST','UNSUPPORTED')),
    published_at timestamptz not null default now()
);
