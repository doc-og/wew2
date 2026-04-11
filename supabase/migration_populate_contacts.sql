-- Migration to populate contacts based on existing data
-- This migration creates contact entries from thread participants and approved contacts

BEGIN;

-- Step 1: Add contacts from thread participants
-- For each user, find all other users they've had conversations with
INSERT INTO users (username, email, first_name, last_name, phone_number, password_hash)
SELECT DISTINCT
  COALESCE(ac.e164, CONCAT('user_', SUBSTRING(p.id::text, 1, 8))) as username,
  COALESCE(ac.e164, CONCAT('user_', SUBSTRING(p.id::text, 1, 8))) || '@contact.local' as email,
  NULL as first_name,
  NULL as last_name,
  ac.e164 as phone_number,
  NULL as password_hash
FROM approved_contacts ac
LEFT JOIN users p ON FALSE  -- This join will always be false, we just need the structure
WHERE ac.status = 'approved'
  AND ac.e164 NOT IN (SELECT phone_number FROM users WHERE phone_number IS NOT NULL)
  AND ac.e164 ~ '^\\+[1-9][0-9]+$'  -- Basic E.164 validation
ON CONFLICT (username) DO NOTHING;

-- Step 2: Update existing users' phone numbers from approved contacts
UPDATE users 
SET phone_number = ac.e164
FROM approved_contacts ac
WHERE users.username = ac.e164 
  AND ac.status = 'approved'
  AND users.phone_number IS NULL;

-- Step 3: Create contact entries based on thread participants
-- Find all unique pairs of users who have had conversations
WITH contact_pairs AS (
  SELECT DISTINCT
    tp1.user_id as user_id,
    u2.id as contact_user_id,
    u2.username as contact_username,
    u2.phone_number as contact_phone_number,
    u2.first_name as contact_first_name,
    u2.last_name as contact_last_name
  FROM thread_participants tp1
  JOIN thread_participants tp2 ON tp1.thread_id = tp2.thread_id AND tp1.user_id != tp2.user_id
  JOIN users u1 ON tp1.user_id = u1.id
  JOIN users u2 ON tp2.user_id = u2.id
  WHERE u1.id != u2.id
),
-- Deduplicate and format for contacts
formatted_contacts AS (
  SELECT DISTINCT
    user_id,
    contact_user_id,
    contact_username,
    contact_phone_number,
    COALESCE(contact_first_name, contact_username) as firstName,
    COALESCE(contact_last_name, '') as lastName
  FROM contact_pairs
)
SELECT 
  user_id,
  contact_user_id,
  contact_username,
  contact_phone_number,
  firstName,
  lastName
FROM formatted_contacts
ORDER BY user_id, contact_username;

-- Step 4: Ensure test users have some contacts
-- Add cross-references between test users (seano, amo, roseo)
DO $$
DECLARE
  seano_id UUID;
  amo_id UUID;
  roseo_id UUID;
BEGIN
  -- Get test user IDs
  SELECT id INTO seano_id FROM users WHERE username = 'seano';
  SELECT id INTO amo_id FROM users WHERE username = 'amo';
  SELECT id INTO roseo_id FROM users WHERE username = 'roseo';
  
  -- Add some sample phone numbers if they don't exist
  UPDATE users SET phone_number = '+15551234567' WHERE username = 'seano' AND phone_number IS NULL;
  UPDATE users SET phone_number = '+15559876543' WHERE username = 'amo' AND phone_number IS NULL;
  UPDATE users SET phone_number = '+15555555555' WHERE username = 'roseo' AND phone_number IS NULL;
  
  -- Add sample approved contacts for test users
  IF seano_id IS NOT NULL THEN
    INSERT INTO approved_contacts (user_id, e164, status) 
    VALUES (seano_id, '+15559876543', 'approved'), (seano_id, '+15555555555', 'approved')
    ON CONFLICT (user_id, e164) DO UPDATE SET status = 'approved';
  END IF;
  
  IF amo_id IS NOT NULL THEN
    INSERT INTO approved_contacts (user_id, e164, status) 
    VALUES (amo_id, '+15551234567', 'approved'), (amo_id, '+15555555555', 'approved')
    ON CONFLICT (user_id, e164) DO UPDATE SET status = 'approved';
  END IF;
  
  IF roseo_id IS NOT NULL THEN
    INSERT INTO approved_contacts (user_id, e164, status) 
    VALUES (roseo_id, '+15551234567', 'approved'), (roseo_id, '+15559876543', 'approved')
    ON CONFLICT (user_id, e164) DO UPDATE SET status = 'approved';
  END IF;
  
END $$;

-- Step 5: Create some additional sample contacts for testing
INSERT INTO users (username, email, first_name, last_name, phone_number)
VALUES 
  ('contact1', 'contact1@example.com', 'John', 'Doe', '+15551111111'),
  ('contact2', 'contact2@example.com', 'Jane', 'Smith', '+15552222222'),
  ('contact3', 'contact3@example.com', 'Bob', 'Johnson', '+15553333333')
ON CONFLICT (username) DO NOTHING;

-- Add these as approved contacts for test users
DO $$
DECLARE
  seano_id UUID;
  roseo_id UUID;
BEGIN
  SELECT id INTO seano_id FROM users WHERE username = 'seano';
  SELECT id INTO roseo_id FROM users WHERE username = 'roseo';
  
  IF seano_id IS NOT NULL THEN
    INSERT INTO approved_contacts (user_id, e164, status) 
    VALUES (seano_id, '+15551111111', 'approved')
    ON CONFLICT (user_id, e164) DO UPDATE SET status = 'approved';
  END IF;
  
  IF roseo_id IS NOT NULL THEN
    INSERT INTO approved_contacts (user_id, e164, status) 
    VALUES (roseo_id, '+15552222222', 'approved')
    ON CONFLICT (user_id, e164) DO UPDATE SET status = 'approved';
  END IF;
END $$;

COMMIT;

-- Verification queries to check the results
-- Uncomment these to see what data was populated:

/*
SELECT 'Total users:' as info, count(*) as count FROM users;
SELECT 'Users with phone numbers:' as info, count(*) as count FROM users WHERE phone_number IS NOT NULL;
SELECT 'Total approved contacts:' as info, count(*) as count FROM approved_contacts WHERE status = 'approved';

-- Show users and their contacts
SELECT 
  u1.username as user,
  u1.phone_number as user_phone,
  ac.e164 as contact_phone,
  u2.username as contact_username,
  u2.first_name as contact_first_name,
  u2.last_name as contact_last_name
FROM users u1
JOIN approved_contacts ac ON u1.id = ac.user_id AND ac.status = 'approved'
LEFT JOIN users u2 ON ac.e164 = u2.phone_number
WHERE u1.username IN ('seano', 'amo', 'roseo')
ORDER BY u1.username, ac.e164;
*/