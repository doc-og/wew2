-- Create message_reactions table
CREATE TABLE message_reactions (
  id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  message_id UUID REFERENCES messages(id) ON DELETE CASCADE,
  user_id UUID REFERENCES users(id) ON DELETE CASCADE,
  emoji TEXT NOT NULL,
  created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
  UNIQUE(message_id, user_id, emoji)
);

-- Create indexes for better performance
CREATE INDEX idx_message_reactions_message_id ON message_reactions(message_id);
CREATE INDEX idx_message_reactions_user_id ON message_reactions(user_id);
CREATE INDEX idx_message_reactions_emoji ON message_reactions(emoji);

-- Add some test reactions to existing messages (if any exist)
-- This will add reactions only if there are existing messages
INSERT INTO message_reactions (message_id, user_id, emoji)
SELECT 
  m.id as message_id,
  u.id as user_id,
  '👍' as emoji
FROM messages m
CROSS JOIN users u
WHERE u.username = 'kid2'
AND EXISTS (SELECT 1 FROM messages WHERE id = m.id)
LIMIT 1; -- Only add one test reaction
