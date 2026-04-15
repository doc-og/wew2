-- Migration 008: Parent contact on device (SOS), SMS thread id for system messages, timezone, apps.media_action_type

-- ── devices: parent phone / name (set by parent app), thread id (synced from child), local timezone
ALTER TABLE public.devices
    ADD COLUMN IF NOT EXISTS parent_phone text,
    ADD COLUMN IF NOT EXISTS parent_display_name text,
    ADD COLUMN IF NOT EXISTS parent_sms_thread_id text,
    ADD COLUMN IF NOT EXISTS timezone text NOT NULL DEFAULT 'America/Los_Angeles';

CREATE INDEX IF NOT EXISTS idx_devices_parent_sms_thread
    ON public.devices(parent_sms_thread_id)
    WHERE parent_sms_thread_id IS NOT NULL;

-- ── apps: optional foreground media billing category (TOKEN_SYSTEM.md)
ALTER TABLE public.apps
    ADD COLUMN IF NOT EXISTS media_action_type text;

ALTER TABLE public.apps DROP CONSTRAINT IF EXISTS apps_media_action_type_check;
ALTER TABLE public.apps ADD CONSTRAINT apps_media_action_type_check
    CHECK (
        media_action_type IS NULL
        OR media_action_type IN (
            'video_watched',
            'game_session',
            'social_scroll',
            'audio_streamed'
        )
    );

-- ── RLS: child launcher uses anon key + device UUID; must read/update device row (capability model)
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_policies
        WHERE schemaname = 'public' AND tablename = 'devices' AND policyname = 'devices_select_capability'
    ) THEN
        CREATE POLICY "devices_select_capability"
            ON public.devices FOR SELECT
            USING (true);
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_policies
        WHERE schemaname = 'public' AND tablename = 'devices' AND policyname = 'devices_update_capability'
    ) THEN
        CREATE POLICY "devices_update_capability"
            ON public.devices FOR UPDATE
            USING (true)
            WITH CHECK (true);
    END IF;
END $$;
