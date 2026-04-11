-- Enable Row Level Security on all tables
ALTER TABLE users ENABLE ROW LEVEL SECURITY;
ALTER TABLE threads ENABLE ROW LEVEL SECURITY;
ALTER TABLE thread_participants ENABLE ROW LEVEL SECURITY;
ALTER TABLE messages ENABLE ROW LEVEL SECURITY;

-- Users can access all user records (auth handled by Express backend)
CREATE POLICY "Users can view all profiles" ON users
  FOR SELECT USING (true);

CREATE POLICY "Users can update all profiles" ON users
  FOR UPDATE USING (true);

-- Users can view all threads (auth handled by Express backend)
CREATE POLICY "Users can view all threads" ON threads
  FOR SELECT USING (true);

-- Users can create new threads
CREATE POLICY "Users can create threads" ON threads
  FOR INSERT WITH CHECK (true);

-- Users can update all threads (auth handled by Express backend)
CREATE POLICY "Users can update all threads" ON threads
  FOR UPDATE USING (true);

-- Users can delete all threads (auth handled by Express backend)
CREATE POLICY "Users can delete all threads" ON threads
  FOR DELETE USING (true);

-- Thread participants policies (auth handled by Express backend)
CREATE POLICY "Users can view all thread participants" ON thread_participants
  FOR SELECT USING (true);

CREATE POLICY "Users can add all participants" ON thread_participants
  FOR INSERT WITH CHECK (true);

-- Messages policies (auth handled by Express backend)
CREATE POLICY "Users can view all messages" ON messages
  FOR SELECT USING (true);

CREATE POLICY "Users can send all messages" ON messages
  FOR INSERT WITH CHECK (true);

CREATE POLICY "Users can update all messages" ON messages
  FOR UPDATE USING (true);

CREATE POLICY "Users can delete all messages" ON messages
  FOR DELETE USING (true);
