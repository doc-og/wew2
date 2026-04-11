-- Fix Supabase RLS Performance Warnings
-- Run this in your Supabase SQL Editor

-- Step 1: Drop existing problematic policies on thread_participants
DROP POLICY IF EXISTS "Allow all thread_participants operations" ON thread_participants;
DROP POLICY IF EXISTS "Simple view own participation" ON thread_participants;
DROP POLICY IF EXISTS "Simple insert own participation" ON thread_participants;
DROP POLICY IF EXISTS "Simple insert others" ON thread_participants;

-- Step 2: Drop existing problematic policies on threads
DROP POLICY IF EXISTS "Allow all thread operations" ON threads;
DROP POLICY IF EXISTS "Users can create threads" ON threads;

-- Step 3: Create optimized, consolidated policies for thread_participants

-- Allow users to view thread_participants where they are a participant
CREATE POLICY "optimized_view_thread_participants" ON thread_participants
FOR SELECT
TO authenticated
USING (
  user_id = (select auth.uid()) OR
  thread_id IN (
    SELECT thread_id FROM thread_participants 
    WHERE user_id = (select auth.uid())
  )
);

-- Allow users to insert thread_participants (for creating new conversations)
CREATE POLICY "optimized_insert_thread_participants" ON thread_participants
FOR INSERT
TO authenticated
WITH CHECK (
  user_id = (select auth.uid()) OR
  thread_id IN (
    SELECT thread_id FROM thread_participants 
    WHERE user_id = (select auth.uid())
  )
);

-- Step 4: Create optimized, consolidated policies for threads

-- Allow users to view threads they participate in
CREATE POLICY "optimized_view_threads" ON threads
FOR SELECT
TO authenticated
USING (
  id IN (
    SELECT thread_id FROM thread_participants 
    WHERE user_id = (select auth.uid())
  )
);

-- Allow authenticated users to create new threads
CREATE POLICY "optimized_insert_threads" ON threads
FOR INSERT
TO authenticated
WITH CHECK (true);

-- Allow users to update threads they participate in
CREATE POLICY "optimized_update_threads" ON threads
FOR UPDATE
TO authenticated
USING (
  id IN (
    SELECT thread_id FROM thread_participants 
    WHERE user_id = (select auth.uid())
  )
)
WITH CHECK (
  id IN (
    SELECT thread_id FROM thread_participants 
    WHERE user_id = (select auth.uid())
  )
);

-- Step 5: Verify existing policies on other tables are optimized

-- Check if messages table has optimized policies
-- If needed, update messages policies to use (select auth.uid())

-- Update message viewing policy if it exists
DROP POLICY IF EXISTS "Users can view messages in their threads" ON messages;
CREATE POLICY "optimized_view_messages" ON messages
FOR SELECT
TO authenticated
USING (
  thread_id IN (
    SELECT thread_id FROM thread_participants 
    WHERE user_id = (select auth.uid())
  )
);

-- Update message insertion policy if it exists
DROP POLICY IF EXISTS "Users can insert messages in their threads" ON messages;
CREATE POLICY "optimized_insert_messages" ON messages
FOR INSERT
TO authenticated
WITH CHECK (
  sender_id = (select auth.uid()) AND
  thread_id IN (
    SELECT thread_id FROM thread_participants 
    WHERE user_id = (select auth.uid())
  )
);

-- Step 6: Create optimized policies for users table if needed
-- Allow users to view their own profile and profiles of users in shared threads
DROP POLICY IF EXISTS "Users can view profiles" ON users;
CREATE POLICY "optimized_view_users" ON users
FOR SELECT
TO authenticated
USING (
  id = (select auth.uid()) OR
  id IN (
    SELECT DISTINCT tp.user_id 
    FROM thread_participants tp
    WHERE tp.thread_id IN (
      SELECT thread_id FROM thread_participants 
      WHERE user_id = (select auth.uid())
    )
  )
);

-- Step 7: Create indexes for better performance
CREATE INDEX IF NOT EXISTS idx_thread_participants_user_id ON thread_participants(user_id);
CREATE INDEX IF NOT EXISTS idx_thread_participants_thread_id ON thread_participants(thread_id);
CREATE INDEX IF NOT EXISTS idx_messages_thread_id ON messages(thread_id);
CREATE INDEX IF NOT EXISTS idx_messages_sender_id ON messages(sender_id);

-- Refresh the statistics
ANALYZE thread_participants;
ANALYZE threads;
ANALYZE messages;
ANALYZE users;

COMMENT ON POLICY "optimized_view_thread_participants" ON thread_participants IS 'Optimized RLS: Users can view thread participants where they participate. Uses (select auth.uid()) for performance.';
COMMENT ON POLICY "optimized_view_threads" ON threads IS 'Optimized RLS: Users can view threads they participate in. Single policy consolidates multiple permissions.';
COMMENT ON POLICY "optimized_view_messages" ON messages IS 'Optimized RLS: Users can view messages in threads they participate in. Uses efficient subquery.';
