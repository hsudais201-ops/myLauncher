-- Store checkout and ownership records.
-- Paid items only become equipable after Stripe confirms payment.
CREATE TABLE IF NOT EXISTS store_orders (
  id TEXT PRIMARY KEY,
  user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  stripe_session_id TEXT UNIQUE,
  status TEXT NOT NULL DEFAULT 'creating' CHECK (status IN ('creating','pending','paid','canceled','failed')),
  items_json TEXT NOT NULL,
  currency TEXT NOT NULL,
  total_cents INTEGER NOT NULL CHECK (total_cents >= 0),
  stripe_payment_intent TEXT,
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL,
  paid_at TEXT
);
CREATE INDEX IF NOT EXISTS store_orders_user_idx ON store_orders(user_id, created_at);
CREATE INDEX IF NOT EXISTS store_orders_session_idx ON store_orders(stripe_session_id);

CREATE TABLE IF NOT EXISTS store_entitlements (
  user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  item_id TEXT NOT NULL REFERENCES store_items(id) ON DELETE CASCADE,
  order_id TEXT NOT NULL REFERENCES store_orders(id) ON DELETE CASCADE,
  purchased_at TEXT NOT NULL,
  PRIMARY KEY (user_id, item_id)
);
CREATE INDEX IF NOT EXISTS store_entitlements_user_idx ON store_entitlements(user_id, purchased_at);

CREATE TABLE IF NOT EXISTS store_webhook_events (
  id TEXT PRIMARY KEY,
  event_type TEXT NOT NULL,
  processed_at TEXT NOT NULL
);
