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

    def test_saved_server_management_persists_and_supports_crud(self):
        source = (JAVA_ROOT / "com" / "voidcraft" / "launcher" / "MainActivity.java").read_text(encoding="utf-8")
        self.assertIn('KEY_SAVED_SERVERS = "saved_servers_json"', source)
        self.assertIn("private JSONArray readServers()", source)
        self.assertIn("private void saveServers(JSONArray servers)", source)
        self.assertIn("showServerEditorForNew", source)
        self.assertIn("showServerEditor(String serverId)", source)
        self.assertIn("confirmRemoveServer(String serverId, String serverName)", source)
        self.assertIn("Status not checked", source)
        self.assertIn("Live online status, MOTD, player count", source)

    def test_play_is_not_advertised_as_ready_without_runtime(self):
        source = (JAVA_ROOT / "com" / "voidcraft" / "launcher" / "MainActivity.java").read_text(encoding="utf-8")
        self.assertIn("PLAY — NOT AVAILABLE YET", source)
        self.assertIn("Minecraft has not been connected to this app yet", source)


    def test_server_manager_supports_local_crud_without_claiming_live_status(self):
        source = (JAVA_ROOT / "com" / "voidcraft" / "launcher" / "MainActivity.java").read_text(encoding="utf-8")
        store = (JAVA_ROOT / "com" / "voidcraft" / "launcher" / "SavedServerStore.java").read_text(encoding="utf-8")

        for label in ("Add server", "Edit server", "Remove", "Refresh saved list"):
            with self.subTest(label=label):
                self.assertIn(label, source)

        self.assertIn("serverStore.add(name, address)", source)
        self.assertIn("serverStore.update(existing.id, name, address)", source)
        self.assertIn("serverStore.remove(entry.id)", source)
        self.assertIn("SharedPreferences", store)
        self.assertIn("new JSONArray(encoded)", store)
        self.assertIn("UUID.randomUUID()", store)
        self.assertIn("Live status: not checked", source)
        self.assertIn("Live status checks are not connected", source)

    def test_clean_room_app_does_not_request_network_permission_yet(self):
        manifest = MANIFEST.read_text(encoding="utf-8")
        self.assertNotIn('android.permission.INTERNET', manifest)


if __name__ == "__main__":
    unittest.main()
