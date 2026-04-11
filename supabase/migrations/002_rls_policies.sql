-- Migration 002: Row Level Security policies

-- Enable RLS on all tables
ALTER TABLE public.users ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.devices ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.apps ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.activity_log ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.credit_ledger ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.location_log ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.schedules ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.notifications_config ENABLE ROW LEVEL SECURITY;

-- Helper function: check if a device belongs to the calling user
CREATE OR REPLACE FUNCTION public.device_belongs_to_user(p_device_id uuid)
RETURNS boolean
LANGUAGE sql
SECURITY DEFINER
STABLE
AS $$
    SELECT EXISTS (
        SELECT 1 FROM public.devices
        WHERE id = p_device_id
          AND parent_user_id = auth.uid()
    );
$$;

-- =====================
-- users policies
-- =====================
CREATE POLICY "users_select_own" ON public.users
    FOR SELECT USING (id = auth.uid());

CREATE POLICY "users_update_own" ON public.users
    FOR UPDATE USING (id = auth.uid());

CREATE POLICY "users_insert_own" ON public.users
    FOR INSERT WITH CHECK (id = auth.uid());

-- =====================
-- devices policies
-- =====================
CREATE POLICY "devices_select_own" ON public.devices
    FOR SELECT USING (parent_user_id = auth.uid());

CREATE POLICY "devices_update_own" ON public.devices
    FOR UPDATE USING (parent_user_id = auth.uid());

CREATE POLICY "devices_insert_own" ON public.devices
    FOR INSERT WITH CHECK (parent_user_id = auth.uid());

CREATE POLICY "devices_delete_own" ON public.devices
    FOR DELETE USING (parent_user_id = auth.uid());

-- =====================
-- apps policies
-- =====================
CREATE POLICY "apps_select_parent" ON public.apps
    FOR SELECT USING (public.device_belongs_to_user(device_id));

CREATE POLICY "apps_insert_parent" ON public.apps
    FOR INSERT WITH CHECK (public.device_belongs_to_user(device_id));

CREATE POLICY "apps_update_parent" ON public.apps
    FOR UPDATE USING (public.device_belongs_to_user(device_id));

CREATE POLICY "apps_delete_parent" ON public.apps
    FOR DELETE USING (public.device_belongs_to_user(device_id));

-- =====================
-- activity_log policies
-- =====================
CREATE POLICY "activity_log_select_parent" ON public.activity_log
    FOR SELECT USING (public.device_belongs_to_user(device_id));

-- Insert via service role only (child device uses service role key or RPC)
CREATE POLICY "activity_log_insert_service" ON public.activity_log
    FOR INSERT WITH CHECK (true); -- restricted further by SECURITY DEFINER functions

-- Parents cannot delete activity logs
-- (no DELETE policy = denied)

-- =====================
-- credit_ledger policies
-- =====================
CREATE POLICY "credit_ledger_select_parent" ON public.credit_ledger
    FOR SELECT USING (public.device_belongs_to_user(device_id));

CREATE POLICY "credit_ledger_insert_service" ON public.credit_ledger
    FOR INSERT WITH CHECK (true); -- handled by SECURITY DEFINER RPCs

-- =====================
-- location_log policies
-- =====================
CREATE POLICY "location_log_select_parent" ON public.location_log
    FOR SELECT USING (public.device_belongs_to_user(device_id));

CREATE POLICY "location_log_insert_service" ON public.location_log
    FOR INSERT WITH CHECK (true);

-- =====================
-- schedules policies
-- =====================
CREATE POLICY "schedules_select_parent" ON public.schedules
    FOR SELECT USING (public.device_belongs_to_user(device_id));

CREATE POLICY "schedules_insert_parent" ON public.schedules
    FOR INSERT WITH CHECK (public.device_belongs_to_user(device_id));

CREATE POLICY "schedules_update_parent" ON public.schedules
    FOR UPDATE USING (public.device_belongs_to_user(device_id));

CREATE POLICY "schedules_delete_parent" ON public.schedules
    FOR DELETE USING (public.device_belongs_to_user(device_id));

-- =====================
-- notifications_config policies
-- =====================
CREATE POLICY "notif_config_select_own" ON public.notifications_config
    FOR SELECT USING (parent_user_id = auth.uid());

CREATE POLICY "notif_config_insert_own" ON public.notifications_config
    FOR INSERT WITH CHECK (parent_user_id = auth.uid());

CREATE POLICY "notif_config_update_own" ON public.notifications_config
    FOR UPDATE USING (parent_user_id = auth.uid());
