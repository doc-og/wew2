-- Migration 010: per-day phone access schedule
-- Stores the allowed phone hours for each day of the week per device.
-- When enabled for a given day, the launcher will only be accessible during
-- allowed_start..allowed_end; outside that window the launcher shows a lock screen.
--
-- day_of_week: 0 = Sunday, 1 = Monday, ..., 6 = Saturday (matches Java DayOfWeek % 7).

create table if not exists access_schedule (
    id            uuid        primary key default gen_random_uuid(),
    device_id     uuid        not null references devices(id) on delete cascade,
    day_of_week   int         not null check (day_of_week between 0 and 6),
    is_enabled    boolean     not null default true,
    allowed_start time        not null default '07:00:00',
    allowed_end   time        not null default '21:00:00',
    created_at    timestamptz not null default now(),
    updated_at    timestamptz not null default now(),
    unique (device_id, day_of_week)
);

create index if not exists idx_access_schedule_device_id
    on access_schedule (device_id);

-- updated_at trigger
create or replace function set_access_schedule_updated_at()
returns trigger language plpgsql as $$
begin
    new.updated_at = now();
    return new;
end;
$$;

create trigger trg_access_schedule_updated_at
    before update on access_schedule
    for each row execute function set_access_schedule_updated_at();

-- RLS
alter table access_schedule enable row level security;

-- Parents can read and write their device's schedule
create policy "parent_manage_access_schedule"
    on access_schedule for all
    using (
        device_id in (
            select id from devices where parent_user_id = auth.uid()
        )
    )
    with check (
        device_id in (
            select id from devices where parent_user_id = auth.uid()
        )
    );

-- Child device (anon key) can read — device_id is treated as a secret
create policy "device_read_access_schedule"
    on access_schedule for select
    using (true);
