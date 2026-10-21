PRAGMA foreign_keys = ON;

CREATE TABLE IF NOT EXISTS users (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  username TEXT NOT NULL,
  username_normalized TEXT NOT NULL UNIQUE,
  email TEXT NOT NULL,
  email_normalized TEXT NOT NULL UNIQUE,
  password_salt TEXT NOT NULL,
  password_hash TEXT NOT NULL,
  display_name TEXT NOT NULL,
  bio TEXT NOT NULL DEFAULT '',
  role TEXT NOT NULL DEFAULT 'member' CHECK (role IN ('member', 'owner', 'moderator')),
  created_at INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS sessions (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  token_hash TEXT NOT NULL UNIQUE,
  expires_at INTEGER NOT NULL,
  created_at INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_sessions_user_expiry ON sessions(user_id, expires_at);
CREATE INDEX IF NOT EXISTS idx_sessions_expiry ON sessions(expires_at);

CREATE TABLE IF NOT EXISTS friend_requests (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  sender_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  recipient_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  status TEXT NOT NULL DEFAULT 'pending' CHECK (status IN ('pending', 'accepted', 'rejected')),
  created_at INTEGER NOT NULL,
  CHECK (sender_id <> recipient_id)
);
CREATE INDEX IF NOT EXISTS idx_friend_requests_recipient_status ON friend_requests(recipient_id, status, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_friend_requests_sender_status ON friend_requests(sender_id, status, created_at DESC);

CREATE TABLE IF NOT EXISTS friendships (
  user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  friend_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  created_at INTEGER NOT NULL,
  CHECK (user_id < friend_id),
  PRIMARY KEY (user_id, friend_id)
);
CREATE INDEX IF NOT EXISTS idx_friendships_friend ON friendships(friend_id, user_id);

CREATE TABLE IF NOT EXISTS messages (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  sender_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  recipient_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  body TEXT NOT NULL CHECK (length(body) BETWEEN 1 AND 2000),
  created_at INTEGER NOT NULL,
  CHECK (sender_id <> recipient_id)
);
CREATE INDEX IF NOT EXISTS idx_messages_pair_id ON messages(sender_id, recipient_id, id DESC);
CREATE INDEX IF NOT EXISTS idx_messages_recipient_id ON messages(recipient_id, id DESC);

CREATE TABLE IF NOT EXISTS blocks (
  blocker_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  blocked_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  created_at INTEGER NOT NULL,
  CHECK (blocker_id <> blocked_id),
  PRIMARY KEY (blocker_id, blocked_id)
);
CREATE INDEX IF NOT EXISTS idx_blocks_blocked ON blocks(blocked_id, blocker_id);

CREATE TABLE IF NOT EXISTS reports (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  reporter_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  target_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  category TEXT NOT NULL CHECK (category IN ('harassment', 'spam', 'impersonation', 'inappropriate-content', 'other')),
  details TEXT NOT NULL DEFAULT '',
  status TEXT NOT NULL DEFAULT 'open' CHECK (status IN ('open', 'reviewing', 'resolved', 'dismissed')),
  created_at INTEGER NOT NULL,
  CHECK (reporter_id <> target_id)
);
CREATE INDEX IF NOT EXISTS idx_reports_status_created ON reports(status, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_reports_target_created ON reports(target_id, created_at DESC);

CREATE TABLE IF NOT EXISTS rate_limits (
  bucket TEXT PRIMARY KEY,
  count INTEGER NOT NULL,
  reset_at INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_rate_limits_expiry ON rate_limits(reset_at);
