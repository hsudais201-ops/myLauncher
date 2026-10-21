-- Adds a persistent website role for authenticated accounts.
-- Safe default: all existing and newly created accounts are ordinary members.
-- Apply once per D1 database after the initial auth schema is present.
ALTER TABLE users
ADD COLUMN role TEXT NOT NULL DEFAULT 'member'
CHECK (role IN ('member', 'owner'));
