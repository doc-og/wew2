-- Migration 015: Daily token reset (timezone-aware) + pg_cron schedule
--
-- Why:
--   Migration 007 introduced reset_daily_tokens() but it resets every device
--   unconditionally, with no time-of-day or timezone check. Migration 005 only
--   schedules the legacy reset_daily_credits() function, so current_tokens was
--   never reset and parents had to bump tokens manually.
--
-- This migration:
--   1. Replaces reset_daily_tokens() with a per-device, timezone-aware version
--      that fires within the current minute matching devices.tokens_reset_time
--      in the device's local timezone, prefers token_budgets.daily_limit when
--      present, and writes a token_ledger entry for traceability.
--   2. Is idempotent within a day: if a 'daily_reset' ledger row already exists
--      since the device's most recent local reset moment, the device is skipped.
--   3. Schedules a pg_cron job (wew_daily_token_reset, * * * * *) when the
--      pg_cron extension is enabled. Safe no-op otherwise.
--
-- Compatible with:
--   - Edge function supabase/functions/daily-credit-reset (already calls
--     reset_daily_tokens via RPC; the new return value of integer is ignored).
--   - The legacy reset_daily_credits cron job from migration 005 is left
--     untouched so the legacy credits column keeps working for the web
--     dashboard until it migrates to tokens.

-- ─────────────────────────────────────────────────────────────
-- 1. New TZ-aware reset_daily_tokens()
--
--    For each device, computes the most recent local "reset boundary"
--    (today's tokens_reset_time if that has already passed in the
--    device's timezone, else yesterday's). If no 'daily_reset' ledger
--    row exists since that boundary, the device is reset and a ledger
--    row is inserted. This makes the function:
--      - safe to call every minute (only fires when a reset is owed)
--      - idempotent within a day (one reset per local day)
--      - resilient to missed cron ticks (catches up at the next call)
--
--    CREATE OR REPLACE cannot change a function's return type, and
--    the version in migration 007 returns void while this one returns
--    integer (count of devices reset). Drop first, then create.
-- ─────────────────────────────────────────────────────────────
DROP FUNCTION IF EXISTS public.reset_daily_tokens();

CREATE OR REPLACE FUNCTION public.reset_daily_tokens()
RETURNS integer
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    r                       RECORD;
    v_tz                    text;
    v_local_ts              timestamp;     -- "wall clock" in device tz
    v_today_reset_utc       timestamptz;
    v_last_reset_boundary   timestamptz;
    v_budget                integer;
    v_new_balance           integer;
    v_count                 integer := 0;
BEGIN
    FOR r IN
        SELECT
            d.id,
            d.timezone,
            d.tokens_reset_time,
            d.daily_token_budget,
            d.current_tokens,
            tb.daily_limit AS budget_override
        FROM public.devices d
        LEFT JOIN public.token_budgets tb ON tb.device_id = d.id
    LOOP
        v_tz := COALESCE(NULLIF(btrim(r.timezone), ''), 'UTC');

        BEGIN
            v_local_ts := (now() AT TIME ZONE v_tz);
        EXCEPTION WHEN invalid_parameter_value THEN
            -- bad timezone string on a device row: fall back to UTC
            v_local_ts := (now() AT TIME ZONE 'UTC');
            v_tz := 'UTC';
        END;

        v_today_reset_utc := (v_local_ts::date + r.tokens_reset_time) AT TIME ZONE v_tz;

        IF v_today_reset_utc > now() THEN
            v_last_reset_boundary :=
                ((v_local_ts::date - 1) + r.tokens_reset_time) AT TIME ZONE v_tz;
        ELSE
            v_last_reset_boundary := v_today_reset_utc;
        END IF;

        IF EXISTS (
            SELECT 1
            FROM public.token_ledger tl
            WHERE tl.device_id   = r.id
              AND tl.action_type = 'daily_reset'
              AND tl.created_at >= v_last_reset_boundary
        ) THEN
            CONTINUE;
        END IF;

        v_budget := COALESCE(r.budget_override, r.daily_token_budget);
        IF v_budget IS NULL OR v_budget <= 0 THEN
            CONTINUE;
        END IF;

        UPDATE public.devices
        SET current_tokens = v_budget,
            updated_at     = now()
        WHERE id = r.id
        RETURNING current_tokens INTO v_new_balance;

        INSERT INTO public.token_ledger (
            device_id,
            action_type,
            tokens_consumed,
            balance_after,
            context_metadata
        ) VALUES (
            r.id,
            'daily_reset',
            0,
            v_new_balance,
            jsonb_build_object(
                'source',         'cron',
                'tz',             v_tz,
                'budget',         v_budget,
                'prev_balance',   r.current_tokens,
                'reset_boundary', to_char(v_last_reset_boundary AT TIME ZONE v_tz, 'YYYY-MM-DD"T"HH24:MI:SS')
            )
        );

        v_count := v_count + 1;
    END LOOP;

    RETURN v_count;
END;
$$;

-- ─────────────────────────────────────────────────────────────
-- 2. Schedule pg_cron job (idempotent)
-- ─────────────────────────────────────────────────────────────
DO $$
DECLARE
    j RECORD;
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_extension WHERE extname = 'pg_cron') THEN
        RAISE NOTICE 'pg_cron not enabled — token reset cron not scheduled. Enable pg_cron in the dashboard, then re-run this migration.';
        RETURN;
    END IF;

    FOR j IN SELECT jobid FROM cron.job WHERE jobname = 'wew_daily_token_reset'
    LOOP
        PERFORM cron.unschedule(j.jobid);
    END LOOP;

    PERFORM cron.schedule(
        'wew_daily_token_reset',
        '* * * * *',
        'SELECT public.reset_daily_tokens();'
    );

    RAISE NOTICE 'wew_daily_token_reset scheduled (every minute).';
EXCEPTION
    WHEN undefined_table THEN
        RAISE NOTICE 'pg_cron tables missing — schedule skipped.';
    WHEN OTHERS THEN
        RAISE NOTICE 'pg_cron schedule skipped: %', SQLERRM;
END $$;
