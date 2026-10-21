-- VoidCraft Social Hub tables. Uses the existing website users.id and sessions;
-- does not create a second account system or store credentials.
CREATE TABLE IF NOT EXISTS social_friend_requests (
  id TEXT PRIMARY KEY,
  sender_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  recipient_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  status TEXT NOT NULL DEFAULT 'pending' CHECK (status IN ('pending','accepted','rejected')),
  created_at INTEGER NOT NULL,
  CHECK (sender_id <> recipient_id)
);
CREATE INDEX IF NOT EXISTS social_requests_recipient_idx ON social_friend_requests(recipient_id,status,created_at DESC);
CREATE INDEX IF NOT EXISTS social_requests_sender_idx ON social_friend_requests(sender_id,status,created_at DESC);
CREATE INDEX IF NOT EXISTS social_requests_sender_rate_idx ON social_friend_requests(sender_id,created_at DESC);
-- Only one pending request may exist between any two accounts, regardless of direction.
CREATE UNIQUE INDEX IF NOT EXISTS social_pending_request_pair_idx
ON social_friend_requests (
  CASE WHEN sender_id < recipient_id THEN sender_id ELSE recipient_id END,
  CASE WHEN sender_id < recipient_id THEN recipient_id ELSE sender_id END
) WHERE status='pending';

CREATE TABLE IF NOT EXISTS social_friendships (
  user_low TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  user_high TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  created_at INTEGER NOT NULL,
  CHECK (user_low < user_high),
  PRIMARY KEY (user_low,user_high)
);
CREATE INDEX IF NOT EXISTS social_friendships_high_idx ON social_friendships(user_high,user_low);

CREATE TABLE IF NOT EXISTS social_messages (
  id TEXT PRIMARY KEY,
  sender_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  recipient_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  body TEXT NOT NULL CHECK (length(body) BETWEEN 1 AND 2000),
  created_at INTEGER NOT NULL,
  CHECK (sender_id <> recipient_id)
);
CREATE INDEX IF NOT EXISTS social_messages_thread_idx ON social_messages(sender_id,recipient_id,created_at DESC);
CREATE INDEX IF NOT EXISTS social_messages_sender_rate_idx ON social_messages(sender_id,created_at DESC);

CREATE TABLE IF NOT EXISTS social_blocks (
  blocker_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  blocked_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  created_at INTEGER NOT NULL,
  CHECK (blocker_id <> blocked_id),
  PRIMARY KEY (blocker_id,blocked_id)
);

CREATE TABLE IF NOT EXISTS social_reports (
  id TEXT PRIMARY KEY,
  reporter_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  target_user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  category TEXT NOT NULL CHECK (category IN ('harassment','spam','inappropriate','impersonation','other')),
  details TEXT NOT NULL DEFAULT '',
  status TEXT NOT NULL DEFAULT 'open' CHECK (status IN ('open','reviewed','dismissed')),
  created_at INTEGER NOT NULL,
  reviewed_by TEXT REFERENCES users(id),
  reviewed_at INTEGER,
  CHECK (reporter_id <> target_user_id)
);
CREATE INDEX IF NOT EXISTS social_reports_status_idx ON social_reports(status,created_at DESC);
