-- VoidCraft online marketplace schema. Safe to apply more than once.
-- Apply after 0001_auth.sql and 0002_account_roles.sql.
CREATE TABLE IF NOT EXISTS store_items (
  id TEXT PRIMARY KEY,
  slug TEXT NOT NULL UNIQUE,
  kind TEXT NOT NULL CHECK (kind IN ('feature','cosmetic')),
  name TEXT NOT NULL,
  tagline TEXT NOT NULL,
  description TEXT NOT NULL,
  image_url TEXT,
  version TEXT,
  compatibility TEXT,
  permissions TEXT,
  license TEXT,
  source_url TEXT,
  package_url TEXT,
  price_cents INTEGER CHECK (price_cents IS NULL OR price_cents >= 0),
  currency TEXT NOT NULL DEFAULT 'USD',
  status TEXT NOT NULL DEFAULT 'pending' CHECK (status IN ('pending','published','rejected','archived')),
  availability TEXT NOT NULL DEFAULT 'preview' CHECK (availability IN ('preview','available','coming_soon','paused')),
  creator_user_id TEXT REFERENCES users(id) ON DELETE SET NULL,
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL,
  reviewed_at TEXT,
  reviewer_user_id TEXT REFERENCES users(id) ON DELETE SET NULL,
  review_note TEXT,
  download_count INTEGER NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS store_items_public_idx ON store_items(status, kind, availability);
CREATE INDEX IF NOT EXISTS store_items_creator_idx ON store_items(creator_user_id, created_at);

-- These are illustrative concepts, not packages offered for download or purchase.
-- Null prices and preview status deliberately prevent misleading purchase/download CTAs.
INSERT OR IGNORE INTO store_items
(id,slug,kind,name,tagline,description,image_url,version,compatibility,permissions,license,price_cents,currency,status,availability,created_at,updated_at)
VALUES
('feature-smart-performance-advisor','smart-performance-advisor','feature','Smart Performance Advisor','Device-aware settings and reversible presets','Shows a device snapshot, explains performance tradeoffs and offers reversible presets. This is a concept listing; no website package is attached yet.','assets/previews/performance-advisor.svg','Concept','Android launcher builds; runtime compatibility must be tested','Reads device metrics exposed by Android; modifies only documented launcher/game settings when installed','TBD',NULL,'USD','published','preview','2026-10-20T00:00:00.000Z','2026-10-20T00:00:00.000Z'),
('feature-server-status','server-status','feature','Server Status','Keep your favorite servers in view','A planned screen for saved servers, online/offline status, MOTD, player counts, and editing server name/address. Service integration is not configured.','assets/previews/server-status.svg','Concept','Minecraft Java server status provider required','Network access to configured server status service','TBD',NULL,'USD','published','preview','2026-10-20T00:00:00.000Z','2026-10-20T00:00:00.000Z'),
('feature-friends-chat','friends-chat','feature','Friends & Chat','Optional player profiles and messaging','Concept for friend requests, a friends list and private messages. Secure messaging, abuse reporting and moderation must be implemented before release.','assets/previews/friends-chat.svg','Concept','Website account and moderation integration required','Network access; account and messaging data','TBD',NULL,'USD','published','preview','2026-10-20T00:00:00.000Z','2026-10-20T00:00:00.000Z'),
('feature-backup-manager','backup-manager','feature','Backup Manager','Protect worlds and instances','Concept for creating, verifying, exporting and restoring backups with clear confirmation before replacing data.','assets/previews/backup-manager.svg','Concept','Launcher file-storage API required','Access to user-selected world and instance files','TBD',NULL,'USD','published','preview','2026-10-20T00:00:00.000Z','2026-10-20T00:00:00.000Z'),
('feature-theme-studio','theme-studio','feature','Theme Studio','Make the launcher feel personal','Concept for custom colors, backgrounds and layout settings applied to the VoidCraft interface.','assets/previews/theme-studio.svg','Concept','VoidCraft launcher UI integration required','Applies user-selected visual preferences','TBD',NULL,'USD','published','preview','2026-10-20T00:00:00.000Z','2026-10-20T00:00:00.000Z'),
('feature-community-packs','community-features','feature','Community Features','Reviewed community-made additions','Concept for browsing community submissions after license, source, permissions, compatibility and security review.','assets/previews/creator-packs.svg','Concept','Review and signature workflow required','Depends on each approved package; declared per submission','TBD',NULL,'USD','published','preview','2026-10-20T00:00:00.000Z','2026-10-20T00:00:00.000Z'),
('cosmetic-neon-night','neon-night','cosmetic','Neon Night','Violet and cyan visual theme','Original concept for a neon-inspired VoidCraft palette and accent treatment. No downloadable theme package is attached yet.','assets/previews/neon-night.svg','Concept','VoidCraft theme engine required','Changes launcher appearance only','TBD',NULL,'USD','published','preview','2026-10-20T00:00:00.000Z','2026-10-20T00:00:00.000Z'),
('cosmetic-void-minimal','void-minimal','cosmetic','Void Minimal','Clean, low-distraction styling','Original monochrome concept focused on readability, reduced visual clutter and contrast. No downloadable theme package is attached yet.','assets/previews/void-minimal.svg','Concept','VoidCraft theme engine required','Changes launcher appearance only','TBD',NULL,'USD','published','preview','2026-10-20T00:00:00.000Z','2026-10-20T00:00:00.000Z'),
('cosmetic-creator-packs','creator-packs','cosmetic','Creator Packs','Community visual packs','Concept listing for community-made themes and wallpapers after permission and licensing review.','assets/previews/creator-packs.svg','Concept','VoidCraft theme engine and review required','Visual customization only; each package declares its own contents','TBD',NULL,'USD','published','preview','2026-10-20T00:00:00.000Z','2026-10-20T00:00:00.000Z');
