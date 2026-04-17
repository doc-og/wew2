-- Migration 011: per-app notification policy for child launcher enforcement
-- Keep app approval and notification approval in the same source-of-truth table.

ALTER TABLE public.apps
    ADD COLUMN IF NOT EXISTS notifications_enabled boolean NOT NULL DEFAULT false;

-- Preserve the current parent expectation that already-approved apps continue
-- showing notifications after this migration lands. Newly added apps inherit
-- the safer default of notifications off until the parent enables them.
UPDATE public.apps
SET notifications_enabled = true
WHERE is_whitelisted = true
  AND notifications_enabled = false;
