"""Database-contract tests for the VoidCraft Social Hub D1 migration.

These tests use Python's standard SQLite library and a minimal auth schema matching
the website's existing users table. They validate migration compatibility/constraints,
not a live Cloudflare Pages deployment or the request handler itself.
"""
from pathlib import Path
import re
import sqlite3
import unittest


MIGRATION = Path(__file__).resolve().parents[1] / "migrations" / "0005_social_hub.sql"


class SocialHubSchemaTests(unittest.TestCase):
    def setUp(self):
        self.db = sqlite3.connect(":memory:")
        self.db.execute("PRAGMA foreign_keys = ON")
        self.db.executescript(
            """
            CREATE TABLE users (
                id TEXT PRIMARY KEY,
                username TEXT NOT NULL UNIQUE COLLATE NOCASE,
                role TEXT NOT NULL DEFAULT 'member'
                    CHECK (role IN ('member', 'owner')),
                created_at TEXT NOT NULL
            );
            INSERT INTO users(id, username, role, created_at) VALUES
                ('u1', 'alice', 'member', '2026-01-01'),
                ('u2', 'player_one', 'member', '2026-01-01'),
                ('u3', 'playerXone', 'member', '2026-01-01'),
                ('u4', 'owner', 'owner', '2026-01-01');
            """
        )
        self.db.executescript(MIGRATION.read_text(encoding="utf-8"))

    def tearDown(self):
        self.db.close()

    def test_migration_creates_all_social_tables(self):
        names = {
            row[0] for row in self.db.execute(
                "SELECT name FROM sqlite_master WHERE type='table'"
            )
        }
        self.assertTrue({
            "social_friend_requests",
            "social_friendships",
            "social_messages",
            "social_blocks",
            "social_reports",
        }.issubset(names))

    def test_only_one_pending_friend_request_per_pair_even_in_reverse_direction(self):
        self.db.execute(
            "INSERT INTO social_friend_requests(id,sender_id,recipient_id,status,created_at) "
            "VALUES('r1','u1','u2','pending',1)"
        )
        with self.assertRaises(sqlite3.IntegrityError):
            self.db.execute(
                "INSERT INTO social_friend_requests(id,sender_id,recipient_id,status,created_at) "
                "VALUES('r2','u2','u1','pending',2)"
            )
        # Rejected requests must not permanently prevent a future request.
        self.db.execute("UPDATE social_friend_requests SET status='rejected' WHERE id='r1'")
        self.db.execute(
            "INSERT INTO social_friend_requests(id,sender_id,recipient_id,status,created_at) "
            "VALUES('r3','u2','u1','pending',3)"
        )

    def test_rate_limit_indexes_exist(self):
        names = {
            row[0] for row in self.db.execute(
                "SELECT name FROM sqlite_master WHERE type='index'"
            )
        }
        self.assertTrue({
            "social_requests_sender_rate_idx",
            "social_messages_sender_rate_idx",
            "social_pending_request_pair_idx",
        }.issubset(names))

    def test_friendship_requires_canonical_user_order_and_is_unique(self):
        self.db.execute(
            "INSERT INTO social_friendships(user_low,user_high,created_at) VALUES('u1','u2',1)"
        )
        with self.assertRaises(sqlite3.IntegrityError):
            self.db.execute(
                "INSERT INTO social_friendships(user_low,user_high,created_at) VALUES('u1','u2',2)"
            )
        with self.assertRaises(sqlite3.IntegrityError):
            self.db.execute(
                "INSERT INTO social_friendships(user_low,user_high,created_at) VALUES('u2','u1',3)"
            )

    def test_messages_require_nonempty_bounded_content_and_distinct_users(self):
        self.db.execute(
            "INSERT INTO social_messages(id,sender_id,recipient_id,body,created_at) "
            "VALUES('m1','u1','u2','hello',1)"
        )
        with self.assertRaises(sqlite3.IntegrityError):
            self.db.execute(
                "INSERT INTO social_messages(id,sender_id,recipient_id,body,created_at) "
                "VALUES('m2','u1','u2','',2)"
            )
        with self.assertRaises(sqlite3.IntegrityError):
            self.db.execute(
                "INSERT INTO social_messages(id,sender_id,recipient_id,body,created_at) "
                "VALUES('m3','u1','u1','hello',3)"
            )
        with self.assertRaises(sqlite3.IntegrityError):
            self.db.execute(
                "INSERT INTO social_messages(id,sender_id,recipient_id,body,created_at) "
                "VALUES('m4','u1','u2',?,4)",
                ("x" * 2001,),
            )

    def test_reports_reject_self_target_and_unknown_category(self):
        with self.assertRaises(sqlite3.IntegrityError):
            self.db.execute(
                "INSERT INTO social_reports(id,reporter_id,target_user_id,category,created_at) "
                "VALUES('rp1','u1','u1','spam',1)"
            )
        with self.assertRaises(sqlite3.IntegrityError):
            self.db.execute(
                "INSERT INTO social_reports(id,reporter_id,target_user_id,category,created_at) "
                "VALUES('rp2','u1','u2','not-a-category',1)"
            )
        self.db.execute(
            "INSERT INTO social_reports(id,reporter_id,target_user_id,category,created_at) "
            "VALUES('rp3','u1','u2','spam',1)"
        )

    def test_blocks_disallow_self_blocks_and_ignore_duplicates(self):
        self.db.execute(
            "INSERT INTO social_blocks(blocker_id,blocked_id,created_at) VALUES('u1','u2',1)"
        )
        self.db.execute(
            "INSERT OR IGNORE INTO social_blocks(blocker_id,blocked_id,created_at) VALUES('u1','u2',2)"
        )
        self.assertEqual(1, self.db.execute("SELECT COUNT(*) FROM social_blocks").fetchone()[0])
        with self.assertRaises(sqlite3.IntegrityError):
            self.db.execute(
                "INSERT INTO social_blocks(blocker_id,blocked_id,created_at) VALUES('u1','u1',3)"
            )

    def test_search_treats_underscore_as_literal_not_like_wildcard(self):
        term = "player_"
        escaped = re.sub(r"[\\%_]", lambda match: "\\" + match.group(0), term)
        rows = self.db.execute(
            "SELECT username FROM users "
            "WHERE username COLLATE NOCASE LIKE ? ESCAPE '\\' AND id<>? "
            "ORDER BY username LIMIT 20",
            ("%" + escaped + "%", "u1"),
        ).fetchall()
        self.assertEqual(["player_one"], [row[0] for row in rows])

    def test_social_rows_are_removed_when_account_is_deleted(self):
        self.db.execute(
            "INSERT INTO social_blocks(blocker_id,blocked_id,created_at) VALUES('u1','u2',1)"
        )
        self.db.execute(
            "INSERT INTO social_messages(id,sender_id,recipient_id,body,created_at) "
            "VALUES('m1','u1','u2','hello',1)"
        )
        self.db.execute("DELETE FROM users WHERE id='u1'")
        self.assertEqual(0, self.db.execute("SELECT COUNT(*) FROM social_blocks").fetchone()[0])
        self.assertEqual(0, self.db.execute("SELECT COUNT(*) FROM social_messages").fetchone()[0])


if __name__ == "__main__":
    unittest.main()
