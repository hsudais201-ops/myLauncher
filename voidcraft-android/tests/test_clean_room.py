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

    def test_official_version_metadata_browser_is_not_a_game_downloader(self):
        source = MAIN_ACTIVITY.read_text(encoding="utf-8")
        client_path = JAVA_ROOT / "com" / "voidcraft" / "launcher" / "MinecraftVersionManifestClient.java"
        client = client_path.read_text(encoding="utf-8")

        self.assertIn('else if ("Minecraft Versions".equals(currentRoute))', source)
        self.assertIn('button("Browse Minecraft versions"', source)
        self.assertIn("MinecraftVersionManifestClient.fetch()", source)
        self.assertIn("serverStatusExecutor.execute", source)
        self.assertIn("metadata only", source)
        self.assertIn("https://piston-meta.mojang.com/mc/game/version_manifest_v2.json", client)
        self.assertIn('OFFICIAL_MANIFEST_HOST = "piston-meta.mojang.com"', client)
        self.assertIn("MAX_MANIFEST_BYTES = 8 * 1024 * 1024", client)
        self.assertIn("getInstanceFollowRedirects(false)", client)
        self.assertIn("startsWith(\"/v1/packages/\")", client)
        self.assertIn("The version manifest exceeded the permitted size.", client)
        self.assertIn("public static Manifest parse(String json)", client)
        self.assertNotIn("net.kdt.pojavlaunch", client)
        self.assertNotIn("Amethyst", client)

    def test_server_status_is_an_optional_installable_feature_with_its_own_page(self):
        source = MAIN_ACTIVITY.read_text(encoding="utf-8")
        self.assertIn("KEY_FEATURE_SERVER_STATUS_INSTALLED", source)
        self.assertIn('button("Install Server Status"', source)
        self.assertIn('button("Open Server Status"', source)
        self.assertIn('button("Uninstall feature"', source)
        self.assertIn('else if ("Server Status".equals(currentRoute))', source)
        self.assertIn("Your saved server names and addresses will remain", source)
        self.assertIn("Server Status uninstalled; saved servers were kept.", source)
        self.assertIn("community-uploaded features can be added later", source)

    def test_in_app_legal_notices_distinguish_clean_room_and_legacy_builds(self):
        source = MAIN_ACTIVITY.read_text(encoding="utf-8")
        self.assertIn('else if ("About & Legal".equals(currentRoute))', source)
        self.assertIn('button("About & legal notices"', source)
        self.assertIn("not affiliated with, endorsed by, or sponsored by Mojang Studios or Microsoft", source)
        self.assertIn("does not bundle Minecraft client JARs", source)
        self.assertIn("repository-wide audit is still incomplete", source)
        self.assertIn("not a playable Minecraft launcher", source)

    def test_server_status_queries_real_java_protocol_and_renders_details(self):
        source = MAIN_ACTIVITY.read_text(encoding="utf-8")
        client = (JAVA_ROOT / "com" / "voidcraft" / "launcher" / "MinecraftServerStatusClient.java").read_text(encoding="utf-8")
        readme = (PROJECT / "README.md").read_text(encoding="utf-8")

        self.assertIn("MinecraftServerStatusClient.query(server.address)", source)
        self.assertIn("serverStatusExecutor.execute", source)
        self.assertIn("serverStatusExecutor.shutdownNow()", source)
        self.assertIn("serverStatuses.remove(serverToEdit.id)", source)
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

        self.assertIn("The optional Server Status feature appears in the category-free Feature Store.", readme)
        self.assertIn("Bedrock status", readme)

    def test_every_java_source_has_a_provenance_record(self):
        provenance_path = PROJECT / "SOURCE_PROVENANCE.md"
        self.assertTrue(provenance_path.is_file())
        provenance = provenance_path.read_text(encoding="utf-8")
        sources = list(JAVA_ROOT.rglob("*.java"))
        self.assertTrue(sources, "Expected first-party Java sources")
        for source in sources:
            relative = source.relative_to(PROJECT).as_posix()
            with self.subTest(source=relative):
                self.assertIn(relative, provenance)

        self.assertIn("not a legal opinion", provenance)
        self.assertIn("not cleared by this record", provenance)

    def test_app_resources_and_build_configuration_have_provenance_records(self):
        provenance = (PROJECT / "SOURCE_PROVENANCE.md").read_text(encoding="utf-8")
        candidates = list((PROJECT / "app" / "src" / "main").rglob("*.xml"))
        candidates.extend([
            PROJECT / "app" / "build.gradle",
            PROJECT / "build.gradle",
            PROJECT / "settings.gradle",
            PROJECT / "gradle.properties",
            PROJECT / "tests" / "test_clean_room.py",
        ])
        for source in candidates:
            with self.subTest(source=str(source.relative_to(PROJECT))):
                self.assertTrue(source.is_file())
                self.assertIn(source.relative_to(PROJECT).as_posix(), provenance)

    def test_clean_room_subtree_does_not_vendor_runtime_binaries(self):
        forbidden_suffixes = {".jar", ".aar", ".so", ".dex", ".class", ".dll", ".dylib"}
        offenders = [
            path.relative_to(PROJECT).as_posix()
            for path in PROJECT.rglob("*")
            if path.is_file() and path.suffix.lower() in forbidden_suffixes
        ]
        self.assertEqual(offenders, [], f"Unexpected vendored runtime/build binaries: {offenders}")

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
        root = PROJECT.parent
        audit = (root / "COPYRIGHT_AND_LICENSE_AUDIT.md").read_text(encoding="utf-8")
        readme = (root / "README.md").read_text(encoding="utf-8")
        legacy_workflow = (root / ".github" / "workflows" / "build.yml").read_text(encoding="utf-8")
        self.assertIn("INCOMPLETE", audit)
        self.assertIn("330c6eae3164df64bdc4828e946a9e62cc5169e4", audit)
        self.assertIn("not a clean-room build", audit)
        self.assertIn("not a wholly first-party or copyright-cleared build", readme)
        self.assertIn("Fetch pinned Amethyst engine and submodules", legacy_workflow)
        self.assertIn("330c6eae3164df64bdc4828e946a9e62cc5169e4", legacy_workflow)

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
