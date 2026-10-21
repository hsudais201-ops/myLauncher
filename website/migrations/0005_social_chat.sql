-- VoidCraft Social Hub tables, using the existing website account IDs and session system.
-- Apply after 0002_account_roles.sql and the core auth schema.
CREATE TABLE IF NOT EXISTS social_friend_requests (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  sender_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  recipient_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  pair_key TEXT NOT NULL,
  status TEXT NOT NULL DEFAULT 'pending' CHECK (status IN ('pending', 'accepted', 'rejected')),
  created_at INTEGER NOT NULL,
  CHECK (sender_id <> recipient_id)
);
CREATE UNIQUE INDEX IF NOT EXISTS idx_social_pending_pair
  ON social_friend_requests(pair_key) WHERE status = 'pending';
CREATE INDEX IF NOT EXISTS idx_social_requests_recipient
  ON social_friend_requests(recipient_id, status, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_social_requests_sender
  ON social_friend_requests(sender_id, status, created_at DESC);

CREATE TABLE IF NOT EXISTS social_friendships (
  user_low_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  user_high_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  created_at INTEGER NOT NULL,
  CHECK (user_low_id < user_high_id),
  PRIMARY KEY (user_low_id, user_high_id)
);
CREATE INDEX IF NOT EXISTS idx_social_friendships_high
  ON social_friendships(user_high_id, user_low_id);

CREATE TABLE IF NOT EXISTS social_messages (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  sender_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  recipient_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  body TEXT NOT NULL CHECK (length(body) BETWEEN 1 AND 2000),
  created_at INTEGER NOT NULL,
  CHECK (sender_id <> recipient_id)
);
CREATE INDEX IF NOT EXISTS idx_social_messages_pair
  ON social_messages(sender_id, recipient_id, id DESC);
CREATE INDEX IF NOT EXISTS idx_social_messages_recipient
  ON social_messages(recipient_id, id DESC);

CREATE TABLE IF NOT EXISTS social_blocks (
  blocker_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  blocked_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  created_at INTEGER NOT NULL,
  CHECK (blocker_id <> blocked_id),
  PRIMARY KEY (blocker_id, blocked_id)
);
CREATE INDEX IF NOT EXISTS idx_social_blocks_reverse
  ON social_blocks(blocked_id, blocker_id);

CREATE TABLE IF NOT EXISTS social_reports (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  reporter_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  target_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  category TEXT NOT NULL CHECK (category IN ('harassment', 'spam', 'impersonation', 'inappropriate-content', 'other')),
  details TEXT NOT NULL DEFAULT '',
  status TEXT NOT NULL DEFAULT 'open' CHECK (status IN ('open', 'reviewing', 'resolved', 'dismissed')),
  created_at INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_social_reports_status
  ON social_reports(status, created_at DESC);

CREATE TABLE IF NOT EXISTS social_rate_limits (
  bucket_key TEXT PRIMARY KEY,
  window_start INTEGER NOT NULL,
  attempts INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_social_rate_limits_window
  ON social_rate_limits(window_start);
