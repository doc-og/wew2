-- Add url_previews column to messages table to store JSON data for URL previews
ALTER TABLE messages ADD COLUMN IF NOT EXISTS url_previews JSONB DEFAULT '[]'::jsonb;

-- Update the type constraint to include video type (if not already updated)
ALTER TABLE messages DROP CONSTRAINT IF EXISTS messages_type_check;
ALTER TABLE messages ADD CONSTRAINT messages_type_check CHECK (type IN ('text', 'image', 'video'));

-- Add index for url_previews JSONB column for better performance
CREATE INDEX IF NOT EXISTS messages_url_previews_idx ON messages USING gin(url_previews);
