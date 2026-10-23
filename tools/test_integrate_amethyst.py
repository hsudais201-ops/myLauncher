import unittest

from integrate_amethyst import patch_launcher_activity


class IntegrateAmethystTest(unittest.TestCase):
    def test_inserts_selected_version_handoff_at_stable_upstream_anchors(self):
        source = """package net.kdt.pojavlaunch;

public class LauncherActivity {
    protected void onCreate() {
        bindViews();
        checkNotificationPermission();
    }

    @Override
    protected void onResume() {
        super.onResume();
    }
}
"""
        patched = patch_launcher_activity(source)

        self.assertIn("applyVoidcraftSelectedVersion();", patched)
        self.assertIn("private void applyVoidcraftSelectedVersion()", patched)
        self.assertIn('getIntent().getStringExtra("voidcraft.selected_version")', patched)
        self.assertIn("LauncherProfiles.write();", patched)
        self.assertIn('new File(Tools.DIR_HOME_VERSION, requestedVersion)', patched)
        self.assertEqual(1, patched.count("applyVoidcraftSelectedVersion();"))

    def test_fails_closed_when_upstream_source_does_not_match_expected_anchors(self):
        with self.assertRaises(RuntimeError):
            patch_launcher_activity("public class LauncherActivity {}")


if __name__ == "__main__":
    unittest.main()
