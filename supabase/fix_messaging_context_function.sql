-- Fix the messaging context function to work with current schema
-- Run this in your Supabase SQL editor

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
    -- Check if recipient is a guardian (simplified - any guardian role)
    IF EXISTS (
      SELECT 1 FROM roles r 
      WHERE r.user_id = recipient_user_id 
      AND r.role IN ('guardian', 'guardian_owner')
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