-- Migration: Add 'video' type to messages.type constraint
-- This allows video messages to be stored in the database

-- Drop the existing check constraint
ALTER TABLE messages DROP CONSTRAINT messages_type_check;

-- Add the new check constraint that includes 'video'
ALTER TABLE messages ADD CONSTRAINT messages_type_check CHECK (type IN ('text', 'image', 'video'));
