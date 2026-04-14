-- Migration 007: Token system + chat infrastructure
-- Replaces the credit system with a token-based usage model.
-- Adds SMS/MMS conversation metadata, URL filter tables, and token request flow.

-- ─────────────────────────────────────────────────────────────
-- 1. Token columns on devices
--    Keeps legacy credit columns intact (set to 0) so old code
--    doesn't break before it's fully replaced.
-- ─────────────────────────────────────────────────────────────
ALTER TABLE public.devices
    ADD COLUMN IF NOT EXISTS current_tokens     integer NOT NULL DEFAULT 10000 CHECK (current_tokens >= 0),
    ADD COLUMN IF NOT EXISTS daily_token_budget integer NOT NULL DEFAULT 10000 CHECK (daily_token_budget > 0),
    ADD COLUMN IF NOT EXISTS tokens_reset_time  time    NOT NULL DEFAULT '00:00:00';

-- ─────────────────────────────────────────────────────────────
-- 2. Token ledger
--    Immutable log of every token-consuming event.
-- ─────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS public.token_ledger (
    id               uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    device_id        uuid        NOT NULL REFERENCES public.devices(id) ON DELETE CASCADE,
    action_type      text        NOT NULL,
    tokens_consumed  integer     NOT NULL DEFAULT 0 CHECK (tokens_consumed >= 0),
    context_metadata jsonb       NOT NULL DEFAULT '{}',
    balance_after    integer     NOT NULL CHECK (balance_after >= 0),
    created_at       timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_token_ledger_device_time
    ON public.token_ledger(device_id, created_at DESC);

ALTER TABLE public.token_ledger ENABLE ROW LEVEL SECURITY;

CREATE POLICY "parent_reads_token_ledger"
    ON public.token_ledger FOR SELECT
    USING (
        EXISTS (
            SELECT 1 FROM public.devices d
            WHERE d.id = device_id AND d.parent_user_id = auth.uid()
        )
    );

CREATE POLICY "device_inserts_token_ledger"
    ON public.token_ledger FOR INSERT WITH CHECK (true);

-- ─────────────────────────────────────────────────────────────
-- 3. Token budgets
--    Parent-configurable per-device daily limits.
--    Seeded automatically when a device is registered.
-- ─────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS public.token_budgets (
    device_id       uuid        PRIMARY KEY REFERENCES public.devices(id) ON DELETE CASCADE,
    daily_limit     integer     NOT NULL DEFAULT 10000 CHECK (daily_limit > 0),
    rollover_enabled boolean    NOT NULL DEFAULT false,
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now()
);

ALTER TABLE public.token_budgets ENABLE ROW LEVEL SECURITY;

CREATE POLICY "parent_manages_token_budgets"
    ON public.token_budgets FOR ALL
    USING (
        EXISTS (
            SELECT 1 FROM public.devices d
            WHERE d.id = device_id AND d.parent_user_id = auth.uid()
        )
    );

CREATE POLICY "device_reads_token_budget"
    ON public.token_budgets FOR SELECT USING (true);

-- ─────────────────────────────────────────────────────────────
-- 4. Token action costs
--    Per-device overrides for action token costs.
--    If no row exists for a device+action, the app uses engine defaults.
-- ─────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS public.token_action_costs (
    id             uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    device_id      uuid        NOT NULL REFERENCES public.devices(id) ON DELETE CASCADE,
    action_type    text        NOT NULL,
    base_cost      integer     NOT NULL DEFAULT 0  CHECK (base_cost >= 0),
    cost_per_unit  integer     NOT NULL DEFAULT 0  CHECK (cost_per_unit >= 0),
    -- unit_type: what one "unit" means for per-unit billing
    unit_type      text        CHECK (unit_type IN ('per_minute', 'per_mb', 'per_message', 'flat')),
    created_at     timestamptz NOT NULL DEFAULT now(),
    updated_at     timestamptz NOT NULL DEFAULT now(),
    UNIQUE (device_id, action_type)
);

CREATE INDEX IF NOT EXISTS idx_token_action_costs_device
    ON public.token_action_costs(device_id);

ALTER TABLE public.token_action_costs ENABLE ROW LEVEL SECURITY;

CREATE POLICY "parent_manages_action_costs"
    ON public.token_action_costs FOR ALL
    USING (
        EXISTS (
            SELECT 1 FROM public.devices d
            WHERE d.id = device_id AND d.parent_user_id = auth.uid()
        )
    );

CREATE POLICY "device_reads_action_costs"
    ON public.token_action_costs FOR SELECT USING (true);

-- ─────────────────────────────────────────────────────────────
-- 5. Token requests
--    Child requests additional tokens for a specific app.
--    Parent approves or denies from the dashboard.
-- ─────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS public.token_requests (
    id               uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    device_id        uuid        NOT NULL REFERENCES public.devices(id) ON DELETE CASCADE,
    app_package      text,
    app_name         text,
    tokens_requested integer     NOT NULL DEFAULT 1000 CHECK (tokens_requested > 0),
    reason           text,
    status           text        NOT NULL DEFAULT 'pending'
                                     CHECK (status IN ('pending', 'approved', 'denied')),
    parent_note      text,
    created_at       timestamptz NOT NULL DEFAULT now(),
    updated_at       timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_token_requests_device_status
    ON public.token_requests(device_id, status);

ALTER TABLE public.token_requests ENABLE ROW LEVEL SECURITY;

CREATE POLICY "parent_manages_token_requests"
    ON public.token_requests FOR ALL
    USING (
        EXISTS (
            SELECT 1 FROM public.devices d
            WHERE d.id = device_id AND d.parent_user_id = auth.uid()
        )
    );

CREATE POLICY "device_reads_token_requests"
    ON public.token_requests FOR SELECT USING (true);

CREATE POLICY "device_inserts_token_requests"
    ON public.token_requests FOR INSERT WITH CHECK (true);

-- ─────────────────────────────────────────────────────────────
-- 6. Conversations
--    Metadata only. Actual SMS/MMS content lives on the device.
--    thread_id matches Android's SMS thread_id integer.
-- ─────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS public.conversations (
    id           uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    device_id    uuid        NOT NULL REFERENCES public.devices(id) ON DELETE CASCADE,
    thread_id    text        NOT NULL,   -- Android SMS content://sms/conversations thread_id
    display_name text,
    is_pinned    boolean     NOT NULL DEFAULT false,
    is_muted     boolean     NOT NULL DEFAULT false,
    created_at   timestamptz NOT NULL DEFAULT now(),
    updated_at   timestamptz NOT NULL DEFAULT now(),
    UNIQUE (device_id, thread_id)
);

CREATE INDEX IF NOT EXISTS idx_conversations_device
    ON public.conversations(device_id);

ALTER TABLE public.conversations ENABLE ROW LEVEL SECURITY;

CREATE POLICY "parent_reads_conversations"
    ON public.conversations FOR SELECT
    USING (
        EXISTS (
            SELECT 1 FROM public.devices d
            WHERE d.id = device_id AND d.parent_user_id = auth.uid()
        )
    );

CREATE POLICY "device_manages_conversations"
    ON public.conversations FOR ALL USING (true);

-- ─────────────────────────────────────────────────────────────
-- 7. Messages log
--    Metadata mirror of SMS/MMS for parent dashboard visibility.
--    Full message body stays on device; only metadata + thumbnails here.
-- ─────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS public.messages (
    id               uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    device_id        uuid        NOT NULL REFERENCES public.devices(id) ON DELETE CASCADE,
    thread_id        text        NOT NULL,
    sender_address   text        NOT NULL,  -- phone number or 'child'
    sender_type      text        NOT NULL
                                     CHECK (sender_type IN ('child', 'contact', 'parent', 'system')),
    message_type     text        NOT NULL
                                     CHECK (message_type IN (
                                         'text', 'mms_image', 'mms_video',
                                         'mms_audio', 'location', 'call_summary'
                                     )),
    has_media        boolean     NOT NULL DEFAULT false,
    thumbnail_url    text,               -- Supabase Storage URL for image/video thumbnail
    tokens_consumed  integer     NOT NULL DEFAULT 0,
    created_at       timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_messages_device_thread
    ON public.messages(device_id, thread_id);
CREATE INDEX IF NOT EXISTS idx_messages_device_time
    ON public.messages(device_id, created_at DESC);

ALTER TABLE public.messages ENABLE ROW LEVEL SECURITY;

CREATE POLICY "parent_reads_messages"
    ON public.messages FOR SELECT
    USING (
        EXISTS (
            SELECT 1 FROM public.devices d
            WHERE d.id = device_id AND d.parent_user_id = auth.uid()
        )
    );

CREATE POLICY "device_inserts_messages"
    ON public.messages FOR INSERT WITH CHECK (true);

-- ─────────────────────────────────────────────────────────────
-- 8. URL filters
--    Allowlist and blocklist for the in-chat WebView.
--    is_global = true means it applies to all devices under a parent.
-- ─────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS public.url_filters (
    id           uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    device_id    uuid        REFERENCES public.devices(id) ON DELETE CASCADE,
    parent_id    uuid        REFERENCES public.users(id)   ON DELETE CASCADE,
    url_pattern  text        NOT NULL,
    filter_type  text        NOT NULL CHECK (filter_type IN ('allow', 'block')),
    is_global    boolean     NOT NULL DEFAULT false,
    created_by   text        NOT NULL DEFAULT 'parent',
    created_at   timestamptz NOT NULL DEFAULT now(),
    -- A pattern is unique per device (or global per parent when device_id is null)
    CONSTRAINT url_filter_scope CHECK (
        (device_id IS NOT NULL AND parent_id IS NOT NULL)
        OR (device_id IS NULL  AND parent_id IS NOT NULL AND is_global = true)
    )
);

CREATE INDEX IF NOT EXISTS idx_url_filters_device
    ON public.url_filters(device_id);
CREATE INDEX IF NOT EXISTS idx_url_filters_parent_global
    ON public.url_filters(parent_id) WHERE is_global = true;

ALTER TABLE public.url_filters ENABLE ROW LEVEL SECURITY;

CREATE POLICY "parent_manages_url_filters"
    ON public.url_filters FOR ALL
    USING (parent_id = auth.uid());

CREATE POLICY "device_reads_url_filters"
    ON public.url_filters FOR SELECT USING (true);

-- ─────────────────────────────────────────────────────────────
-- 9. URL access requests
--    When the child tries to navigate to a blocked URL in WebView,
--    a request is logged here for parent approval.
-- ─────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS public.url_access_requests (
    id          uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    device_id   uuid        NOT NULL REFERENCES public.devices(id) ON DELETE CASCADE,
    url         text        NOT NULL,
    page_title  text,
    status      text        NOT NULL DEFAULT 'pending'
                                CHECK (status IN ('pending', 'approved', 'denied')),
    created_at  timestamptz NOT NULL DEFAULT now(),
    updated_at  timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_url_access_requests_device_status
    ON public.url_access_requests(device_id, status);

ALTER TABLE public.url_access_requests ENABLE ROW LEVEL SECURITY;

CREATE POLICY "parent_manages_url_access_requests"
    ON public.url_access_requests FOR ALL
    USING (
        EXISTS (
            SELECT 1 FROM public.devices d
            WHERE d.id = device_id AND d.parent_user_id = auth.uid()
        )
    );

CREATE POLICY "device_inserts_url_access_requests"
    ON public.url_access_requests FOR INSERT WITH CHECK (true);

CREATE POLICY "device_reads_url_access_requests"
    ON public.url_access_requests FOR SELECT USING (true);

-- ─────────────────────────────────────────────────────────────
-- 10. tokens_consumed column on activity_log
--     Parallel to (and eventually replacing) credits_deducted.
-- ─────────────────────────────────────────────────────────────
ALTER TABLE public.activity_log
    ADD COLUMN IF NOT EXISTS tokens_consumed integer NOT NULL DEFAULT 0 CHECK (tokens_consumed >= 0);

-- ─────────────────────────────────────────────────────────────
-- 11. Realtime publications
-- ─────────────────────────────────────────────────────────────
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_publication_tables
        WHERE pubname = 'supabase_realtime' AND tablename = 'token_requests'
    ) THEN
        ALTER PUBLICATION supabase_realtime ADD TABLE public.token_requests;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_publication_tables
        WHERE pubname = 'supabase_realtime' AND tablename = 'token_budgets'
    ) THEN
        ALTER PUBLICATION supabase_realtime ADD TABLE public.token_budgets;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_publication_tables
        WHERE pubname = 'supabase_realtime' AND tablename = 'url_access_requests'
    ) THEN
        ALTER PUBLICATION supabase_realtime ADD TABLE public.url_access_requests;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_publication_tables
        WHERE pubname = 'supabase_realtime' AND tablename = 'url_filters'
    ) THEN
        ALTER PUBLICATION supabase_realtime ADD TABLE public.url_filters;
    END IF;
END $$;

-- ─────────────────────────────────────────────────────────────
-- 12. Daily token reset function (replaces credit reset)
-- ─────────────────────────────────────────────────────────────
CREATE OR REPLACE FUNCTION public.reset_daily_tokens()
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
BEGIN
    UPDATE public.devices d
    SET current_tokens = tb.daily_limit,
        updated_at     = now()
    FROM public.token_budgets tb
    WHERE tb.device_id = d.id;

    -- Also reset devices that have no token_budget row yet (use device default)
    UPDATE public.devices
    SET current_tokens = daily_token_budget,
        updated_at     = now()
    WHERE id NOT IN (SELECT device_id FROM public.token_budgets);
END;
$$;
