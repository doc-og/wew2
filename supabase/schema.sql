-- Enable UUID extension
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Create users table
CREATE TABLE users (
  id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  username TEXT UNIQUE NOT NULL,
  email TEXT UNIQUE NOT NULL,
  password_hash TEXT NOT NULL,
  created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
  updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- Create threads table
CREATE TABLE threads (
  id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  title TEXT,
  created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
  updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- Create thread_participants table (many-to-many relationship)
CREATE TABLE thread_participants (
  id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  thread_id UUID REFERENCES threads(id) ON DELETE CASCADE,
  user_id UUID REFERENCES users(id) ON DELETE CASCADE,
  joined_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
  UNIQUE(thread_id, user_id)
);

-- Create messages table
CREATE TABLE messages (
  id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  thread_id UUID REFERENCES threads(id) ON DELETE CASCADE,
  sender_id UUID REFERENCES users(id) ON DELETE CASCADE,
  body TEXT,
  type TEXT DEFAULT 'text' CHECK (type IN ('text', 'image')),
  media_url TEXT,
  client_id TEXT,
  created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
  updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- Create indexes for better performance
CREATE INDEX idx_thread_participants_thread_id ON thread_participants(thread_id);
CREATE INDEX idx_thread_participants_user_id ON thread_participants(user_id);
CREATE INDEX idx_messages_thread_id ON messages(thread_id);
CREATE INDEX idx_messages_sender_id ON messages(sender_id);
CREATE INDEX idx_messages_created_at ON messages(created_at);

-- Create updated_at trigger function
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
  NEW.updated_at = NOW();
  RETURN NEW;
END;
$$ language 'plpgsql';

-- Create triggers to automatically update updated_at
CREATE TRIGGER update_users_updated_at BEFORE UPDATE ON users
  FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_threads_updated_at BEFORE UPDATE ON threads
  FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_messages_updated_at BEFORE UPDATE ON messages
  FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- Insert test users (passwords are bcrypt hashed 'pass123')
INSERT INTO users (username, email, password_hash) VALUES
  ('kid1', 'kid1@example.com', '$2b$10$XoEw8P9GJQ1xR3o3n2WL4uE1.8xYz4B5v1B3P2A7Q9Z8H6F2K4M3E'),
  ('kid2', 'kid2@example.com', '$2b$10$XoEw8P9GJQ1xR3o3n2WL4uE1.8xYz4B5v1B3P2A7Q9Z8H6F2K4M3E'),
  ('parent1', 'parent1@example.com', '$2b$10$XoEw8P9GJQ1xR3o3n2WL4uE1.8xYz4B5v1B3P2A7Q9Z8H6F2K4M3E');

-- Create a test thread
WITH 
  kid1_id AS (SELECT id FROM users WHERE username = 'kid1'),
  kid2_id AS (SELECT id FROM users WHERE username = 'kid2'),
  new_thread AS (
    INSERT INTO threads (title) 
    VALUES ('Family Chat') 
    RETURNING id
  )
INSERT INTO thread_participants (thread_id, user_id)
SELECT new_thread.id, kid1_id.id FROM new_thread, kid1_id
UNION ALL
SELECT new_thread.id, kid2_id.id FROM new_thread, kid2_id;

-- Create message_reads table for tracking read status
CREATE TABLE message_reads (
  id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  message_id UUID REFERENCES messages(id) ON DELETE CASCADE,
  user_id UUID REFERENCES users(id) ON DELETE CASCADE,
  read_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
  UNIQUE(message_id, user_id)
);

-- Create indexes for better performance
CREATE INDEX idx_message_reads_message_id ON message_reads(message_id);
CREATE INDEX idx_message_reads_user_id ON message_reads(user_id);

-- Insert a test message
WITH 
  thread_id AS (SELECT id FROM threads WHERE title = 'Family Chat'),
  sender_id AS (SELECT id FROM users WHERE username = 'kid1')
INSERT INTO messages (thread_id, sender_id, body, type)
SELECT thread_id.id, sender_id.id, 'Hey there!', 'text'
FROM thread_id, sender_id;
