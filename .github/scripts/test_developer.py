from __future__ import annotations

import importlib.util
import tempfile
import unittest
import xml.etree.ElementTree as ET
from pathlib import Path

SCRIPT = Path(__file__).with_name("customize_developer.py")
SPEC = importlib.util.spec_from_file_location("customize_developer", SCRIPT)
assert SPEC is not None and SPEC.loader is not None
customizer = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(customizer)


class DeveloperScreenCustomizerTests(unittest.TestCase):
    def fixture(self, root: Path) -> None:
        res = root / "app_pojavlauncher/src/main/res"
        (res / "xml").mkdir(parents=True)
        (res / "values").mkdir(parents=True)
        (res / "xml/pref_main.xml").write_text(
            """<?xml version="1.0" encoding="utf-8"?>
<PreferenceScreen xmlns:android="http://schemas.android.com/apk/res/android">
    <Preference android:key="existing_setting" android:title="Existing" />
</PreferenceScreen>
""",
            encoding="utf-8",
        )
        (res / "values/strings.xml").write_text(
            """<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="existing_string">Existing</string>
</resources>
""",
            encoding="utf-8",
        )

    def files_under_test(self, root: Path) -> list[Path]:
        app = root / "app_pojavlauncher"
        return [
            app / "src/main/res/xml/pref_main.xml",
            app / "src/main/res/xml/pref_developer.xml",
            app / "src/main/res/values/strings.xml",
            app / "src/main/java/net/kdt/pojavlaunch/prefs/screens/LauncherPreferenceDeveloperFragment.java",
        ]

    def test_adds_settings_entry_security_policy_and_all_stages(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            self.fixture(root)
            customizer.patch_upstream(root)

            files = self.files_under_test(root)
            for path in files[:3]:
                ET.parse(path)

            main_xml = files[0].read_text(encoding="utf-8")
            page_xml = files[1].read_text(encoding="utf-8")
            strings_xml = files[2].read_text(encoding="utf-8")
            fragment_java = files[3].read_text(encoding="utf-8")

            self.assertIn('android:key="voidcraft_developer_testing"', main_xml)
            self.assertIn("LauncherPreferenceDeveloperFragment", main_xml)
            self.assertIn("voidcraft_dev_stage_local", page_xml)
            self.assertIn("voidcraft_dev_stage_private", page_xml)
            self.assertIn("voidcraft_dev_stage_web", page_xml)
            self.assertIn("voidcraft_dev_restrict_code", page_xml)
            self.assertIn("voidcraft_dev_restrict_data", page_xml)
            self.assertIn("voidcraft_dev_restrict_system", page_xml)
            self.assertIn("voidcraft_dev_restrict_files", page_xml)
            self.assertIn("voidcraft_dev_restrict_sources", page_xml)
            self.assertIn("voidcraft_dev_privacy", page_xml)
            self.assertIn("read-only explanation", fragment_java.lower())
            self.assertIn("not configured", strings_xml.lower())
            self.assertIn("not implemented", strings_xml.lower())
            self.assertIn("not private", strings_xml.lower())

            parsed_strings = ET.parse(files[2]).getroot()
            names = [node.get("name") for node in parsed_strings.findall("string")]
            self.assertEqual(len(names), len(set(names)), "String resource names must be unique")

    def test_screen_is_informational_and_requests_no_new_android_permissions(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            self.fixture(root)
            manifest = root / "app_pojavlauncher/src/main/AndroidManifest.xml"
            manifest.parent.mkdir(parents=True, exist_ok=True)
            manifest_text = (
                '<manifest xmlns:android="http://schemas.android.com/apk/res/android">'
                '<application /></manifest>\n'
            )
            manifest.write_text(manifest_text, encoding="utf-8")

            customizer.patch_upstream(root)

            self.assertEqual(manifest.read_text(encoding="utf-8"), manifest_text)
            fragment_java = self.files_under_test(root)[3].read_text(encoding="utf-8")
            page_xml = self.files_under_test(root)[1].read_text(encoding="utf-8")
            self.assertNotIn("requestPermissions(", fragment_java)
            self.assertNotIn("INTERNET", fragment_java)
            self.assertNotIn("android:permission", page_xml)
            self.assertNotIn("setOnPreferenceClickListener", fragment_java)

    def test_patch_is_idempotent(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            self.fixture(root)
            customizer.patch_upstream(root)
            files = self.files_under_test(root)
            before = {path: path.read_text(encoding="utf-8") for path in files}
            customizer.patch_upstream(root)
            after = {path: path.read_text(encoding="utf-8") for path in files}
            self.assertEqual(before, after)

    def test_refuses_to_overwrite_unexpected_existing_managed_fragment(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            self.fixture(root)
            conflict = (
                root / "app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/"
                "prefs/screens/LauncherPreferenceDeveloperFragment.java"
            )
            conflict.parent.mkdir(parents=True, exist_ok=True)
            conflict.write_text("// unknown existing content\n", encoding="utf-8")
            with self.assertRaisesRegex(ValueError, "Refusing to overwrite"):
                customizer.patch_upstream(root)


if __name__ == "__main__":
    unittest.main()
