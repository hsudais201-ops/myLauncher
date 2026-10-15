from __future__ import annotations

import importlib.util
import tempfile
import unittest
from pathlib import Path


SCRIPT = Path(__file__).with_name("customize_amethyst.py")
SPEC = importlib.util.spec_from_file_location("customize_amethyst", SCRIPT)
assert SPEC is not None and SPEC.loader is not None
customizer = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(customizer)


class CustomizeAmethystTests(unittest.TestCase):
    def fixture(self, root: Path) -> None:
        app = root / "app_pojavlauncher"
        (app / "src/main/res/values").mkdir(parents=True)
        (app / "build.gradle").write_text(
            '''android {
  defaultConfig { applicationId "org.angelauramc.amethyst" }
  buildTypes {
    debug {
      applicationIdSuffix '.debug'
      resValue "string", "app_name", "Amethyst (Debug)"
      resValue "string", "app_short_name", "Amethyst (Debug)"
      resValue 'string', 'application_package', 'org.angelauramc.amethyst.debug'
      resValue 'string', 'storageProviderAuthorities', 'org.angelauramc.amethyst.scoped.gamefolder.debug'
      resValue 'string', 'shareProviderAuthority', 'org.angelauramc.amethyst.scoped.controlfolder.debug'
    }
    release {
      resValue "string", "app_name", "Amethyst"
      resValue "string", "app_short_name", "Amethyst"
      resValue 'string', 'storageProviderAuthorities', 'org.angelauramc.amethyst.scoped.gamefolder'
      resValue 'string', 'application_package', 'org.angelauramc.amethyst'
    }
  }
}
''',
            encoding="utf-8",
        )
        (app / "src/main/res/values/strings.xml").write_text(
            '''<resources>
  <string name="app_name">Amethyst</string>
  <string name="app_short_name">Amethyst</string>
  <string name="error_fatal">Amethyst crashed</string>
  <string name="storageProviderAuthorities">org.angelauramc.amethyst.scoped.gamefolder</string>
</resources>
''',
            encoding="utf-8",
        )
        (root / "settings.gradle").write_text("rootProject.name='Amethyst'\n", encoding="utf-8")

    def test_patches_branding_and_ids(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            self.fixture(root)
            customizer.patch_upstream(root)
            build = (root / "app_pojavlauncher/build.gradle").read_text(encoding="utf-8")
            strings = (root / "app_pojavlauncher/src/main/res/values/strings.xml").read_text(encoding="utf-8")
            settings = (root / "settings.gradle").read_text(encoding="utf-8")
            self.assertIn('applicationId "com.mf.mylauncher"', build)
            self.assertIn("com.mf.mylauncher.scoped.gamefolder", build)
            self.assertIn('"myLauncher (Debug)"', build)
            self.assertNotIn("org.angelauramc.amethyst", build)
            self.assertNotIn("Amethyst", strings)
            self.assertIn("myLauncher crashed", strings)
            self.assertIn("rootProject.name='myLauncher'", settings)

    def test_is_idempotent(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            self.fixture(root)
            customizer.patch_upstream(root)
            first = (root / "app_pojavlauncher/build.gradle").read_text(encoding="utf-8")
            customizer.patch_upstream(root)
            second = (root / "app_pojavlauncher/build.gradle").read_text(encoding="utf-8")
            self.assertEqual(first, second)

    def test_missing_upstream_file_fails_clearly(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            with self.assertRaises(FileNotFoundError):
                customizer.patch_upstream(Path(temp))


if __name__ == "__main__":
    unittest.main()