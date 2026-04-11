-- Migration 003: PostgreSQL functions and triggers

-- =====================
-- Auto-create user profile on signup
-- =====================
CREATE OR REPLACE FUNCTION public.handle_new_user()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
    INSERT INTO public.users (id, email, full_name)
    VALUES (
        NEW.id,
        NEW.email,
        COALESCE(NEW.raw_user_meta_data->>'full_name', '')
    )
    ON CONFLICT (id) DO NOTHING;
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS on_auth_user_created ON auth.users;
CREATE TRIGGER on_auth_user_created
    AFTER INSERT ON auth.users
    FOR EACH ROW EXECUTE FUNCTION public.handle_new_user();

-- =====================
-- Atomic credit deduction
-- =====================
-- Checks balance, deducts, logs to credit_ledger and activity_log atomically.
-- Returns: success, new_balance, message
CREATE OR REPLACE FUNCTION public.deduct_credits(
    p_device_id   uuid,
    p_amount      integer,
    p_action_type text,
    p_app_package text DEFAULT NULL,
    p_app_name    text DEFAULT NULL
)
RETURNS TABLE(success boolean, new_balance integer, message text)
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_current_credits integer;
    v_new_balance     integer;
BEGIN
    -- Lock the row to prevent concurrent deductions
    SELECT current_credits INTO v_current_credits
    FROM public.devices
    WHERE id = p_device_id
    FOR UPDATE;

    IF NOT FOUND THEN
        RETURN QUERY SELECT false, 0, 'Device not found'::text;
        RETURN;
    END IF;

    IF v_current_credits < p_amount THEN
        -- Log the exhaustion event
        INSERT INTO public.activity_log (device_id, action_type, app_package, app_name, credits_deducted)
        VALUES (p_device_id, 'credit_exhausted', p_app_package, p_app_name, 0);

        RETURN QUERY SELECT false, v_current_credits, 'Insufficient credits'::text;
        RETURN;
    END IF;

    v_new_balance := v_current_credits - p_amount;

    -- Deduct from device
    UPDATE public.devices
    SET current_credits = v_new_balance,
        last_seen_at    = now(),
        updated_at      = now()
    WHERE id = p_device_id;

    -- Record in credit ledger
    INSERT INTO public.credit_ledger (device_id, change_amount, balance_after, reason, action_type)
    VALUES (p_device_id, -p_amount, v_new_balance, 'action_deduction', p_action_type);

    -- Record in activity log
    INSERT INTO public.activity_log (device_id, action_type, app_package, app_name, credits_deducted)
    VALUES (p_device_id, p_action_type, p_app_package, p_app_name, p_amount);

    RETURN QUERY SELECT true, v_new_balance, 'OK'::text;
END;
$$;

-- =====================
-- Add/remove credits (parent action)
-- =====================
CREATE OR REPLACE FUNCTION public.add_credits(
    p_device_id   uuid,
    p_amount      integer,
    p_reason      text DEFAULT 'parent_add',
    p_parent_note text DEFAULT NULL
)
RETURNS integer
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_new_balance integer;
BEGIN
    UPDATE public.devices
    SET current_credits = GREATEST(0, current_credits + p_amount),
        updated_at      = now()
    WHERE id = p_device_id
    RETURNING current_credits INTO v_new_balance;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'Device not found: %', p_device_id;
    END IF;

    INSERT INTO public.credit_ledger (device_id, change_amount, balance_after, reason, parent_note)
    VALUES (p_device_id, p_amount, v_new_balance, p_reason, p_parent_note);

    RETURN v_new_balance;
END;
$$;

-- =====================
-- Daily credit reset
-- =====================
-- Resets all devices whose credits_reset_time is within the current minute.
-- Designed to be called every minute by a cron job.
CREATE OR REPLACE FUNCTION public.reset_daily_credits()
RETURNS integer
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_count  integer := 0;
    v_device record;
BEGIN
    FOR v_device IN
        SELECT id, daily_credit_budget, current_credits
        FROM public.devices
        WHERE date_trunc('minute', now()::time) = date_trunc('minute', credits_reset_time::time)
    LOOP
        UPDATE public.devices
        SET current_credits = v_device.daily_credit_budget,
            updated_at      = now()
        WHERE id = v_device.id;

        INSERT INTO public.credit_ledger (device_id, change_amount, balance_after, reason)
        VALUES (
            v_device.id,
            v_device.daily_credit_budget - v_device.current_credits,
            v_device.daily_credit_budget,
            'daily_reset'
        );

        v_count := v_count + 1;
    END LOOP;

    RETURN v_count;
END;
$$;

-- =====================
-- Daily usage summary
-- =====================
CREATE OR REPLACE FUNCTION public.get_daily_summary(
    p_device_id uuid,
    p_date      date DEFAULT CURRENT_DATE
)
RETURNS TABLE(
    total_credits_used      integer,
    top_apps                jsonb,
    total_action_count      integer,
    blocked_attempts        integer
)
LANGUAGE plpgsql
SECURITY DEFINER
STABLE
SET search_path = public
AS $$
BEGIN
    RETURN QUERY
    SELECT
        COALESCE(SUM(al.credits_deducted), 0)::integer            AS total_credits_used,
        COALESCE(
            jsonb_agg(
                jsonb_build_object('app', al.app_name, 'credits', sub.credits)
                ORDER BY sub.credits DESC
            ) FILTER (WHERE al.app_name IS NOT NULL),
            '[]'::jsonb
        )                                                           AS top_apps,
        COUNT(*)::integer                                           AS total_action_count,
        COUNT(*) FILTER (WHERE al.action_type = 'app_blocked')::integer AS blocked_attempts
    FROM public.activity_log al
    LEFT JOIN LATERAL (
        SELECT SUM(credits_deducted) AS credits
        FROM public.activity_log
        WHERE device_id = p_device_id
          AND app_name = al.app_name
          AND created_at::date = p_date
    ) sub ON true
    WHERE al.device_id = p_device_id
      AND al.created_at::date = p_date;
END;
$$;
