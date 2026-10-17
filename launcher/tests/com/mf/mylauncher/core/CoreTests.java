package com.mf.mylauncher.core;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import java.util.concurrent.atomic.AtomicBoolean;

/** Dependency-free regression tests so the launcher core can be tested with only a JDK. */
public final class CoreTests {
    private static int checks;
    private CoreTests() { }

    public static void main(String[] args) throws Exception {
        versions(); catalogs(); manifests(); jsonDecoder(); versionDetails(); launchArguments(); safeArchiveExtraction(); javaProcessRunner(); javaRuntimeProbe(); javaRuntimeLocator(); assetIndex(); assetIndexDownload(); assetLayoutMaterializer(); installationPlan(); classpathResolver(); nativeLibraryPlanner(); installationService(); nativeInstallationService(); verifiedDownloads(); downloader(); profiles(); memory(); installationProgress();
        System.out.println("PASS: " + checks + " launcher-core assertions");
    }

    private static void versions() {
        MinecraftVersion v = version("1.21.4", "release", "2024-12-03T10:00:00Z");
        eq("1.21.4", v.getId(), "ID retained"); yes(v.isRelease(), "release recognized");
        eq("https", v.getMetadataUrl().getScheme(), "HTTPS retained");
        bad(() -> version("../outside", "release", time1), "path-like ID rejected");
        bad(() -> version("1.21/4", "release", time1), "slash in ID rejected");
        bad(() -> new MinecraftVersion("1.21.4", "release", URI.create("http://example.test/v.json"), time1), "HTTP metadata URL rejected");
        bad(() -> version("1.21.4", "release", "bad timestamp"), "bad timestamp rejected");
        bad(() -> new MinecraftVersion("1.21.4", "release", URI.create("https://user@example.test/v.json"), time1), "URL userinfo rejected");
    }
    private static final String time1 = "2024-12-03T10:00:00Z";

    private static void catalogs() {
        MinecraftVersion old = version("1.20.6", "release", "2024-04-29T10:00:00Z");
        MinecraftVersion snap = version("25w01a", "snapshot", "2025-01-01T10:00:00Z");
        MinecraftVersion latest = version("1.21.4", "release", time1);
        VersionCatalog c = new VersionCatalog(Arrays.asList(old, snap, latest));
        eq(3, c.size(), "catalog size"); eq("25w01a", c.getVersionsNewestFirst().get(0).getId(), "newest-first sort");
        eq("1.21.4", c.getLatestRelease().get().getId(), "latest release skips newer snapshot");
        eq("25w01a", c.getLatestSnapshot().get().getId(), "latest snapshot selected");
        eq(latest, c.findById("1.21.4").get(), "lookup by ID");
        yes(!c.findById("missing").isPresent() && !c.findById(null).isPresent(), "unknown/null lookup is empty");
        eq(2, c.getReleasesNewestFirst().size(), "release filter");
        bad(() -> new VersionCatalog(null), "null list rejected");
        bad(() -> new VersionCatalog(Arrays.asList(latest, null)), "null entry rejected");
        bad(() -> new VersionCatalog(Arrays.asList(latest, version("1.21.4", "snapshot", "2025-01-01T00:00:00Z"))), "duplicate IDs rejected");
        unsupported(() -> c.getVersionsNewestFirst().clear(), "catalog list immutable");
    }

    @SuppressWarnings("unchecked")
    private static void manifests() {
        VersionManifest m = VersionManifestMapper.fromDecodedJson(manifestObject());
        eq("1.21.4", m.getLatestRelease().getId(), "latest release mapped");
        eq("25w01a", m.getLatestSnapshot().getId(), "latest snapshot mapped");
        eq(2, m.getCatalog().size(), "versions mapped");
        bad(() -> VersionManifestMapper.fromDecodedJson(null), "null root rejected");
        Map<String,Object> noLatest = manifestObject(); noLatest.remove("latest");
        bad(() -> VersionManifestMapper.fromDecodedJson(noLatest), "missing latest rejected");
        Map<String,Object> wrongArray = manifestObject(); wrongArray.put("versions", "bad");
        bad(() -> VersionManifestMapper.fromDecodedJson(wrongArray), "non-array versions rejected");
        Map<String,Object> wrongLatest = manifestObject();
        ((Map<String,Object>) wrongLatest.get("latest")).put("release", "25w01a");
        bad(() -> VersionManifestMapper.fromDecodedJson(wrongLatest), "release cannot point to snapshot");
        Map<String,Object> unknown = manifestObject();
        ((Map<String,Object>) unknown.get("latest")).put("release", "9.99");
        bad(() -> VersionManifestMapper.fromDecodedJson(unknown), "latest ID must exist");
        Map<String,Object> unsafe = manifestObject();
        ((List<Map<String,Object>>) unsafe.get("versions")).get(0).put("url", "http://example.test/v.json");
        bad(() -> VersionManifestMapper.fromDecodedJson(unsafe), "insecure metadata URL rejected");
    }

    private static void jsonDecoder() {
        String json = "{\"latest\":{\"release\":\"1.21.4\",\"snapshot\":\"25w01a\"},"
                + "\"versions\":[{\"id\":\"1.21.4\",\"type\":\"release\","
                + "\"url\":\"https://piston-meta.mojang.com/versions/1.21.4.json\","
                + "\"releaseTime\":\"2024-12-03T10:00:00Z\",\"complianceLevel\":1},"
                + "{\"id\":\"25w01a\",\"type\":\"snapshot\","
                + "\"url\":\"https://piston-meta.mojang.com/versions/25w01a.json\","
                + "\"releaseTime\":\"2025-01-01T10:00:00Z\"}]}";
        VersionManifest manifest = VersionManifestJsonDecoder.decode(json);
        eq("1.21.4", manifest.getLatestRelease().getId(), "JSON text decodes to release version");
        eq("25w01a", manifest.getLatestSnapshot().getId(), "JSON text decodes to snapshot version");
        eq(2, manifest.getCatalog().size(), "JSON versions array integrated end-to-end");
        Map<?, ?> primitives = (Map<?, ?>) JsonParser.parse("{\"text\":\"line\\nquote:\\u0022\",\"number\":-1.25e2,\"yes\":true,\"nothing\":null}");
        eq("line\nquote:\"", primitives.get("text"), "JSON escapes decoded");
        eq(new java.math.BigDecimal("-1.25e2"), primitives.get("number"), "JSON exponent number parsed");
        eq(Boolean.TRUE, primitives.get("yes"), "JSON boolean parsed");
        yes(primitives.containsKey("nothing") && primitives.get("nothing") == null, "JSON null retained");
        bad(() -> VersionManifestJsonDecoder.decode("[]"), "array root rejected");
        bad(() -> VersionManifestJsonDecoder.decode("{\"latest\":null}"), "missing manifest values rejected");
        bad(() -> JsonParser.parse("{\"duplicate\":1,\"duplicate\":2}"), "duplicate JSON keys rejected");
        bad(() -> JsonParser.parse("{\"bad\":01}"), "leading zero rejected");
        bad(() -> JsonParser.parse("{\"bad\":\"\\x\"}"), "invalid string escape rejected");
        bad(() -> JsonParser.parse("{\"bad\":1,}"), "trailing comma rejected");
        bad(() -> JsonParser.parse("true false"), "trailing document data rejected");
        bad(() -> JsonParser.parse("\"\\uＦＦＦＦ\""), "non-ASCII Unicode escape digits rejected");
        bad(() -> JsonParser.parse("\"\\uD800\""), "unpaired Unicode surrogate rejected");
        bad(() -> JsonParser.parse("[" + repeat("[", 66) + "0" + repeat("]", 66) + "]"), "extreme JSON nesting rejected");
        bad(() -> JsonParser.parse("\"" + repeat("x", 8 * 1024 * 1024) + "\""), "oversized JSON text rejected");
    }

    private static String repeat(String value, int count) {
        StringBuilder result = new StringBuilder(value.length() * count);
        for (int i = 0; i < count; i++) result.append(value);
        return result.toString();
    }

    private static void versionDetails() {
        String sha1 = "0123456789abcdef0123456789abcdef01234567";
        String sha2 = "fedcba9876543210fedcba9876543210fedcba98";
        String json = "{\"id\":\"1.21.4\",\"mainClass\":\"net.minecraft.client.main.Main\","
                + "\"assets\":\"1.21.4\",\"assetIndex\":{\"id\":\"1.21.4\","
                + "\"url\":\"https://piston-meta.mojang.com/v1/packages/" + sha1 + "/1.21.4.json\","
                + "\"sha1\":\"" + sha1 + "\",\"size\":88,\"totalSize\":424},"
                + "\"downloads\":{\"client\":{\"url\":\"https://piston-data.mojang.com/v1/objects/" + sha2 + "/client.jar\","
                + "\"sha1\":\"" + sha2 + "\",\"size\":9000000}},\"javaVersion\":{\"majorVersion\":21},"
                + "\"libraries\":[{\"name\":\"org.example:core:1.0\",\"downloads\":{\"artifact\":{"
                + "\"path\":\"org/example/core/1.0/core-1.0.jar\",\"url\":\"https://libraries.minecraft.net/core.jar\","
                + "\"sha1\":\"" + sha1 + "\",\"size\":123}},\"rules\":["
                + "{\"action\":\"allow\",\"os\":{\"name\":\"linux\",\"arch\":\"aarch64\",\"version\":\"6\\\\..*\"}},"
                + "{\"action\":\"disallow\",\"features\":{\"is_demo_user\":true}}]},"
                + "{\"name\":\"org.lwjgl:lwjgl:3.3.3:natives-linux-arm64\",\"downloads\":{\"classifiers\":{"
                + "\"natives-linux-arm64\":{\"path\":\"org/lwjgl/lwjgl/3.3.3/lwjgl-natives.jar\","
                + "\"url\":\"https://libraries.minecraft.net/lwjgl-natives.jar\",\"sha1\":\"" + sha2 + "\",\"size\":456}}},"
                + "\"natives\":{\"linux\":\"natives-linux-${arch}\"},\"extract\":{\"exclude\":[\"META-INF/\"]}}]}";
        VersionDetails details = VersionDetailsJsonDecoder.decode(json, "1.21.4");
        eq("1.21.4", details.getId(), "per-version ID mapped");
        eq("net.minecraft.client.main.Main", details.getMainClass(), "main class mapped");
        eq(21, details.getJavaMajorVersion(), "required Java major version mapped");
        eq("1.21.4", details.getAssetIndex().getId(), "asset-index ID mapped");
        eq(424L, details.getAssetIndex().getTotalSizeBytes(), "asset-index total size mapped");
        eq(9000000L, details.getClientDownload().getSizeBytes(), "client artifact size mapped");
        yes(!details.getLoggingConfiguration().isPresent(), "logging metadata remains optional for older versions");

        @SuppressWarnings("unchecked")
        Map<String, Object> rangedJson = (Map<String, Object>) JsonParser.parse(json);
        @SuppressWarnings("unchecked")
        List<Object> rangedLibraries = (List<Object>) rangedJson.get("libraries");
        @SuppressWarnings("unchecked")
        Map<String, Object> rangedCoreLibrary = (Map<String, Object>) rangedLibraries.get(0);
        @SuppressWarnings("unchecked")
        List<Object> rangedRules = (List<Object>) rangedCoreLibrary.get("rules");
        @SuppressWarnings("unchecked")
        Map<String, Object> rangedAllowRule = (Map<String, Object>) rangedRules.get(0);
        @SuppressWarnings("unchecked")
        Map<String, Object> rangedOs = (Map<String, Object>) rangedAllowRule.get("os");
        rangedOs.remove("version");
        rangedOs.put("versionRange", java.util.Collections.singletonMap("min", "10.0.17134"));
        VersionDetails rangeDetails = VersionDetailsMapper.fromDecodedJson(rangedJson, "1.21.4");
        yes(rangeDetails.getLibraries().get(0).isAllowedOn("linux", "aarch64", "10.0.17134",
                java.util.Collections.emptyMap()), "modern versionRange min is decoded and inclusive");
        yes(!rangeDetails.getLibraries().get(0).isAllowedOn("linux", "aarch64", "10.0.17133",
                java.util.Collections.emptyMap()), "modern versionRange excludes lower OS versions");

        @SuppressWarnings("unchecked")
        Map<String, Object> withLogging = (Map<String, Object>) JsonParser.parse(json);
        Map<String, Object> loggingFile = new LinkedHashMap<>();
        loggingFile.put("id", "client-1.12.xml");
        loggingFile.put("url", "https://piston-data.mojang.com/v1/objects/" + sha1 + "/client-1.12.xml");
        loggingFile.put("sha1", sha1);
        loggingFile.put("size", 512);
        Map<String, Object> loggingClient = new LinkedHashMap<>();
        loggingClient.put("argument", "-Dlog4j.configurationFile=${path}");
        loggingClient.put("file", loggingFile);
        loggingClient.put("type", "log4j2-xml");
        Map<String, Object> loggingRoot = new LinkedHashMap<>();
        loggingRoot.put("client", loggingClient);
        withLogging.put("logging", loggingRoot);
        VersionDetails loggedDetails = VersionDetailsMapper.fromDecodedJson(withLogging, "1.21.4");
        eq("client-1.12.xml", loggedDetails.getLoggingConfiguration().get().getId(), "logging configuration ID parsed");
        eq(512L, loggedDetails.getLoggingConfiguration().get().getSizeBytes(), "logging configuration size parsed");
        eq(sha1, loggedDetails.getLoggingConfiguration().get().getSha1(), "logging configuration SHA-1 parsed");
        eq("-Dlog4j.configurationFile=${path}", loggedDetails.getLoggingConfiguration().get().getArgument(),
                "Minecraft logging JVM argument is retained from metadata");
        Map<String, Object> missingLoggingArgumentClient = new LinkedHashMap<>(loggingClient);
        missingLoggingArgumentClient.remove("argument");
        Map<String, Object> missingLoggingArgumentRoot = new LinkedHashMap<>(loggingRoot);
        missingLoggingArgumentRoot.put("client", missingLoggingArgumentClient);
        Map<String, Object> missingLoggingArgumentDocument = new LinkedHashMap<>(withLogging);
        missingLoggingArgumentDocument.put("logging", missingLoggingArgumentRoot);
        bad(() -> VersionDetailsMapper.fromDecodedJson(missingLoggingArgumentDocument, "1.21.4"),
                "missing mandatory logging argument rejected");
        Map<String, Object> unsafeLoggingFile = new LinkedHashMap<>(loggingFile);
        unsafeLoggingFile.put("id", "../escape.xml");
        Map<String, Object> unsafeLoggingClient = new LinkedHashMap<>(loggingClient);
        unsafeLoggingClient.put("file", unsafeLoggingFile);
        Map<String, Object> unsafeLoggingRoot = new LinkedHashMap<>();
        unsafeLoggingRoot.put("client", unsafeLoggingClient);
        Map<String, Object> unsafeLoggingDocument = new LinkedHashMap<>(withLogging);
        unsafeLoggingDocument.put("logging", unsafeLoggingRoot);
        bad(() -> VersionDetailsMapper.fromDecodedJson(unsafeLoggingDocument, "1.21.4"),
                "unsafe logging configuration path rejected");

        yes(!details.getClientDownload().hasPath(), "client JAR destination is launcher-selected");
        eq(2, details.getLibraries().size(), "library list mapped");
        eq("org.lwjgl:lwjgl:3.3.3:natives-linux-arm64", details.getLibraries().get(1).getName(),
                "four-part Maven classifier coordinate accepted for native library metadata");
        LibraryMetadata core = details.getLibraries().get(0);
        eq("org/example/core/1.0/core-1.0.jar", core.getArtifact().get().getPath(), "library path mapped");
        yes(core.isAllowedOn("linux", "aarch64", "6.1.0", java.util.Collections.singletonMap("is_demo_user", false)), "matching allow rule accepted");
        yes(!core.isAllowedOn("linux", "aarch64", "6.1.0", java.util.Collections.singletonMap("is_demo_user", true)), "later matching disallow rule overrides allow");
        yes(!core.isAllowedOn("windows", "aarch64", "6.1.0", java.util.Collections.emptyMap()), "non-matching rule set denies library");
        LibraryMetadata lwjgl = details.getLibraries().get(1);
        eq("0123456789abcdef0123456789abcdef01234567", core.getArtifact().get().getSha1(), "artifact hash normalized");
        eq("natives-linux-arm64", lwjgl.findNativeArtifact("linux", "arm64").map(a -> "natives-linux-arm64").orElse("missing"), "native classifier resolved");
        eq(1, lwjgl.getExtractionExcludes().size(), "native extraction exclusions mapped");
        eq(8, VersionDetailsJsonDecoder.decode(json.replace("\"javaVersion\":{\"majorVersion\":21},", "")).getJavaMajorVersion(), "legacy metadata defaults Java major to 8");
        bad(() -> VersionDetailsJsonDecoder.decode(json, "1.20.6"), "requested version mismatch rejected");
        String badHash = json.replace(sha1, "invalid");
        bad(() -> VersionDetailsJsonDecoder.decode(badHash), "malformed artifact hashes rejected");
        bad(() -> VersionDetailsJsonDecoder.decode(json.replace("org/example/core/1.0/core-1.0.jar", "../escape.jar")), "artifact path traversal rejected");
        bad(() -> VersionDetailsJsonDecoder.decode(json.replace("6\\\\..*", "[broken")), "invalid rule regex rejected");
        bad(() -> VersionDetailsJsonDecoder.decode(json.replace("\"majorVersion\":21", "\"majorVersion\":4294967317")), "overflowed Java major version rejected");
        unsupported(() -> details.getLibraries().clear(), "version libraries immutable");
        yes(LibraryRule.isAllowed(java.util.Collections.emptyList(), "linux", "arm64", "6.1", null), "libraries with no rules are allowed");

        URI metadataUrl = URI.create("https://piston-meta.mojang.com/v1/packages/" + sha1 + "/1.21.4.json");
        MinecraftVersion manifestEntry = new MinecraftVersion("1.21.4", "release", metadataUrl, time1);
        Fake response = new Fake(200, json.getBytes(StandardCharsets.UTF_8), -1);
        VersionDetailsDownloader detailsDownloader = new VersionDetailsDownloader(new VersionManifestDownloader(uri -> {
            eq(metadataUrl, uri, "version-details downloader uses manifest URL");
            return response;
        }));
        try {
            eq("1.21.4", detailsDownloader.download(manifestEntry).getId(), "per-version HTTP metadata decoded end-to-end");
        } catch (IOException exception) { throw new AssertionError(exception); }
        yes(response.disconnected && !response.followRedirects, "version metadata request is bounded and connection is closed");
        yes(VersionManifestDownloader.isApprovedMetadataUri(metadataUrl), "official piston metadata URL allowed");
        yes(VersionManifestDownloader.isApprovedMetadataUri(URI.create("https://launchermeta.mojang.com/v1/packages/abc/1.12.2.json")), "legacy official metadata host allowed");
        yes(!VersionManifestDownloader.isApprovedMetadataUri(URI.create("http://piston-meta.mojang.com/v1/packages/abc/1.21.4.json")), "HTTP metadata URL blocked");
        yes(!VersionManifestDownloader.isApprovedMetadataUri(URI.create("https://piston-meta.mojang.com.evil.test/v1/packages/abc/1.21.4.json")), "lookalike host blocked");
        yes(!VersionManifestDownloader.isApprovedMetadataUri(URI.create("https://piston-meta.mojang.com:444/v1/packages/abc/1.21.4.json")), "unexpected TLS port blocked");
        MinecraftVersion untrusted = new MinecraftVersion("1.21.4", "release", URI.create("https://evil.test/v1/packages/abc/1.21.4.json"), time1);
        io(() -> new VersionDetailsDownloader(new VersionManifestDownloader(uri -> { throw new AssertionError("unapproved URL reached connection opener"); })).download(untrusted), "unapproved metadata host blocked before network access");
    }

