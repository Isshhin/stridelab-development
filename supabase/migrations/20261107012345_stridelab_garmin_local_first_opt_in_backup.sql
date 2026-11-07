-- Already applied to project difvgjwhkzipfxjngtnp as version 20261107012345.
-- Dedicated, immutable backup snapshots. Never put local Garmin source IDs
-- into public.activities.id (UUID), or expose Garmin credentials in this table.
create table if not exists public.stridelab_garmin_backups (
  user_id uuid not null references auth.users(id) on delete cascade,
  record_kind text not null check (record_kind in ('activity', 'activity_detail')),
  record_id text not null check (char_length(record_id) between 1 and 512),
  payload_sha256 text not null check (payload_sha256 ~ '^[a-f0-9]{64}$'),
  payload jsonb not null,
  created_at timestamptz not null default now(),
  primary key (user_id, record_kind, record_id, payload_sha256),
  constraint stridelab_garmin_backup_object check (jsonb_typeof(payload) = 'object'),
  constraint stridelab_garmin_backup_owner check (payload->>'user_id' = user_id::text),
  constraint stridelab_garmin_backup_identity check (
    (record_kind = 'activity' and payload->>'id' = record_id and payload->>'source' = 'garmin')
    or (record_kind = 'activity_detail' and payload->>'activity_id' = record_id)
  ),
  constraint stridelab_garmin_backup_size check (pg_column_size(payload) <= 3145728)
);
alter table public.stridelab_garmin_backups enable row level security;
revoke all on table public.stridelab_garmin_backups from public, anon, authenticated;
grant select, insert on table public.stridelab_garmin_backups to authenticated;
create policy "Owner selects own Garmin backups"
on public.stridelab_garmin_backups for select to authenticated
using ((select auth.uid()) = user_id);
create policy "Owner inserts own Garmin backups"
on public.stridelab_garmin_backups for insert to authenticated
with check ((select auth.uid()) = user_id);
