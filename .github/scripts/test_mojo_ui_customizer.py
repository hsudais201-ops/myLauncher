from __future__ import annotations
import importlib.util
import tempfile
import unittest
import xml.etree.ElementTree as ET
from pathlib import Path

spec = importlib.util.spec_from_file_location("customize_mojo_ui", Path(__file__).with_name("customize_mojo_ui.py"))
assert spec is not None and spec.loader is not None
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)

class MojoUiCustomizerTests(unittest.TestCase):
    def fixture(self, root: Path) -> None:
        app = root / "app_mojo"
        (app / "src/main/res/layout").mkdir(parents=True)
        (app / "src/main/res/values").mkdir(parents=True)
        (app / "src/main/res/layout/fragment_launcher.xml").write_text('''<?xml version="1.0" encoding="utf-8"?>
<androidx.constraintlayout.widget.ConstraintLayout xmlns:android="http://schemas.android.com/apk/res/android" xmlns:app="http://schemas.android.com/apk/res-auto">
<com.kdt.mcgui.LauncherMenuButton\n android:id="@+id/news_button"\n app:layout_constraintTop_toTopOf="parent" />
<View android:id="@+id/social_divider" app:layout_constraintTop_toTopOf="parent" />
<com.kdt.mcgui.LauncherMenuButton\n android:id="@+id/social_media_button"\n app:layout_constraintTop_toTopOf="parent" />
</androidx.constraintlayout.widget.ConstraintLayout>
''', encoding="utf-8")
        (app / "src/main/res/values/strings.xml").write_text("<resources></resources>\n", encoding="utf-8")

    def test_adds_hero_and_updates_layout_constraints(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            self.fixture(root)
            module.patch_upstream(root)
            layout = (root / "app_mojo/src/main/res/layout/fragment_launcher.xml").read_text()
            ET.fromstring(layout)
            self.assertIn('android:id="@+id/voidcraft_hero"', layout)
            self.assertIn('app:layout_constraintTop_toBottomOf="@id/voidcraft_hero"', layout)
            self.assertIn('app:layout_constraintTop_toTopOf="@id/news_button"', layout)
            self.assertTrue((root / "app_mojo/src/main/res/drawable/voidcraft_hero_background.xml").is_file())

    def test_is_idempotent(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            self.fixture(root)
            module.patch_upstream(root)
            first = (root / "app_mojo/src/main/res/layout/fragment_launcher.xml").read_text()
            module.patch_upstream(root)
            self.assertEqual(first, (root / "app_mojo/src/main/res/layout/fragment_launcher.xml").read_text())

    def test_fails_closed_if_anchor_changes(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            self.fixture(root)
            p = root / "app_mojo/src/main/res/layout/fragment_launcher.xml"
            p.write_text("<layout />")
            with self.assertRaises(ValueError):
                module.patch_upstream(root)

if __name__ == "__main__":
    unittest.main()
