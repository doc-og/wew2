-- Child launcher (anon key + device UUID) must upsert app inventory for the parent dashboard.
-- Mirrors the capability-style policies on public.devices (migration 008).

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_policies
        WHERE schemaname = 'public' AND tablename = 'apps' AND policyname = 'apps_select_capability'
    ) THEN
        CREATE POLICY "apps_select_capability"
            ON public.apps FOR SELECT
            USING (true);
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_policies
        WHERE schemaname = 'public' AND tablename = 'apps' AND policyname = 'apps_insert_capability'
    ) THEN
        CREATE POLICY "apps_insert_capability"
            ON public.apps FOR INSERT
            WITH CHECK (true);
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_policies
        WHERE schemaname = 'public' AND tablename = 'apps' AND policyname = 'apps_update_capability'
    ) THEN
        CREATE POLICY "apps_update_capability"
            ON public.apps FOR UPDATE
            USING (true)
            WITH CHECK (true);
    END IF;
END $$;