    private static void launchArguments() {
        String hash = "0123456789abcdef0123456789abcdef01234567";
        String jvmUrl = "https://piston-data.mojang.com/v1/objects/" + hash + "/client.jar";
        String argsJson = "{\"game\":[\"--username\",\"{auth_player_name}\",\"--accessToken\","
                + "\"{auth_access_token}\",\"--version\",\"{version_name}\","
                + "{\"rules\":[{\"action\":\"allow\",\"os\":{\"name\":\"linux\"}}],"
                + "\"value\":[\"--width\",\"{resolution_width}\"]},{\"rules\":[{\"action\":\"allow\","
                + "\"os\":{\"name\":\"windows\"}}],\"value\":\"--windows-only\"}],"
                + "\"jvm\":[\"-Djava.library.path=${natives_directory}\",\"-cp\",\"{classpath}\","
                + "{\"rules\":[{\"action\":\"allow\"}],\"value\":\"-Dlog4j.configurationFile=${path}\"}]}";
        @SuppressWarnings("unchecked")
        Map<String, ?> parsedArguments = (Map<String, ?>) JsonParser.parse(argsJson);
        Map<String,Object> root = new LinkedHashMap<>();
        root.put("id", "1.21.4");
        root.put("mainClass", "net.minecraft.client.main.Main");
        root.put("assets", "1.21.4");
        Map<String,Object> assetIndex = new LinkedHashMap<>();
        assetIndex.put("id", "1.21.4");
        assetIndex.put("url", "https://piston-meta.mojang.com/v1/packages/" + hash + "/1.21.4.json");
        assetIndex.put("sha1", hash); assetIndex.put("size", 80);
        root.put("assetIndex", assetIndex);
        Map<String,Object> client = new LinkedHashMap<>();
        client.put("url", jvmUrl); client.put("sha1", hash); client.put("size", 100);
        Map<String,Object> downloads = new LinkedHashMap<>(); downloads.put("client", client);
        root.put("downloads", downloads);
        root.put("libraries", java.util.Collections.emptyList());
        root.put("arguments", parsedArguments);
        VersionDetails details = VersionDetailsMapper.fromDecodedJson(root, "1.21.4");
        LibraryPlatform linux = new LibraryPlatform("linux", "x86_64", "6.1.0",
                java.util.Collections.emptyMap());
        LaunchContext context = new LaunchContext.Builder("Steve_123",
                "12345678-1234-1234-1234-1234567890ab", "test-access-token")
                .gameDirectory(java.nio.file.Paths.get("/tmp/mylauncher/game"))
                .assetsRoot(java.nio.file.Paths.get("/tmp/mylauncher/game/assets"))
                .librariesDirectory(java.nio.file.Paths.get("/tmp/mylauncher/game/libraries"))
                .nativesDirectory(java.nio.file.Paths.get("/tmp/mylauncher/game/natives/1.21.4"))
                .classpath("/tmp/mylauncher/game/versions/1.21.4/1.21.4.jar:/tmp/mylauncher/game/libraries/example.jar")
                .loggingConfigPath("/tmp/mylauncher/game/assets/log_configs/client.xml")
                .resolution(1280, 720).heapMiB(256, 768).platform(linux).build();
        List<String> command = LaunchCommandBuilder.build(details, context);
        eq("-Xms256M", command.get(0), "minimum heap added");
        eq("-Xmx768M", command.get(1), "maximum heap added");
        yes(command.contains("-Djava.library.path=/tmp/mylauncher/game/natives/1.21.4"),
                "native directory placeholder expanded");
        yes(command.contains("-Dlog4j.configurationFile=/tmp/mylauncher/game/assets/log_configs/client.xml"),
                "dollar-brace logging path placeholder expanded");
        eq("/tmp/mylauncher/game/versions/1.21.4/1.21.4.jar:/tmp/mylauncher/game/libraries/example.jar",
                command.get(command.indexOf("-cp") + 1), "classpath placeholder expanded as one argv value");
        yes(command.contains("net.minecraft.client.main.Main"), "main class inserted");
        yes(command.contains("Steve_123"), "game argument player name expanded");
        yes(command.contains("1.21.4"), "version-name placeholder expanded");
        yes(command.contains("--width") && command.contains("1280"), "platform-specific argument rule applied");
        yes(!command.contains("--windows-only"), "non-matching platform argument omitted");
        yes(command.contains("test-access-token"), "access token passed as a distinct game argument when referenced");
        eq("123456781234123412341234567890ab", context.getUuid(), "UUID normalized to dashless lowercase form");
        unsupported(() -> command.add("shell injection"), "launch command list is immutable");
        bad(() -> new LaunchContext.Builder("bad name", "123456781234123412341234567890ab", "token")
                .gameDirectory(java.nio.file.Paths.get("/tmp/game")).assetsRoot(java.nio.file.Paths.get("/tmp/assets"))
                .librariesDirectory(java.nio.file.Paths.get("/tmp/libraries")).nativesDirectory(java.nio.file.Paths.get("/tmp/natives"))
                .classpath("/tmp/client.jar").platform(linux).build(), "invalid profile rejected");
        bad(() -> new LaunchContext.Builder("Steve_123", "bad uuid", "token")
                .gameDirectory(java.nio.file.Paths.get("/tmp/game")).assetsRoot(java.nio.file.Paths.get("/tmp/assets"))
                .librariesDirectory(java.nio.file.Paths.get("/tmp/libraries")).nativesDirectory(java.nio.file.Paths.get("/tmp/natives"))
                .classpath("/tmp/client.jar").platform(linux).build(), "invalid profile UUID rejected");
        bad(() -> new LaunchContext.Builder("Steve_123", "12345678123412341234123412345678", "")
                .gameDirectory(java.nio.file.Paths.get("/tmp/game")).assetsRoot(java.nio.file.Paths.get("/tmp/assets"))
                .librariesDirectory(java.nio.file.Paths.get("/tmp/libraries")).nativesDirectory(java.nio.file.Paths.get("/tmp/natives"))
                .classpath("/tmp/client.jar").platform(linux).build(), "missing authentication token rejected");

        Map<String,Object> legacy = new LinkedHashMap<>(root);
        legacy.remove("arguments");
        legacy.put("minecraftArguments", "--username \"{auth_player_name}\" --version {version_name} --demo");
        VersionDetails legacyDetails = VersionDetailsMapper.fromDecodedJson(legacy, "1.21.4");
        List<String> legacyCommand = LaunchCommandBuilder.build(legacyDetails, context);
        yes(legacyCommand.contains("Steve_123") && legacyCommand.contains("--demo"),
                "legacy minecraftArguments tokenization and expansion works");
        bad(() -> LaunchCommandBuilder.tokenizeLegacyArguments("--username \"unterminated"),
                "unclosed legacy argument quote rejected");
        Map<String, Object> rangeRuleOs = new LinkedHashMap<>();
        rangeRuleOs.put("name", "windows");
        rangeRuleOs.put("versionRange", java.util.Collections.singletonMap("min", "10.0.17134"));
        Map<String, Object> rangeRule = new LinkedHashMap<>();
        rangeRule.put("action", "allow");
        rangeRule.put("os", rangeRuleOs);
        Map<String, Object> defaultJvmEntry = new LinkedHashMap<>();
        defaultJvmEntry.put("value", java.util.Collections.singletonList("-XX:+UseZGC"));
        defaultJvmEntry.put("rules", java.util.Collections.singletonList(rangeRule));
        Map<String, Object> modernArguments = new LinkedHashMap<>();
        modernArguments.put("default-user-jvm", java.util.Collections.singletonList(defaultJvmEntry));
        VersionArgumentSet modernArgumentSet = VersionArgumentSet.fromDecodedJson(modernArguments);
        LibraryPlatform recentWindows = new LibraryPlatform("windows", "amd64", "10.0.19045",
                java.util.Collections.emptyMap());
        LibraryPlatform olderWindows = new LibraryPlatform("windows", "amd64", "10.0.17133",
                java.util.Collections.emptyMap());
        eq(java.util.Collections.singletonList("-XX:+UseZGC"),
                modernArgumentSet.resolveDefaultUserJvmArguments(recentWindows),
                "26.1 default-user-jvm and OS versionRange are parsed");
        eq(java.util.Collections.emptyList(), modernArgumentSet.resolveDefaultUserJvmArguments(olderWindows),
                "default-user-jvm versionRange excludes older OS versions");
        eq(1, modernArgumentSet.getDefaultUserJvm().size(), "default-user-jvm entries are retained separately from required JVM args");

        LibraryPlatform quickPlayPlatform = new LibraryPlatform("linux", "x86_64", "6.1.0",
                java.util.Collections.singletonMap("has_quick_plays_support", true));
        VersionArgumentSet quickPlayArguments = new VersionArgumentSet(
                java.util.Collections.singletonList(new VersionArgumentSet.ArgumentEntry(
                        Arrays.asList("--quickPlayPath", "${quickPlayPath}"),
                        java.util.Collections.singletonList(new LibraryRule(true, null, null, null,
                                java.util.Collections.singletonMap("has_quick_plays_support", true))))),
                java.util.Collections.emptyList());
        VersionDetails quickPlayDetails = new VersionDetails(details.getId(), details.getMainClass(),
                details.getAssetsId(), details.getAssetIndex(), details.getClientDownload(),
                details.getJavaMajorVersion(), details.getLibraries(), quickPlayArguments, null);
        LaunchContext quickPlayContext = new LaunchContext.Builder(context.getUsername(), context.getUuid(),
                context.getAccessToken())
                .gameDirectory(context.getGameDirectoryPath()).assetsRoot(context.getAssetsRootPath())
                .librariesDirectory(context.getLibrariesDirectoryPath()).nativesDirectory(context.getNativesDirectoryPath())
                .classpath(context.getClasspath()).loggingConfigPath(context.getLoggingConfigPath())
                .heapMiB(context.getMinHeapMiB(), context.getMaxHeapMiB()).platform(quickPlayPlatform)
                .quickPlayPath(java.nio.file.Paths.get("/tmp/mylauncher/game/quick-play.json")).build();
        List<String> quickPlayCommand = LaunchCommandBuilder.build(quickPlayDetails, quickPlayContext);
        eq("/tmp/mylauncher/game/quick-play.json",
                quickPlayCommand.get(quickPlayCommand.indexOf("--quickPlayPath") + 1),
                "Minecraft 26.1 quickPlayPath placeholder expands to its configured file");
        LaunchContext.Builder missingQuickPlayPath = new LaunchContext.Builder(context.getUsername(),
                context.getUuid(), context.getAccessToken())
                .gameDirectory(context.getGameDirectoryPath()).assetsRoot(context.getAssetsRootPath())
                .librariesDirectory(context.getLibrariesDirectoryPath()).nativesDirectory(context.getNativesDirectoryPath())
                .classpath(context.getClasspath()).platform(quickPlayPlatform);
        bad(missingQuickPlayPath::build,
                "launch context rejects quick-play support without a required quickPlayPath");

        bad(() -> VersionArgumentSet.fromDecodedJson(java.util.Collections.singletonMap("game", "not-an-array")),
                "malformed modern arguments shape rejected");
        bad(() -> LaunchCommandBuilder.build(details, null), "null launch context rejected");
    }

