-- Child launcher reports SMS readiness so the parent app can show status remotely.
ALTER TABLE public.devices
    ADD COLUMN IF NOT EXISTS child_default_sms_app boolean NOT NULL DEFAULT false,
    ADD COLUMN IF NOT EXISTS child_sms_permissions_ok boolean NOT NULL DEFAULT false,
    ADD COLUMN IF NOT EXISTS child_messaging_health_at timestamptz;

COMMENT ON COLUMN public.devices.child_default_sms_app IS 'True when WeW launcher holds default SMS role / package on the child device (last sync).';
COMMENT ON COLUMN public.devices.child_sms_permissions_ok IS 'True when core SMS runtime perms granted on child (last sync).';
COMMENT ON COLUMN public.devices.child_messaging_health_at IS 'When messaging health was last pushed from the child launcher.';
