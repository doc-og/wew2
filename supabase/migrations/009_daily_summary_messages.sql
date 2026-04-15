-- Migration 009: Daily usage summary messages (system), message body, pg_cron hook

-- ── messages: body + idempotency key + expanded message_type
ALTER TABLE public.messages
    ADD COLUMN IF NOT EXISTS body text,
    ADD COLUMN IF NOT EXISTS summary_for_date date;

ALTER TABLE public.messages DROP CONSTRAINT IF EXISTS messages_message_type_check;
ALTER TABLE public.messages ADD CONSTRAINT messages_message_type_check
    CHECK (message_type IN (
        'text',
        'mms_image',
        'mms_video',
        'mms_audio',
        'location',
        'call_summary',
        'daily_summary'
    ));

CREATE UNIQUE INDEX IF NOT EXISTS idx_messages_daily_summary_once
    ON public.messages (device_id, summary_for_date)
    WHERE message_type = 'daily_summary' AND summary_for_date IS NOT NULL;

-- Child launcher merges server system messages into parent SMS thread
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_policies
        WHERE schemaname = 'public' AND tablename = 'messages' AND policyname = 'device_reads_messages'
    ) THEN
        CREATE POLICY "device_reads_messages"
            ON public.messages FOR SELECT
            USING (true);
    END IF;
END $$;

-- ── Generate end-of-day summaries at each parent's notifications_config.daily_summary_time (local device TZ)
CREATE OR REPLACE FUNCTION public.generate_daily_usage_summaries()
RETURNS integer
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    r RECORD;
    v_local_ts timestamp without time zone;
    v_yesterday date;
    v_win_start timestamptz;
    v_win_end timestamptz;
    v_top_action text;
    v_top_tokens bigint;
    v_second_action text;
    v_top_duration_app text;
    v_body text;
    v_inserted int := 0;
BEGIN
    FOR r IN
        SELECT
            d.id AS device_id,
            d.parent_sms_thread_id,
            d.tokens_reset_time,
            d.timezone AS tz,
            nc.daily_summary_time,
            nc.daily_summary_enabled
        FROM public.devices d
        INNER JOIN public.notifications_config nc
            ON nc.parent_user_id = d.parent_user_id
        WHERE nc.daily_summary_enabled = true
          AND d.parent_sms_thread_id IS NOT NULL
          AND btrim(d.parent_sms_thread_id) <> ''
    LOOP
        v_top_action := NULL;
        v_top_tokens := NULL;
        v_second_action := NULL;
        v_top_duration_app := NULL;

        v_local_ts := (now() AT TIME ZONE r.tz);

        -- Fire within a 2-minute window starting at daily_summary_time (local)
        IF NOT (
            v_local_ts::time >= r.daily_summary_time
            AND v_local_ts::time < r.daily_summary_time + interval '2 minutes'
        ) THEN
            CONTINUE;
        END IF;

        -- "Phone day" for the calendar day that just ended in local TZ
        v_yesterday := (v_local_ts::date - 1);

        v_win_start := (v_yesterday + r.tokens_reset_time) AT TIME ZONE r.tz;
        v_win_end := ((v_yesterday + 1) + r.tokens_reset_time) AT TIME ZONE r.tz;

        IF EXISTS (
            SELECT 1 FROM public.messages m
            WHERE m.device_id = r.device_id
              AND m.message_type = 'daily_summary'
              AND m.summary_for_date = v_yesterday
        ) THEN
            CONTINUE;
        END IF;

        SELECT t.action_type, SUM(t.tokens_consumed)::bigint
 INTO v_top_action, v_top_tokens
        FROM public.token_ledger t
        WHERE t.device_id = r.device_id
          AND t.created_at >= v_win_start
          AND t.created_at < v_win_end
          AND t.tokens_consumed > 0
        GROUP BY t.action_type
        ORDER BY SUM(t.tokens_consumed) DESC
        LIMIT 1;

        SELECT t.action_type
        INTO v_second_action
        FROM public.token_ledger t
        WHERE t.device_id = r.device_id
          AND t.created_at >= v_win_start
          AND t.created_at < v_win_end
          AND t.tokens_consumed > 0
        GROUP BY t.action_type
        ORDER BY SUM(t.tokens_consumed) DESC
        OFFSET 1
        LIMIT 1;

        SELECT s.lbl INTO v_top_duration_app
        FROM (
            SELECT
                COALESCE(
                    t.context_metadata->>'app_name',
                    t.context_metadata->>'app_package',
                    'apps'
                ) AS lbl,
                SUM(COALESCE((t.context_metadata->>'duration_minutes')::numeric, 0)) AS mins
            FROM public.token_ledger t
            WHERE t.device_id = r.device_id
              AND t.created_at >= v_win_start
              AND t.created_at < v_win_end
              AND (t.context_metadata->>'duration_minutes') IS NOT NULL
            GROUP BY 1
            ORDER BY mins DESC
            LIMIT 1
        ) s;

        IF v_top_action IS NULL THEN
            v_body :=
                'Hi! Yesterday was a light day on your phone — almost no tokens were used. '
                || 'That usually means more time for other things. Nice work.';
        ELSE
            v_body := format(
                E'Hi! Here is how yesterday went: your biggest token use was %s (%s tokens). ',
                replace(replace(v_top_action, '_', ' '), 'sms sent', 'texting'),
                v_top_tokens
            );
            IF v_second_action IS NOT NULL THEN
                v_body := v_body || format(
                    E'Next up was %s. ',
                    replace(v_second_action, '_', ' ')
                );
            END IF;
            IF v_top_duration_app IS NOT NULL AND v_top_duration_app <> '' THEN
                v_body := v_body || format(
                    E'Where you spent the most tracked time was around %s.',
                    v_top_duration_app
                );
            ELSE
                v_body := v_body || E'Keep balancing screen time with the rest of your day!';
            END IF;
        END IF;

        INSERT INTO public.messages (
            device_id,
            thread_id,
            sender_address,
            sender_type,
            message_type,
            has_media,
            tokens_consumed,
            body,
            summary_for_date
        ) VALUES (
            r.device_id,
            r.parent_sms_thread_id,
            'wew',
            'system',
            'daily_summary',
            false,
            0,
            v_body,
            v_yesterday
        );

        v_inserted := v_inserted + 1;
    END LOOP;

    RETURN v_inserted;
END;
$$;

-- Optional: schedule with pg_cron (enable extension in dashboard first)
DO $$
DECLARE j RECORD;
BEGIN
    IF EXISTS (SELECT 1 FROM pg_extension WHERE extname = 'pg_cron') THEN
        FOR j IN SELECT jobid FROM cron.job WHERE jobname = 'wew_daily_usage_summaries'
        LOOP
            PERFORM cron.unschedule(j.jobid);
        END LOOP;
        PERFORM cron.schedule(
            'wew_daily_usage_summaries',
            '* * * * *',
            'SELECT public.generate_daily_usage_summaries()'
        );
    END IF;
EXCEPTION
    WHEN undefined_table THEN
        RAISE NOTICE 'pg_cron not available';
    WHEN OTHERS THEN
        RAISE NOTICE 'pg_cron schedule skipped: %', SQLERRM;
END $$;
