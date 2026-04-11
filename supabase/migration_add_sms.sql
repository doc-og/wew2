-- SMS feature tables and indices
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Reuse/ensure updated_at trigger function exists
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
  NEW.updated_at = NOW();
  RETURN NEW;
END;
$$ language 'plpgsql';

-- Profiles table to store quiet hours and per-user SMS quota
CREATE TABLE IF NOT EXISTS profiles (
  user_id UUID PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
  quiet_hours JSONB,                              -- {"start":"22:00","end":"06:30","tz":"America/Chicago"}
  sms_daily_quota_minutes INTEGER NOT NULL DEFAULT 60,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

DROP TRIGGER IF EXISTS update_profiles_updated_at ON profiles;
CREATE TRIGGER update_profiles_updated_at BEFORE UPDATE ON profiles
  FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- Roles table used by tiny RBAC helper
CREATE TABLE IF NOT EXISTS roles (
  id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  role TEXT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  UNIQUE(user_id, role)
);

-- Mapping of a user to a Twilio phone number (E.164)
CREATE TABLE IF NOT EXISTS sms_numbers (
  id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  twilio_number_e164 TEXT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  UNIQUE(twilio_number_e164)
);

DROP TRIGGER IF EXISTS update_sms_numbers_updated_at ON sms_numbers;
CREATE TRIGGER update_sms_numbers_updated_at BEFORE UPDATE ON sms_numbers
  FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE INDEX IF NOT EXISTS idx_sms_numbers_user_id ON sms_numbers(user_id);

-- Approved contacts for a given user (E.164), with status
CREATE TABLE IF NOT EXISTS approved_contacts (
  id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  e164 TEXT NOT NULL,
  status TEXT NOT NULL CHECK (status IN ('approved','pending','blocked')),
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  UNIQUE(user_id, e164)
);

CREATE INDEX IF NOT EXISTS idx_approved_contacts_user_id ON approved_contacts(user_id);
CREATE INDEX IF NOT EXISTS idx_approved_contacts_status ON approved_contacts(status);

-- SMS messages inbox/outbox
CREATE TABLE IF NOT EXISTS sms_messages (
  id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  direction TEXT NOT NULL CHECK (direction IN ('in','out')),
  from_e164 TEXT NOT NULL,
  to_e164 TEXT NOT NULL,
  body TEXT,
  media_json JSONB,
  thread_key TEXT NOT NULL,                -- normalized E.164 of the other party
  queued BOOLEAN NOT NULL DEFAULT FALSE,
  pending_contact BOOLEAN NOT NULL DEFAULT FALSE,
  twilio_sid TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

DROP TRIGGER IF EXISTS update_sms_messages_updated_at ON sms_messages;
CREATE TRIGGER update_sms_messages_updated_at BEFORE UPDATE ON sms_messages
  FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE INDEX IF NOT EXISTS idx_sms_messages_user_id ON sms_messages(user_id);
CREATE INDEX IF NOT EXISTS idx_sms_messages_thread ON sms_messages(user_id, thread_key);
CREATE INDEX IF NOT EXISTS idx_sms_messages_thread_created ON sms_messages(thread_key, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_sms_messages_created ON sms_messages(created_at DESC);

-- Daily ledger of SMS minutes used per user (America/Chicago date key)
CREATE TABLE IF NOT EXISTS sms_daily_ledger (
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  date DATE NOT NULL,
  daily_quota_minutes INTEGER NOT NULL DEFAULT 60,
  minutes_used INTEGER NOT NULL DEFAULT 0,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  PRIMARY KEY (user_id, date)
);

CREATE INDEX IF NOT EXISTS idx_sms_ledger_user ON sms_daily_ledger(user_id);

-- Optional: permissive RLS similar to existing tables (auth handled by Express backend)
ALTER TABLE profiles ENABLE ROW LEVEL SECURITY;
ALTER TABLE roles ENABLE ROW LEVEL SECURITY;
ALTER TABLE sms_numbers ENABLE ROW LEVEL SECURITY;
ALTER TABLE approved_contacts ENABLE ROW LEVEL SECURITY;
ALTER TABLE sms_messages ENABLE ROW LEVEL SECURITY;
ALTER TABLE sms_daily_ledger ENABLE ROW LEVEL SECURITY;

DO $$ BEGIN
  -- profiles
  IF NOT EXISTS (
    SELECT 1 FROM pg_policies WHERE tablename = 'profiles' AND policyname = 'profiles_select_all'
  ) THEN
    CREATE POLICY profiles_select_all ON profiles FOR SELECT USING (true);
  END IF;
  IF NOT EXISTS (
    SELECT 1 FROM pg_policies WHERE tablename = 'profiles' AND policyname = 'profiles_modify_all'
  ) THEN
    CREATE POLICY profiles_modify_all ON profiles FOR ALL USING (true) WITH CHECK (true);
  END IF;

  -- roles
  IF NOT EXISTS (
    SELECT 1 FROM pg_policies WHERE tablename = 'roles' AND policyname = 'roles_select_all'
  ) THEN
    CREATE POLICY roles_select_all ON roles FOR SELECT USING (true);
  END IF;
  IF NOT EXISTS (
    SELECT 1 FROM pg_policies WHERE tablename = 'roles' AND policyname = 'roles_modify_all'
  ) THEN
    CREATE POLICY roles_modify_all ON roles FOR ALL USING (true) WITH CHECK (true);
  END IF;

  -- sms_numbers
  IF NOT EXISTS (
    SELECT 1 FROM pg_policies WHERE tablename = 'sms_numbers' AND policyname = 'sms_numbers_select_all'
  ) THEN
    CREATE POLICY sms_numbers_select_all ON sms_numbers FOR SELECT USING (true);
  END IF;
  IF NOT EXISTS (
    SELECT 1 FROM pg_policies WHERE tablename = 'sms_numbers' AND policyname = 'sms_numbers_modify_all'
  ) THEN
    CREATE POLICY sms_numbers_modify_all ON sms_numbers FOR ALL USING (true) WITH CHECK (true);
  END IF;

  -- approved_contacts
  IF NOT EXISTS (
    SELECT 1 FROM pg_policies WHERE tablename = 'approved_contacts' AND policyname = 'approved_contacts_select_all'
  ) THEN
    CREATE POLICY approved_contacts_select_all ON approved_contacts FOR SELECT USING (true);
  END IF;
  IF NOT EXISTS (
    SELECT 1 FROM pg_policies WHERE tablename = 'approved_contacts' AND policyname = 'approved_contacts_modify_all'
  ) THEN
    CREATE POLICY approved_contacts_modify_all ON approved_contacts FOR ALL USING (true) WITH CHECK (true);
  END IF;

  -- sms_messages
  IF NOT EXISTS (
    SELECT 1 FROM pg_policies WHERE tablename = 'sms_messages' AND policyname = 'sms_messages_select_all'
  ) THEN
    CREATE POLICY sms_messages_select_all ON sms_messages FOR SELECT USING (true);
  END IF;
  IF NOT EXISTS (
    SELECT 1 FROM pg_policies WHERE tablename = 'sms_messages' AND policyname = 'sms_messages_modify_all'
  ) THEN
    CREATE POLICY sms_messages_modify_all ON sms_messages FOR ALL USING (true) WITH CHECK (true);
  END IF;

  -- sms_daily_ledger
  IF NOT EXISTS (
    SELECT 1 FROM pg_policies WHERE tablename = 'sms_daily_ledger' AND policyname = 'sms_daily_ledger_select_all'
  ) THEN
    CREATE POLICY sms_daily_ledger_select_all ON sms_daily_ledger FOR SELECT USING (true);
  END IF;
  IF NOT EXISTS (
    SELECT 1 FROM pg_policies WHERE tablename = 'sms_daily_ledger' AND policyname = 'sms_daily_ledger_modify_all'
  ) THEN
    CREATE POLICY sms_daily_ledger_modify_all ON sms_daily_ledger FOR ALL USING (true) WITH CHECK (true);
  END IF;
END $$;
