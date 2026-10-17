from __future__ import annotations

import importlib.util
import tempfile
import unittest
from pathlib import Path


SCRIPT = Path(__file__).with_name("patch_renderpearl_workaround.py")
SPEC = importlib.util.spec_from_file_location("patch_renderpearl_workaround", SCRIPT)
assert SPEC is not None and SPEC.loader is not None
patcher = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(patcher)


class RenderPearlWorkaroundTests(unittest.TestCase):
    def fixture(self, root: Path) -> Path:
        main_activity = root / patcher.MAIN_ACTIVITY
        preferences = root / patcher.LAUNCHER_PREFERENCES
        main_activity.parent.mkdir(parents=True)
        preferences.parent.mkdir(parents=True)
        main_activity.write_text(
            """class MainActivity {
    void launch(String versionId) {
        LauncherPreferences.writeMGRendererSettings();
    }
}
""",
            encoding="utf-8",
        )
        preferences.write_text(
            """import java.io.IOException;
class LauncherPreferences {
    static Preferences DEFAULT_PREF;
    static Logger Log;
    public static void writeMGRendererSettings() throws IOException {
        LinkedHashMap<String, Object> MGConfigJson = new LinkedHashMap<>();
        MGConfigJson.put("enableNoError", Integer.parseInt(DEFAULT_PREF.getString("mg_renderer_setting_errorSetting", "0")));
    }
}
""",
            encoding="utf-8",
        )
        return root

    def test_installs_version_gated_partial_filter_and_passes_version_id(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = self.fixture(Path(temp))
            patcher.patch_upstream(root)
            activity = (root / patcher.MAIN_ACTIVITY).read_text(encoding="utf-8")
            preferences = (root / patcher.LAUNCHER_PREFERENCES).read_text(encoding="utf-8")

            self.assertIn("LauncherPreferences.writeMGRendererSettings(versionId);", activity)
            self.assertIn("writeMGRendererSettings(String versionId)", preferences)
            self.assertIn(r'^26\\.3(?:$|[-+].*)', preferences)
            self.assertIn(r'^26\\.3-snapshot-1(?:$|[-+].*)', preferences)
            self.assertIn("isRenderPearlWorkaroundTarget(versionId) && errorSetting == 0", preferences)
            self.assertIn("errorSetting = 2;", preferences)
            self.assertIn('MGConfigJson.put("enableNoError", errorSetting);', preferences)

    def test_patch_is_idempotent(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = self.fixture(Path(temp))
            patcher.patch_upstream(root)
            first_activity = (root / patcher.MAIN_ACTIVITY).read_text(encoding="utf-8")
            first_preferences = (root / patcher.LAUNCHER_PREFERENCES).read_text(encoding="utf-8")
            patcher.patch_upstream(root)
            self.assertEqual(first_activity, (root / patcher.MAIN_ACTIVITY).read_text(encoding="utf-8"))
            self.assertEqual(first_preferences, (root / patcher.LAUNCHER_PREFERENCES).read_text(encoding="utf-8"))

    def test_fails_closed_when_upstream_anchor_changes(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = self.fixture(Path(temp))
            path = root / patcher.MAIN_ACTIVITY
            path.write_text("class MainActivity { }\n", encoding="utf-8")
            with self.assertRaises(ValueError):
                patcher.patch_upstream(root)


if __name__ == "__main__":
    unittest.main()
