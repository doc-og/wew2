-- Migration: Add messaging preferences for guardians
-- Run this in your Supabase SQL editor

-- Add messaging preference to profiles table
ALTER TABLE profiles ADD COLUMN IF NOT EXISTS preferred_messaging VARCHAR(10) DEFAULT 'sms' CHECK (preferred_messaging IN ('sms', 'chat'));

-- Update existing guardian users to default to SMS preference
UPDATE profiles 
SET preferred_messaging = 'sms'
WHERE user_id IN (
  SELECT user_id 
  FROM roles 
  WHERE role IN ('guardian', 'guardian_owner')
);

-- Add index for performance on messaging preference queries
CREATE INDEX IF NOT EXISTS idx_profiles_preferred_messaging ON profiles(preferred_messaging);

-- Add comment for documentation
COMMENT ON COLUMN profiles.preferred_messaging IS 'Guardian preferred communication method: sms (device native) or chat (in-app)';

-- Update the view or create a helper function to get user messaging context
CREATE OR REPLACE FUNCTION get_messaging_context(sender_user_id UUID, recipient_phone TEXT)
RETURNS TABLE(
  recipient_user_id UUID,
  recipient_type TEXT, -- 'guardian', 'user', 'contact_only'
  messaging_method TEXT, -- 'chat', 'sms'
  recipient_name TEXT
) 
LANGUAGE plpgsql
AS $$
BEGIN
  -- First, try to find if recipient phone matches a user
  SELECT u.id, u.username
  INTO recipient_user_id, recipient_name
  FROM users u 
  JOIN profiles p ON u.id = p.user_id 
  WHERE p.phone_number = recipient_phone;
  
  IF recipient_user_id IS NOT NULL THEN
    -- Check if recipient is a guardian of the sender
    IF EXISTS (
      SELECT 1 FROM roles r 
      WHERE r.user_id = recipient_user_id 
      AND r.role IN ('guardian', 'guardian_owner')
      AND (r.target_user_id = sender_user_id OR r.target_user_id IS NULL)
    ) THEN
      -- Recipient is a guardian, use their preference
      SELECT 'guardian', COALESCE(p.preferred_messaging, 'sms')
      INTO recipient_type, messaging_method
      FROM profiles p 
      WHERE p.user_id = recipient_user_id;
    ELSE
      -- Recipient is a regular user, always use chat
      SELECT 'user', 'chat' 
      INTO recipient_type, messaging_method;
    END IF;
  ELSE
    -- Recipient is not a user, must be contact-only
    SELECT NULL, 'contact_only', 'sms', recipient_phone
    INTO recipient_user_id, recipient_type, messaging_method, recipient_name;
  END IF;
  
  RETURN QUERY SELECT recipient_user_id, recipient_type, messaging_method, recipient_name;
END;
$$;

-- Grant necessary permissions
GRANT EXECUTE ON FUNCTION get_messaging_context(UUID, TEXT) TO authenticated;