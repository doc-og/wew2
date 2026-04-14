-- Migration 006: passcodes, temp app access, contacts, check-ins
-- All new tables for the launcher overhaul feature set.

-- ─────────────────────────────────────────────────────────────
-- 1. Device passcode
--    Set by the parent. Child enters it to unlock unauthorized apps.
--    Stored as a SHA-256 hex digest (salted with device_id).
-- ─────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS public.device_passcode (
    device_id   uuid PRIMARY KEY REFERENCES public.devices(id) ON DELETE CASCADE,
    passcode_hash text NOT NULL,
    created_at  timestamptz NOT NULL DEFAULT now(),
    updated_at  timestamptz NOT NULL DEFAULT now()
);

-- RLS: only the parent who owns the device can read/write the passcode
ALTER TABLE public.device_passcode ENABLE ROW LEVEL SECURITY;

CREATE POLICY "parent_owns_passcode"
    ON public.device_passcode
    FOR ALL
    USING (
        EXISTS (
            SELECT 1 FROM public.devices d
            WHERE d.id = device_id
              AND d.parent_user_id = auth.uid()
        )
    );

-- ─────────────────────────────────────────────────────────────
-- 2. Temporary app access
--    Granted when the child enters the correct passcode.
--    Expires at the recorded timestamp.
-- ─────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS public.temporary_app_access (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    device_id       uuid NOT NULL REFERENCES public.devices(id) ON DELETE CASCADE,
    package_name    text NOT NULL,
    expires_at      timestamptz NOT NULL,
    granted_by      text NOT NULL DEFAULT 'passcode',   -- 'passcode' | 'parent'
    created_at      timestamptz NOT NULL DEFAULT now(),
    UNIQUE (device_id, package_name)
);

CREATE INDEX IF NOT EXISTS idx_temp_access_device_pkg
    ON public.temporary_app_access(device_id, package_name);

CREATE INDEX IF NOT EXISTS idx_temp_access_expires
    ON public.temporary_app_access(expires_at);

-- RLS: parent can see all grants for their device; launcher inserts via service role
ALTER TABLE public.temporary_app_access ENABLE ROW LEVEL SECURITY;

CREATE POLICY "parent_reads_temp_access"
    ON public.temporary_app_access
    FOR SELECT
    USING (
        EXISTS (
            SELECT 1 FROM public.devices d
            WHERE d.id = device_id
              AND d.parent_user_id = auth.uid()
        )
    );

-- Allow anon (child device) to read its own temp access rows
CREATE POLICY "device_reads_own_temp_access"
    ON public.temporary_app_access
    FOR SELECT
    USING (true);  -- scoped by device_id in query; full RLS would require device auth

-- Allow anon (child device) to insert/upsert temp access
CREATE POLICY "device_upserts_temp_access"
    ON public.temporary_app_access
    FOR INSERT
    WITH CHECK (true);

CREATE POLICY "device_updates_temp_access"
    ON public.temporary_app_access
    FOR UPDATE
    USING (true);

