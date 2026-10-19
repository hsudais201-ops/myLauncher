"""Documentation regression tests for the VoidCraft Feature Store catalog.

These tests verify planning/documentation consistency only. They do not claim that
the planned Feature Store features are implemented or working at runtime.
"""
from pathlib import Path
import re
import unittest


ROOT = Path(__file__).resolve().parents[2]
SPEC_PATH = ROOT / "FEATURE_STORE_SPEC.md"
BUILT_IN_ONLY = {
    4: "Crash Assistant",
    7: "Touch Control Designer",
    8: "Device Compatibility Checker",
    9: "Modpack Import & Export",
    11: "Performance Advisor",
}


class FeatureStoreSpecTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.spec = SPEC_PATH.read_text(encoding="utf-8")
        # The numbered feature matrices use: | number | feature | placement | ...
        cls.rows = [
            (int(number), name.strip(), placement.strip())
            for number, name, placement in re.findall(
                r"^\|\s*(\d+)\s*\|\s*([^|]+?)\s*\|\s*([^|]+?)\s*\|",
                cls.spec,
                flags=re.MULTILINE,
            )
        ]

    def test_all_seventy_planned_items_are_present_once(self):
        ids = [number for number, _name, _placement in self.rows]
        self.assertEqual(list(range(1, 71)), sorted(ids))
        self.assertEqual(70, len(ids))
        self.assertEqual(70, len(set(ids)))

    def test_only_the_five_agreed_features_are_builtin(self):
        by_id = {number: (name, placement) for number, name, placement in self.rows}
        for number, expected_name in BUILT_IN_ONLY.items():
            with self.subTest(item=number):
                name, placement = by_id[number]
                self.assertEqual(expected_name, name)
                self.assertIn("Built into launcher", placement)

    def test_all_other_items_are_store_extensions_or_store_publishing(self):
        for number, name, placement in self.rows:
            if number in BUILT_IN_ONLY:
                continue
            with self.subTest(item=number, name=name):
                self.assertIn("Feature Store", placement)
                self.assertNotIn("Built into launcher", placement)

    def test_store_is_one_searchable_catalog_without_categories(self):
        self.assertIn("## Store experience — no category system", self.spec)
        self.assertIn(
            "Do not introduce mandatory feature categories, category tabs, or category-based navigation.",
            self.spec,
        )
        self.assertIn("one searchable catalog", self.spec.lower())

    def test_extension_security_and_isolation_requirements_are_documented(self):
        required_phrases = (
            "Never execute arbitrary uploaded code",
            "Verify publisher signature and package hash",
            "Install/update atomically",
            "permissions",
            "dependency cycles",
            "rollback",
            "Community uploads require review",
        )
        for phrase in required_phrases:
            with self.subTest(phrase=phrase):
                self.assertIn(phrase, self.spec)

    def test_core_launcher_and_existing_user_data_must_be_preserved(self):
        for phrase in (
            "Java runtimes",
            "native libraries",
            "renderer/input components",
            "Minecraft version metadata/download system",
            "loader installers",
            "existing user data",
        ):
            with self.subTest(phrase=phrase):
                self.assertIn(phrase, self.spec)

    def test_additional_backlog_is_explicitly_planned_not_claimed_complete(self):
        self.assertIn("Additional Feature Store backlog — items 16–70", self.spec)
        self.assertIn("Every item in this table is an optional Feature Store extension", self.spec)
        self.assertIn("does not claim these planned features are implemented", self.spec)


if __name__ == "__main__":
    unittest.main()
