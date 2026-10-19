from __future__ import annotations

import importlib.util
import tempfile
import unittest
import zipfile
from pathlib import Path


SCRIPT = Path(__file__).with_name("audit_third_party.py")
SPEC = importlib.util.spec_from_file_location("audit_third_party", SCRIPT)
assert SPEC is not None and SPEC.loader is not None
audit = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(audit)


class ThirdPartyAuditTests(unittest.TestCase):
    def test_license_inventory_records_sha256(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            license_file = root / "LICENSE"
            license_file.write_text("example license\n", encoding="utf-8")
            inventory = audit.license_inventory(root)
            self.assertEqual(len(inventory), 1)
            self.assertEqual(inventory[0]["path"], "LICENSE")
            self.assertEqual(inventory[0]["sha256"], audit.sha256_file(license_file))
            self.assertEqual(inventory[0]["bytes"], str(license_file.stat().st_size))

    def test_license_inventory_finds_suffixes_and_license_directories(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            licenses = root / "app" / "assets" / "licenses"
            licenses.mkdir(parents=True)
            for name in ("ANGLE_LICENSE", "PFFFT_LICENSE", "simple-license.txt"):
                (licenses / name).write_text("notice text\n", encoding="utf-8")
            other = root / "app" / "regular-resource.txt"
            other.write_text("ordinary content\n", encoding="utf-8")
            found = {item["path"] for item in audit.license_inventory(root)}
            self.assertIn("app/assets/licenses/ANGLE_LICENSE", found)
            self.assertIn("app/assets/licenses/PFFFT_LICENSE", found)
            self.assertIn("app/assets/licenses/simple-license.txt", found)
            self.assertNotIn("app/regular-resource.txt", found)

    def test_submodule_inventory_marks_mismatched_checked_out_and_missing(self) -> None:
        commit = "a" * 40
        inventory = audit.submodule_inventory(
            " " + commit + " libs/ready (v1.0)\n+"
            + commit + " libs/mismatch\n-" + commit + " libs/missing\n"
        )
        self.assertEqual(
            [item["state"] for item in inventory],
            ["checked-out", "wrong-commit", "not-initialized"],
        )
        self.assertEqual(inventory[0]["commit"], commit)

    def test_declared_submodules_show_missing_status_entries(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            (root / ".gitmodules").write_text(
                '[submodule "present"]\n\tpath = lib/present\n\turl = https://example.com/present.git\n'
                '[submodule "missing"]\n\tpath = lib/missing\n\turl = https://example.com/missing.git\n',
                encoding="utf-8",
            )
            declarations = audit.declared_submodule_inventory(
                root, " " + ("a" * 40) + " lib/present (main)\n"
            )
            self.assertEqual(len(declarations), 2)
            self.assertEqual(declarations[0]["checkout_state"], "checked-out")
            self.assertEqual(
                declarations[1]["checkout_state"],
                "declared-but-no-submodule-status-entry",
            )
            self.assertEqual(declarations[1]["checked_out_commit"], "UNKNOWN")

    def test_upstream_credit_inventory_flags_unknown_and_all_rights_reserved(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            (root / "README.md").write_text(
                "## Credits & Dependencies\n"
                "* [ANGLE](https://example.com/angle): All Rights Reserved.\n"
                "* [Widget](https://example.com/widget): Unknown license.\n"
                "* [Library](https://example.com/library): MIT License.\n"
                "## Roadmap\n",
                encoding="utf-8",
            )
            credits = audit.upstream_credit_inventory(root)
            self.assertEqual(len(credits), 3)
            self.assertEqual(
                [item["license_review"] for item in credits],
                [
                    "review-required",
                    "review-required",
                    "verify-exact-component-license",
                ],
            )
            self.assertIn("https://example.com/angle", credits[0]["source_urls"])

    def test_apk_inventory_hashes_native_and_notice_entries(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            apk = Path(temp) / "sample.apk"
            with zipfile.ZipFile(apk, "w") as archive:
                archive.writestr("lib/arm64-v8a/libsample.so", b"native")
                archive.writestr("META-INF/NOTICE.txt", b"notice")
                archive.writestr("res/layout/main.xml", b"layout")
            inventory = audit.apk_inventory(apk)
            self.assertEqual(
                [item["path"] for item in inventory],
                ["META-INF/NOTICE.txt", "lib/arm64-v8a/libsample.so"],
            )
            self.assertTrue(all(len(item["sha256"]) == 64 for item in inventory))

    def test_report_never_claims_legal_clearance(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            (root / "LICENSE").write_text("example\n", encoding="utf-8")
            apk = root / "sample.apk"
            with zipfile.ZipFile(apk, "w") as archive:
                archive.writestr("classes.dex", b"dex")
            report = audit.build_report(root, apk, " " + ("b" * 40) + " lib/module (main)")
            self.assertIn("INCOMPLETE", report["legal_status"])
            self.assertIn("limitations", report)
            self.assertEqual(len(report["apk_sha256"]), 64)
            self.assertEqual(len(report["apk_selected_entries"]), 1)

    def test_gradle_scan_ignores_comments_and_non_dependency_log_strings(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            (root / "app.gradle").write_text(
                "dependencies {\n"
                "    implementation 'group:artifact:1.2.3'\n"
                "    // implementation 'commented:out:9.9'\n"
                "    logger.warn('no implementation dependency here')\n"
                "}\n",
                encoding="utf-8",
            )
            found = audit.gradle_dependency_lines(root)
            self.assertEqual(len(found), 1)
            self.assertIn("group:artifact:1.2.3", found[0]["text"])


if __name__ == "__main__":
    unittest.main()
