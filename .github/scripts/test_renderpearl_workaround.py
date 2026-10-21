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

    def test_installs_version_gate_and_preserves_explicit_settings(self) -> None:
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
            self.assertIn("Applying MobileGlues partial error filtering workaround", preferences)
            self.assertNotIn('MGConfigJson.put("enableNoError", Integer.parseInt', preferences)

    def test_patch_is_idempotent(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = self.fixture(Path(temp))
            patcher.patch_upstream(root)
            first_activity = (root / patcher.MAIN_ACTIVITY).read_text(encoding="utf-8")
            first_preferences = (root / patcher.LAUNCHER_PREFERENCES).read_text(encoding="utf-8")
            patcher.patch_upstream(root)
            self.assertEqual(first_activity, (root / patcher.MAIN_ACTIVITY).read_text(encoding="utf-8"))
            self.assertEqual(first_preferences, (root / patcher.LAUNCHER_PREFERENCES).read_text(encoding="utf-8"))

    def test_fails_closed_when_activity_anchor_changes(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = self.fixture(Path(temp))
            activity = root / patcher.MAIN_ACTIVITY
            original_preferences = (root / patcher.LAUNCHER_PREFERENCES).read_text(encoding="utf-8")
            activity.write_text("class MainActivity { }\n", encoding="utf-8")
            with self.assertRaisesRegex(ValueError, "Expected exactly one patch anchor"):
                patcher.patch_upstream(root)
            self.assertEqual("class MainActivity { }\n", activity.read_text(encoding="utf-8"))
            self.assertEqual(original_preferences, (root / patcher.LAUNCHER_PREFERENCES).read_text(encoding="utf-8"))

    def test_fails_without_partially_patching_when_preferences_anchor_changes(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = self.fixture(Path(temp))
            activity = root / patcher.MAIN_ACTIVITY
            preferences = root / patcher.LAUNCHER_PREFERENCES
            original_activity = activity.read_text(encoding="utf-8")
            prefs_text = preferences.read_text(encoding="utf-8").replace(
                'MGConfigJson.put("enableNoError", Integer.parseInt(DEFAULT_PREF.getString("mg_renderer_setting_errorSetting", "0")));',
                'MGConfigJson.put("enableNoError", -1);',
            )
            preferences.write_text(prefs_text, encoding="utf-8")
            with self.assertRaisesRegex(ValueError, "Expected exactly one patch anchor"):
                patcher.patch_upstream(root)
            self.assertEqual(original_activity, activity.read_text(encoding="utf-8"))

    def test_missing_upstream_file_fails_clearly(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            with self.assertRaisesRegex(FileNotFoundError, "Expected upstream file is missing"):
                patcher.patch_upstream(Path(temp))


class RenderPearlWorkflowTests(unittest.TestCase):
    def test_mojo_workflow_does_not_run_amethyst_specific_patch(self) -> None:
        workflow_path = Path(__file__).parents[1] / "workflows" / "build.yml"
        workflow = workflow_path.read_text(encoding="utf-8")
        self.assertIn("Apply VoidCraft package identity, app label, and night-violet palette", workflow)
        self.assertNotIn("python3 .github/scripts/patch_renderpearl_workaround.py upstream", workflow)
        self.assertNotIn("Apply RenderPearl MobileGlues compatibility workaround", workflow)
        # The standalone patcher tests remain useful for its old upstream fixture,
        # but it must not be applied to the Mojo module without a separate port.
        self.assertIn("patch_renderpearl_workaround.py", Path(__file__).with_name("patch_renderpearl_workaround.py").name)


if __name__ == "__main__":
    unittest.main()