-- ─────────────────────────────────────────────────────────────
-- 3. Wew contacts
--    Child's contact book managed inside the launcher.
--    Contacts must be authorized by the parent before the child
--    can message or call them.
-- ─────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS public.contacts (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    device_id       uuid NOT NULL REFERENCES public.devices(id) ON DELETE CASCADE,
    name            text NOT NULL,
    phone           text,
    email           text,
    address         text,
    photo_url       text,
    is_authorized   boolean NOT NULL DEFAULT false,
    notes           text,
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_contacts_device
    ON public.contacts(device_id);

ALTER TABLE public.contacts ENABLE ROW LEVEL SECURITY;

-- Parent reads/writes contacts for their device
CREATE POLICY "parent_manages_contacts"
    ON public.contacts
    FOR ALL
    USING (
        EXISTS (
            SELECT 1 FROM public.devices d
            WHERE d.id = device_id
              AND d.parent_user_id = auth.uid()
        )
    );

-- Child device can read and insert (new contacts pending authorization)
CREATE POLICY "device_reads_contacts"
    ON public.contacts
    FOR SELECT
    USING (true);

CREATE POLICY "device_inserts_contacts"
    ON public.contacts
    FOR INSERT
    WITH CHECK (true);

CREATE POLICY "device_updates_contacts"
    ON public.contacts
    FOR UPDATE
    USING (true);

-- ─────────────────────────────────────────────────────────────
-- 4. Contact authorization requests
--    Created by the child when they add a new contact.
--    Parent approves or denies in the dashboard.
-- ─────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS public.contact_auth_requests (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    device_id   uuid NOT NULL REFERENCES public.devices(id) ON DELETE CASCADE,
    contact_id  uuid NOT NULL REFERENCES public.contacts(id) ON DELETE CASCADE,
    status      text NOT NULL DEFAULT 'pending'
                    CHECK (status IN ('pending', 'approved', 'denied')),
    created_at  timestamptz NOT NULL DEFAULT now(),
    updated_at  timestamptz NOT NULL DEFAULT now(),
    UNIQUE (device_id, contact_id)
);

CREATE INDEX IF NOT EXISTS idx_contact_auth_device
    ON public.contact_auth_requests(device_id, status);

ALTER TABLE public.contact_auth_requests ENABLE ROW LEVEL SECURITY;

CREATE POLICY "parent_manages_auth_requests"
    ON public.contact_auth_requests
    FOR ALL
    USING (
        EXISTS (
            SELECT 1 FROM public.devices d
            WHERE d.id = device_id
              AND d.parent_user_id = auth.uid()
        )
    );

CREATE POLICY "device_reads_auth_requests"
    ON public.contact_auth_requests
    FOR SELECT
    USING (true);

CREATE POLICY "device_inserts_auth_requests"
    ON public.contact_auth_requests
    FOR INSERT
    WITH CHECK (true);

-- ─────────────────────────────────────────────────────────────
-- 5. Check-ins
--    Explicit location shares initiated by the child.
--    Distinct from passive location_log pings.
--    Triggers an FCM notification to the parent.
-- ─────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS public.check_ins (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    device_id       uuid NOT NULL REFERENCES public.devices(id) ON DELETE CASCADE,
    latitude        double precision NOT NULL,
    longitude       double precision NOT NULL,
    accuracy_meters real,
    message         text,               -- optional note from child ("at school", "at park")
    created_at      timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_check_ins_device_time
    ON public.check_ins(device_id, created_at DESC);

ALTER TABLE public.check_ins ENABLE ROW LEVEL SECURITY;

CREATE POLICY "parent_reads_checkins"
    ON public.check_ins
    FOR SELECT
    USING (
        EXISTS (
            SELECT 1 FROM public.devices d
            WHERE d.id = device_id
              AND d.parent_user_id = auth.uid()
        )
    );

CREATE POLICY "device_inserts_checkins"
    ON public.check_ins
    FOR INSERT
    WITH CHECK (true);

-- ─────────────────────────────────────────────────────────────
-- 6. Realtime publication — add new tables
-- ─────────────────────────────────────────────────────────────
DO $$
BEGIN
    -- check_ins should stream to parent in real time
    IF NOT EXISTS (
        SELECT 1 FROM pg_publication_tables
        WHERE pubname = 'supabase_realtime'
          AND tablename = 'check_ins'
    ) THEN
        ALTER PUBLICATION supabase_realtime ADD TABLE public.check_ins;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_publication_tables
        WHERE pubname = 'supabase_realtime'
          AND tablename = 'contacts'
    ) THEN
        ALTER PUBLICATION supabase_realtime ADD TABLE public.contacts;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_publication_tables
        WHERE pubname = 'supabase_realtime'
          AND tablename = 'contact_auth_requests'
    ) THEN
        ALTER PUBLICATION supabase_realtime ADD TABLE public.contact_auth_requests;
    END IF;
END $$;