    private static void safeArchiveExtraction() throws Exception {
        Path temporary = Files.createTempDirectory("mylauncher-extract-tests-");
        try {
            byte[] archiveBytes = makeZip(new String[][] {
                    {"META-INF/", ""},
                    {"META-INF/MANIFEST.MF", "discard"},
                    {"lib/", ""},
                    {"lib/liblwjgl.so", "native"},
                    {"config.txt", "ok"}
            });
            Path archive = temporary.resolve("natives.zip");
            Files.write(archive, archiveBytes);
            Path destination = temporary.resolve("natives/1.21.4");
            SafeArchiveExtractor.ExtractionResult extracted = SafeArchiveExtractor.extract(
                    archive, destination, java.util.Collections.singletonList("META-INF/"));
            eq(2, extracted.getFileCount(), "safe extractor counts extracted files");
            eq(1, extracted.getDirectoryCount(), "safe extractor counts non-excluded directories");
            eq(8L, extracted.getBytesWritten(), "excluded metadata is not written");
            eq(15L, extracted.getBytesRead(), "archive data is bounded even in excluded entries");
            yes(Files.isRegularFile(destination.resolve("lib/liblwjgl.so")), "native file extracted");
            eq("native", new String(Files.readAllBytes(destination.resolve("lib/liblwjgl.so")), StandardCharsets.UTF_8),
                    "native bytes preserved");
            yes(Files.isRegularFile(destination.resolve("config.txt")), "normal archive resource extracted");
            yes(!Files.exists(destination.resolve("META-INF/MANIFEST.MF")), "excluded META-INF contents omitted");
            eq(destination.toAbsolutePath().normalize(), extracted.getDestination(), "destination normalized");

            Path archiveOne = temporary.resolve("native-one.zip");
            Path archiveTwo = temporary.resolve("native-two.zip");
            Files.write(archiveOne, makeZip(new String[][] {
                    {"META-INF/", ""}, {"META-INF/MANIFEST.MF", "one"},
                    {"lib/", ""}, {"lib/a.so", "a"}
            }));
            Files.write(archiveTwo, makeZip(new String[][] {
                    {"META-INF/", ""}, {"META-INF/MANIFEST.MF", "two"},
                    {"lib/", ""}, {"lib/b.so", "b"}
            }));
            Path combinedDestination = temporary.resolve("natives/combined");
            SafeArchiveExtractor.ExtractionResult combined = SafeArchiveExtractor.extractAll(
                    Arrays.asList(
                            new SafeArchiveExtractor.ArchiveSpec(archiveOne,
                                    java.util.Collections.singletonList("META-INF/")),
                            new SafeArchiveExtractor.ArchiveSpec(archiveTwo,
                                    java.util.Collections.singletonList("META-INF/"))),
                    combinedDestination);
            eq(2, combined.getFileCount(), "multi-archive extraction counts both native files");
            eq(2L, combined.getBytesWritten(), "multi-archive extraction counts bytes written");
            eq(8L, combined.getBytesRead(), "multi-archive extraction bounds excluded and included data together");
            eq("a", new String(Files.readAllBytes(combinedDestination.resolve("lib/a.so")), StandardCharsets.UTF_8),
                    "first archive content retained in combined native directory");
            eq("b", new String(Files.readAllBytes(combinedDestination.resolve("lib/b.so")), StandardCharsets.UTF_8),
                    "second archive content retained in combined native directory");

            Path cancelDestination = temporary.resolve("natives/cancelled");
            Files.createDirectories(cancelDestination);
            Files.write(cancelDestination.resolve("keep.txt"), "old-native".getBytes(StandardCharsets.UTF_8));
            java.util.concurrent.atomic.AtomicInteger cancellationChecks = new java.util.concurrent.atomic.AtomicInteger();
            CancellationToken cancelDuringExtraction = () -> cancellationChecks.incrementAndGet() >= 4;
            io(() -> SafeArchiveExtractor.extractAll(
                    java.util.Collections.singletonList(new SafeArchiveExtractor.ArchiveSpec(archiveOne,
                            java.util.Collections.emptyList())),
                    cancelDestination, cancelDuringExtraction), "cancellation interrupts native extraction");
            eq("old-native", new String(Files.readAllBytes(cancelDestination.resolve("keep.txt")),
                    StandardCharsets.UTF_8), "cancelled extraction preserves previous native directory");
            yes(!Files.exists(cancelDestination.resolve("lib/a.so")),
                    "cancelled extraction does not publish partial native files");

            yes(!Files.exists(combinedDestination.resolve("META-INF/MANIFEST.MF")),
                    "per-archive excludes prevent duplicate manifest files");

            Files.write(combinedDestination.resolve("keep.txt"), "previous".getBytes(StandardCharsets.UTF_8));
            Path duplicateArchive = temporary.resolve("native-duplicate.zip");
            Files.write(duplicateArchive, makeZip(new String[][] {{"lib/a.so", "different"}}));
            io(() -> SafeArchiveExtractor.extractAll(Arrays.asList(
                    new SafeArchiveExtractor.ArchiveSpec(archiveOne, java.util.Collections.emptyList()),
                    new SafeArchiveExtractor.ArchiveSpec(duplicateArchive, java.util.Collections.emptyList())),
                    combinedDestination), "duplicate native file across archives rejected");
            yes(Files.exists(combinedDestination.resolve("keep.txt")),
                    "failed multi-archive extraction preserves previous native directory");
            bad(() -> {
                try { SafeArchiveExtractor.extractAll(java.util.Collections.emptyList(), temporary.resolve("empty-archive-set")); }
                catch (IOException exception) { throw new AssertionError("empty archive list should be rejected before IO", exception); }
            }, "empty native archive collection rejected");


            Files.write(destination.resolve("stale.bin"), new byte[] {1, 2, 3});
            byte[] unsafeBytes = makeZip(new String[][] {{"../escape.txt", "bad"}});
            Path unsafeArchive = temporary.resolve("unsafe.zip");
            Files.write(unsafeArchive, unsafeBytes);
            io(() -> SafeArchiveExtractor.extract(unsafeArchive, destination, java.util.Collections.emptyList()),
                    "ZIP traversal rejected");
            yes(Files.exists(destination.resolve("stale.bin")), "failed extraction preserves previous destination");
            yes(!Files.exists(temporary.resolve("escape.txt")), "ZIP traversal writes nothing outside destination");

            byte[] absoluteBytes = makeZip(new String[][] {{"/absolute.txt", "bad"}});
            Path absoluteArchive = temporary.resolve("absolute.zip");
            Files.write(absoluteArchive, absoluteBytes);
            io(() -> SafeArchiveExtractor.extract(absoluteArchive, temporary.resolve("absolute-out"),
                    java.util.Collections.emptyList()), "absolute ZIP path rejected");
            bad(() -> {
                try { SafeArchiveExtractor.extract(archive, temporary.resolve("invalid-excludes"),
                        java.util.Collections.singletonList("../outside/")); }
                catch (IOException exception) { throw new AssertionError("unsafe exclusion fails validation before writing", exception); }
            }, "unsafe exclusion path rejected");

            Path outside = temporary.resolve("outside");
            Files.createDirectories(outside);
            Path outsideSentinel = outside.resolve("sentinel.txt");
            Files.write(outsideSentinel, "safe".getBytes(StandardCharsets.UTF_8));
            Path archiveParentTarget = temporary.resolve("native-archive-parent-target");
            Files.createDirectories(archiveParentTarget);
            Path copiedArchive = archiveParentTarget.resolve("source.zip");
            Files.copy(archive, copiedArchive);
            Path linkedArchiveParent = temporary.resolve("linked-native-archive-parent");
            try {
                Files.createSymbolicLink(linkedArchiveParent, archiveParentTarget);
                io(() -> SafeArchiveExtractor.extract(linkedArchiveParent.resolve("source.zip"),
                        temporary.resolve("linked-archive-output"), java.util.Collections.emptyList()),
                        "symlinked ancestor of native archive rejected");
                yes(!Files.exists(temporary.resolve("linked-archive-output")),
                        "symlinked native archive is rejected before extraction output is created");
            } catch (UnsupportedOperationException | SecurityException | java.nio.file.FileSystemException exception) {
                // Some filesystems disable symbolic links for ordinary users.
            }

            Path linked = temporary.resolve("linked-natives");
            try {
                Files.createSymbolicLink(linked, outside);
                io(() -> SafeArchiveExtractor.extract(archive, linked, java.util.Collections.emptyList()),
                        "symlink extraction destination rejected");
                eq("safe", new String(Files.readAllBytes(outsideSentinel), StandardCharsets.UTF_8),
                        "symlink destination cannot overwrite outside data");
            } catch (UnsupportedOperationException | SecurityException exception) {
                // Some platforms disable symlink creation for ordinary users.
            }
        } finally {
            try (java.util.stream.Stream<Path> paths = Files.walk(temporary)) {
                paths.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                    try { Files.deleteIfExists(path); } catch (IOException exception) { throw new java.io.UncheckedIOException(exception); }
                });
            }
        }
    }

    private static byte[] makeZip(String[][] entries) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            for (String[] entry : entries) {
                zip.putNextEntry(new ZipEntry(entry[0]));
                if (!entry[0].endsWith("/")) zip.write(entry[1].getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        return bytes.toByteArray();
    }

    private static void javaRuntimeProbe() throws Exception {
        eq(8, JavaRuntimeProbe.parseMajorVersion("java version \"1.8.0_442\""), "legacy Java 8 version parsed");
        eq(17, JavaRuntimeProbe.parseMajorVersion("openjdk version \"17.0.10\" 2024-01-16"), "quoted Java 17 version parsed");
        eq(21, JavaRuntimeProbe.parseMajorVersion("openjdk 21 2023-09-19"), "bare modern Java major parsed");
        bad(() -> JavaRuntimeProbe.parseMajorVersion("Java is not installed"), "unknown Java version text rejected");
        bad(() -> JavaRuntimeProbe.parseMajorVersion(""), "empty Java version output rejected");

        Path temporary = Files.createTempDirectory("mylauncher-runtime-probe-");
        try {
            Path working = Files.createDirectories(temporary.resolve("work"));
            Path javaExecutable = java.nio.file.Paths.get(System.getProperty("java.home"), "bin", "java").toRealPath();
            String specificationVersion = System.getProperty("java.specification.version");
            int actualMajor = specificationVersion.startsWith("1.")
                    ? Integer.parseInt(specificationVersion.substring(2))
                    : Integer.parseInt(specificationVersion);
            JavaRuntimeProbe probe = new JavaRuntimeProbe();
            JavaRuntimeProbe.RuntimeInfo info = probe.inspect(javaExecutable, working, actualMajor,
                    CancellationToken.NONE, 15_000);
            eq(actualMajor, info.getMajorVersion(), "runtime probe detects the running JDK major");
            yes(info.getExecutable().isAbsolute(), "runtime executable path resolved");
            yes(info.getVersionOutput().toLowerCase(java.util.Locale.ROOT).contains("version"),
                    "runtime output retained for diagnostics");

            int wrongMajor = actualMajor == 8 ? 9 : 8;
            try {
                probe.inspect(javaExecutable, working, wrongMajor, CancellationToken.NONE, 15_000);
                throw new AssertionError("runtime major mismatch should be rejected");
            } catch (IOException expected) {
                checks++;
                yes(expected.getMessage().contains("requires Java " + wrongMajor),
                        "runtime mismatch message includes required major version");
            }

            bad(() -> {
                try { probe.inspect(javaExecutable, working, 7, CancellationToken.NONE, 1_000); }
                catch (IOException | InterruptedException exception) {
                    throw new AssertionError("unsupported requested version should fail before probing", exception);
                }
            }, "unsupported required Java version rejected");
        } finally {
            try (java.util.stream.Stream<Path> paths = Files.walk(temporary)) {
                paths.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                    try { Files.deleteIfExists(path); } catch (IOException exception) { throw new java.io.UncheckedIOException(exception); }
                });
            }
        }
    }

    private static void javaRuntimeLocator() throws Exception {
        Path temporary = Files.createTempDirectory("mylauncher-runtime-locator-");
        try {
            JavaRuntimeLocator locator = new JavaRuntimeLocator(temporary);
            yes(!locator.locate("java-21").isPresent(), "missing runtime returns empty without downloading");
            bad(() -> {
                try { locator.locate("../outside"); }
                catch (IOException exception) { throw new AssertionError("invalid ID should fail before filesystem access", exception); }
            }, "runtime traversal ID rejected");

            Path executable = Files.createDirectories(
                    temporary.resolve("runtimes/java-21/bin")).resolve("java");
            Files.write(executable, "#!/bin/sh\nexit 0\n".getBytes(StandardCharsets.UTF_8));
            yes(executable.toFile().setExecutable(true, true), "test runtime executable permission set");
            Path located = locator.locate("java-21").get();
            eq(executable.toRealPath(), located, "runtime locator resolves private installed Java executable");

            Path nonExecutable = Files.createDirectories(
                    temporary.resolve("runtimes/not-executable/bin")).resolve("java");
            Files.write(nonExecutable, new byte[] {1, 2, 3});
            nonExecutable.toFile().setExecutable(false, false);
            io(() -> locator.locate("not-executable"), "non-executable runtime rejected");

            Path linkedRuntime = temporary.resolve("runtimes/linked");
            try {
                Files.createSymbolicLink(linkedRuntime, temporary.resolve("runtimes/java-21"));
                io(() -> locator.locate("linked"), "symlinked runtime directory rejected");
            } catch (UnsupportedOperationException | java.nio.file.FileSystemException unsupported) {
                // The CI filesystem normally supports symlinks; skip only where the OS forbids creation.
            }
        } finally {
            try (java.util.stream.Stream<Path> paths = Files.walk(temporary)) {
                paths.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                    try { Files.deleteIfExists(path); }
                    catch (IOException exception) { throw new java.io.UncheckedIOException(exception); }
                });
            }
        }
    }

    private static void assetIndex() {
        String sha1 = "0123456789abcdef0123456789abcdef01234567";
        String sha2 = "fedcba9876543210fedcba9876543210fedcba98";
        String json = "{\"virtual\":true,\"map_to_resources\":true,\"objects\":{"
                + "\"minecraft/sounds/ambient/cave.ogg\":{\"hash\":\"" + sha1 + "\",\"size\":123},"
                + "\"minecraft/textures/block/stone.png\":{\"hash\":\"" + sha2 + "\",\"size\":0}}}";
        AssetIndex index = AssetIndexJsonDecoder.decode(json);
        eq(2L, index.getObjectCount(), "asset object count mapped");
        eq(123L, index.getTotalBytes(), "total asset bytes calculated");
        yes(index.isVirtual(), "virtual asset mode mapped");
        yes(index.isMapToResources(), "map-to-resources flag mapped");
        eq("minecraft/sounds/ambient/cave.ogg", index.getOrderedObjects().get(0).getName(), "asset objects ordered deterministically");
        AssetObjectMetadata asset = index.getObjects().get("minecraft/sounds/ambient/cave.ogg");
        eq("objects/01/0123456789abcdef0123456789abcdef01234567", asset.getObjectRelativePath(), "content-addressed object path resolved");
        eq("https://resources.download.minecraft.net/01/0123456789abcdef0123456789abcdef01234567", asset.getDownloadUrl().toString(), "official asset CDN URL resolved");
        eq("minecraft/sounds/ambient/cave.ogg", asset.getResourceRelativePath(), "logical resource path retained");
        bad(() -> AssetIndexJsonDecoder.decode("[]"), "non-object asset index rejected");
        bad(() -> AssetIndexJsonDecoder.decode("{}"), "missing asset objects rejected");
        bad(() -> AssetIndexJsonDecoder.decode(json.replace(sha1, "not-a-sha1")), "invalid asset hash rejected");
        bad(() -> AssetIndexJsonDecoder.decode(json.replace("minecraft/sounds/ambient/cave.ogg", "../escape.ogg")), "asset path traversal rejected");
        bad(() -> AssetIndexJsonDecoder.decode(json.replace("\"size\":123", "\"size\":1.5")), "fractional asset size rejected");
        bad(() -> AssetIndexJsonDecoder.decode(json.replace("\"virtual\":true", "\"virtual\":\"yes\"")), "non-boolean asset flags rejected");
        unsupported(() -> index.getObjects().clear(), "asset map immutable");
        unsupported(() -> index.getOrderedObjects().clear(), "ordered assets list immutable");
        bad(() -> new AssetIndex(java.util.Collections.singletonMap("wrong", asset), false, false), "asset key must match its object metadata");
    }

    private static void assetIndexDownload() throws Exception {
        String objectHash = "86f7e437faa5a7fce15d1ddcb9eaeaea377667b8";
        String json = "{\"virtual\":true,\"objects\":{\"test/a.txt\":{\"hash\":\"" + objectHash + "\",\"size\":1}}}";
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        String indexHash = sha1Hex(bytes);
        URI url = URI.create("https://piston-meta.mojang.com/v1/packages/" + indexHash + "/1.21.4.json");
        AssetIndexMetadata metadata = new AssetIndexMetadata("1.21.4", url, indexHash, bytes.length, bytes.length);
        Fake ok = new Fake(200, bytes, bytes.length);
        AssetIndexDownloader downloader = new AssetIndexDownloader(new VersionManifestDownloader(uri -> {
            eq(url, uri, "asset index requested from official metadata URL");
            return ok;
        }));
        AssetIndex index = downloader.download(metadata);
        eq(1L, index.getObjectCount(), "verified asset index parsed");
        eq("test/a.txt", index.getOrderedObjects().get(0).getName(), "verified asset name retained");
        yes(ok.disconnected && !ok.followRedirects, "asset-index connection closed with redirects disabled");
        eq(10_000, ok.connectTimeout, "asset-index connect timeout configured");
        eq(15_000, ok.readTimeout, "asset-index read timeout configured");

        AssetIndexMetadata wrongHash = new AssetIndexMetadata("1.21.4", url,
                "0000000000000000000000000000000000000000", bytes.length, bytes.length);
        io(() -> new AssetIndexDownloader(new VersionManifestDownloader(uri -> new Fake(200, bytes, bytes.length))).download(wrongHash), "asset-index SHA-1 mismatch rejected");
        AssetIndexMetadata wrongSize = new AssetIndexMetadata("1.21.4", url, indexHash, bytes.length + 1L, bytes.length + 1L);
        io(() -> new AssetIndexDownloader(new VersionManifestDownloader(uri -> new Fake(200, bytes, bytes.length))).download(wrongSize), "asset-index size mismatch rejected");
        byte[] invalid = "not json".getBytes(StandardCharsets.UTF_8);
        AssetIndexMetadata invalidJson = new AssetIndexMetadata("1.21.4", url, sha1Hex(invalid), invalid.length, invalid.length);
        io(() -> new AssetIndexDownloader(new VersionManifestDownloader(uri -> new Fake(200, invalid, invalid.length))).download(invalidJson), "invalid asset-index JSON rejected after hash check");
        AssetIndexMetadata evilUrl = new AssetIndexMetadata("1.21.4", URI.create("https://evil.example/v1/packages/x/1.21.4.json"), indexHash, bytes.length, bytes.length);
        io(() -> new AssetIndexDownloader(new VersionManifestDownloader(uri -> { throw new AssertionError("untrusted metadata host must not be contacted"); })).download(evilUrl), "unapproved asset-index host rejected before fetch");
    }

    private static String sha1Hex(byte[] bytes) throws Exception {
        byte[] digest = java.security.MessageDigest.getInstance("SHA-1").digest(bytes);
        StringBuilder result = new StringBuilder();
        for (byte value : digest) result.append(String.format(java.util.Locale.ROOT, "%02x", value & 0xff));
        return result.toString();
    }

    private static void assetLayoutMaterializer() throws Exception {
        Path temporary = Files.createTempDirectory("mylauncher-assets-layout-");
        try {
            byte[] payload = "legacy asset content".getBytes(StandardCharsets.UTF_8);
            String hash = sha1Hex(payload);
            AssetObjectMetadata asset = new AssetObjectMetadata("minecraft/sounds/cave.ogg", hash, payload.length);
            Map<String, AssetObjectMetadata> objectMap = new LinkedHashMap<>();
            objectMap.put(asset.getName(), asset);
            AssetIndex index = new AssetIndex(objectMap, true, true);
            Path assetsRoot = temporary.resolve("game/assets");
            Path gameRoot = temporary.resolve("game");
            Path objectFile = assetsRoot.resolve(asset.getObjectRelativePath());
            Files.createDirectories(objectFile.getParent());
            Files.write(objectFile, payload);

            AssetLayoutMaterializer.Result first = AssetLayoutMaterializer.materialize(
                    index, "1.12.2", assetsRoot, gameRoot, CancellationToken.NONE);
            Path virtual = assetsRoot.resolve("virtual/1.12.2/minecraft/sounds/cave.ogg");
            Path resource = gameRoot.resolve("resources/minecraft/sounds/cave.ogg");
            eq(2, first.getFilesWritten(), "virtual and legacy resource copies created");
            eq(0, first.getFilesReused(), "first materialization has no cache hits");
            eq((long) payload.length * 2L, first.getBytesWritten(), "materialized bytes counted");
            eq(new String(payload, StandardCharsets.UTF_8),
                    new String(Files.readAllBytes(virtual), StandardCharsets.UTF_8), "virtual asset bytes retained");
            eq(new String(payload, StandardCharsets.UTF_8),
                    new String(Files.readAllBytes(resource), StandardCharsets.UTF_8), "resources asset bytes retained");

            AssetLayoutMaterializer.Result second = AssetLayoutMaterializer.materialize(
                    index, "1.12.2", assetsRoot, gameRoot, CancellationToken.NONE);
            eq(0, second.getFilesWritten(), "repeat materialization creates no new copies");
            eq(2, second.getFilesReused(), "valid materialized copies reused");

            Files.write(resource, "corrupt".getBytes(StandardCharsets.UTF_8));
            AssetLayoutMaterializer.Result repaired = AssetLayoutMaterializer.materialize(
                    index, "1.12.2", assetsRoot, gameRoot, CancellationToken.NONE);
            eq(1, repaired.getFilesWritten(), "corrupt legacy resource copy repaired");
            eq(1, repaired.getFilesReused(), "correct virtual asset reused during repair");
            eq(new String(payload, StandardCharsets.UTF_8),
                    new String(Files.readAllBytes(resource), StandardCharsets.UTF_8), "corrupt resource copy repaired from verified object");

            AssetIndex modern = new AssetIndex(objectMap, false, false);
            AssetLayoutMaterializer.Result noLayout = AssetLayoutMaterializer.materialize(
                    modern, "1.21.4", assetsRoot, gameRoot, CancellationToken.NONE);
            eq(0, noLayout.getFilesWritten(), "modern index does not create legacy aliases");

            bad(() -> {
                try { AssetLayoutMaterializer.materialize(index, "../escape", assetsRoot, gameRoot, CancellationToken.NONE); }
                catch (IOException exception) { throw new AssertionError("unsafe asset-index ID should be rejected before IO", exception); }
            }, "unsafe asset-index ID rejected");
            yes(!Files.exists(temporary.resolve("escape")), "unsafe index ID creates no path outside the expected asset layout");

            byte[] nestedPayload = "linked asset".getBytes(StandardCharsets.UTF_8);
            String nestedHash = sha1Hex(nestedPayload);
            AssetObjectMetadata nestedAsset = new AssetObjectMetadata("jump/sentinel", nestedHash, nestedPayload.length);
            Path nestedObject = assetsRoot.resolve(nestedAsset.getObjectRelativePath());
            Files.createDirectories(nestedObject.getParent());
            Files.write(nestedObject, nestedPayload);
            AssetIndex nestedIndex = new AssetIndex(java.util.Collections.singletonMap(nestedAsset.getName(), nestedAsset),
                    false, true);
            Path outside = temporary.resolve("outside");
            Files.createDirectories(outside);
            Path outsideFile = outside.resolve("sentinel");
            Files.write(outsideFile, "safe".getBytes(StandardCharsets.UTF_8));
            Path linkParent = gameRoot.resolve("resources/jump");
            try {
                Files.createSymbolicLink(linkParent, outside);
                io(() -> AssetLayoutMaterializer.materialize(nestedIndex, "1.12.2",
                        assetsRoot, gameRoot, CancellationToken.NONE), "symlinked resource directory rejected");
                eq("safe", new String(Files.readAllBytes(outsideFile), StandardCharsets.UTF_8),
                        "materializer does not write through symlink");
            } catch (UnsupportedOperationException | SecurityException exception) {
                // Some platforms disable symlink creation for ordinary users.
            }

            final boolean[] cancelled = {true};
            try {
                AssetLayoutMaterializer.materialize(index, "1.12.2", assetsRoot, gameRoot, () -> cancelled[0]);
                throw new AssertionError("already-cancelled asset materialization should stop");
            } catch (InterruptedIOException expected) {
                checks++;
            }
        } finally {
            try (java.util.stream.Stream<Path> paths = Files.walk(temporary)) {
                paths.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                    try { Files.deleteIfExists(path); } catch (IOException exception) { throw new java.io.UncheckedIOException(exception); }
                });
            }
        }
    }

    private static void installationPlan() {
        String hash1 = "0123456789abcdef0123456789abcdef01234567";
        String hash2 = "fedcba9876543210fedcba9876543210fedcba98";
        URI clientUrl = URI.create("https://piston-data.mojang.com/v1/objects/" + hash1 + "/client.jar");
        DownloadArtifact client = new DownloadArtifact(clientUrl, hash1, 700, null);
        AssetIndexMetadata indexMetadata = new AssetIndexMetadata("1.21.4",
                URI.create("https://piston-meta.mojang.com/v1/packages/" + hash2 + "/1.21.4.json"), hash2, 80, 1000);
        DownloadArtifact libraryArtifact = new DownloadArtifact(
                URI.create("https://libraries.minecraft.net/org/example/core/1.0/core.jar"),
                hash1, 100, "org/example/core/1.0/core.jar");
        LibraryRule linuxRule = new LibraryRule(true, "linux", "aarch64", "6\\..*", null);
        yes(new LibraryRule(true, "windows", null, null, "10.0.17134", null, null)
                .matches("windows", "amd64", "10.0.17134", null),
                "OS versionRange lower bound is inclusive");
        yes(!new LibraryRule(true, "windows", null, null, null, "10.0.17134", null)
                .matches("windows", "amd64", "10.0.17134", null),
                "OS versionRange upper bound is exclusive");
        yes(new LibraryRule(true, null, null, null,
                java.util.Collections.singletonMap("has_custom_resolution", false))
                .matches("linux", "aarch64", "5.10.0", java.util.Collections.emptyMap()),
                "omitted launcher feature defaults to false");
        yes(!new LibraryRule(true, null, null, null,
                java.util.Collections.singletonMap("is_demo_user", true))
                .matches("linux", "aarch64", "5.10.0", java.util.Collections.emptyMap()),
                "omitted launcher feature does not satisfy true");
        bad(() -> new LibraryRule(true, "windows", null, null, "10.0.19045", "10.0.17134", null),
                "descending OS versionRange rejected");
        yes(new LibraryRule(true, "osx", null, "^10\\.", null)
                .matches("osx", "aarch64", "10.14.6", null),
                "prefix-only legacy OS-version regex matches a longer version string");
        yes(!new LibraryRule(true, "osx", null, "^10\\..*", null)
                .matches("osx", "aarch64", "110.14.6", null),
                "OS-version regex does not match a partial prefix of another version");
        yes(new LibraryRule(true, "linux", ".*64.*", null, null)
                .matches("linux", "x86_64", "6.1", null),
                "OS-architecture rule is evaluated as a regex");
        yes(!new LibraryRule(true, "linux", "x86", null, null)
                .matches("linux", "x86_64", "6.1", null),
                "32-bit architecture rule must not match 64-bit architecture containing the same substring");
        bad(() -> new LibraryRule(true, null, "[", null, null),
                "invalid OS-architecture regex rejected");
        LibraryMetadata included = new LibraryMetadata("org.example:core:1.0", libraryArtifact,
                java.util.Collections.emptyMap(), java.util.Collections.emptyMap(),
                java.util.Collections.singletonList(linuxRule), java.util.Collections.emptyList());
        LibraryMetadata duplicate = new LibraryMetadata("org.example:core:1.0-copy", libraryArtifact,
                java.util.Collections.emptyMap(), java.util.Collections.emptyMap(),
                java.util.Collections.singletonList(linuxRule), java.util.Collections.emptyList());
        DownloadArtifact windowsArtifact = new DownloadArtifact(
                URI.create("https://libraries.minecraft.net/org/example/windows/1.0/windows.jar"),
                hash2, 200, "org/example/windows/1.0/windows.jar");
        LibraryMetadata excluded = new LibraryMetadata("org.example:windows:1.0", windowsArtifact,
                java.util.Collections.emptyMap(), java.util.Collections.emptyMap(),
                java.util.Collections.singletonList(new LibraryRule(true, "windows", null, null, null)),
                java.util.Collections.emptyList());
        DownloadArtifact nativeArtifact = new DownloadArtifact(
                URI.create("https://libraries.minecraft.net/org/example/native/1.0/native.jar"),
                hash2, 300, "org/example/native/1.0/native.jar");
        LibraryMetadata nativeOnly = new LibraryMetadata("org.example:native:1.0", null,
                java.util.Collections.singletonMap("natives-linux-arm64", nativeArtifact),
                java.util.Collections.singletonMap("linux", "natives-linux-arch"),
                java.util.Collections.emptyList(), java.util.Collections.emptyList());
        VersionDetails details = new VersionDetails("1.21.4", "net.minecraft.client.main.Main", "1.21.4",
                indexMetadata, client, 21, java.util.Arrays.asList(included, duplicate, excluded, nativeOnly));
        AssetObjectMetadata assetA = new AssetObjectMetadata("textures/block/a.png", hash1, 4);
        AssetObjectMetadata assetB = new AssetObjectMetadata("textures/block/b.png", hash1, 4);
        AssetObjectMetadata assetC = new AssetObjectMetadata("textures/block/c.png", hash2, 9);
        java.util.Map<String, AssetObjectMetadata> objects = new java.util.LinkedHashMap<>();
        objects.put(assetA.getName(), assetA); objects.put(assetB.getName(), assetB); objects.put(assetC.getName(), assetC);
        AssetIndex assets = new AssetIndex(objects, false, false);
        LibraryPlatform platform = new LibraryPlatform("linux", "aarch64", "6.1.0",
                java.util.Collections.emptyMap());
        InstallationPlan plan = InstallationPlanner.create(details, assets, platform);
        eq("1.21.4", plan.getVersionId(), "plan version ID retained");
        eq("1.21.4", plan.getAssetIndexId(), "plan asset index ID retained");
        eq(4, plan.getTaskCount(), "plan deduplicates identical library and asset content tasks");
        eq(1, plan.count(DownloadTask.Kind.CLIENT), "one client JAR task");
        eq(1, plan.count(DownloadTask.Kind.LIBRARY), "allowed regular library included once");
        eq(2, plan.count(DownloadTask.Kind.ASSET), "asset objects deduplicated by content hash");
        eq(813L, plan.getTotalBytes(), "plan total bytes computed from unique downloads");
        eq("libraries/org/example/core/1.0/core.jar", plan.getTasks().get(0).getRelativePath(), "game downloads sorted by path");
        yes(plan.getTasks().stream().noneMatch(task -> task.getRelativePath().contains("windows")), "non-matching OS library rule excluded");
        yes(plan.getTasks().stream().noneMatch(task -> task.getRelativePath().contains("native")), "native-only library skipped until Android runtime selection exists");
        eq(DownloadTask.Destination.ASSET_DIRECTORY, plan.getTasks().get(2).getDestination(), "assets directed to dedicated asset root");
        eq("objects/01/0123456789abcdef0123456789abcdef01234567", plan.getTasks().get(2).getRelativePath(), "asset destination uses hash object path");
        unsupported(() -> plan.getTasks().clear(), "installation task list immutable");
        bad(() -> new LibraryPlatform("linux", "aarch64", null,
                java.util.Collections.singletonMap("feature", null)), "null feature value rejected");
        bad(() -> new DownloadTask(DownloadTask.Kind.ASSET, DownloadTask.Destination.GAME_DIRECTORY,
                URI.create("https://resources.download.minecraft.net/01/" + hash1), "objects/01/" + hash1, 4, hash1),
                "asset task cannot use game directory root");
        bad(() -> new DownloadTask(DownloadTask.Kind.CLIENT, DownloadTask.Destination.GAME_DIRECTORY,
                URI.create("https://evil.example/client.jar"), "versions/1.21.4/1.21.4.jar", 4, hash1),
                "unapproved task URL rejected");

        DownloadArtifact collision = new DownloadArtifact(
                URI.create("https://libraries.minecraft.net/org/example/core/1.0/core.jar"), hash2,
                100, "org/example/core/1.0/core.jar");
        LibraryMetadata conflicting = new LibraryMetadata("org.example:conflict:1.0", collision,
                java.util.Collections.emptyMap(), java.util.Collections.emptyMap(),
                java.util.Collections.singletonList(linuxRule), java.util.Collections.emptyList());
        VersionDetails withConflict = new VersionDetails("1.21.4", "net.minecraft.client.main.Main", "1.21.4",
                indexMetadata, client, 21, java.util.Arrays.asList(included, conflicting));
        bad(() -> InstallationPlanner.create(withConflict, assets, platform), "conflicting output path metadata rejected");

        LoggingConfigurationMetadata logConfig = new LoggingConfigurationMetadata("client.xml",
                URI.create("https://piston-data.mojang.com/v1/objects/" + hash2 + "/client.xml"),
                hash2, 50);
        VersionDetails withLogging = new VersionDetails("1.21.4", "net.minecraft.client.main.Main", "1.21.4",
                indexMetadata, client, 21, java.util.Arrays.asList(included, duplicate, excluded, nativeOnly),
                VersionArgumentSet.empty(), null, logConfig);
        InstallationPlan loggingPlan = InstallationPlanner.create(withLogging, assets, platform);
        eq(1, loggingPlan.count(DownloadTask.Kind.LOGGING), "optional logging config included once");
        eq(863L, loggingPlan.getTotalBytes(), "logging configuration bytes added to total");
        DownloadTask loggingTask = loggingPlan.getTasks().stream()
                .filter(task -> task.getKind() == DownloadTask.Kind.LOGGING).findFirst().get();
        eq("log_configs/client.xml", loggingTask.getRelativePath(), "logging configuration uses Minecraft assets/log_configs layout");
        eq(DownloadTask.Destination.ASSET_DIRECTORY, loggingTask.getDestination(), "logging configuration downloads to assets directory");
        bad(() -> new DownloadTask(DownloadTask.Kind.LOGGING, DownloadTask.Destination.GAME_DIRECTORY,
                URI.create("https://piston-data.mojang.com/v1/objects/" + hash2 + "/client.xml"),
                "log_configs/client.xml", 50, hash2), "logging config cannot target game root");

    }

    private static void classpathResolver() throws Exception {
        byte[] clientBytes = "client-jar-bytes".getBytes(StandardCharsets.UTF_8);
        byte[] libraryBytes = "library-a".getBytes(StandardCharsets.UTF_8);
        byte[] loggingBytes = "<Configuration/>".getBytes(StandardCharsets.UTF_8);
        byte[] indexBytes = "{\"objects\":{}}".getBytes(StandardCharsets.UTF_8);
        String clientHash = sha1Hex(clientBytes);
        String libraryHash = sha1Hex(libraryBytes);
        String loggingHash = sha1Hex(loggingBytes);
        String indexHash = sha1Hex(indexBytes);
        String placeholderHash = "0123456789abcdef0123456789abcdef01234567";

        DownloadArtifact clientArtifact = new DownloadArtifact(
                URI.create("https://piston-data.mojang.com/v1/objects/" + clientHash + "/client.jar"),
                clientHash, clientBytes.length, null);
        DownloadArtifact libraryArtifact = new DownloadArtifact(
                URI.create("https://libraries.minecraft.net/org/example/core/1.0/core-1.0.jar"),
                libraryHash, libraryBytes.length, "org/example/core/1.0/core-1.0.jar");
        LibraryMetadata linuxLibrary = new LibraryMetadata("org.example:core:1.0", libraryArtifact,
                java.util.Collections.emptyMap(), java.util.Collections.emptyMap(),
                java.util.Collections.singletonList(new LibraryRule(true, "linux", "aarch64", null, null)),
                java.util.Collections.emptyList());
        LibraryMetadata duplicateLibrary = new LibraryMetadata("org.example:core-copy:1.0", libraryArtifact,
                java.util.Collections.emptyMap(), java.util.Collections.emptyMap(),
                java.util.Collections.emptyList(), java.util.Collections.emptyList());
        DownloadArtifact windowsArtifact = new DownloadArtifact(
                URI.create("https://libraries.minecraft.net/org/example/windows/1.0/windows-1.0.jar"),
                placeholderHash, 12, "org/example/windows/1.0/windows-1.0.jar");
        LibraryMetadata windowsLibrary = new LibraryMetadata("org.example:windows:1.0", windowsArtifact,
                java.util.Collections.emptyMap(), java.util.Collections.emptyMap(),
                java.util.Collections.singletonList(new LibraryRule(true, "windows", null, null, null)),
                java.util.Collections.emptyList());
        AssetIndexMetadata assetIndex = new AssetIndexMetadata("1.21.4",
                URI.create("https://piston-meta.mojang.com/v1/packages/" + indexHash + "/index.json"),
                indexHash, indexBytes.length, 200);
        LoggingConfigurationMetadata logging = new LoggingConfigurationMetadata("client-log4j.xml",
                URI.create("https://piston-data.mojang.com/v1/objects/" + loggingHash + "/logging.xml"),
                loggingHash, loggingBytes.length);
        VersionDetails details = new VersionDetails("1.21.4", "net.minecraft.client.main.Main",
                "1.21.4", assetIndex, clientArtifact, 21,
                Arrays.asList(linuxLibrary, duplicateLibrary, windowsLibrary),
                VersionArgumentSet.empty(), null, logging);

        Path temporary = Files.createTempDirectory("mylauncher-classpath-tests-");
        try {
            Path game = temporary.resolve("game");
            Path clientPath = game.resolve("versions/1.21.4/1.21.4.jar");
            Path libraryPath = game.resolve("libraries/org/example/core/1.0/core-1.0.jar");
            Path loggingPath = game.resolve("assets/log_configs/client-log4j.xml");
            Path indexPath = game.resolve("assets/indexes/1.21.4.json");
            Files.createDirectories(clientPath.getParent());
            Files.createDirectories(libraryPath.getParent());
            Files.createDirectories(loggingPath.getParent());
            Files.createDirectories(indexPath.getParent());
            Files.write(clientPath, clientBytes);
            Files.write(libraryPath, libraryBytes);
            Files.write(loggingPath, loggingBytes);
            Files.write(indexPath, indexBytes);

            LibraryPlatform linux = new LibraryPlatform("linux", "aarch64", "6.1.0",
                    java.util.Collections.emptyMap());
            ClasspathResolver.Result result = ClasspathResolver.resolve(details, linux, game);
            eq(2, result.getClasspathEntries().size(), "compatible libraries deduplicated in resolved classpath");
            eq(libraryPath.toAbsolutePath().normalize(), result.getClasspathEntries().get(0),
                    "library order follows version metadata");
            eq(clientPath.toAbsolutePath().normalize(), result.getClasspathEntries().get(1),
                    "client JAR appended to the classpath");
            eq(result.getClasspathEntries().get(0) + java.io.File.pathSeparator
                            + result.getClasspathEntries().get(1),
                    result.getClasspath(), "classpath string uses host path separator");
            eq(loggingPath.toAbsolutePath().normalize().toString(), result.getLoggingConfigurationPath(),
                    "logging config path resolved from checked asset cache");

            DownloadArtifact desktopNativeArtifact = new DownloadArtifact(
                    URI.create("https://libraries.minecraft.net/org/lwjgl/lwjgl-glfw/3.4.1/"
                            + "lwjgl-glfw-3.4.1-natives-linux.jar"),
                    placeholderHash, 321,
                    "org/lwjgl/lwjgl-glfw/3.4.1/lwjgl-glfw-3.4.1-natives-linux.jar");
            LibraryMetadata desktopNativeLibrary = new LibraryMetadata(
                    "org.lwjgl:lwjgl-glfw:3.4.1:natives-linux", desktopNativeArtifact,
                    java.util.Collections.emptyMap(), java.util.Collections.emptyMap(),
                    java.util.Collections.singletonList(new LibraryRule(true, "linux", null, null, null)),
                    java.util.Collections.emptyList());
            VersionDetails desktopDetails = new VersionDetails("1.21.4", "net.minecraft.client.main.Main",
                    "1.21.4", assetIndex, clientArtifact, 21,
                    Arrays.asList(duplicateLibrary, desktopNativeLibrary),
                    VersionArgumentSet.empty(), null, logging);
            LibraryPlatform desktopLinux = new LibraryPlatform("linux", "x86_64", "6.1.0",
                    java.util.Collections.emptyMap());
            ClasspathResolver.Result desktopClasspath = ClasspathResolver.resolve(desktopDetails, desktopLinux, game);
            eq(2, desktopClasspath.getClasspathEntries().size(),
                    "extractable natives-linux archive is omitted from the classpath even on matching x86_64 target");
            yes(desktopClasspath.getClasspathEntries().stream().noneMatch(entry ->
                    entry.toString().endsWith(desktopNativeArtifact.getPath())),
                    "native classifier JAR is never appended as an ordinary classpath dependency");
            InstallationPlan noNativeTargetPlan = InstallationPlanner.create(
                    desktopDetails, new AssetIndex(java.util.Collections.emptyMap(), false, false), desktopLinux);
            eq(0, noNativeTargetPlan.count(DownloadTask.Kind.NATIVE),
                    "native archives are not silently treated as ordinary libraries without a native plan");
            eq(1, noNativeTargetPlan.count(DownloadTask.Kind.LIBRARY),
                    "regular classifier-free JAR remains in the install plan when native target is absent");
            yes(noNativeTargetPlan.getTasks().stream().noneMatch(task ->
                    task.getRelativePath().equals(desktopNativeArtifact.getPath())),
                    "explicit natives-linux archive is omitted until native extraction is enabled");

            Path hostJava = java.nio.file.Paths.get(System.getProperty("java.home"), "bin", "java").toRealPath();
            String specificationVersion = System.getProperty("java.specification.version");
            int hostMajor = specificationVersion.startsWith("1.")
                    ? Integer.parseInt(specificationVersion.substring(2))
                    : Integer.parseInt(specificationVersion);
            VersionDetails hostRuntimeDetails = new VersionDetails("1.21.4", "net.minecraft.client.main.Main",
                    "1.21.4", assetIndex, clientArtifact, hostMajor,
                    Arrays.asList(linuxLibrary, duplicateLibrary, windowsLibrary),
                    VersionArgumentSet.empty(), null, logging);
            LaunchPreflight.Result preflight = new LaunchPreflight().inspect(hostRuntimeDetails, linux, game,
                    hostJava, CancellationToken.NONE, 15_000);
            eq(hostMajor, preflight.getRequiredJavaMajorVersion(),
                    "launch preflight verifies metadata-required Java runtime major");
            eq(result.getClasspath(), preflight.getClasspath().getClasspath(),
                    "launch preflight returns the checksum-verified classpath");

            VersionArgumentSet launchArguments = new VersionArgumentSet(
                    java.util.Collections.singletonList(new VersionArgumentSet.ArgumentEntry(
                            java.util.Collections.singletonList("--version"),
                            java.util.Collections.emptyList())),
                    java.util.Collections.emptyList());
            VersionDetails coordinatorDetails = new VersionDetails("1.21.4", "net.minecraft.client.main.Main",
                    "1.21.4", assetIndex, clientArtifact, hostMajor,
                    Arrays.asList(linuxLibrary, duplicateLibrary, windowsLibrary),
                    launchArguments, null, logging);
            Path nativesDirectory = game.resolve("natives/1.21.4");
            Files.createDirectories(nativesDirectory);
            LaunchContext launchContext = new LaunchContext.Builder("Steve_123",
                    "12345678-1234-1234-1234-1234567890ab", "test-access-token")
                    .gameDirectory(game).assetsRoot(game.resolve("assets"))
                    .librariesDirectory(game.resolve("libraries")).nativesDirectory(nativesDirectory)
                    .classpath(result.getClasspath()).loggingConfigPath(result.getLoggingConfigurationPath())
                    .platform(linux).build();
            List<String> loggingCommand = LaunchCommandBuilder.build(coordinatorDetails, launchContext);
            eq("-Dlog4j.configurationFile=" + result.getLoggingConfigurationPath(),
                    loggingCommand.stream().filter(value -> value.startsWith("-Dlog4j.configurationFile="))
                            .findFirst().orElse(""),
                    "logging configuration argument is injected into JVM argv when metadata keeps it outside arguments.jvm");
            LaunchContext missingLoggingPath = new LaunchContext.Builder("Steve_123",
                    "12345678-1234-1234-1234-1234567890ab", "test-access-token")
                    .gameDirectory(game).assetsRoot(game.resolve("assets"))
                    .librariesDirectory(game.resolve("libraries")).nativesDirectory(nativesDirectory)
                    .classpath(result.getClasspath()).platform(linux).build();
            bad(() -> LaunchCommandBuilder.build(coordinatorDetails, missingLoggingPath),
                    "launch command fails clearly if logging metadata requires a missing configuration path");
            LaunchCoordinator.LaunchResult launchResult = new LaunchCoordinator().launch(
                    coordinatorDetails, launchContext, hostJava, CancellationToken.NONE, 15_000, null);
            yes(!launchResult.isSuccess(),
                    "launch coordinator reports nonzero child exit for deliberately invalid fixture client");
            yes(launchResult.getElapsedMillis() >= 0,
                    "launch coordinator reports child process timing");
            LaunchContext mismatchedClasspath = new LaunchContext.Builder("Steve_123",
                    "12345678-1234-1234-1234-1234567890ab", "test-access-token")
                    .gameDirectory(game).assetsRoot(game.resolve("assets"))
                    .librariesDirectory(game.resolve("libraries")).nativesDirectory(nativesDirectory)
                    .classpath("unverified-classpath").loggingConfigPath(result.getLoggingConfigurationPath())
                    .platform(linux).build();
            io(() -> {
                try {
                    new LaunchCoordinator().launch(coordinatorDetails, mismatchedClasspath, hostJava,
                            CancellationToken.NONE, 15_000, null);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Interrupted during launch classpath mismatch test", exception);
                }
            }, "launch coordinator rejects a classpath that differs from verified installed files");
            VersionDetails wrongRuntimeDetails = new VersionDetails("1.21.4", "net.minecraft.client.main.Main",
                    "1.21.4", assetIndex, clientArtifact, hostMajor == 8 ? 9 : 8,
                    Arrays.asList(linuxLibrary, duplicateLibrary, windowsLibrary),
                    VersionArgumentSet.empty(), null, logging);
            io(() -> {
                try {
                    new LaunchPreflight().inspect(wrongRuntimeDetails, linux, game, hostJava,
                            CancellationToken.NONE, 15_000);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Interrupted during launch preflight mismatch test", exception);
                }
            }, "launch preflight rejects Java runtime major mismatch");
            unsupported(() -> result.getClasspathEntries().clear(), "resolved classpath entries immutable");
            io(() -> ClasspathResolver.resolve(details, linux, game.resolve("missing")),
                    "missing game directory rejected");
            io(() -> ClasspathResolver.resolve(details, linux, temporary.resolve("absent")),
                    "absent required client file rejected");

            Files.write(libraryPath, "librarX-a".getBytes(StandardCharsets.UTF_8));
            io(() -> ClasspathResolver.resolve(details, linux, game), "library checksum mismatch rejected");
            Files.write(libraryPath, libraryBytes);
            Files.write(clientPath, "corrupt-client!!".getBytes(StandardCharsets.UTF_8));
            io(() -> ClasspathResolver.resolve(details, linux, game), "client checksum mismatch rejected");
            Files.write(clientPath, clientBytes);
            Files.write(loggingPath, "different-config".getBytes(StandardCharsets.UTF_8));
            io(() -> ClasspathResolver.resolve(details, linux, game), "logging configuration checksum mismatch rejected");
            Files.write(loggingPath, loggingBytes);

            Path linkedRootParent = temporary.resolve("linked-game-parent");
            try {
                Files.createSymbolicLink(linkedRootParent, temporary);
                io(() -> ClasspathResolver.resolve(details, linux, linkedRootParent.resolve("game")),
                        "symlinked ancestor of classpath root rejected");
            } catch (UnsupportedOperationException | SecurityException | java.nio.file.FileSystemException exception) {
                // Some filesystems disable symbolic links for ordinary users.
            }

            Path linkRoot = temporary.resolve("linked-game");
            Files.createDirectories(linkRoot);
            try {
                Files.createSymbolicLink(linkRoot.resolve("versions"), game.resolve("versions"));
                io(() -> ClasspathResolver.resolve(details, linux, linkRoot),
                        "classpath symlink component rejected");
            } catch (UnsupportedOperationException | SecurityException exception) {
                // Symlink creation is unavailable on some platforms.
            }
        } finally {
            try (java.util.stream.Stream<Path> paths = Files.walk(temporary)) {
                paths.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                    try { Files.deleteIfExists(path); }
                    catch (IOException exception) { throw new java.io.UncheckedIOException(exception); }
                });
            }
        }
    }

    private static void nativeLibraryPlanner() {
        String hash1 = "0123456789abcdef0123456789abcdef01234567";
        String hash2 = "fedcba9876543210fedcba9876543210fedcba98";
        AssetIndexMetadata indexMetadata = new AssetIndexMetadata("1.21.4",
                URI.create("https://piston-meta.mojang.com/v1/packages/" + hash1 + "/index.json"),
                hash1, 80, 800);
        DownloadArtifact client = new DownloadArtifact(
                URI.create("https://piston-data.mojang.com/v1/objects/" + hash1 + "/client.jar"),
                hash1, 700, null);
        DownloadArtifact arm64 = new DownloadArtifact(
                URI.create("https://libraries.minecraft.net/org/example/native/1.0/native-arm64.jar"),
                hash1, 99, "org/example/native/1.0/native-1.0-natives.jar");
        LibraryRule linuxRule = new LibraryRule(true, "linux", "aarch64", null, null);
        Map<String, DownloadArtifact> classifiers = new LinkedHashMap<>();
        classifiers.put("natives-linux-arm64", arm64);
        Map<String, String> linuxNatives = java.util.Collections.singletonMap("linux", "natives-linux-${arch}");
        LibraryMetadata nativeOne = new LibraryMetadata("org.example:native-one:1.0", null,
                classifiers, linuxNatives, java.util.Collections.singletonList(linuxRule),
                java.util.Collections.singletonList("META-INF/"));
        LibraryMetadata nativeDuplicate = new LibraryMetadata("org.example:native-two:1.0", null,
                classifiers, linuxNatives, java.util.Collections.singletonList(linuxRule),
                java.util.Collections.singletonList("META-INF/"));
        DownloadArtifact excludedArtifact = new DownloadArtifact(
                URI.create("https://libraries.minecraft.net/org/example/native/1.0/windows-native.jar"),
                hash2, 120, "org/example/native/1.0/windows-natives.jar");
        LibraryMetadata windowsOnly = new LibraryMetadata("org.example:windows-native:1.0", null,
                java.util.Collections.singletonMap("natives-windows-64", excludedArtifact),
                java.util.Collections.singletonMap("windows", "natives-windows-${arch}"),
                java.util.Collections.singletonList(new LibraryRule(true, "windows", null, null, null)),
                java.util.Collections.emptyList());
        VersionDetails details = new VersionDetails("1.21.4", "net.minecraft.client.main.Main", "1.21.4",
                indexMetadata, client, 21,
                Arrays.asList(nativeOne, nativeDuplicate, windowsOnly),
                VersionArgumentSet.empty(), null);
        LibraryPlatform linux = new LibraryPlatform("linux", "aarch64", "6.1.0",
                java.util.Collections.emptyMap());
        NativeLibraryPlan plan = NativeLibraryPlanner.create(details, linux, "linux", "arm64");
        eq("1.21.4", plan.getVersionId(), "native plan version retained");
        eq("linux", plan.getNativeOsName(), "native OS target retained explicitly");
        eq("arm64", plan.getArchitectureToken(), "native architecture token retained explicitly");
        eq(1, plan.getArchiveCount(), "identical native archive paths deduplicated");
        eq(99L, plan.getTotalBytes(), "unique native archive bytes totaled");
        NativeLibraryArchive archive = plan.getArchives().get(0);
        eq("libraries/org/example/native/1.0/native-1.0-natives.jar", archive.getRelativeLibraryPath(),
                "native jar resolved under libraries cache");
        eq("org.example:native-one:1.0", archive.getLibraryName(),
                "first metadata occurrence preserved when native payload is deduplicated");
        eq(1, archive.getExtractionExcludes().size(), "native extraction exclusions preserved");
        unsupported(() -> plan.getArchives().clear(), "native archive list immutable");
        eq(0, NativeLibraryPlanner.create(details, linux, "windows", "64").getArchiveCount(),
                "platform-rule-incompatible native library skipped");
        LibraryMetadata osNeutralWindowsNative = new LibraryMetadata(
                "org.example:os-neutral-windows-native:1.0", null,
                java.util.Collections.singletonMap("natives-windows-64", excludedArtifact),
                java.util.Collections.singletonMap("windows", "natives-windows-${arch}"),
                java.util.Collections.emptyList(), java.util.Collections.emptyList());
        VersionDetails osNeutralNativeDetails = new VersionDetails("1.21.4", "net.minecraft.client.main.Main",
                "1.21.4", indexMetadata, client, 21, Arrays.asList(osNeutralWindowsNative),
                VersionArgumentSet.empty(), null);
        eq(0, NativeLibraryPlanner.create(osNeutralNativeDetails, linux, "windows", "64").getArchiveCount(),
                "native plan cannot select a foreign OS archive from a library with no OS rule");
        bad(() -> NativeLibraryPlanner.create(details, linux, "../linux", "arm64"),
                "unsafe native OS token rejected");

        LibraryMetadata missingClassifier = new LibraryMetadata("org.example:missing-native:1.0", null,
                java.util.Collections.singletonMap("natives-windows-64", excludedArtifact),
                java.util.Collections.singletonMap("linux", "natives-linux-${arch}"),
                java.util.Collections.singletonList(linuxRule), java.util.Collections.emptyList());
        VersionDetails missingDetails = new VersionDetails("1.21.4", "net.minecraft.client.main.Main",
                "1.21.4", indexMetadata, client, 21,
                Arrays.asList(missingClassifier), VersionArgumentSet.empty(), null);
        bad(() -> NativeLibraryPlanner.create(missingDetails, linux, "linux", "arm64"),
                "declared native template with absent classifier rejected");

        DownloadArtifact conflictingArtifact = new DownloadArtifact(
                URI.create("https://libraries.minecraft.net/org/example/native/1.0/native-conflict.jar"),
                hash2, 101, "org/example/native/1.0/native-1.0-natives.jar");
        LibraryMetadata conflicting = new LibraryMetadata("org.example:conflict-native:1.0", null,
                java.util.Collections.singletonMap("natives-linux-arm64", conflictingArtifact),
                linuxNatives, java.util.Collections.singletonList(linuxRule), java.util.Collections.singletonList("META-INF/"));
        VersionDetails conflictingDetails = new VersionDetails("1.21.4", "net.minecraft.client.main.Main",
                "1.21.4", indexMetadata, client, 21,
                Arrays.asList(nativeOne, conflicting), VersionArgumentSet.empty(), null);
        bad(() -> NativeLibraryPlanner.create(conflictingDetails, linux, "linux", "arm64"),
                "conflicting native outputs rejected");
        AssetIndex noAssets = new AssetIndex(java.util.Collections.emptyMap(), false, false);
        InstallationPlan withNativeTask = InstallationPlanner.create(details, noAssets, linux, plan);
        eq(2, withNativeTask.getTaskCount(), "native plan adds one archive to the client plan");
        eq(1, withNativeTask.count(DownloadTask.Kind.NATIVE), "one native download task planned");
        eq(799L, withNativeTask.getTotalBytes(), "native bytes included in install total");
        yes(withNativeTask.getTasks().stream().anyMatch(task ->
                task.getKind() == DownloadTask.Kind.NATIVE
                        && task.getRelativePath().equals(archive.getRelativeLibraryPath())),
                "native archive path connected to install task");

        LibraryRule modernLinuxRule = new LibraryRule(true, "linux", null, null, null);
        DownloadArtifact epollArm64 = new DownloadArtifact(
                URI.create("https://libraries.minecraft.net/io/netty/netty-transport-native-epoll/4.2.7.Final/"
                        + "netty-transport-native-epoll-4.2.7.Final-linux-aarch_64.jar"),
                hash1, 151, "io/netty/netty-transport-native-epoll/4.2.7.Final/"
                        + "netty-transport-native-epoll-4.2.7.Final-linux-aarch_64.jar");
        DownloadArtifact epollX64 = new DownloadArtifact(
                URI.create("https://libraries.minecraft.net/io/netty/netty-transport-native-epoll/4.2.7.Final/"
                        + "netty-transport-native-epoll-4.2.7.Final-linux-x86_64.jar"),
                hash2, 152, "io/netty/netty-transport-native-epoll/4.2.7.Final/"
                        + "netty-transport-native-epoll-4.2.7.Final-linux-x86_64.jar");
        DownloadArtifact lwjglDesktop = new DownloadArtifact(
                URI.create("https://libraries.minecraft.net/org/lwjgl/lwjgl-glfw/3.4.1/"
                        + "lwjgl-glfw-3.4.1-natives-linux.jar"),
                hash2, 153, "org/lwjgl/lwjgl-glfw/3.4.1/lwjgl-glfw-3.4.1-natives-linux.jar");
        LibraryMetadata epollArm64Library = new LibraryMetadata(
                "io.netty:netty-transport-native-epoll:4.2.7.Final:linux-aarch_64",
                epollArm64, java.util.Collections.emptyMap(), java.util.Collections.emptyMap(),
                java.util.Collections.singletonList(modernLinuxRule), java.util.Collections.emptyList());
        LibraryMetadata epollX64Library = new LibraryMetadata(
                "io.netty:netty-transport-native-epoll:4.2.7.Final:linux-x86_64",
                epollX64, java.util.Collections.emptyMap(), java.util.Collections.emptyMap(),
                java.util.Collections.singletonList(modernLinuxRule), java.util.Collections.emptyList());
        LibraryMetadata lwjglDesktopLibrary = new LibraryMetadata(
                "org.lwjgl:lwjgl-glfw:3.4.1:natives-linux",
                lwjglDesktop, java.util.Collections.emptyMap(), java.util.Collections.emptyMap(),
                java.util.Collections.singletonList(modernLinuxRule), java.util.Collections.emptyList());
        VersionDetails modernDetails = new VersionDetails("26.1.2", "net.minecraft.client.main.Main", "30",
                indexMetadata, client, 25,
                Arrays.asList(epollArm64Library, epollX64Library, lwjglDesktopLibrary),
                VersionArgumentSet.empty(), null);
        yes(epollArm64Library.isCompatibleWithPlatform(linux),
                "new Linux aarch_64 classifier maps to an ARM64 platform");
        yes(!epollX64Library.isCompatibleWithPlatform(linux),
                "new Linux x86_64 classifier is excluded from ARM64 platform");
        yes(!lwjglDesktopLibrary.isCompatibleWithPlatform(linux),
                "unqualified natives-linux classifier is treated as desktop x86_64");
        yes(!epollArm64Library.hasExplicitNativeClassifier(),
                "Netty's linux-aarch_64 classifier remains a classpath library with embedded native resources");
        NativeLibraryPlan modernPlan = NativeLibraryPlanner.create(modernDetails, linux, "linux", "arm64");
        eq(0, modernPlan.getArchiveCount(),
                "Minecraft 26.1 metadata does not misclassify Netty's embedded ARM64 native resources as extractable archives");
        InstallationPlan modernInstallPlan = InstallationPlanner.create(modernDetails, noAssets, linux, modernPlan);
        eq(2, modernInstallPlan.getTaskCount(),
                "compatible ARM64 Netty artifact and client JAR are downloaded exactly once");
        eq(0, modernInstallPlan.count(DownloadTask.Kind.NATIVE),
                "embedded Netty native resource is not unpacked into the launch native directory");
        eq(1, modernInstallPlan.count(DownloadTask.Kind.LIBRARY),
                "compatible ARM64 classifier remains on the launch classpath");
        yes(modernInstallPlan.getTasks().stream().anyMatch(task ->
                task.getRelativePath().equals(epollArm64.getPath())),
                "compatible ARM64 classifier is retained in installation plan");
        yes(modernInstallPlan.getTasks().stream().noneMatch(task ->
                task.getRelativePath().equals(epollX64.getPath())
                        || task.getRelativePath().equals(lwjglDesktop.getPath())),
                "incompatible desktop native classifiers are not downloaded for Android ARM64");

        DownloadArtifact windowsArm64Native = new DownloadArtifact(
                URI.create("https://libraries.minecraft.net/org/lwjgl/lwjgl-glfw/3.4.1/"
                        + "lwjgl-glfw-3.4.1-natives-windows-arm64.jar"),
                hash1, 154, "org/lwjgl/lwjgl-glfw/3.4.1/lwjgl-glfw-3.4.1-natives-windows-arm64.jar");
        LibraryMetadata windowsArm64NativeLibrary = new LibraryMetadata(
                "org.lwjgl:lwjgl-glfw:3.4.1:natives-windows-arm64",
                windowsArm64Native, java.util.Collections.emptyMap(), java.util.Collections.emptyMap(),
                java.util.Collections.singletonList(new LibraryRule(true, "windows", null, null, null)),
                java.util.Collections.emptyList());
        VersionDetails windowsNativeDetails = new VersionDetails("26.1.2", "net.minecraft.client.main.Main", "30",
                indexMetadata, client, 25, Arrays.asList(windowsArm64NativeLibrary),
                VersionArgumentSet.empty(), null);
        LibraryPlatform windowsArm64Platform = new LibraryPlatform("windows", "aarch64", "10.0.19045",
                java.util.Collections.emptyMap());
        NativeLibraryPlan windowsNativePlan = NativeLibraryPlanner.create(
                windowsNativeDetails, windowsArm64Platform, "windows", "arm64");
        eq(1, windowsNativePlan.getArchiveCount(),
                "explicit natives-windows-arm64 coordinate is recognized as an extractable native archive");
        yes(windowsNativePlan.getArchives().get(0).getExtractionExcludes().contains("META-INF/"),
                "native extraction excludes META-INF by default when metadata omits exclusions");
        VersionDetails otherVersion = new VersionDetails("1.20.6", "net.minecraft.client.main.Main",
                "1.21.4", indexMetadata, client, 21,
                Arrays.asList(nativeOne), VersionArgumentSet.empty(), null);
        bad(() -> InstallationPlanner.create(otherVersion, noAssets, linux, plan),
                "native plan from another version rejected");
    }

    private static void installationService() throws Exception {
        byte[] clientBytes = "client".getBytes(StandardCharsets.UTF_8);
        byte[] libraryBytes = "lib".getBytes(StandardCharsets.UTF_8);
        byte[] assetBytes = "hello".getBytes(StandardCharsets.UTF_8);
        String clientHash = sha1Hex(clientBytes);
        String libraryHash = sha1Hex(libraryBytes);
        String assetHash = sha1Hex(assetBytes);
        String assetIndexJson = "{\"virtual\":false,\"objects\":{\"demo.txt\":{\"hash\":\""
                + assetHash + "\",\"size\":" + assetBytes.length + "}}}";
        byte[] assetIndexBytes = assetIndexJson.getBytes(StandardCharsets.UTF_8);
        String assetIndexHash = sha1Hex(assetIndexBytes);
        String metadataUrlPathHash = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
        URI versionMetadataUrl = URI.create("https://piston-meta.mojang.com/v1/packages/"
                + metadataUrlPathHash + "/1.21.4.json");
        URI assetIndexUrl = URI.create("https://piston-meta.mojang.com/v1/packages/"
                + assetIndexHash + "/1.21.4.json");
        String versionJson = "{\"id\":\"1.21.4\",\"mainClass\":\"net.minecraft.client.main.Main\","
                + "\"assets\":\"1.21.4\",\"assetIndex\":{\"id\":\"1.21.4\",\"url\":\"" + assetIndexUrl
                + "\",\"sha1\":\"" + assetIndexHash + "\",\"size\":" + assetIndexBytes.length
                + ",\"totalSize\":" + assetBytes.length + "},"
                + "\"downloads\":{\"client\":{\"url\":\"https://piston-data.mojang.com/v1/objects/"
                + clientHash + "/client.jar\",\"sha1\":\"" + clientHash + "\",\"size\":" + clientBytes.length + "}},"
                + "\"libraries\":[{\"name\":\"org.demo:lib:1.0\",\"downloads\":{\"artifact\":{\"path\":"
                + "\"org/demo/lib/1.0/lib-1.0.jar\",\"url\":\"https://libraries.minecraft.net/org/demo/lib.jar\","
                + "\"sha1\":\"" + libraryHash + "\",\"size\":" + libraryBytes.length + "}}}]}";
        byte[] versionJsonBytes = versionJson.getBytes(StandardCharsets.UTF_8);
        MinecraftVersion selected = new MinecraftVersion("1.21.4", "release", versionMetadataUrl, time1);
        VersionManifestDownloader metadataNetwork = new VersionManifestDownloader(uri -> {
            if (uri.equals(versionMetadataUrl)) return new Fake(200, versionJsonBytes, versionJsonBytes.length);
            if (uri.equals(assetIndexUrl)) return new Fake(200, assetIndexBytes, assetIndexBytes.length);
            throw new IOException("Unexpected metadata URL in test: " + uri);
        });

        Map<String, byte[]> payloads = new LinkedHashMap<>();
        URI clientUrl = URI.create("https://piston-data.mojang.com/v1/objects/" + clientHash + "/client.jar");
        URI libraryUrl = URI.create("https://libraries.minecraft.net/org/demo/lib.jar");
        URI assetUrl = URI.create("https://resources.download.minecraft.net/" + assetHash.substring(0, 2) + "/" + assetHash);
        payloads.put(clientUrl.toString(), clientBytes);
        payloads.put(libraryUrl.toString(), libraryBytes);
        payloads.put(assetUrl.toString(), assetBytes);
        final int[] transferOpenCount = {0};
        VerifiedFileDownloader files = new VerifiedFileDownloader(uri -> {
            byte[] payload = payloads.get(uri.toString());
            if (payload == null) throw new IOException("Unexpected file URL in test: " + uri);
            transferOpenCount[0]++;
            return new FakeFileConnection(200, payload, payload.length);
        });
        InstallationService installer = new InstallationService(new VersionDetailsDownloader(metadataNetwork),
                new AssetIndexDownloader(metadataNetwork), files);
        Path temporary = Files.createTempDirectory("mylauncher-installer-tests-");
        try {
            Path gameDirectory = temporary.resolve("game");
            List<InstallationProgress> progress = new java.util.ArrayList<>();
            InstallationResult result = installer.install(selected, gameDirectory,
                    new LibraryPlatform("linux", "aarch64", "6.1", java.util.Collections.emptyMap()),
                    CancellationToken.NONE, progress::add);
            eq("1.21.4", result.getVersionId(), "installer result version retained");
            eq(3, result.getPlan().getTaskCount(), "installer executes planned client/library/asset tasks");
            eq(3, result.getDownloadedTaskCount(), "all planned files downloaded on first run");
            eq(0, result.getCachedTaskCount(), "first install contains no cached task hits");
            eq(transferOpenCount[0], 3, "exact planned file count requested");
            eq(14L, result.getPlan().getTotalBytes(), "planned install size includes each unique payload");
            eq("client", new String(Files.readAllBytes(gameDirectory.resolve("versions/1.21.4/1.21.4.jar")), StandardCharsets.UTF_8),
                    "client JAR stored at version path");
            eq("lib", new String(Files.readAllBytes(gameDirectory.resolve("libraries/org/demo/lib/1.0/lib-1.0.jar")), StandardCharsets.UTF_8),
                    "library stored at Maven path");
            eq("hello", new String(Files.readAllBytes(gameDirectory.resolve("assets/objects/" + assetHash.substring(0, 2) + "/" + assetHash)), StandardCharsets.UTF_8),
                    "asset object stored at content-addressed cache path");
            eq(assetIndexJson, new String(Files.readAllBytes(result.getAssetIndexPath()), StandardCharsets.UTF_8),
                    "verified asset index JSON persisted under assets/indexes");
            yes(progress.get(progress.size() - 1).getStage() == InstallationProgress.Stage.COMPLETED,
                    "successful installation ends with completed progress");
            eq(100, progress.get(progress.size() - 1).getPercent(), "completed progress is 100 percent");
            for (int i = 1; i < progress.size(); i++) {
                yes(progress.get(i).getCompletedBytes() >= progress.get(i - 1).getCompletedBytes(),
                        "progress byte count is monotonic");
            }

            InstallationService cacheInstaller = new InstallationService(new VersionDetailsDownloader(metadataNetwork),
                    new AssetIndexDownloader(metadataNetwork),
                    new VerifiedFileDownloader(uri -> { throw new AssertionError("verified cache should avoid file network request"); }));
            InstallationResult cached = cacheInstaller.install(selected, gameDirectory,
                    new LibraryPlatform("linux", "aarch64", "6.1", java.util.Collections.emptyMap()));
            eq(0, cached.getDownloadedTaskCount(), "second install makes no duplicate downloads");
            eq(3, cached.getCachedTaskCount(), "second install reuses every verified file");
            yes(Files.isRegularFile(cached.getAssetIndexPath()), "verified asset index remains available");

            Path cancelledDirectory = temporary.resolve("cancelled-game");
            final boolean[] cancelled = {false};
            final boolean[] sawCompletedTask = {false};
            InstallationService cancellableInstaller = new InstallationService(new VersionDetailsDownloader(metadataNetwork),
                    new AssetIndexDownloader(metadataNetwork), new VerifiedFileDownloader(uri -> {
                        byte[] payload = payloads.get(uri.toString());
                        if (payload == null) throw new IOException("Unexpected file URL in cancellation test: " + uri);
                        return new FakeFileConnection(200, payload, payload.length);
                    }));
            try {
                cancellableInstaller.install(selected, cancelledDirectory,
                        new LibraryPlatform("linux", "aarch64", "6.1", java.util.Collections.emptyMap()),
                        () -> cancelled[0], snapshot -> {
                            if (snapshot.getStage() == InstallationProgress.Stage.DOWNLOADING
                                    && snapshot.getCompletedTasks() >= 1) {
                                cancelled[0] = true;
                                sawCompletedTask[0] = true;
                            }
                        });
                throw new AssertionError("cancellation should stop before the next task");
            } catch (java.io.InterruptedIOException expected) {
                checks++;
            }
            yes(sawCompletedTask[0], "cancellation fixture stopped after one complete task");
            int downloadedBeforeCancellation = 0;
            try (java.util.stream.Stream<Path> paths = Files.walk(cancelledDirectory)) {
                downloadedBeforeCancellation = (int) paths.filter(Files::isRegularFile)
                        .filter(path -> !path.toString().endsWith(".json")).count();
            }
            eq(1, downloadedBeforeCancellation, "cancelled installation leaves only completed verified task and no partial files");
        } finally {
            try (java.util.stream.Stream<Path> paths = Files.walk(temporary)) {
                paths.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                    try { Files.deleteIfExists(path); } catch (IOException exception) { throw new java.io.UncheckedIOException(exception); }
                });
            }
        }
    }

    private static void nativeInstallationService() throws Exception {
        byte[] clientBytes = "client".getBytes(StandardCharsets.UTF_8);
        byte[] libraryBytes = "library".getBytes(StandardCharsets.UTF_8);
        byte[] nativeBytes = makeZip(new String[][] {
                {"META-INF/", ""}, {"META-INF/MANIFEST.MF", "metadata"},
                {"lib/", ""}, {"lib/libtest.so", "native"}
        });
        String clientHash = sha1Hex(clientBytes);
        String libraryHash = sha1Hex(libraryBytes);
        String nativeHash = sha1Hex(nativeBytes);
        byte[] assetIndexBytes = "{\"objects\":{}}".getBytes(StandardCharsets.UTF_8);
        String assetIndexHash = sha1Hex(assetIndexBytes);
        URI assetIndexUrl = URI.create("https://piston-meta.mojang.com/v1/packages/"
                + assetIndexHash + "/1.21.4.json");
        String versionHash = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
        URI versionUrl = URI.create("https://piston-meta.mojang.com/v1/packages/"
                + versionHash + "/1.21.4.json");
        URI clientUrl = URI.create("https://piston-data.mojang.com/v1/objects/" + clientHash + "/client.jar");
        URI libraryUrl = URI.create("https://libraries.minecraft.net/org/demo/lib.jar");
        URI nativeUrl = URI.create("https://libraries.minecraft.net/org/demo/native.jar");

        String versionJson = "{\"id\":\"1.21.4\",\"mainClass\":\"net.minecraft.client.main.Main\","
                + "\"assets\":\"1.21.4\",\"assetIndex\":{\"id\":\"1.21.4\",\"url\":\"" + assetIndexUrl
                + "\",\"sha1\":\"" + assetIndexHash + "\",\"size\":" + assetIndexBytes.length + "},"
                + "\"downloads\":{\"client\":{\"url\":\"" + clientUrl + "\",\"sha1\":\"" + clientHash
                + "\",\"size\":" + clientBytes.length + "}},"
                + "\"libraries\":["
                + "{\"name\":\"org.demo:lib:1.0\",\"downloads\":{\"artifact\":{\"path\":"
                + "\"org/demo/lib/1.0/lib-1.0.jar\",\"url\":\"" + libraryUrl + "\",\"sha1\":\""
                + libraryHash + "\",\"size\":" + libraryBytes.length + "}}},"
                + "{\"name\":\"org.demo:native:1.0\",\"downloads\":{\"classifiers\":{\"natives-linux-arm64\":{"
                + "\"path\":\"org/demo/native/1.0/native-1.0-natives.jar\",\"url\":\"" + nativeUrl
                + "\",\"sha1\":\"" + nativeHash + "\",\"size\":" + nativeBytes.length + "}}},"
                + "\"natives\":{\"linux\":\"natives-linux-@@ARCH@@\"},"
                + "\"rules\":[{\"action\":\"allow\",\"os\":{\"name\":\"linux\",\"arch\":\"aarch64\"}}],"
                + "\"extract\":{\"exclude\":[\"META-INF/\"]}}]}";
        versionJson = versionJson.replace("natives-linux-@@ARCH@@", "natives-linux-" + "$" + "{arch}");
        byte[] versionBytes = versionJson.getBytes(StandardCharsets.UTF_8);
        MinecraftVersion selected = new MinecraftVersion("1.21.4", "release", versionUrl, time1);
        VersionManifestDownloader metadataNetwork = new VersionManifestDownloader(uri -> {
            if (uri.equals(versionUrl)) return new Fake(200, versionBytes, versionBytes.length);
            if (uri.equals(assetIndexUrl)) return new Fake(200, assetIndexBytes, assetIndexBytes.length);
            throw new IOException("Unexpected native-install metadata URL: " + uri);
        });
        Map<String, byte[]> payloads = new LinkedHashMap<>();
        payloads.put(clientUrl.toString(), clientBytes);
        payloads.put(libraryUrl.toString(), libraryBytes);
        payloads.put(nativeUrl.toString(), nativeBytes);
        VerifiedFileDownloader fileNetwork = new VerifiedFileDownloader(uri -> {
            byte[] payload = payloads.get(uri.toString());
            if (payload == null) throw new IOException("Unexpected native-install file URL: " + uri);
            return new FakeFileConnection(200, payload, payload.length);
        });
        InstallationService installer = new InstallationService(new VersionDetailsDownloader(metadataNetwork),
                new AssetIndexDownloader(metadataNetwork), fileNetwork);
        VersionDetails details = VersionDetailsJsonDecoder.decode(versionJson, "1.21.4");
        LibraryPlatform platform = new LibraryPlatform("linux", "aarch64", "6.1.0",
                java.util.Collections.emptyMap());
        NativeLibraryPlan nativePlan = NativeLibraryPlanner.create(details, platform, "linux", "arm64");

        Path temporary = Files.createTempDirectory("mylauncher-native-installer-");
        try {
            Path game = temporary.resolve("game");
            List<InstallationProgress> snapshots = new java.util.ArrayList<>();
            InstallationResult result = installer.install(selected, game, platform, nativePlan,
                    CancellationToken.NONE, snapshots::add);
            eq(3, result.getPlan().getTaskCount(), "installer plan includes client, library and native archive");
            eq(1, result.getPlan().count(DownloadTask.Kind.NATIVE), "installer download task is classified native");
            eq(3, result.getDownloadedTaskCount(), "all native-install files downloaded and verified");
            eq(0, result.getCachedTaskCount(), "first native install starts with an empty cache");
            yes(snapshots.stream().anyMatch(snapshot ->
                    snapshot.getStage() == InstallationProgress.Stage.EXTRACTING_NATIVES),
                    "installer reports the native extraction stage");
            Path extracted = game.resolve("natives/1.21.4/lib/libtest.so");
            yes(Files.isRegularFile(extracted), "native archive extracted to version-specific directory");
            eq("native", new String(Files.readAllBytes(extracted), StandardCharsets.UTF_8),
                    "native bytes extracted correctly");
            yes(!Files.exists(game.resolve("natives/1.21.4/META-INF/MANIFEST.MF")),
                    "native extraction exclusions applied during install");
            yes(VerifiedFileDownloader.isVerifiedFile(
                    game.resolve("libraries/org/demo/native/1.0/native-1.0-natives.jar"),
                    nativeBytes.length, nativeHash), "native archive remains checksum verified in library cache");
        } finally {
            try (java.util.stream.Stream<Path> paths = Files.walk(temporary)) {
                paths.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                    try { Files.deleteIfExists(path); }
                    catch (IOException exception) { throw new java.io.UncheckedIOException(exception); }
                });
            }
        }
    }

    private static void verifiedDownloads() throws Exception {
        String helloSha1 = "aaf4c61ddcc5e8a2dabede0f3b482cd9aea9434d";
        URI source = URI.create("https://resources.download.minecraft.net/aa/sample");
        Path temp = Files.createTempDirectory("mylauncher-download-tests-");
        try {
            Path root = temp.resolve("cache");
            FakeFileConnection good = new FakeFileConnection(200, "hello".getBytes(StandardCharsets.UTF_8), 5);
            VerifiedFileDownloader downloader = new VerifiedFileDownloader(uri -> {
                eq(source, uri, "verified downloader uses approved source");
                return good;
            });
            VerifiedFileDownloader.DownloadResult result = downloader.download(source, root, "objects/aa/sample", 5, helloSha1);
            eq(5L, result.getSizeBytes(), "verified download size returned");
            yes(!result.isFromCache() && Files.isRegularFile(result.getPath()), "verified new file published");
            eq("hello", new String(Files.readAllBytes(result.getPath()), StandardCharsets.UTF_8), "verified bytes stored");
            eq(10_000, good.connectTimeout, "file connect timeout configured");
            eq(30_000, good.readTimeout, "file read timeout configured");
            eq("GET", good.method, "file request uses GET");
            eq("application/octet-stream", good.headers.get("Accept"), "binary Accept header configured");
            yes(!good.followRedirects && good.disconnected, "file redirects disabled and connection closed");

            VerifiedFileDownloader cacheOnly = new VerifiedFileDownloader(uri -> { throw new AssertionError("verified cached file must not be fetched again"); });
            VerifiedFileDownloader.DownloadResult cached = cacheOnly.download(source, root, "objects/aa/sample", 5, helloSha1);
            yes(cached.isFromCache(), "verified cache hit detected");

            FakeFileConnection badHash = new FakeFileConnection(200, "jello".getBytes(StandardCharsets.UTF_8), 5);
            io(() -> new VerifiedFileDownloader(uri -> badHash).download(source, root, "objects/aa/bad-hash", 5, helloSha1), "bad file hash rejected");
            yes(badHash.disconnected && !Files.exists(root.resolve("objects/aa/bad-hash")), "bad hash not published");

            FakeFileConnection badLength = new FakeFileConnection(200, "hello".getBytes(StandardCharsets.UTF_8), 6);
            io(() -> new VerifiedFileDownloader(uri -> badLength).download(source, root, "objects/aa/bad-length", 5, helloSha1), "declared size mismatch rejected");
            FakeFileConnection oversized = new FakeFileConnection(200, "hello!".getBytes(StandardCharsets.UTF_8), -1);
            io(() -> new VerifiedFileDownloader(uri -> oversized).download(source, root, "objects/aa/oversized", 5, helloSha1), "stream larger than expected rejected");
            FakeFileConnection truncated = new FakeFileConnection(200, "hell".getBytes(StandardCharsets.UTF_8), -1);
            io(() -> new VerifiedFileDownloader(uri -> truncated).download(source, root, "objects/aa/truncated", 5, helloSha1), "stream smaller than expected rejected");
            final boolean[] cancelCurrentTransfer = {false};
            FakeFileConnection cancelledConnection = new FakeFileConnection(200, "hello".getBytes(StandardCharsets.UTF_8), 5);
            io(() -> new VerifiedFileDownloader(uri -> cancelledConnection).download(
                    source, root, "objects/aa/cancelled", 5, helloSha1,
                    () -> cancelCurrentTransfer[0], bytes -> cancelCurrentTransfer[0] = true),
                    "cancellation during an active file transfer stops the download");
            yes(cancelledConnection.disconnected && !Files.exists(root.resolve("objects/aa/cancelled")),
                    "cancelled transfer closes connection and never publishes the partial file");
            FakeFileConnection unavailable = new FakeFileConnection(503, new byte[0], -1);
            io(() -> new VerifiedFileDownloader(uri -> unavailable).download(source, root, "objects/aa/unavailable", 5, helloSha1), "HTTP failure rejected");
            yes(!Files.exists(root.resolve("objects/aa/oversized")) && !Files.exists(root.resolve("objects/aa/truncated")), "invalid streams are not published");
            yes(unavailable.disconnected, "failed HTTP connection closed");

            Path corruptedCache = root.resolve("objects/aa/sample");
            Files.write(corruptedCache, "jello".getBytes(StandardCharsets.UTF_8));
            FakeFileConnection repair = new FakeFileConnection(200, "hello".getBytes(StandardCharsets.UTF_8), 5);
            VerifiedFileDownloader.DownloadResult repaired = new VerifiedFileDownloader(uri -> repair).download(source, root, "objects/aa/sample", 5, helloSha1);
            yes(!repaired.isFromCache(), "corrupt cache entry replaced from network");
            eq("hello", new String(Files.readAllBytes(corruptedCache), StandardCharsets.UTF_8), "corrupt cached bytes repaired");

            String emptyHash = "da39a3ee5e6b4b0d3255bfef95601890afd80709";
            FakeFileConnection empty = new FakeFileConnection(200, new byte[0], 0);
            VerifiedFileDownloader.DownloadResult emptyResult = new VerifiedFileDownloader(uri -> empty).download(
                    source, root, "objects/empty", 0, emptyHash);
            yes(Files.exists(emptyResult.getPath()) && Files.size(emptyResult.getPath()) == 0, "zero-byte asset downloaded and verified");

            io(() -> new VerifiedFileDownloader(uri -> { throw new AssertionError("unapproved host must be blocked before opener"); })
                    .download(URI.create("https://evil.example/hello"), root, "objects/evil", 5, helloSha1), "unapproved host rejected before networking");
            io(() -> new VerifiedFileDownloader(uri -> { throw new AssertionError("HTTP URL must be blocked before opener"); })
                    .download(URI.create("http://resources.download.minecraft.net/hello"), root, "objects/http", 5, helloSha1), "plain HTTP rejected");
            yes(!VerifiedFileDownloader.isApprovedDownloadUri(URI.create("https://resources.download.minecraft.net.evil.example/hello")), "lookalike CDN hostname rejected");
            yes(!VerifiedFileDownloader.isApprovedDownloadUri(URI.create("https://resources.download.minecraft.net:444/hello")), "non-default TLS port rejected");
            yes(!VerifiedFileDownloader.isApprovedDownloadUri(URI.create("https://user@resources.download.minecraft.net/hello")), "URL user info rejected");
            bad(() -> {
                try { downloader.download(source, root, "../escape", 5, helloSha1); }
                catch (IOException exception) { throw new AssertionError("path traversal should be rejected before network", exception); }
            }, "parent traversal destination rejected");
            bad(() -> {
                try { downloader.download(source, root, "assets//broken", 5, helloSha1); }
                catch (IOException exception) { throw new AssertionError("empty path component should be rejected before network", exception); }
            }, "empty path component rejected");

            Path outside = temp.resolve("outside"); Files.createDirectories(outside);
            Path outsideFile = outside.resolve("sentinel"); Files.write(outsideFile, new byte[] {1});
            Path linkRoot = temp.resolve("linked-cache"); Files.createDirectories(linkRoot);
            try {
                Files.createSymbolicLink(linkRoot.resolve("jump"), outside);
                io(() -> new VerifiedFileDownloader(uri -> { throw new AssertionError("symlink cache path must be rejected before networking"); })
                        .download(source, linkRoot, "jump/sentinel", 1, "bf8b4530d8d246dd74ac53a13471bba17941dff7"), "symbolic-link parent rejected");
                eq(1L, Files.size(outsideFile), "outside file not modified through symlink");
            } catch (UnsupportedOperationException | SecurityException exception) {
                // Some filesystems do not permit symlink creation; traversal checks above still run.
            }
            try (java.util.stream.Stream<Path> files = Files.walk(root)) {
                eq(0L, files.filter(path -> path.getFileName().toString().endsWith(".part")).count(), "temporary partial files cleaned up");
            }
        } finally {
            try (java.util.stream.Stream<Path> paths = Files.walk(temp)) {
                paths.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                    try { Files.deleteIfExists(path); } catch (IOException exception) { throw new java.io.UncheckedIOException(exception); }
                });
            }
        }
    }

    private static final class FakeFileConnection implements VerifiedFileDownloader.FileConnection {
        final int status;
        final byte[] body;
        final long length;
        int connectTimeout, readTimeout;
        boolean followRedirects = true, disconnected;
        String method;
        final Map<String, String> headers = new LinkedHashMap<>();
        FakeFileConnection(int status, byte[] body, long length) { this.status = status; this.body = body; this.length = length; }
        public void setConnectTimeout(int value) { connectTimeout = value; }
        public void setReadTimeout(int value) { readTimeout = value; }
        public void setInstanceFollowRedirects(boolean value) { followRedirects = value; }
        public void setRequestMethod(String value) { method = value; }
        public void setRequestProperty(String key, String value) { headers.put(key, value); }
        public int getResponseCode() { return status; }
        public long getContentLengthLong() { return length; }
        public InputStream getInputStream() throws IOException {
            if (status != 200) throw new IOException("HTTP " + status);
            return new ByteArrayInputStream(body);
        }
        public void disconnect() { disconnected = true; }
    }

    private static void javaProcessRunner() throws Exception {
        Path temporary = Files.createTempDirectory("mylauncher-process-tests-");
        try {
            Path working = Files.createDirectories(temporary.resolve("work"));
            Path javaExecutable = java.nio.file.Paths.get(System.getProperty("java.home"), "bin", "java").toRealPath();
            JavaProcessRunner runner = new JavaProcessRunner();
            List<String> output = java.util.Collections.synchronizedList(new java.util.ArrayList<>());
            JavaProcessRunner.Result result = runner.run(javaExecutable, working, java.util.Collections.singletonList("-version"),
                    CancellationToken.NONE, 15_000, (stream, line) -> output.add(stream + ":" + line));
            yes(result.isSuccess(), "Java process exits successfully");
            yes(result.getElapsedMillis() >= 0, "process duration reported");
            Path linkedWorkingParent = temporary.resolve("linked-working-parent");
            try {
                Files.createSymbolicLink(linkedWorkingParent, temporary);
                io(() -> runner.run(javaExecutable, linkedWorkingParent.resolve("work"),
                        java.util.Collections.singletonList("-version"), CancellationToken.NONE, 15_000, null),
                        "symlinked ancestor of Java working directory rejected");
            } catch (UnsupportedOperationException | SecurityException | java.nio.file.FileSystemException exception) {
                // Some filesystems disable symbolic links for ordinary users.
            }
            Path linkedExecutable = temporary.resolve("linked-java");
            try {
                Files.createSymbolicLink(linkedExecutable, javaExecutable);
                io(() -> runner.run(linkedExecutable, working, java.util.Collections.singletonList("-version"),
                        CancellationToken.NONE, 15_000, null),
                        "symlinked Java executable rejected before process start");
            } catch (UnsupportedOperationException | SecurityException
                     | java.nio.file.FileSystemException exception) {
                // Some filesystems disable symlink creation for ordinary users.
            }
            yes(result.getStderrLineCount() > 0, "Java stderr is drained");
            yes(!output.isEmpty() && output.get(0).contains("STDERR"), "process output listener receives stream identity");

            List<String> sleeper = Arrays.asList("-cp", System.getProperty("java.class.path"),
                    CoreTests.SleeperMain.class.getName(), "10000");
            AtomicBoolean cancel = new AtomicBoolean(false);
            Thread cancelThread = new Thread(() -> {
                try { Thread.sleep(350); } catch (InterruptedException exception) { Thread.currentThread().interrupt(); }
                cancel.set(true);
            });
            cancelThread.setDaemon(true);
            cancelThread.start();
            try {
                runner.run(javaExecutable, working, sleeper, cancel::get, 15_000, null);
                throw new AssertionError("cancelled Java process should not report success");
            } catch (InterruptedIOException expected) {
                checks++;
                yes(expected.getMessage().contains("cancelled"), "process cancellation is reported clearly");
            } finally {
                cancelThread.join(2_000);
            }

            try {
                runner.run(javaExecutable, working, sleeper, CancellationToken.NONE, 300, null);
                throw new AssertionError("timed-out Java process should not report success");
            } catch (IOException expected) {
                checks++;
                yes(expected.getMessage().contains("time limit"), "process timeout is reported clearly");
            }

            bad(() -> {
                try { runner.run(javaExecutable, working, Arrays.asList("-Dx=" + (char) 0),
                        CancellationToken.NONE, 1_000, null); }
                catch (IOException | InterruptedException exception) {
                    throw new AssertionError("invalid argv should be rejected before process start", exception);
                }
            }, "NUL in process argv rejected");
            bad(() -> {
                try { runner.run(javaExecutable, working, Arrays.asList("-version", null),
                        CancellationToken.NONE, 1_000, null); }
                catch (IOException | InterruptedException exception) {
                    throw new AssertionError("null argv should be rejected before process start", exception);
                }
            }, "null process argv rejected");
            bad(() -> {
                try { runner.run(javaExecutable, working, Arrays.asList("-version"),
                        CancellationToken.NONE, 0, null); }
                catch (IOException | InterruptedException exception) {
                    throw new AssertionError("invalid timeout should fail before process start", exception);
                }
            }, "zero timeout rejected");
            yes(!Files.isSymbolicLink(working), "runner fixture uses a regular working directory");
        } finally {
            try (java.util.stream.Stream<Path> paths = Files.walk(temporary)) {
                paths.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                    try { Files.deleteIfExists(path); } catch (IOException exception) { throw new java.io.UncheckedIOException(exception); }
                });
            }
        }
    }

    /** Child process used to verify active-process cancellation and timeout handling. */
    public static final class SleeperMain {
        private SleeperMain() { }
        public static void main(String[] args) throws InterruptedException {
            System.out.println("child started");
            long duration = Long.parseLong(args[0]);
            Thread.sleep(duration);
            System.out.println("child finished");
        }
    }

    private static void downloader() {
        String body = "{\"latest\":{\"release\":\"1.21.4\",\"snapshot\":\"25w01a\"},"
                + "\"versions\":[{\"id\":\"1.21.4\",\"type\":\"release\","
                + "\"url\":\"https://piston-meta.mojang.com/versions/1.21.4.json\","
                + "\"releaseTime\":\"2024-12-03T10:00:00Z\"},{"
                + "\"id\":\"25w01a\",\"type\":\"snapshot\","
                + "\"url\":\"https://piston-meta.mojang.com/versions/25w01a.json\","
                + "\"releaseTime\":\"2025-01-01T10:00:00Z\"}]}";
        Fake ok = new Fake(200, body.getBytes(StandardCharsets.UTF_8), -1);
        VersionManifestDownloader d = new VersionManifestDownloader(uri -> { eq(VersionManifestDownloader.MANIFEST_URI, uri, "fixed Mojang URL"); return ok; });
        try {
            VersionManifest downloaded = d.downloadManifest();
            eq("1.21.4", downloaded.getLatestRelease().getId(), "HTTP download decodes directly to a validated manifest");
            eq(2, downloaded.getCatalog().size(), "HTTP manifest version list is populated");
        } catch (IOException ex) { throw new AssertionError(ex); }
        eq(10_000, ok.connectTimeout, "connect timeout"); eq(15_000, ok.readTimeout, "read timeout");
        eq("GET", ok.method, "GET used"); eq("application/json", ok.headers.get("Accept"), "JSON Accept header");
        yes(!ok.followRedirects && ok.disconnected, "redirects disabled and connection closed");
        Fake status = new Fake(503, new byte[0], -1); io(() -> new VersionManifestDownloader(uri -> status).download(), "HTTP error rejected");
        Fake length = new Fake(200, new byte[0], VersionManifestDownloader.MAX_MANIFEST_BYTES + 1L); io(() -> new VersionManifestDownloader(uri -> length).download(), "large declared body rejected");
        byte[] oversized = new byte[VersionManifestDownloader.MAX_MANIFEST_BYTES + 1]; oversized[0] = '{';
        io(() -> new VersionManifestDownloader(uri -> new Fake(200, oversized, -1)).download(), "large streamed body rejected");
        io(() -> new VersionManifestDownloader(uri -> new Fake(200, new byte[0], 0)).download(), "empty body rejected");
        io(() -> new VersionManifestDownloader(uri -> new Fake(200, "gateway error".getBytes(StandardCharsets.UTF_8), -1)).download(), "non-JSON body rejected");
        io(() -> new VersionManifestDownloader(uri -> new Fake(200, new byte[] {(byte)0xC3, (byte)0x28}, -1)).download(), "malformed UTF-8 rejected");
        io(() -> new VersionManifestDownloader(uri -> null).download(), "null connection rejected");
        yes(status.disconnected && length.disconnected, "connections closed after errors");
    }

    private static void profiles() {
        yes(OfflineProfileValidator.isValid("Steve_123"), "valid profile accepted");
        yes(OfflineProfileValidator.isValid("abc") && OfflineProfileValidator.isValid("abcdefghijklmnop"), "length boundaries accepted");
        for (String s : new String[] {null, "ab", "abcdefghijklmnopq", "Bad Name", "éclair", "player-name"})
            yes(!OfflineProfileValidator.isValid(s), "invalid profile rejected: " + s);
        eq("Enter a username.", OfflineProfileValidator.validationMessage(""), "empty-name message");
        eq("", OfflineProfileValidator.validationMessage("Player_1"), "valid-name message empty");
    }

    private static void memory() {
        eq(296, MemoryDefaults.suggestedHeapMiB(768), "sub-1-GiB heap");
        eq(448, MemoryDefaults.suggestedHeapMiB(1024), "1-GiB heap");
        eq(448, MemoryDefaults.suggestedHeapMiB(1535), "1.5-GiB boundary below");
        eq(512, MemoryDefaults.suggestedHeapMiB(1536), "1.5-GiB heap");
        eq(512, MemoryDefaults.suggestedHeapMiB(2048), "2-GiB heap");
        eq(768, MemoryDefaults.suggestedHeapMiB(3072), "higher-RAM conservative heap");
        eq(1024, MemoryDefaults.suggestedHeapMiB(8192), "heap suggestion maximum");
        eq(384, MemoryDefaults.capToRuntimeMaximum(512, 384), "heap capped to runtime maximum");
        bad(() -> MemoryDefaults.suggestedHeapMiB(0), "zero RAM rejected");
        bad(() -> MemoryDefaults.capToRuntimeMaximum(512, 0), "zero runtime heap rejected");
    }

    private static void installationProgress() {
        eq(50, new InstallationProgress(InstallationProgress.Stage.DOWNLOADING, null,
                0, 0, Long.MAX_VALUE / 2, Long.MAX_VALUE).getPercent(),
                "download percentage does not overflow on large metadata byte totals");
        eq(99, new InstallationProgress(InstallationProgress.Stage.DOWNLOADING, null,
                0, 0, Long.MAX_VALUE - 1, Long.MAX_VALUE).getPercent(),
                "almost-complete huge download reports 99 percent");
        eq(33, new InstallationProgress(InstallationProgress.Stage.DOWNLOADING, null,
                1, 3, 0, 0).getPercent(), "task-based progress works when total bytes are zero");
        eq(100, new InstallationProgress(InstallationProgress.Stage.COMPLETED, null,
                0, 0, 0, 0).getPercent(), "completed installation always reports 100 percent");
    }

    private static MinecraftVersion version(String id, String type, String time) {
        return new MinecraftVersion(id, type, URI.create("https://piston-meta.mojang.com/versions/" + id + ".json"), time);
    }
    private static Map<String,Object> manifestObject() {
        Map<String,Object> root = new LinkedHashMap<>(), latest = new LinkedHashMap<>();
        latest.put("release", "1.21.4"); latest.put("snapshot", "25w01a"); root.put("latest", latest);
        Map<String,Object> release = new LinkedHashMap<>(), snapshot = new LinkedHashMap<>();
        release.put("id", "1.21.4"); release.put("type", "release"); release.put("url", "https://piston-meta.mojang.com/versions/1.21.4.json"); release.put("releaseTime", time1);
        snapshot.put("id", "25w01a"); snapshot.put("type", "snapshot"); snapshot.put("url", "https://piston-meta.mojang.com/versions/25w01a.json"); snapshot.put("releaseTime", "2025-01-01T10:00:00Z");
        root.put("versions", Arrays.asList(release, snapshot)); return root;
    }
    private static final class Fake implements VersionManifestDownloader.ManifestConnection {
        final int status; final byte[] body; final long length; int connectTimeout, readTimeout; boolean followRedirects = true, disconnected; String method; final Map<String,String> headers = new LinkedHashMap<>();
        Fake(int status, byte[] body, long length) { this.status = status; this.body = body; this.length = length; }
        public void setConnectTimeout(int n) { connectTimeout = n; }
        public void setReadTimeout(int n) { readTimeout = n; }
        public void setInstanceFollowRedirects(boolean b) { followRedirects = b; }
        public void setRequestMethod(String s) { method = s; }
        public void setRequestProperty(String k, String v) { headers.put(k, v); }
        public int getResponseCode() { return status; }
        public long getContentLengthLong() { return length; }
        public InputStream getInputStream() throws IOException { if (status != 200) throw new IOException("HTTP " + status); return new ByteArrayInputStream(body); }
        public void disconnect() { disconnected = true; }
    }
    @FunctionalInterface private interface Io { void run() throws IOException; }
    private static void io(Io op, String message) { checks++; try { op.run(); } catch (IOException expected) { return; } throw new AssertionError(message); }
    private static void bad(Runnable op, String message) { checks++; try { op.run(); } catch (IllegalArgumentException expected) { return; } throw new AssertionError(message); }
    private static void unsupported(Runnable op, String message) { checks++; try { op.run(); } catch (UnsupportedOperationException expected) { return; } throw new AssertionError(message); }
    private static void eq(Object expected, Object actual, String message) { checks++; if (expected == null ? actual != null : !expected.equals(actual)) throw new AssertionError(message + ": expected=" + expected + " actual=" + actual); }
    private static void yes(boolean ok, String message) { checks++; if (!ok) throw new AssertionError(message); }
}
