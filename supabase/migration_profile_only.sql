-- Migration: Add profile fields and set up guardian relationships (SIMPLIFIED)
-- Run this in your Supabase SQL editor

-- Add profile fields to existing profiles table
ALTER TABLE profiles ADD COLUMN IF NOT EXISTS first_name TEXT;
ALTER TABLE profiles ADD COLUMN IF NOT EXISTS last_name TEXT;
ALTER TABLE profiles ADD COLUMN IF NOT EXISTS preferred_name TEXT;
ALTER TABLE profiles ADD COLUMN IF NOT EXISTS profile_image_url TEXT;

-- Update existing users with default profile data
INSERT INTO profiles (user_id, first_name, last_name, sms_daily_quota_minutes)
SELECT 
    u.id,
    CASE 
        WHEN u.username = 'seano' THEN 'Sean'
        WHEN u.username = 'roseo' THEN 'Rose'
        WHEN u.username = 'amo' THEN 'Amy'
        ELSE INITCAP(u.username)
    END,
    CASE 
        WHEN u.username = 'seano' THEN 'O''Grady'
        WHEN u.username = 'roseo' THEN 'O''Grady'
        WHEN u.username = 'amo' THEN 'Smith'
        ELSE 'User'
    END,
    60 -- default SMS quota
FROM users u
WHERE u.id NOT IN (SELECT user_id FROM profiles WHERE user_id IS NOT NULL)
ON CONFLICT (user_id) DO NOTHING;

-- Set up approved contacts relationships:
-- roseo has approved contacts seano and amo
-- seano has approved contacts amo and roseo
-- amo has approved contacts roseo and seano

DO $$
DECLARE 
    seano_id UUID;
    roseo_id UUID;
    amo_id UUID;
BEGIN
    -- Get user IDs
    SELECT id INTO seano_id FROM users WHERE username = 'seano';
    SELECT id INTO roseo_id FROM users WHERE username = 'roseo';  
    SELECT id INTO amo_id FROM users WHERE username = 'amo';
    
    -- Skip if any user is not found
    IF seano_id IS NULL OR roseo_id IS NULL OR amo_id IS NULL THEN
        RAISE NOTICE 'One or more users not found, skipping guardian setup';
        RETURN;
    END IF;

    -- roseo has approved contacts seano and amo
    INSERT INTO approved_contacts (user_id, e164, status, created_at) VALUES
    (roseo_id, '+1seano', 'approved', NOW()),
    (roseo_id, '+1amo', 'approved', NOW())
    ON CONFLICT (user_id, e164) DO UPDATE SET 
        status = 'approved';

    -- seano has approved contacts amo and roseo
    INSERT INTO approved_contacts (user_id, e164, status, created_at) VALUES
    (seano_id, '+1amo', 'approved', NOW()),
    (seano_id, '+1roseo', 'approved', NOW())
    ON CONFLICT (user_id, e164) DO UPDATE SET 
        status = 'approved';

    -- amo has approved contacts roseo and seano
    INSERT INTO approved_contacts (user_id, e164, status, created_at) VALUES
    (amo_id, '+1roseo', 'approved', NOW()),
    (amo_id, '+1seano', 'approved', NOW())
    ON CONFLICT (user_id, e164) DO UPDATE SET 
        status = 'approved';

    RAISE NOTICE 'Approved contacts relationships set up successfully';
END $$;

-- Create indexes for better performance
CREATE INDEX IF NOT EXISTS idx_profiles_user_id ON profiles(user_id);
CREATE INDEX IF NOT EXISTS idx_approved_contacts_user_id ON approved_contacts(user_id);
CREATE INDEX IF NOT EXISTS idx_approved_contacts_status ON approved_contacts(status);

-- Add comments for documentation
COMMENT ON COLUMN profiles.first_name IS 'User''s first name';
COMMENT ON COLUMN profiles.last_name IS 'User''s last name';  
COMMENT ON COLUMN profiles.preferred_name IS 'User''s preferred display name';
COMMENT ON COLUMN profiles.profile_image_url IS 'URL to user''s profile image';

-- Show success message
SELECT 'Profile migration completed successfully!' as result;