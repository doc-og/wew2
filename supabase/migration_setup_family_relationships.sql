-- Migration: Set up family relationships between seano (guardian owner), amo (guardian), and roseo (child)
-- Run this in your Supabase SQL editor

-- First, clear any existing roles to start fresh
DELETE FROM roles WHERE user_id IN (
  SELECT id FROM users WHERE username IN ('seano', 'amo', 'roseo')
);

-- Set up the role structure
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
        RAISE NOTICE 'One or more users not found, skipping family setup';
        RETURN;
    END IF;

    -- Set up roles with proper family relationships
    -- seano is Guardian Owner
    INSERT INTO roles (user_id, role, target_user_id, created_at) VALUES
    (seano_id, 'guardian_owner', roseo_id, NOW()), -- seano is guardian owner of roseo
    (seano_id, 'family_member', amo_id, NOW()),     -- seano has family member amo
    (seano_id, 'family_member', roseo_id, NOW());   -- seano has family member roseo
    
    -- amo is Guardian  
    INSERT INTO roles (user_id, role, target_user_id, created_at) VALUES
    (amo_id, 'guardian', roseo_id, NOW()),          -- amo is guardian of roseo
    (amo_id, 'family_member', seano_id, NOW()),     -- amo has family member seano
    (amo_id, 'family_member', roseo_id, NOW());     -- amo has family member roseo
    
    -- roseo is Child
    INSERT INTO roles (user_id, role, target_user_id, created_at) VALUES
    (roseo_id, 'child', NULL, NOW()),               -- roseo is a child
    (roseo_id, 'family_member', seano_id, NOW()),   -- roseo has family member seano
    (roseo_id, 'family_member', amo_id, NOW());     -- roseo has family member amo

    RAISE NOTICE 'Family relationships set up successfully';
END $$;

-- Update the profiles to have proper default messaging preferences based on roles
UPDATE profiles 
SET preferred_messaging = 'sms'
WHERE user_id IN (
  SELECT r.user_id 
  FROM roles r 
  WHERE r.role IN ('guardian', 'guardian_owner')
);

-- Children should default to chat (they don't set messaging preferences, they receive based on guardian preferences)
UPDATE profiles 
SET preferred_messaging = 'chat'
WHERE user_id IN (
  SELECT r.user_id 
  FROM roles r 
  WHERE r.role = 'child'
);

-- Update the get_messaging_context function to use the new family relationship structure
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
    -- Check if recipient is a guardian (has guardian or guardian_owner role)
    IF EXISTS (
      SELECT 1 FROM roles r 
      WHERE r.user_id = recipient_user_id 
      AND r.role IN ('guardian', 'guardian_owner')
    ) THEN
      -- Recipient is a guardian, use their messaging preference
      SELECT 'guardian', COALESCE(p.preferred_messaging, 'sms')
      INTO recipient_type, messaging_method
      FROM profiles p 
      WHERE p.user_id = recipient_user_id;
    ELSE
      -- Recipient is a regular user (could be child or other user), always use chat
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

-- Create a helper function to get family members for a user (for the profile display)
CREATE OR REPLACE FUNCTION get_family_members(user_id UUID)
RETURNS TABLE(
  family_member_id UUID,
  family_member_username TEXT,
  family_member_role TEXT,
  phone_number TEXT
) 
LANGUAGE plpgsql
AS $$
BEGIN
  RETURN QUERY
  SELECT 
    u.id as family_member_id,
    u.username as family_member_username,
    CASE 
      WHEN EXISTS (SELECT 1 FROM roles r2 WHERE r2.user_id = u.id AND r2.role = 'guardian_owner') THEN 'guardian_owner'
      WHEN EXISTS (SELECT 1 FROM roles r2 WHERE r2.user_id = u.id AND r2.role = 'guardian') THEN 'guardian'  
      WHEN EXISTS (SELECT 1 FROM roles r2 WHERE r2.user_id = u.id AND r2.role = 'child') THEN 'child'
      ELSE 'user'
    END as family_member_role,
    p.phone_number
  FROM roles r
  JOIN users u ON u.id = r.target_user_id  
  LEFT JOIN profiles p ON p.user_id = u.id
  WHERE r.user_id = get_family_members.user_id 
  AND r.role = 'family_member'
  ORDER BY 
    CASE 
      WHEN EXISTS (SELECT 1 FROM roles r2 WHERE r2.user_id = u.id AND r2.role = 'guardian_owner') THEN 1
      WHEN EXISTS (SELECT 1 FROM roles r2 WHERE r2.user_id = u.id AND r2.role = 'guardian') THEN 2
      ELSE 3
    END,
    u.username;
END;
$$;

-- Grant permissions
GRANT EXECUTE ON FUNCTION get_family_members(UUID) TO authenticated;

-- Add comments
COMMENT ON FUNCTION get_family_members(UUID) IS 'Returns family members for a given user ID with their roles and contact info';

-- Verify the setup
DO $$
DECLARE 
    seano_id UUID;
    roseo_id UUID;
    amo_id UUID;
    role_count INTEGER;
BEGIN
    -- Get user IDs
    SELECT id INTO seano_id FROM users WHERE username = 'seano';
    SELECT id INTO roseo_id FROM users WHERE username = 'roseo';  
    SELECT id INTO amo_id FROM users WHERE username = 'amo';
    
    -- Check roles were created
    SELECT COUNT(*) INTO role_count FROM roles WHERE user_id IN (seano_id, roseo_id, amo_id);
    
    RAISE NOTICE 'Setup verification:';
    RAISE NOTICE '- seano ID: %', seano_id;
    RAISE NOTICE '- roseo ID: %', roseo_id;
    RAISE NOTICE '- amo ID: %', amo_id;
    RAISE NOTICE '- Total roles created: %', role_count;
    
    -- Show role breakdown
    FOR role_count IN 
        SELECT COUNT(*) 
        FROM roles r 
        JOIN users u ON u.id = r.user_id 
        WHERE u.username = 'seano'
    LOOP
        RAISE NOTICE '- seano has % roles', role_count;
    END LOOP;
    
    FOR role_count IN 
        SELECT COUNT(*) 
        FROM roles r 
        JOIN users u ON u.id = r.user_id 
        WHERE u.username = 'amo'  
    LOOP
        RAISE NOTICE '- amo has % roles', role_count;
    END LOOP;
    
    FOR role_count IN 
        SELECT COUNT(*) 
        FROM roles r 
        JOIN users u ON u.id = r.user_id 
        WHERE u.username = 'roseo'
    LOOP
        RAISE NOTICE '- roseo has % roles', role_count;
    END LOOP;
END $$;