-- VoidCraft password-reset codes.
-- Apply after 0001_auth.sql and 0002_account_roles.sql.
CREATE TABLE IF NOT EXISTS password_reset_codes (
  id TEXT PRIMARY KEY,
  user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  code_hash TEXT NOT NULL,
  created_at INTEGER NOT NULL,
  expires_at INTEGER NOT NULL,
  attempts INTEGER NOT NULL DEFAULT 0 CHECK (attempts >= 0),
  used_at INTEGER
);
CREATE INDEX IF NOT EXISTS password_reset_user_idx ON password_reset_codes(user_id, created_at);
CREATE INDEX IF NOT EXISTS password_reset_expiry_idx ON password_reset_codes(expires_at);
