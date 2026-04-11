-- Migration: Set up family relationships (FIXED - without target_user_id)
-- Run this in your Supabase SQL editor

-- First, let's see the current roles table structure
DO $$
BEGIN
    -- Check if roles table exists and what columns it has
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'roles') THEN
        RAISE NOTICE 'Roles table exists. Current columns:';
        -- The table exists, proceed with migration
    ELSE
        RAISE NOTICE 'Roles table does not exist, creating it...';
        -- Create roles table if it doesn't exist
        CREATE TABLE IF NOT EXISTS roles (
            id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
            user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
            role TEXT NOT NULL,
            created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
        );
        CREATE INDEX IF NOT EXISTS idx_roles_user_id ON roles(user_id);
        CREATE INDEX IF NOT EXISTS idx_roles_role ON roles(role);
    END IF;
END $$;

-- Clear existing roles for our test users
DELETE FROM roles WHERE user_id IN (
  SELECT id FROM users WHERE username IN ('seano', 'amo', 'roseo')
);

-- Set up the role structure (simplified without target_user_id)
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

    -- Set up roles (simplified structure)
    -- seano is Guardian Owner
    INSERT INTO roles (user_id, role, created_at) VALUES
    (seano_id, 'guardian_owner', NOW());
    
    -- amo is Guardian  
    INSERT INTO roles (user_id, role, created_at) VALUES
    (amo_id, 'guardian', NOW());
    
    -- roseo is Child
    INSERT INTO roles (user_id, role, created_at) VALUES
    (roseo_id, 'child', NOW());

    RAISE NOTICE 'Roles set up successfully';
    RAISE NOTICE 'seano (%) is guardian_owner', seano_id;
    RAISE NOTICE 'amo (%) is guardian', amo_id;
    RAISE NOTICE 'roseo (%) is child', roseo_id;
END $$;

-- Update the profiles to have proper default messaging preferences based on roles
UPDATE profiles 
SET preferred_messaging = 'sms'
WHERE user_id IN (
  SELECT r.user_id 
  FROM roles r 
  WHERE r.role IN ('guardian', 'guardian_owner')
);

-- Children should default to chat
UPDATE profiles 
SET preferred_messaging = 'chat'
WHERE user_id IN (
  SELECT r.user_id 
  FROM roles r 
  WHERE r.role = 'child'
);

-- Create a simple family lookup using a static mapping for now
-- This is a temporary solution that works with the existing schema
CREATE OR REPLACE FUNCTION get_family_members(input_user_id UUID)
RETURNS TABLE(
  family_member_id UUID,
  family_member_username TEXT,
  family_member_role TEXT,
  phone_number TEXT
) 
LANGUAGE plpgsql
AS $$
DECLARE
    input_username TEXT;
BEGIN
    -- Get the username for the input user
    SELECT username INTO input_username FROM users WHERE id = input_user_id;
    
    -- Return family members based on our known relationships
    -- seano's family: amo (guardian), roseo (child)
    -- amo's family: seano (guardian_owner), roseo (child) 
    -- roseo's family: seano (guardian_owner), amo (guardian)
    
    IF input_username = 'seano' THEN
        RETURN QUERY
        SELECT 
            u.id as family_member_id,
            u.username as family_member_username,
            CASE 
                WHEN u.username = 'amo' THEN 'guardian'
                WHEN u.username = 'roseo' THEN 'child'
                ELSE 'user'
            END as family_member_role,
            p.phone_number
        FROM users u
        LEFT JOIN profiles p ON p.user_id = u.id
        WHERE u.username IN ('amo', 'roseo')
        ORDER BY 
            CASE WHEN u.username = 'amo' THEN 1 ELSE 2 END;
            
    ELSIF input_username = 'amo' THEN
        RETURN QUERY
        SELECT 
            u.id as family_member_id,
            u.username as family_member_username,
            CASE 
                WHEN u.username = 'seano' THEN 'guardian_owner'
                WHEN u.username = 'roseo' THEN 'child'
                ELSE 'user'
            END as family_member_role,
            p.phone_number
        FROM users u
        LEFT JOIN profiles p ON p.user_id = u.id
        WHERE u.username IN ('seano', 'roseo')
        ORDER BY 
            CASE WHEN u.username = 'seano' THEN 1 ELSE 2 END;
            
    ELSIF input_username = 'roseo' THEN
        RETURN QUERY
        SELECT 
            u.id as family_member_id,
            u.username as family_member_username,
            CASE 
                WHEN u.username = 'seano' THEN 'guardian_owner'
                WHEN u.username = 'amo' THEN 'guardian'
                ELSE 'user'
            END as family_member_role,
            p.phone_number
        FROM users u
        LEFT JOIN profiles p ON p.user_id = u.id
        WHERE u.username IN ('seano', 'amo')
        ORDER BY 
            CASE WHEN u.username = 'seano' THEN 1 ELSE 2 END;
    END IF;
    
    RETURN;
END;
$$;

-- Grant permissions
GRANT EXECUTE ON FUNCTION get_family_members(UUID) TO authenticated;

-- Update the smart routing function to use the simplified structure
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

-- Verify the setup
DO $$
DECLARE 
    seano_id UUID;
    roseo_id UUID;
    amo_id UUID;
    role_record RECORD;
BEGIN
    -- Get user IDs
    SELECT id INTO seano_id FROM users WHERE username = 'seano';
    SELECT id INTO roseo_id FROM users WHERE username = 'roseo';  
    SELECT id INTO amo_id FROM users WHERE username = 'amo';
    
    RAISE NOTICE 'Setup verification:';
    RAISE NOTICE 'seano ID: %, roseo ID: %, amo ID: %', seano_id, roseo_id, amo_id;
    
    -- Show all roles
    FOR role_record IN 
        SELECT u.username, r.role 
        FROM roles r
        JOIN users u ON u.id = r.user_id 
        WHERE u.username IN ('seano', 'amo', 'roseo')
        ORDER BY u.username
    LOOP
        RAISE NOTICE '- % has role: %', role_record.username, role_record.role;
    END LOOP;
    
    -- Show messaging preferences
    FOR role_record IN 
        SELECT u.username, p.preferred_messaging 
        FROM profiles p
        JOIN users u ON u.id = p.user_id 
        WHERE u.username IN ('seano', 'amo', 'roseo')
        ORDER BY u.username
    LOOP
        RAISE NOTICE '- % messaging preference: %', role_record.username, role_record.preferred_messaging;
    END LOOP;
END $$;