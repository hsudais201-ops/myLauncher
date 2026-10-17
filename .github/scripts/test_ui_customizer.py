from __future__ import annotations

import importlib.util
import tempfile
import unittest
import xml.etree.ElementTree as ET
from pathlib import Path

SCRIPT = Path(__file__).with_name("customize_ui.py")
SPEC = importlib.util.spec_from_file_location("customize_ui", SCRIPT)
assert SPEC is not None and SPEC.loader is not None
customizer = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(customizer)


class UiCustomizerTests(unittest.TestCase):
    def fixture(self, root: Path) -> None:
        app = root / "app_pojavlauncher"
        res = app / "src/main/res"
        (res / "values").mkdir(parents=True)
        (res / "layout").mkdir(parents=True)
        (res / "layout-land").mkdir(parents=True)
        (res / "drawable").mkdir(parents=True)
        (app / "src/main/java/net/kdt/pojavlaunch").mkdir(parents=True)
        (app / "src/main/java/net/kdt/pojavlaunch/prefs").mkdir(parents=True)
        (app / "src/main/java/com/kdt/mcgui").mkdir(parents=True)

        (res / "values/colors.xml").write_text("""<resources>
    <color name="minebutton_color">#9649b8</color>
    <color name="background_app">#181818</color>
    <color name="background_overlay">#464646</color>
    <color name="divider">@color/background_overlay</color>
    <color name="background_status_bar">#242424</color>
    <color name="background_bottom_bar">#232323</color>
    <color name="primary_text">#FFFFFF</color>
    <color name="secondary_text">#B2B2B2</color>
</resources>
""", encoding="utf-8")
        (res / "values/styles.xml").write_text("""<resources>
    <style name="AppTheme" parent="@style/Theme.AppCompat.NoActionBar">
        <item name="windowNoTitle">true</item>
        <item name="windowActionBar">false</item>
        <item name="android:windowContentOverlay">@null</item>
        <item name="android:statusBarColor">@color/background_status_bar</item>
    </style>
    <style name="LauncherMenuButton" parent="Widget.AppCompat.Button">
        <item name="android:background">?android:attr/selectableItemBackground</item>
    </style>
    <style name="LauncherMenuButton.Universal" parent="LauncherMenuButton">
        <item name="android:layout_height">66dp</item>
    </style>
</resources>
""", encoding="utf-8")
        (res / "values/strings.xml").write_text("""<resources>
    <string name="main_add_account">Add account</string>
</resources>
""", encoding="utf-8")

        for relative in ("layout/fragment_launcher.xml", "layout-land/fragment_launcher.xml"):
            (res / relative).write_text("""<androidx.constraintlayout.widget.ConstraintLayout
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto">
    <TextView android:id="@+id/mylauncher_hero_title"
        android:layout_width="0dp" android:text="myLauncher" />
    <TextView android:id="@+id/mylauncher_hero_subtitle"
        android:layout_width="0dp" android:text="Minecraft Java Edition" />
    <com.kdt.mcgui.LauncherMenuButton
        android:id="@+id/news_button"
        app:layout_constraintTop_toBottomOf="@id/mylauncher_hero_subtitle" />
    <View android:id="@+id/social_divider"
        app:layout_constraintTop_toTopOf="@id/news_button" />
    <com.kdt.mcgui.LauncherMenuButton
        android:id="@+id/discord_button"
        app:layout_constraintTop_toBottomOf="@id/mylauncher_hero_subtitle" />
</androidx.constraintlayout.widget.ConstraintLayout>
""", encoding="utf-8")

        (res / "drawable/mylauncher_icon.xml").write_text("""<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp" android:height="108dp" android:viewportWidth="108" android:viewportHeight="108">
    <path android:fillColor="#171B26" android:pathData="M0,0h108v108h-108z" />
    <path android:fillColor="#7C5CFF" android:pathData="M18,28H32L54,54L76,28H90V80H76V50L54,76L32,50V80H18Z" />
</vector>
""", encoding="utf-8")

        (app / "src/main/java/com/kdt/mcgui/MineButton.java").write_text(
            'class MineButton { void init() { setBackground(ResourcesCompat.getDrawable(getResources(), R.drawable.mine_button_background, null)); } }\n',
            encoding="utf-8",
        )
        (app / "src/main/java/com/kdt/mcgui/MineEditText.java").write_text(
            'class MineEditText { void init() { setBackgroundColor(Color.parseColor("#131313")); } }\n',
            encoding="utf-8",
        )
        (app / "src/main/java/net/kdt/pojavlaunch/prefs/LauncherPreferences.java").write_text("""package net.kdt.pojavlaunch.prefs;
import net.kdt.pojavlaunch.Tools;
class LauncherPreferences {
    public static SharedPreferences DEFAULT_PREF;
    public static void writeMGRendererSettings() throws IOException {
        LinkedHashMap<String, Object> MGConfigJson = new LinkedHashMap<>();
        MGConfigJson.put("enableNoError", Integer.parseInt(DEFAULT_PREF.getString("mg_renderer_setting_errorSetting", "0")));
    }
}
""", encoding="utf-8")
        (app / "src/main/java/net/kdt/pojavlaunch/LauncherActivity.java").write_text("""package net.kdt.pojavlaunch;
class LauncherActivity {
    void launch(String normalizedVersionId) {
        new MinecraftDownloader().start(
            this, normalizedVersionId
        );
    }
}
""", encoding="utf-8")

    def test_original_theme_applies_across_shared_controls(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            self.fixture(root)
            customizer.patch_upstream(root)
            res = root / "app_pojavlauncher/src/main/res"
            colors = (res / "values/colors.xml").read_text(encoding="utf-8")
            styles = (res / "values/styles.xml").read_text(encoding="utf-8")
            play = (root / "app_pojavlauncher/src/main/java/com/kdt/mcgui/MineButton.java").read_text(encoding="utf-8")
            edit = (root / "app_pojavlauncher/src/main/java/com/kdt/mcgui/MineEditText.java").read_text(encoding="utf-8")
            self.assertIn("#0C1020", colors)
            self.assertIn("@color/mylauncher_accent", styles)
            self.assertIn("@drawable/mylauncher_button_background", styles)
            self.assertIn("R.drawable.mylauncher_play_button_background", play)
            self.assertIn("R.drawable.mylauncher_input_background", edit)
            for filename in (
                "mylauncher_button_background.xml",
                "mylauncher_play_button_background.xml",
                "mylauncher_input_background.xml",
                "mylauncher_hero_background.xml",
            ):
                self.assertTrue((res / "drawable" / filename).is_file())

    def test_renderpearl_override_is_version_and_renderer_scoped(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            self.fixture(root)
            customizer.patch_upstream(root)
            activity = (root / "app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/LauncherActivity.java").read_text(encoding="utf-8")
            prefs = (root / "app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/prefs/LauncherPreferences.java").read_text(encoding="utf-8")
            self.assertIn('normalizedVersionId.matches("^26\\\\.(?:3|[4-9])(?:[.-].*|$)")', activity)
            self.assertIn("LauncherPreferences.writeMGRendererSettings();", activity)
            self.assertNotIn('"opengles_mobileglues".equals(Tools.LOCAL_RENDERER)', activity)
            self.assertIn("LauncherPreferences.MYLAUNCHER_RENDERPEARL_COMPAT = false;", activity)
            self.assertIn('errorSetting = "1";', prefs)
            self.assertIn('DEFAULT_PREF.getString("mg_renderer_setting_errorSetting", "0")', prefs)
            self.assertIn("mylauncher_26x_renderer_compat", (root / "app_pojavlauncher/src/main/res/values/strings.xml").read_text(encoding="utf-8"))

    def test_home_panel_is_present_in_both_orientations(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            self.fixture(root)
            customizer.patch_upstream(root)
            res = root / "app_pojavlauncher/src/main/res"
            for relative in ("layout/fragment_launcher.xml", "layout-land/fragment_launcher.xml"):
                text = (res / relative).read_text(encoding="utf-8")
                self.assertEqual(text.count('android:id="@+id/mylauncher_hero_surface"'), 1)
                self.assertIn("@drawable/mylauncher_hero_background", text)
                self.assertIn('app:layout_constraintTop_toBottomOf="@id/mylauncher_hero_surface"', text)

    def test_resources_are_well_formed_and_patch_is_idempotent(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            self.fixture(root)
            customizer.patch_upstream(root)
            res = root / "app_pojavlauncher/src/main/res"
            xml_paths = list((res / "values").glob("*.xml")) + list((res / "layout").glob("*.xml")) + list((res / "layout-land").glob("*.xml")) + list((res / "drawable").glob("*.xml"))
            source_paths = [
                root / "app_pojavlauncher/src/main/java/com/kdt/mcgui/MineButton.java",
                root / "app_pojavlauncher/src/main/java/com/kdt/mcgui/MineEditText.java",
                root / "app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/LauncherActivity.java",
                root / "app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/prefs/LauncherPreferences.java",
            ]
            paths = xml_paths + source_paths
            before = {p: p.read_text(encoding="utf-8") for p in paths}
            for path in xml_paths:
                ET.parse(path)
            customizer.patch_upstream(root)
            after = {p: p.read_text(encoding="utf-8") for p in paths}
            self.assertEqual(before, after)


if __name__ == "__main__":
    unittest.main()
