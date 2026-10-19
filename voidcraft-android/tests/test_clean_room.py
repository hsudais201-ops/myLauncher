import re
import unittest
from pathlib import Path


PROJECT = Path(__file__).resolve().parents[1]
JAVA_ROOT = PROJECT / "app" / "src" / "main" / "java"
MANIFEST = PROJECT / "app" / "src" / "main" / "AndroidManifest.xml"
APP_GRADLE = PROJECT / "app" / "build.gradle"


class CleanRoomBoundaryTests(unittest.TestCase):
    def test_original_java_sources_do_not_import_other_launcher_packages(self):
        sources = list(JAVA_ROOT.rglob("*.java"))
        self.assertTrue(sources, "Expected first-party Java source files")
        banned = re.compile(r"\b(?:Amethyst|PojavLauncher)\b|net\.kdt\.pojavlaunch", re.IGNORECASE)
        for source in sources:
            with self.subTest(source=str(source)):
                self.assertIsNone(
                    banned.search(source.read_text(encoding="utf-8")),
                    f"External launcher source reference found in {source}",
                )

    def test_independent_application_id_and_android_9_minimum(self):
        build = APP_GRADLE.read_text(encoding="utf-8")
        self.assertIn("applicationId 'com.voidcraft.launcher'", build)
        self.assertRegex(build, r"minSdk\s+28")
        self.assertTrue(MANIFEST.is_file())

    def test_play_is_not_advertised_as_ready_without_runtime(self):
        source = (JAVA_ROOT / "com" / "voidcraft" / "launcher" / "MainActivity.java").read_text(encoding="utf-8")
        self.assertIn("PLAY — NOT AVAILABLE YET", source)
        self.assertIn("Minecraft has not been connected to this app yet", source)


if __name__ == "__main__":
    unittest.main()
