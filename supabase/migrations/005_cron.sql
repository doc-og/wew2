-- Migration 005: pg_cron for scheduled jobs
-- IMPORTANT: Before running this migration, enable pg_cron in Supabase:
--   Dashboard → Database → Extensions → search "pg_cron" → enable it
--
-- Then run this in the Supabase SQL editor:
--   SELECT cron.schedule(
--       'wew-daily-credit-reset',
--       '* * * * *',
--       $$SELECT public.reset_daily_credits();$$
--   );
--
-- This migration is intentionally a no-op when pg_cron is not yet enabled.
-- The reset_daily_credits() function (from 003_functions.sql) is already deployed
-- and will be called once the cron job is registered manually.

DO $$
BEGIN
  IF EXISTS (
    SELECT 1 FROM pg_extension WHERE extname = 'pg_cron'
  ) THEN
    PERFORM cron.schedule(
      'wew-daily-credit-reset',
      '* * * * *',
      'SELECT public.reset_daily_credits();'
    );
    RAISE NOTICE 'pg_cron job scheduled successfully.';
  ELSE
    RAISE NOTICE 'pg_cron not enabled — skipping cron schedule. Enable it in Dashboard → Database → Extensions, then re-run this migration.';
  END IF;
END;
$$;
