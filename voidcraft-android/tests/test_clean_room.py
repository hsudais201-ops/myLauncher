import re
import unittest
from pathlib import Path


PROJECT = Path(__file__).resolve().parents[1]
JAVA_ROOT = PROJECT / "app" / "src" / "main" / "java"
MAIN_ACTIVITY = JAVA_ROOT / "com" / "voidcraft" / "launcher" / "MainActivity.java"
SERVER_STORE = JAVA_ROOT / "com" / "voidcraft" / "launcher" / "SavedServerStore.java"
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
        source = MAIN_ACTIVITY.read_text(encoding="utf-8")
        self.assertIn("PLAY — NOT AVAILABLE YET", source)
        self.assertIn("Minecraft has not been connected to this app yet", source)
        self.assertEqual(source.count("private void buildDashboard()"), 1)

    def test_saved_server_management_persists_and_supports_crud(self):
        source = MAIN_ACTIVITY.read_text(encoding="utf-8")
        store = SERVER_STORE.read_text(encoding="utf-8")

        self.assertIn("new SavedServerStore(this)", source)
        self.assertIn("serverStore = new SavedServerStore(this)", source)
        self.assertIn('button("＋  Add server"', source)
        self.assertIn('button("Refresh saved list"', source)
        self.assertIn("showServerEditorForNew", source)
        self.assertIn("showServerEditor(String serverId)", source)
        self.assertIn("serverStore.add(name, address)", source)
        self.assertIn("serverStore.update(serverToEdit.id, name, address)", source)
        self.assertIn("confirmRemoveServer(String serverId, String serverName)", source)
        self.assertIn("serverStore.remove(serverId)", source)

        self.assertIn("SharedPreferences", store)
        self.assertIn("List<Entry> load()", store)
        self.assertIn("Entry add(String name, String address)", store)
        self.assertIn("boolean update(String id, String name, String address)", store)
        self.assertIn("boolean remove(String id)", store)

    def test_server_status_queries_real_java_protocol_and_renders_details(self):
        source = MAIN_ACTIVITY.read_text(encoding="utf-8")
        client = (JAVA_ROOT / "com" / "voidcraft" / "launcher" / "MinecraftServerStatusClient.java").read_text(encoding="utf-8")
        readme = (PROJECT / "README.md").read_text(encoding="utf-8")

        self.assertIn("MinecraftServerStatusClient.query(server.address)", source)
        self.assertIn('button("Check all server statuses"', source)
        self.assertIn("● ONLINE", source)
        self.assertIn("● OFFLINE / UNREACHABLE", source)
        self.assertIn("result.motd", source)
        self.assertIn("result.playersOnline", source)
        self.assertIn("result.playersMax", source)
        self.assertIn("result.version", source)
        self.assertIn("result.faviconDataUri", source)

        self.assertIn("new InetSocketAddress(endpoint.host, endpoint.port)", client)
        self.assertIn("CONNECT_TIMEOUT_MS = 3_000", client)
        self.assertIn("READ_TIMEOUT_MS = 3_000", client)
        self.assertIn("MAX_PACKET_BYTES = 1_048_576", client)
        self.assertIn("writeVarInt(handshake, 0)", client)
        self.assertIn("writeVarInt(request, 0)", client)
        self.assertIn("new JSONObject(json)", client)

        self.assertIn("Minecraft: Java Edition status request", readme)
        self.assertIn("Bedrock status", readme)

    def test_clean_room_workflow_does_not_fetch_legacy_launcher(self):
        workflow = (PROJECT.parent / ".github" / "workflows" / "clean-room-build.yml").read_text(encoding="utf-8")
        for forbidden in ("Amethyst-Android.git", "PojavLauncher.git", "git -C upstream", "app_pojavlauncher"):
            with self.subTest(forbidden=forbidden):
                self.assertNotIn(forbidden, workflow)

    def test_clean_room_project_has_no_declared_external_runtime_libraries(self):
        build = APP_GRADLE.read_text(encoding="utf-8")
        self.assertNotIn("dependencies {", build)
        sources = list(JAVA_ROOT.rglob("*.java"))
        forbidden = re.compile(r"\b(?:Amethyst|PojavLauncher)\b|net\.kdt\.pojavlaunch", re.IGNORECASE)
        for source in sources:
            self.assertIsNone(forbidden.search(source.read_text(encoding="utf-8")), str(source))

    def test_copyright_audit_clearly_marks_legacy_path_not_cleared(self):
        audit = (PROJECT.parent / "COPYRIGHT_AND_LICENSE_AUDIT.md").read_text(encoding="utf-8")
        self.assertIn("INCOMPLETE", audit)
        self.assertIn("330c6eae3164df64bdc4828e946a9e62cc5169e4", audit)
        self.assertIn("not a clean-room build", audit)

    def test_dependency_inventory_exists_and_marks_unimplemented_runtime(self):
        inventory_path = PROJECT / "DEPENDENCY_INVENTORY.md"
        self.assertTrue(inventory_path.is_file())
        inventory = inventory_path.read_text(encoding="utf-8")
        self.assertIn("No third-party runtime library currently declared", inventory)
        self.assertIn("not cleared by this table", inventory)
        self.assertIn("UNKNOWN", inventory)

    def test_clean_room_app_declares_network_permission_for_server_status(self):
        manifest = MANIFEST.read_text(encoding="utf-8")
        self.assertIn("android.permission.INTERNET", manifest)


if __name__ == "__main__":
    unittest.main()
