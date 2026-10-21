from __future__ import annotations
import importlib.util
import tempfile
import unittest
from pathlib import Path

spec = importlib.util.spec_from_file_location("customize_mojo", Path(__file__).with_name("customize_mojo.py"))
assert spec is not None and spec.loader is not None
customizer = importlib.util.module_from_spec(spec)
spec.loader.exec_module(customizer)

class MojoCustomizerTests(unittest.TestCase):
    def fixture(self, root: Path) -> None:
        app = root / "app_mojo"
        res = app / "src/main/res"
        (res / "values").mkdir(parents=True)
        (res / "values-fr").mkdir()
        (app / "build.gradle").write_text('''android {
 namespace 'git.artdeell.mojo'
 defaultConfig {
  applicationId "git.artdeell.mjlaunch"
  minSdkVersion 21
 }
 buildTypes {
  debug {
   resValue 'string', 'application_package', 'git.artdeell.mjlaunch.debug'
   resValue 'string', 'storageProviderAuthorities', 'git.artdeell.mjlaunch.scoped.gamefolder.debug'
  }
  release {
   resValue 'string', 'application_package', 'git.artdeell.mjlaunch'
   resValue 'string', 'storageProviderAuthorities', 'git.artdeell.mjlaunch.scoped.gamefolder'
  }
}
}
return "justicia-build-label"
''', encoding="utf-8")
        (root / "settings.gradle").write_text("rootProject.name='MojoLauncher'\n", encoding="utf-8")
        (app / "src/main/AndroidManifest.xml").write_text("<manifest />\n", encoding="utf-8")
        (res / "values/colors.xml").write_text('''<resources>
<color name="minebutton_color">#91dffb</color>
<color name="background_app">#181818</color>
<color name="background_overlay">#464646</color>
<color name="background_status_bar">#242424</color>
<color name="background_bottom_bar">#232323</color>
<color name="primary_text">#FFFFFF</color>
<color name="secondary_text">#B2B2B2</color>
</resources>
''', encoding="utf-8")
        for locale in ("values", "values-fr"):
            (res / locale / "strings.xml").write_text('''<resources>
<string name="app_short_name">MJLauncher</string>
<string name="error_fatal">MJLauncher has unexpectedly crashed</string>
</resources>
''', encoding="utf-8")
        (root / "LICENSE").write_text("LGPL-3.0 test fixture\n", encoding="utf-8")

    def test_branding_authorities_palette_and_license(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            self.fixture(root)
            customizer.patch_upstream(root)
            gradle = (root / "app_mojo/build.gradle").read_text()
            self.assertIn('applicationId "com.mf.mylauncher"', gradle)
            self.assertIn("com.mf.mylauncher.debug", gradle)
            self.assertNotIn("git.artdeell.mjlaunch", gradle)
            self.assertIn("namespace 'git.artdeell.mojo'", gradle)
            self.assertIn('return "voidcraft-', gradle)
            for locale in ("values", "values-fr"):
                text = (root / "app_mojo/src/main/res" / locale / "strings.xml").read_text()
                self.assertIn("VoidCraft Launcher", text)
            self.assertIn("#8D6BFF", (root / "app_mojo/src/main/res/values/colors.xml").read_text())
            self.assertTrue((root / "VOIDCRAFT_MOJO_LICENSE.txt").is_file())

    def test_patch_is_idempotent(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            self.fixture(root)
            customizer.patch_upstream(root)
            first = (root / "app_mojo/build.gradle").read_text()
            customizer.patch_upstream(root)
            self.assertEqual(first, (root / "app_mojo/build.gradle").read_text())

    def test_rejects_api_above_28(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            self.fixture(root)
            p = root / "app_mojo/build.gradle"
            p.write_text(p.read_text().replace("minSdkVersion 21", "minSdkVersion 29"))
            with self.assertRaises(ValueError):
                customizer.patch_upstream(root)

if __name__ == "__main__":
    unittest.main()
