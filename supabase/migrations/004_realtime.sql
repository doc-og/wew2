-- Migration 004: Enable Supabase realtime on key tables

-- Enable realtime publication for live dashboard and activity feed
ALTER PUBLICATION supabase_realtime ADD TABLE public.devices;
ALTER PUBLICATION supabase_realtime ADD TABLE public.activity_log;
ALTER PUBLICATION supabase_realtime ADD TABLE public.location_log;
ALTER PUBLICATION supabase_realtime ADD TABLE public.credit_ledger;
