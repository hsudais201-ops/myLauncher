import hashlib
import json
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import feature_store


class FeatureStoreTests(unittest.TestCase):
    def test_catalog_packages_have_matching_hashes_and_ids(self):
        catalog_path = feature_store.REPO_ROOT / "feature-store" / "catalog.json"
        catalog = json.loads(catalog_path.read_text(encoding="utf-8"))
        self.assertEqual(catalog["schemaVersion"], 1)
        self.assertGreaterEqual(len(catalog["features"]), 20)
        seen = set()
        for entry in catalog["features"]:
            self.assertNotIn(entry["id"], seen)
            self.assertNotIn("category", entry)
            self.assertNotIn("asset", entry)
            seen.add(entry["id"])
            package_name = entry["packageUrl"].split("?", 1)[0].rsplit("/", 1)[-1]
            package_path = catalog_path.parent / "packages" / package_name
            raw = package_path.read_bytes()
            self.assertEqual(hashlib.sha256(raw).hexdigest(), entry["sha256"])
            package = json.loads(raw.decode("utf-8"))
            self.assertEqual(package["schemaVersion"], 1)
            self.assertEqual(package["id"], entry["id"])
            for metadata_key in ("capabilities", "minAndroidApi", "maxAndroidApi", "dataUse"):
                self.assertEqual(entry.get(metadata_key), package.get(metadata_key), metadata_key)
            screen = package.get("screen", {})
            self.assertIn(screen.get("type"), {"page", "theme", "server_status", "social", "workspace", "device_report"})
            if screen.get("type") == "workspace":
                self.assertGreaterEqual(len(screen.get("fields", [])), 1)
                self.assertLessEqual(len(screen.get("fields", [])), 8)
            self.assertTrue(entry["packageUrl"].startswith("https://"))
            self.assertNotIn("category", package)
            self.assertIn(package["owner"], ("VoidCraft Launcher", "VoidCraft Community"))

    def test_patch_adds_feature_store_without_deleting_existing_core_files(self):
        with tempfile.TemporaryDirectory() as directory:
            upstream = Path(directory)
            app = upstream / "app_pojavlauncher" / "src" / "main"
            (app / "res" / "values").mkdir(parents=True)
            (app / "res" / "layout").mkdir(parents=True)
            (app / "res" / "layout-land").mkdir(parents=True)
            package = app / "java" / "net" / "kdt" / "pojavlaunch"
            (package / "fragments").mkdir(parents=True)
            (package / "BaseActivity.java").write_text(
                "class BaseActivity { void onPostResume() { "
                "Tools.ignoreNotch(shouldIgnoreNotch(),this); } }\n", encoding="utf-8")
            (package / "fragments" / "MainMenuFragment.java").write_text(
                "package net.kdt.pojavlaunch.fragments;\n"
                "import android.content.Intent;\n"
                "import android.widget.Button;\n"
                "import net.kdt.pojavlaunch.CustomControlsActivity;\n"
                "class MainMenuFragment {\n"
                "  void onViewCreated(View view) {\n"
                "    Button mMyLauncherAccountButton = view.findViewById(R.id.mylauncher_account_button);\n"
                "    mVersionSpinner = view.findViewById(R.id.mc_version_spinner);\n"
                "  }\n}\n", encoding="utf-8")
            layout = (
                '<androidx.constraintlayout.widget.ConstraintLayout '
                'xmlns:android="http://schemas.android.com/apk/res/android" '
                'xmlns:app="http://schemas.android.com/apk/res-auto">'
                '<com.kdt.mcgui.LauncherMenuButton '
                'android:id="@+id/mylauncher_account_button" '
                'style="@style/LauncherMenuButton.Universal" '
                'app:layout_constraintTop_toBottomOf="@id/mylauncher_repair_version_button" />\n'
                '</androidx.constraintlayout.widget.ConstraintLayout>\n')
            (app / "res" / "layout" / "fragment_launcher.xml").write_text(layout, encoding="utf-8")
            (app / "res" / "layout-land" / "fragment_launcher.xml").write_text(layout, encoding="utf-8")
            (app / "res" / "values" / "strings.xml").write_text(
                '<resources><string name="app_name">Test</string></resources>\n', encoding="utf-8")
            (app / "AndroidManifest.xml").write_text(
                '<manifest xmlns:android="http://schemas.android.com/apk/res/android"><application>\n'
                '        <activity\n            android:name=".LauncherActivity"\n'
                '            android:label="@string/app_short_name" />\n'
                '    </application></manifest>\n', encoding="utf-8")

            feature_store.patch_upstream(upstream, feature_store.REPO_ROOT)
            feature_store.patch_upstream(upstream, feature_store.REPO_ROOT)

            manifest = (app / "AndroidManifest.xml").read_text(encoding="utf-8")
            strings = (app / "res" / "values" / "strings.xml").read_text(encoding="utf-8")
            menu = (package / "fragments" / "MainMenuFragment.java").read_text(encoding="utf-8")
            base = (package / "BaseActivity.java").read_text(encoding="utf-8")
            self.assertEqual(manifest.count('android:name=".FeatureStoreActivity"'), 1)
            self.assertEqual(strings.count('name="mylauncher_feature_store"'), 1)
            self.assertEqual(menu.count("mylauncher_feature_store_button"), 1)
            self.assertEqual(menu.count("FeatureStoreActivity.class"), 1)
            self.assertEqual(base.count("FeatureTheme.apply(this);"), 1)
            self.assertTrue((package / "FeatureStoreActivity.java").is_file())
            self.assertTrue((package / "FeatureTheme.java").is_file())
            self.assertFalse((app / "assets" / "feature-store").exists(),
                             "Available feature packages/catalog must not be copied into APK assets")
            for relative in ("res/layout/fragment_launcher.xml", "res/layout-land/fragment_launcher.xml"):
                text = (app / relative).read_text(encoding="utf-8")
                self.assertEqual(text.count('android:id="@+id/mylauncher_feature_store_button"'), 1)
                self.assertIn('app:layout_constraintTop_toBottomOf="@id/mylauncher_account_button"', text)

    def test_optional_features_are_downloaded_only_after_install(self):
        root = feature_store.REPO_ROOT
        script = (root / ".github/scripts/feature_store.py").read_text(encoding="utf-8")
        activity = (root / "feature-store/src/main/java/net/kdt/pojavlaunch/FeatureStoreActivity.java").read_text(encoding="utf-8")

        self.assertIn("_validate_store_catalog()", script)
        self.assertIn("Feature Store catalog/packages must not be bundled into APK assets", script)
        self.assertNotIn("shutil.copyfile(SOURCE_ROOT / \"catalog.json\"", script)
        self.assertNotIn("packages_destination", script)
        self.assertIn("loadCachedCatalog();", activity)
        self.assertIn("cacheCatalog(raw);", activity)
        self.assertIn("String packageText = fetchHttps(url, MAX_PACKAGE_BYTES);", activity)
        self.assertIn("Never load a feature from APK assets", activity)
        self.assertNotIn("loadBundledCatalog", activity)
        self.assertNotIn("readAsset(", activity)
        self.assertNotIn("getAssets().open", activity)

    def test_server_status_source_has_live_fields_and_safe_icon_checks(self):
        root = feature_store.REPO_ROOT / "feature-store"
        catalog = json.loads((root / "catalog.json").read_text(encoding="utf-8"))
        entry = next(x for x in catalog["features"] if x["id"] == "community.server-status")
        package_name = entry["packageUrl"].split("?", 1)[0].rsplit("/", 1)[-1]
        package = json.loads((root / "packages" / package_name).read_text(encoding="utf-8"))
        self.assertEqual(package["screen"]["type"], "server_status")
        self.assertIn("online/offline", package["description"].lower())
        self.assertIn("server icon", package["description"].lower())
        self.assertIn("MOTD", package["screen"]["description"])
        self.assertIn("player count", package["screen"]["description"].lower())
        activity = (root / "src/main/java/net/kdt/pojavlaunch/FeatureStoreActivity.java").read_text(encoding="utf-8")
        for marker in ("decodeServerIcon", "fetchHttpsBytes", "Players online:", "MOTD:",
                       "● ONLINE", "● OFFLINE", "UNKNOWN", "api.mcsrvstat.us/3/",
                       "api.mcsrvstat.us/icon/", "BitmapFactory.Options",
                       "setInstanceFollowRedirects(false)"):
            self.assertIn(marker, activity)

    def test_server_status_supports_saved_servers_edit_remove_and_refresh(self):
        activity = (feature_store.REPO_ROOT / "feature-store" / "src/main/java/net/kdt/pojavlaunch/FeatureStoreActivity.java").read_text(encoding="utf-8")
        for marker in ("serverStorageKey", "loadSavedServers", "saveServerList", "showServerEditor",
                       "confirmRemoveServer", "refreshServerStatus", "validServerHost",
                       "＋ Add server", "Remove server", "Server updated", "Server added",
                       "if (existing != null) hostInput.setText(existing.optString(\"host\", \"\"))",
                       "getSharedPreferences(\"voidcraft_feature_store\", MODE_PRIVATE)",
                       "Math.min(stored.length(), 100)"):
            self.assertIn(marker, activity)
        self.assertIn("server_status.", activity)
        self.assertIn('saved.put("name", name)', activity)
        self.assertIn('saved.put("host", host)', activity)

    def test_social_hub_is_catalogued_as_explicit_local_preview(self):
        catalog_path = feature_store.REPO_ROOT / "feature-store" / "catalog.json"
        catalog = json.loads(catalog_path.read_text(encoding="utf-8"))
        entry = next(item for item in catalog["features"] if item["id"] == "voidcraft.social-hub")
        package_name = entry["packageUrl"].split("?", 1)[0].rsplit("/", 1)[-1]
        package_path = catalog_path.parent / "packages" / package_name
        package = json.loads(package_path.read_text(encoding="utf-8"))
        self.assertEqual(package["screen"]["type"], "social")
        self.assertEqual(package["screen"]["mode"], "local_preview")
        self.assertIn("not connected", package["description"].lower())
        activity = (catalog_path.parent / "src/main/java/net/kdt/pojavlaunch/FeatureStoreActivity.java").read_text(encoding="utf-8")
        for marker in ("renderSocialScreen", "renderSocialProfile", "renderSocialFriends",
                       "renderSocialRequests", "renderSocialChats", "Swipe → Next",
                       "Microsoft / Xbox", "Google", "Send local preview message"):
            self.assertIn(marker, activity)
        self.assertNotIn('optString("category"', activity)
        self.assertNotIn('put("category"', activity)



    def test_new_store_screens_and_install_review_are_wired(self):
        store = feature_store.REPO_ROOT / "feature-store"
        catalog = json.loads((store / "catalog.json").read_text(encoding="utf-8"))
        ids = {entry["id"] for entry in catalog["features"]}
        expected = {"voidcraft.performance-advisor", "voidcraft.device-compatibility", "voidcraft.storage-inspector", "voidcraft.scratchpad", "voidcraft.personal-goals", "voidcraft.build-planner", "voidcraft.world-waypoints", "voidcraft.play-journal", "voidcraft.server-guidebook", "voidcraft.server-maintenance", "voidcraft.server-events", "voidcraft.playtime-log", "voidcraft.mod-collection-builder", "voidcraft.launch-preset-notes", "voidcraft.config-change-log", "voidcraft.screenshot-albums", "voidcraft.profile-card-draft", "voidcraft.feedback-drafts"}
        self.assertTrue(expected.issubset(ids))
        activity = (store / "src/main/java/net/kdt/pojavlaunch/FeatureStoreActivity.java").read_text(encoding="utf-8")
        for marker in ("renderWorkspaceScreen", "renderDeviceReportScreen", "showInstallReview",
                       "Declared capabilities", "validCompatibility", "sameCapabilities", "showImportedFeatureReview",
                       "No saved entries yet.", "Copy all", "Delete data & uninstall",
                       "Search features by name, description, or creator"):
            self.assertIn(marker, activity)
        self.assertIn('"workspace".equals(type)', activity)
        self.assertIn('"device_report".equals(type)', activity)

if __name__ == "__main__":
    unittest.main()
