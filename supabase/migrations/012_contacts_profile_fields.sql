-- Structured contact fields used by parent app and child compose search.
ALTER TABLE public.contacts ADD COLUMN IF NOT EXISTS first_name text;
ALTER TABLE public.contacts ADD COLUMN IF NOT EXISTS last_name text;
ALTER TABLE public.contacts ADD COLUMN IF NOT EXISTS nickname text;
ALTER TABLE public.contacts ADD COLUMN IF NOT EXISTS relationship text;
ALTER TABLE public.contacts ADD COLUMN IF NOT EXISTS status text DEFAULT 'approved';
