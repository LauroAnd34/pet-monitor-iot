create extension if not exists pgcrypto;

create table if not exists public.devices (
  id uuid primary key default gen_random_uuid(),
  name text not null,
  hardware_type text not null default 'esp32',
  token text not null unique,
  dashboard_token text not null unique default replace(gen_random_uuid()::text, '-', ''),
  created_at timestamptz not null default now()
);

create table if not exists public.telemetry_events (
  id bigint generated always as identity primary key,
  device_id uuid not null references public.devices(id) on delete cascade,
  created_at timestamptz not null default now(),
  temperature_c numeric(5,2),
  humidity numeric(5,2),
  food_level_percent integer,
  water_level_percent integer,
  gas_raw integer,
  light_raw integer,
  is_dark boolean,
  motion_detected boolean,
  pump_on boolean,
  lamp_on boolean,
  feed_motor_on boolean,
  alert_text text,
  payload jsonb not null default '{}'::jsonb
);

create index if not exists telemetry_events_device_created_at_idx
on public.telemetry_events(device_id, created_at desc);

create table if not exists public.device_commands (
  id bigint generated always as identity primary key,
  device_id uuid not null references public.devices(id) on delete cascade,
  command_type text not null,
  payload jsonb not null default '{}'::jsonb,
  status text not null default 'pending',
  created_at timestamptz not null default now(),
  consumed_at timestamptz
);

create or replace view public.latest_device_snapshot as
select distinct on (device_id)
  device_id,
  created_at,
  temperature_c,
  humidity,
  food_level_percent,
  water_level_percent,
  gas_raw,
  light_raw,
  is_dark,
  motion_detected,
  pump_on,
  lamp_on,
  feed_motor_on,
  alert_text,
  payload
from public.telemetry_events
order by device_id, created_at desc;

create index if not exists device_commands_device_status_created_at_idx
on public.device_commands(device_id, status, created_at asc);
