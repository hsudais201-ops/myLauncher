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
        self.db.execute("UPDATE social_friend_requests SET status='cancelled' WHERE id='r3'")
        self.db.execute(
            "INSERT INTO social_friend_requests(id,sender_id,recipient_id,status,created_at) "
            "VALUES('r4','u1','u2','pending',4)"
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
            "social_reports_reporter_rate_idx",
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

    def test_report_rate_limit_index_exists(self):
        names = {
            row[0] for row in self.db.execute(
                "SELECT name FROM sqlite_master WHERE type='index'"
            )
        }
        self.assertIn("social_reports_reporter_rate_idx", names)

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



class SocialHubApiContractTests(unittest.TestCase):
    """Fast source-level regression checks for security-sensitive handler branches.

    These complement (but do not replace) live Pages/D1 integration tests.
    """

    @classmethod
    def setUpClass(cls):
        root = Path(__file__).resolve().parents[1]
        cls.api = (root / "functions" / "api" / "social" / "[[path]].js").read_text(encoding="utf-8")
        cls.page = (root / "social.html").read_text(encoding="utf-8")

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
                ('u4', 'owner', 'owner', '2026-01-01');
            """
        )
        self.db.executescript(MIGRATION.read_text(encoding="utf-8"))

    def tearDown(self):
        self.db.close()

    def test_api_requires_existing_signed_in_session_and_origin_checks_mutations(self):
        self.assertIn("await signedIn(request, env)", self.api)
        self.assertIn('if (method !== "GET" && !sameOrigin(request))', self.api)
        self.assertIn("const db = env.VOIDCRAFT_AUTH", self.api)

    def test_friend_request_delete_distinguishes_sender_cancel_from_recipient_decline(self):
        self.assertIn("CASE WHEN sender_id=? THEN 'cancelled' ELSE 'rejected' END", self.api)
        self.assertIn("status='pending' AND (sender_id=? OR recipient_id=?)", self.api)
        self.assertIn("Pending friend request not found.", self.api)

    def test_private_messages_require_friendship_and_no_active_block(self):
        self.assertGreaterEqual(self.api.count("You must be friends to"), 2)
        self.assertGreaterEqual(self.api.count("blockedEither(db,user.id,other)"), 2)
        self.assertIn("Messages must be 2000 characters or fewer.", self.api)
        self.assertIn("recentMessages?.total", self.api)

    def test_owner_role_is_checked_server_side_for_moderation(self):
        self.assertGreaterEqual(self.api.count('user.role!=="owner"'), 2)
        self.assertIn("Owner permission required.", self.api)

    def test_report_limits_and_allowlisted_categories_are_enforced_server_side(self):
        self.assertIn('["harassment","spam","inappropriate","impersonation","other"]', self.api)
        self.assertIn("recentReports?.total", self.api)
        self.assertIn("reporter_id,target_user_id,category,details,status,created_at", self.api)

    def test_moderation_status_is_allowlisted_and_missing_report_returns_404(self):
        self.assertIn('["open","reviewed","dismissed"].includes(status)', self.api)
        self.assertIn('["reviewed","dismissed"].includes(status)', self.api)
        self.assertIn('if (Number(result?.meta?.changes || 0) < 1) return fail("Report not found.",404);', self.api)

    def test_blocking_removes_friendship_disables_chat_and_invalidates_pending_requests(self):
        self.assertIn('DELETE FROM social_friendships WHERE user_low=? AND user_high=?', self.api)
        self.assertGreaterEqual(self.api.count("blockedEither(db,user.id,other)"), 2)
        self.assertIn("UPDATE social_friend_requests SET status=CASE WHEN sender_id=? THEN 'cancelled' ELSE 'rejected' END WHERE status='pending'", self.api)
        self.assertIn("A block must also invalidate pending requests", self.api)


    def test_page_renders_user_supplied_content_as_text_not_html(self):
        self.assertIn(".textContent=", self.page)
        self.assertNotRegex(self.page, r"\\.innerHTML\\s*=")



    def test_request_sender_cancels_and_recipient_rejects_pending_request(self):
        self.db.execute(
            "INSERT INTO social_friend_requests(id,sender_id,recipient_id,status,created_at) "
            "VALUES('cancel-me','u1','u2','pending',1)"
        )
        self.db.execute(
            "UPDATE social_friend_requests SET status=CASE WHEN sender_id=? "
            "THEN 'cancelled' ELSE 'rejected' END WHERE id=? AND status='pending' "
            "AND (sender_id=? OR recipient_id=?)",
            ("u1", "cancel-me", "u1", "u1"),
        )
        self.assertEqual(
            "cancelled",
            self.db.execute(
                "SELECT status FROM social_friend_requests WHERE id='cancel-me'"
            ).fetchone()[0],
        )
        self.db.execute(
            "INSERT INTO social_friend_requests(id,sender_id,recipient_id,status,created_at) "
            "VALUES('decline-me','u1','u2','pending',2)"
        )
        self.db.execute(
            "UPDATE social_friend_requests SET status=CASE WHEN sender_id=? "
            "THEN 'cancelled' ELSE 'rejected' END WHERE id=? AND status='pending' "
            "AND (sender_id=? OR recipient_id=?)",
            ("u2", "decline-me", "u2", "u2"),
        )
        self.assertEqual(
            "rejected",
            self.db.execute(
                "SELECT status FROM social_friend_requests WHERE id='decline-me'"
            ).fetchone()[0],
        )

    def test_moderation_status_constraint_accepts_only_supported_states(self):
        self.db.execute(
            "INSERT INTO social_reports(id,reporter_id,target_user_id,category,status,created_at) "
            "VALUES('rp-review','u1','u2','spam','reviewed',1)"
        )
        with self.assertRaises(sqlite3.IntegrityError):
            self.db.execute(
                "INSERT INTO social_reports(id,reporter_id,target_user_id,category,status,created_at) "
                "VALUES('rp-invalid','u1','u2','spam','approved',2)"
            )

    def test_blocking_invalidates_pending_requests_in_both_directions(self):
        self.db.executemany(
            "INSERT INTO social_friend_requests(id,sender_id,recipient_id,status,created_at) "
            "VALUES(?,?,?,'pending',?)",
            [
                ("outgoing", "u1", "u2", 1),
                ("incoming", "u2", "u1", 2),
            ],
        )
        # Mirror the status transition used by the blocking API.
        self.db.execute(
            "UPDATE social_friend_requests SET status=CASE WHEN sender_id=? "
            "THEN 'cancelled' ELSE 'rejected' END WHERE status='pending' "
            "AND ((sender_id=? AND recipient_id=?) OR (sender_id=? AND recipient_id=?))",
            ("u1", "u1", "u2", "u2", "u1"),
        )
        statuses = dict(self.db.execute(
            "SELECT id,status FROM social_friend_requests ORDER BY id"
        ).fetchall())
        self.assertEqual({"incoming": "rejected", "outgoing": "cancelled"}, statuses)


if __name__ == "__main__":
    unittest.main()
