-- Wew parental control schema
-- Migration 001: initial tables

-- Parent user profiles (extends auth.users)
CREATE TABLE IF NOT EXISTS public.users (
    id          uuid PRIMARY KEY REFERENCES auth.users(id) ON DELETE CASCADE,
    email       text NOT NULL,
    full_name   text,
    created_at  timestamptz NOT NULL DEFAULT now(),
    updated_at  timestamptz NOT NULL DEFAULT now()
);

-- Child device records
CREATE TABLE IF NOT EXISTS public.devices (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    parent_user_id      uuid NOT NULL REFERENCES public.users(id) ON DELETE CASCADE,
    device_name         text NOT NULL DEFAULT 'Child Phone',
    fcm_token           text,
    is_locked           boolean NOT NULL DEFAULT false,
    current_credits     integer NOT NULL DEFAULT 100 CHECK (current_credits >= 0),
    daily_credit_budget integer NOT NULL DEFAULT 100 CHECK (daily_credit_budget > 0),
    credits_reset_time  time NOT NULL DEFAULT '07:00:00',
    last_seen_at        timestamptz,
    created_at          timestamptz NOT NULL DEFAULT now(),
    updated_at          timestamptz NOT NULL DEFAULT now()
);

-- Installed apps per device with whitelist status
CREATE TABLE IF NOT EXISTS public.apps (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    device_id       uuid NOT NULL REFERENCES public.devices(id) ON DELETE CASCADE,
    package_name    text NOT NULL,
    app_name        text NOT NULL,
    is_whitelisted  boolean NOT NULL DEFAULT false,
    is_system_app   boolean NOT NULL DEFAULT false,
    credit_cost     integer NOT NULL DEFAULT 1 CHECK (credit_cost >= 0),
    icon_url        text,
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now(),
    UNIQUE (device_id, package_name)
);

-- Action-by-action usage log
CREATE TABLE IF NOT EXISTS public.activity_log (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    device_id       uuid NOT NULL REFERENCES public.devices(id) ON DELETE CASCADE,
    action_type     text NOT NULL,
    -- Allowed action_type values:
    -- 'app_open', 'message_sent', 'call_made', 'call_received',
    -- 'photo_taken', 'photo_shared', 'web_link_opened',
    -- 'app_blocked', 'settings_tamper', 'device_admin_revoked',
    -- 'lock_activated', 'lock_deactivated', 'credit_exhausted'
    app_package     text,
    app_name        text,
    credits_deducted integer NOT NULL DEFAULT 0,
    metadata        jsonb NOT NULL DEFAULT '{}',
    created_at      timestamptz NOT NULL DEFAULT now()
);

-- Credit balance history
CREATE TABLE IF NOT EXISTS public.credit_ledger (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    device_id       uuid NOT NULL REFERENCES public.devices(id) ON DELETE CASCADE,
    change_amount   integer NOT NULL,
    balance_after   integer NOT NULL,
    reason          text NOT NULL CHECK (reason IN ('daily_reset', 'parent_add', 'parent_remove', 'action_deduction')),
    action_type     text,
    parent_note     text,
    created_at      timestamptz NOT NULL DEFAULT now()
);

-- GPS location pings
CREATE TABLE IF NOT EXISTS public.location_log (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    device_id       uuid NOT NULL REFERENCES public.devices(id) ON DELETE CASCADE,
    latitude        double precision NOT NULL,
    longitude       double precision NOT NULL,
    accuracy_meters real,
    created_at      timestamptz NOT NULL DEFAULT now()
);

-- Bedtime and school lock schedules
CREATE TABLE IF NOT EXISTS public.schedules (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    device_id       uuid NOT NULL REFERENCES public.devices(id) ON DELETE CASCADE,
    schedule_type   text NOT NULL CHECK (schedule_type IN ('bedtime', 'school')),
    start_time      time NOT NULL,
    end_time        time NOT NULL,
    days_of_week    integer[] NOT NULL DEFAULT '{1,2,3,4,5}',
    is_enabled      boolean NOT NULL DEFAULT true,
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now(),
    UNIQUE (device_id, schedule_type)
);

-- Per-parent notification preferences
CREATE TABLE IF NOT EXISTS public.notifications_config (
    id                          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    parent_user_id              uuid NOT NULL REFERENCES public.users(id) ON DELETE CASCADE UNIQUE,
    low_credit_threshold_pct    integer NOT NULL DEFAULT 20 CHECK (low_credit_threshold_pct BETWEEN 5 AND 95),
    daily_summary_enabled       boolean NOT NULL DEFAULT true,
    daily_summary_time          time NOT NULL DEFAULT '20:00:00',
    notify_blocked_apps         boolean NOT NULL DEFAULT true,
    notify_tamper_attempts      boolean NOT NULL DEFAULT true,
    notify_location_updates     boolean NOT NULL DEFAULT false,
    created_at                  timestamptz NOT NULL DEFAULT now(),
    updated_at                  timestamptz NOT NULL DEFAULT now()
);

-- Indexes for common query patterns
CREATE INDEX IF NOT EXISTS idx_devices_parent ON public.devices(parent_user_id);
CREATE INDEX IF NOT EXISTS idx_activity_log_device_time ON public.activity_log(device_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_credit_ledger_device_time ON public.credit_ledger(device_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_location_log_device_time ON public.location_log(device_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_apps_device ON public.apps(device_id);
CREATE INDEX IF NOT EXISTS idx_schedules_device ON public.schedules(device_id);
