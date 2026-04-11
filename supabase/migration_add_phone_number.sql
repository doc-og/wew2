-- Migration: Add phone number field to profiles
-- Run this in your Supabase SQL editor

-- Add phone number field to profiles table
ALTER TABLE profiles ADD COLUMN IF NOT EXISTS phone_number TEXT;

-- Add some test phone numbers for our users
UPDATE profiles 
SET phone_number = CASE 
  WHEN EXISTS (SELECT 1 FROM users WHERE users.id = profiles.user_id AND username = 'seano') THEN '+14155551001'
  WHEN EXISTS (SELECT 1 FROM users WHERE users.id = profiles.user_id AND username = 'roseo') THEN '+14155551002' 
  WHEN EXISTS (SELECT 1 FROM users WHERE users.id = profiles.user_id AND username = 'amo') THEN '+14155551003'
  ELSE phone_number
END;

-- Add index for phone number lookups
CREATE INDEX IF NOT EXISTS idx_profiles_phone_number ON profiles(phone_number);

-- Add comment
COMMENT ON COLUMN profiles.phone_number IS 'User''s mobile phone number in E.164 format';

SELECT 'Phone number field added successfully!' as result;