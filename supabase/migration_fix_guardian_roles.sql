-- Migration: Fix guardian roles and relationships
-- Run this in your Supabase SQL editor

-- Check if roles table exists and create if needed
CREATE TABLE IF NOT EXISTS roles (
    id SERIAL PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    UNIQUE(user_id, role)
);

-- Get user IDs and insert guardian roles
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

    -- Clear existing roles first
    DELETE FROM roles WHERE user_id IN (seano_id, roseo_id, amo_id);

    -- Insert guardian roles
    INSERT INTO roles (user_id, role) VALUES
    (seano_id, 'guardian_owner'),
    (amo_id, 'guardian');
    
    -- roseo gets no special role (stays as regular user)
    
    RAISE NOTICE 'Guardian roles set up: seano=guardian_owner, amo=guardian';
END $$;

-- Ensure approved_contacts exists and has proper data
CREATE TABLE IF NOT EXISTS approved_contacts (
    id SERIAL PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    e164 TEXT NOT NULL,
    status TEXT NOT NULL DEFAULT 'pending',
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    UNIQUE(user_id, e164)
);

-- Setup approved contacts relationships  
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
    
    -- Clear existing contacts first
    DELETE FROM approved_contacts WHERE user_id IN (seano_id, roseo_id, amo_id);
    
    -- roseo has approved contacts seano and amo
    INSERT INTO approved_contacts (user_id, e164, status) VALUES
    (roseo_id, '+1seano', 'approved'),
    (roseo_id, '+1amo', 'approved');

    -- seano has approved contacts amo and roseo
    INSERT INTO approved_contacts (user_id, e164, status) VALUES
    (seano_id, '+1amo', 'approved'),
    (seano_id, '+1roseo', 'approved');

    -- amo has approved contacts roseo and seano
    INSERT INTO approved_contacts (user_id, e164, status) VALUES
    (amo_id, '+1roseo', 'approved'),
    (amo_id, '+1seano', 'approved');

    RAISE NOTICE 'Approved contacts set up successfully';
END $$;

-- Create indexes
CREATE INDEX IF NOT EXISTS idx_roles_user_id ON roles(user_id);
CREATE INDEX IF NOT EXISTS idx_roles_role ON roles(role);
CREATE INDEX IF NOT EXISTS idx_approved_contacts_user_status ON approved_contacts(user_id, status);

SELECT 'Guardian roles and contacts migration completed!' as result;